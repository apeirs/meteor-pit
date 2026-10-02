package dev.goldenhead.utils;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

/**
 * Pit spawn: the protected platform above the pit. Nobody there can be hit, so targeting stops at its edge,
 * and automation walks out of it instead of idling. Learned automatically: the server teleports you to spawn
 * when you join and when you respawn, so the first teleport after either is the spawn point.
 * {@code .pitspawn} can set or clear it by hand.
 */
public final class SpawnArea {
    public static final SpawnArea INSTANCE = new SpawnArea();

    /** Horizontal radius around the spawn point that counts as spawn. */
    public static final double RADIUS = 30;
    /** Blocks below the spawn point that still count as spawn (stairs, edges). */
    public static final double BELOW = 3;

    private static Vec3d spawn;
    private static boolean manual;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private long learnUntil;
    private boolean wasDead;
    private int recordIn = -1;

    private SpawnArea() {
    }

    public static Vec3d get() {
        return spawn;
    }

    public static void set(Vec3d pos) {
        spawn = pos;
        manual = pos != null;
    }

    public static boolean isInSpawn(Entity entity) {
        return isInSpawn(entity.getEntityPos());
    }

    public static boolean isInSpawn(Vec3d pos) {
        if (spawn == null) return false;
        double dx = pos.x - spawn.x, dz = pos.z - spawn.z;
        return pos.y >= spawn.y - BELOW && dx * dx + dz * dz <= RADIUS * RADIUS;
    }

    /** Y level that is safely out of spawn (a few blocks under the platform). */
    public static int exitY() {
        return spawn == null ? Integer.MIN_VALUE : (int) Math.floor(spawn.y - BELOW - 3);
    }

    @EventHandler
    private void onJoin(GameJoinedEvent event) {
        if (!manual) spawn = null;
        learnUntil = System.currentTimeMillis() + 10_000;
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        // Record a couple of ticks after the teleport has been applied.
        if (event.packet instanceof PlayerPositionLookS2CPacket && !manual && System.currentTimeMillis() < learnUntil) recordIn = 2;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        boolean dead = mc.player.isDead() || mc.player.getHealth() <= 0;
        if (wasDead && !dead) learnUntil = System.currentTimeMillis() + 5_000; // respawned: next teleport is spawn
        wasDead = dead;

        if (recordIn >= 0 && --recordIn < 0) {
            spawn = mc.player.getEntityPos();
            learnUntil = 0;
        }
    }
}
