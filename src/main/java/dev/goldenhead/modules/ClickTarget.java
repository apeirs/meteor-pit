package dev.goldenhead.modules;

import dev.goldenhead.utils.BaritonePather;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

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
    private boolean moving;
    private boolean inReach;
    private int scanTimer;
    private int repathTimer;
    private long nextClick;
    private BaritonePather pather;

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
        repathTimer = 0;
        nextClick = 0;
        inReach = false;
    }

    @Override
    public void onDeactivate() {
        stopMoving();
        if (pather != null) pather.release();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (--scanTimer <= 0) {
            scanTimer = 5;
            findTarget();
        }
        if (targetBlock != null && !(candidateBlocks().contains(mc.world.getBlockState(targetBlock).getBlock()) && isTargetBlock(targetBlock))) targetBlock = null;
        if (targetEntity != null && (targetEntity.isRemoved() || !isTargetEntity(targetEntity))) targetEntity = null;

        Vec3d aim = aimPoint();
        if (aim == null || mc.currentScreen != null) {
            inReach = false;
            stopMoving();
            if (pather != null) pather.release();
            return;
        }

        inReach = mc.player.getEyePos().distanceTo(aim) <= reach.get();
        if (!run.get() || inReach) {
            if (pather != null) pather.stop();
            stopMoving();
            lookAt(aim);
            return;
        }

        // Baritone drives (keys and rotation) while it has a path; otherwise run straight at the target.
        if (pathfind.get() && BaritoneUtils.IS_AVAILABLE) {
            if (pather == null) pather = new BaritonePather();
            BlockPos goal = targetBlock != null ? targetBlock : targetEntity.getBlockPos();
            if (pather.isActive() || --repathTimer <= 0) {
                repathTimer = 10; // a failed search is retried twice a second, not every tick
                pather.pathTo(goal, Math.max(1, (int) Math.floor(reach.get()) - 1));
            }
            if (pather.isActive()) {
                stopMoving();
                return;
            }
        }

        lookAt(aim);
        press(mc.options.forwardKey, true);
        press(mc.options.sprintKey, true);
        press(mc.options.jumpKey, autoJump.get() && mc.player.horizontalCollision && mc.player.isOnGround());
        moving = true;
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

        // Blocks: only walk chunk sections whose palette contains a candidate block.
        Set<Block> candidates = candidateBlocks();
        int chunkRange = (int) Math.ceil(range.get() / 16) + 1;
        ChunkPos playerChunk = mc.player.getChunkPos();
        for (Chunk chunk : Utils.chunks()) {
            ChunkPos cp = chunk.getPos();
            if (Math.abs(cp.x - playerChunk.x) > chunkRange || Math.abs(cp.z - playerChunk.z) > chunkRange) continue;

            ChunkSection[] sections = chunk.getSectionArray();
            for (int i = 0; i < sections.length; i++) {
                ChunkSection section = sections[i];
                if (section.isEmpty() || !section.hasAny(state -> candidates.contains(state.getBlock()))) continue;

                int baseY = chunk.sectionIndexToCoord(i) << 4;
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                    if (!candidates.contains(section.getBlockState(x, y, z).getBlock())) continue;
                    BlockPos pos = new BlockPos(cp.getStartX() + x, baseY + y, cp.getStartZ() + z);
                    double d = eye.squaredDistanceTo(Vec3d.ofCenter(pos));
                    if (d < best && isTargetBlock(pos)) {
                        best = d;
                        targetBlock = pos;
                    }
                }
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

    private void lookAt(Vec3d pos) {
        float yaw = (float) Rotations.getYaw(pos);
        float pitch = (float) Rotations.getPitch(pos);
        float speed = rotationSpeed.get().floatValue();
        mc.player.setYaw(mc.player.getYaw() + MathHelper.wrapDegrees(yaw - mc.player.getYaw()) * speed);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + (pitch - mc.player.getPitch()) * speed, -90, 90));
    }

    private void click(Vec3d aim) {
        boolean left = clickMode.get() != ClickMode.Right;
        boolean right = clickMode.get() != ClickMode.Left;

        if (targetEntity != null) {
            if (left) mc.interactionManager.attackEntity(mc.player, targetEntity);
            if (right) mc.interactionManager.interactEntity(mc.player, targetEntity, Hand.MAIN_HAND);
        } else {
            Direction side = visibleSide(aim);
            // Raw start/abort so the left click reaches the server even in adventure mode, where
            // the client would otherwise not send a block hit at all.
            if (left) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, targetBlock, side));
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, targetBlock, side));
            }
            if (right) mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(aim, side, targetBlock, false));
        }

        if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    // The face we are actually looking at, or the one facing us if something is in the way.
    private Direction visibleSide(Vec3d aim) {
        Vec3d eye = mc.player.getEyePos();
        BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, aim, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(targetBlock)) return hit.getSide();
        return Direction.getFacing(eye.subtract(aim));
    }

    private void press(KeyBinding key, boolean pressed) {
        key.setPressed(pressed || Input.isPressed(key));
    }

    private void stopMoving() {
        if (!moving) return;
        moving = false;
        press(mc.options.forwardKey, false);
        press(mc.options.sprintKey, false);
        press(mc.options.jumpKey, false);
    }

    @Override
    public String getInfoString() {
        Vec3d aim = aimPoint();
        return aim == null || mc.player == null ? null : String.format("%.0fm", mc.player.getEyePos().distanceTo(aim));
    }
}
