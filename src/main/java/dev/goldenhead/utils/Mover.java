package dev.goldenhead.utils;

import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Gets the player somewhere: Baritone for long or blocked routes (Pit rules: no mining/placing, any drop),
 * straight sprinting when Baritone is missing, has no path, or the goal is close and in sight.
 * Call {@link #tick} every tick while moving and {@link #stop} / {@link #release} when done.
 */
public class Mover {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private BaritonePather pather;
    private boolean pressing;
    private int repathTimer;

    public boolean pathfind = true;
    public boolean autoJump = true;
    public double rotationSpeed = 0.6;
    /** Within this distance and with a clear line of sight, sprint straight instead of pathing. */
    public double directRange = 6;

    /** One tick of moving toward {@code goal}; {@code look} is what to face while running straight. */
    public void tick(BlockPos goal, int range, Vec3d look) {
        if (mc.player == null) return;

        boolean direct = mc.player.getEyePos().distanceTo(look) <= directRange && canSee(look, goal);
        if (!direct && pathfind && BaritoneUtils.IS_AVAILABLE) {
            if (pather == null) pather = new BaritonePather();
            if (pather.isActive() || --repathTimer <= 0) {
                repathTimer = 10; // a failed search is retried twice a second, not every tick
                pather.pathTo(goal, range);
            }
            if (pather.isActive()) {
                unpress();
                return;
            }
        } else if (pather != null) pather.stop();

        lookAt(look, rotationSpeed);
        press(mc.options.forwardKey, true);
        press(mc.options.sprintKey, true);
        press(mc.options.jumpKey, autoJump && mc.player.horizontalCollision && mc.player.isOnGround());
        pressing = true;
    }

    /** One tick of getting out of spawn: Baritone down below the platform, else run straight away from its center. */
    public void leaveSpawn() {
        Vec3d spawn = SpawnArea.get();
        if (mc.player == null || spawn == null) return;

        if (pathfind && BaritoneUtils.IS_AVAILABLE) {
            if (pather == null) pather = new BaritonePather();
            if (pather.isActive() || --repathTimer <= 0) {
                repathTimer = 10;
                pather.pathDownTo(SpawnArea.exitY());
            }
            if (pather.isActive()) {
                unpress();
                return;
            }
        }

        Vec3d me = mc.player.getEntityPos();
        Vec3d away = new Vec3d(me.x - spawn.x, 0, me.z - spawn.z);
        if (away.lengthSquared() < 0.01) away = Vec3d.fromPolar(0, mc.player.getYaw());
        lookAt(me.add(away.normalize().multiply(10)).add(0, 0.5, 0), rotationSpeed);
        press(mc.options.forwardKey, true);
        press(mc.options.sprintKey, true);
        press(mc.options.jumpKey, autoJump && mc.player.horizontalCollision && mc.player.isOnGround());
        pressing = true;
    }

    /** Stop moving but keep Baritone in Pit mode (more moves coming). */
    public void stop() {
        if (pather != null) pather.stop();
        unpress();
    }

    /** Stop moving and give Baritone its own settings back. */
    public void release() {
        if (pather != null) pather.release();
        unpress();
    }

    public void lookAt(Vec3d pos, double speed) {
        float yaw = (float) Rotations.getYaw(pos);
        float pitch = (float) Rotations.getPitch(pos);
        float s = (float) speed;
        mc.player.setYaw(mc.player.getYaw() + MathHelper.wrapDegrees(yaw - mc.player.getYaw()) * s);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + (pitch - mc.player.getPitch()) * s, -90, 90));
    }

    // Nothing solid between us and the point (hitting the goal block itself counts as clear).
    private boolean canSee(Vec3d pos, BlockPos goal) {
        BlockHitResult hit = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), pos,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(goal);
    }

    private void press(KeyBinding key, boolean pressed) {
        key.setPressed(pressed || Input.isPressed(key));
    }

    private void unpress() {
        if (!pressing) return;
        pressing = false;
        press(mc.options.forwardKey, false);
        press(mc.options.sprintKey, false);
        press(mc.options.jumpKey, false);
    }
}
