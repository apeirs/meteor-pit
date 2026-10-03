package dev.goldenhead.utils;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.Vec3d;

/**
 * Pit spawn: the protected platform above the pit. Nobody there can be hit, so targeting stops at its edge,
 * and automation walks out of it instead of idling. Learned automatically: the server teleports you to spawn
 * when you arrive and when you respawn, so the first teleport after either is the spawn point. Arriving
 * includes every server switch behind the proxy (Hypixel lobby -> Pit sends a respawn packet, not a new join),
 * otherwise the lobby's spawn would stick.
 * {@code .pitspawn} can set or clear it by hand.
 */
public final class SpawnArea {
    public static final SpawnArea INSTANCE = new SpawnArea();

    /** Horizontal radius around the spawn point that counts as spawn. */
    public static final double RADIUS = 30;
    /** Blocks below the spawn point that still count as spawn (stairs, edges). */
    public static final double BELOW = 3;

    private static volatile Vec3d spawn;
    private static boolean manual;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    // Written from the network thread (PacketEvent.Receive), read on the render thread.
    private volatile long learnUntil;
    private volatile int recordIn = -1;
    private boolean wasDead;

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
        relearn();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        // New server or world: the old spawn is meaningless there.
        if (event.packet instanceof GameJoinS2CPacket || event.packet instanceof PlayerRespawnS2CPacket) relearn();
        // Record a couple of ticks after the teleport has been applied.
        else if (event.packet instanceof PlayerPositionLookS2CPacket && !manual && System.currentTimeMillis() < learnUntil) recordIn = 2;
    }

    private void relearn() {
        if (manual) return;
        spawn = null;
        recordIn = -1;
        learnUntil = System.currentTimeMillis() + 10_000;
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
            ChatUtils.info("Pit spawn learned at %.0f, %.0f, %.0f (.pitspawn set to correct it).", spawn.x, spawn.y, spawn.z);
        }
    }
}
