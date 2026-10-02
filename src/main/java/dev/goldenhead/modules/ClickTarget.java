package dev.goldenhead.modules;

import dev.goldenhead.utils.BlockScan;
import dev.goldenhead.utils.Clicker;
import dev.goldenhead.utils.Mover;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared engine for event objectives you have to click: find the closest target (block or entity), get there
 * with Baritone (or by running straight), lock on and spam clicks at a random interval. Clicks run per rendered
 * frame, not per tick, so intervals under 50 ms (one tick) actually happen.
 */
public abstract class ClickTarget extends Module {
    public enum ClickMode { Left, Right, Both }

    protected final SettingGroup sgGeneral = settings.getDefaultGroup();
    protected final SettingGroup sgMove = settings.createGroup("Movement");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away to look for the target.")
        .defaultValue(48)
        .range(4, 128)
        .sliderRange(8, 96)
        .build()
    );

    private final Setting<ClickMode> clickMode = sgGeneral.add(new EnumSetting.Builder<ClickMode>()
        .name("click")
        .description("Which click the server counts. Both sends a left and a right click each time.")
        .defaultValue(ClickMode.Both)
        .build()
    );

    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder()
        .name("min-delay-ms")
        .description("Shortest time between clicks.")
        .defaultValue(20)
        .range(1, 5000)
        .sliderRange(1, 500)
        .build()
    );

    private final Setting<Integer> maxDelay = sgGeneral.add(new IntSetting.Builder()
        .name("max-delay-ms")
        .description("Longest time between clicks. Each wait is random between min and max.")
        .defaultValue(100)
        .range(1, 5000)
        .sliderRange(1, 500)
        .build()
    );

    private final Setting<Double> reach = sgGeneral.add(new DoubleSetting.Builder()
        .name("reach")
        .description("Click once the target's center is this close to your eyes.")
        .defaultValue(4.0)
        .range(1, 6)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing your arm on each click.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> rotationSpeed = sgMove.add(new DoubleSetting.Builder()
        .name("rotation-speed")
        .description("How fast to turn toward the target each tick. 1 = instant.")
        .defaultValue(0.6)
        .range(0.05, 1)
        .build()
    );

    private final Setting<Boolean> run = sgMove.add(new BoolSetting.Builder()
        .name("run")
        .description("Move to the target until it is in reach.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pathfind = sgMove.add(new BoolSetting.Builder()
        .name("pathfind")
        .description("Use Baritone for the fastest route (no mining or placing, any drop height). Falls back to running straight if Baritone is missing or finds no path.")
        .defaultValue(true)
        .visible(run::get)
        .build()
    );

    private final Setting<Boolean> autoJump = sgMove.add(new BoolSetting.Builder()
        .name("auto-jump")
        .description("Jump when running into something (straight-line running only).")
        .defaultValue(true)
        .visible(run::get)
        .build()
    );

    protected BlockPos targetBlock;
    protected Entity targetEntity;
    private boolean inReach;
    private int scanTimer;
    private long nextClick;
    private final Mover mover = new Mover();
    private final Map<BlockPos, Integer> clicks = new HashMap<>();

    protected ClickTarget(Category category, String name, String description) {
        super(category, name, description);
    }

    /** Blocks worth scanning for; sections without any of them are skipped. */
    protected abstract Set<Block> candidateBlocks();

    /** Whether a candidate block at this position is an actual target. */
    protected boolean isTargetBlock(BlockPos pos) {
        return true;
    }

    protected boolean isTargetEntity(Entity entity) {
        return false;
    }

    /** Bonus (in squared blocks) that makes a block win over closer ones, e.g. 400 = worth 20 blocks of walking. */
    protected double weight(BlockPos pos) {
        return 0;
    }

    /** Stop clicking a block after this many clicks if it is still there (0 = no limit). */
    protected int clickLimit(BlockPos pos) {
        return 0;
    }

    /** Extra gate for clicking (e.g. not while a looting screen is open). */
    protected boolean canClick() {
        return mc.currentScreen == null;
    }

    protected void onClicked() {
    }

    @Override
    public void onActivate() {
        targetBlock = null;
        targetEntity = null;
        scanTimer = 0;
        nextClick = 0;
        inReach = false;
        clicks.clear();
    }

    @Override
    public void onDeactivate() {
        mover.release();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (--scanTimer <= 0) {
            scanTimer = 5;
            findTarget();
        }
        if (targetBlock != null && !(candidateBlocks().contains(mc.world.getBlockState(targetBlock).getBlock()) && isUsableBlock(targetBlock))) targetBlock = null;
        if (targetEntity != null && (targetEntity.isRemoved() || !isTargetEntity(targetEntity))) targetEntity = null;

        Vec3d aim = aimPoint();
        if (aim == null || mc.currentScreen != null) {
            inReach = false;
            mover.release();
            return;
        }

        inReach = mc.player.getEyePos().distanceTo(aim) <= reach.get();
        if (!run.get() || inReach) {
            mover.stop();
            mover.lookAt(aim, rotationSpeed.get());
            return;
        }

        mover.pathfind = pathfind.get();
        mover.autoJump = autoJump.get();
        mover.rotationSpeed = rotationSpeed.get();
        BlockPos goal = targetBlock != null ? targetBlock : targetEntity.getBlockPos();
        mover.tick(goal, Math.max(1, (int) Math.floor(reach.get()) - 1), aim);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!inReach || mc.player == null || !canClick()) return;
        Vec3d aim = aimPoint();
        if (aim == null || System.currentTimeMillis() < nextClick) return;

        click(aim);
        onClicked();
        int min = Math.min(minDelay.get(), maxDelay.get()), max = Math.max(minDelay.get(), maxDelay.get());
        nextClick = System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private Vec3d aimPoint() {
        if (targetBlock != null) return Vec3d.ofCenter(targetBlock);
        if (targetEntity != null) return targetEntity.getBoundingBox().getCenter();
        return null;
    }

    private void findTarget() {
        Vec3d eye = mc.player.getEyePos();
        double best = range.get() * range.get();
        targetBlock = null;
        targetEntity = null;

        for (BlockPos pos : BlockScan.find(candidateBlocks(), range.get() + 2)) {
            double d = eye.squaredDistanceTo(Vec3d.ofCenter(pos)) - weight(pos);
            if (d < best && isUsableBlock(pos)) {
                best = d;
                targetBlock = pos;
            }
        }

        for (Entity entity : mc.world.getEntities()) {
            if (!isTargetEntity(entity)) continue;
            double d = eye.squaredDistanceTo(entity.getBoundingBox().getCenter());
            if (d < best) {
                best = d;
                targetEntity = entity;
                targetBlock = null;
            }
        }
    }

    private boolean isUsableBlock(BlockPos pos) {
        int limit = clickLimit(pos);
        return isTargetBlock(pos) && (limit <= 0 || clicks.getOrDefault(pos, 0) < limit);
    }

    private void click(Vec3d aim) {
        boolean left = clickMode.get() != ClickMode.Right;
        boolean right = clickMode.get() != ClickMode.Left;

        if (targetEntity != null) Clicker.entity(targetEntity, left, right, swing.get());
        else {
            Clicker.block(targetBlock, left, right, swing.get());
            clicks.merge(targetBlock, 1, Integer::sum);
        }
    }

    @Override
    public String getInfoString() {
        Vec3d aim = aimPoint();
        return aim == null || mc.player == null ? null : String.format("%.0fm", mc.player.getEyePos().distanceTo(aim));
    }
}
