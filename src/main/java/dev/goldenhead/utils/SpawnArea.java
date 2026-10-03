package dev.goldenhead.utils;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pit spawn: the protected platform above the pit. Nobody there can be hit, so targeting stops at its edge,
 * and automation walks out of it instead of idling. Learned from where the server puts you on arrival and
 * on respawn. Arrival includes a proxy switch (Hypixel lobby, then "Sending to mega3D"): that rebuilds the
 * client world and is not always a fresh join, so the lobby point must be dropped or ZAimbot keeps aiming
 * on the Pit platform. The last teleport in the learn window wins; an intermediate one must not stick.
 * Until the new point is known, everyone counts as in spawn so aiming stays off across the switch.
 * {@code .pitspawn} can set or clear it by hand.
 */
public final class SpawnArea {
    public static final SpawnArea INSTANCE = new SpawnArea();
    private static final Logger LOG = LoggerFactory.getLogger("meteor-pit");

    /** Horizontal radius around the spawn point that counts as spawn. */
    public static final double RADIUS = 30;
    /** Blocks below the spawn point that still count as spawn (stairs, edges). */
    public static final double BELOW = 3;

    private static volatile Vec3d spawn;
    private static volatile boolean learning;
    private static volatile boolean manual;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private ClientWorld trackedWorld;
    private long learnUntil;
    /** Ticks to wait after the last teleport or world change before committing the player position. */
    private volatile int settleTicks = -1;
    private boolean wasDead;

    private SpawnArea() {
    }

    public static Vec3d get() {
        return spawn;
    }

    public static void set(Vec3d pos) {
        spawn = pos;
        manual = pos != null;
        if (pos != null) learning = false;
    }

    public static boolean isInSpawn(Entity entity) {
        return isInSpawn(entity.getEntityPos());
    }

    public static boolean isInSpawn(Vec3d pos) {
        // Mid server-switch the old point is gone and the new one is not in yet. Aiming here is the bug.
        if (spawn == null) return learning;
        double dx = pos.x - spawn.x, dz = pos.z - spawn.z;
        return pos.y >= spawn.y - BELOW && dx * dx + dz * dz <= RADIUS * RADIUS;
    }

    /** Y level that is safely out of spawn (a few blocks under the platform). */
    public static int exitY() {
        return spawn == null ? Integer.MIN_VALUE : (int) Math.floor(spawn.y - BELOW - 3);
    }

    @EventHandler
    private void onJoin(GameJoinedEvent event) {
        arm();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.packet instanceof GameJoinS2CPacket || event.packet instanceof PlayerRespawnS2CPacket) arm();
        else if (event.packet instanceof PlayerPositionLookS2CPacket && learning && System.currentTimeMillis() < learnUntil) settleTicks = 10;
    }

    /** Drop the previous spawn and take the position once teleports stop arriving. */
    private void arm() {
        if (manual) return;
        spawn = null;
        learning = true;
        learnUntil = System.currentTimeMillis() + 10_000;
        settleTicks = 10;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Lobby -> Pit rebuilds the client world (log: "Sending to mega3D", then a new overworld).
        // A respawn packet is not guaranteed, and the first teleport of the switch is often not the platform.
        if (mc.world != trackedWorld) {
            trackedWorld = mc.world;
            if (trackedWorld != null) arm();
        }
        if (mc.player == null) return;

        boolean dead = mc.player.isDead() || mc.player.getHealth() <= 0;
        if (wasDead && !dead) arm(); // respawned: the next settled position is spawn
        wasDead = dead;

        if (!learning) return;
        if (settleTicks >= 0 && --settleTicks < 0) commit();
        if (settleTicks < 0 && System.currentTimeMillis() > learnUntil) learning = false;
    }

    private void commit() {
        Vec3d pos = mc.player.getEntityPos();
        if (spawn != null && pos.squaredDistanceTo(spawn) <= 1) return;
        spawn = pos;
        // A follow-up teleport still replaces this. A launch a few seconds later must not.
        learnUntil = Math.min(learnUntil, System.currentTimeMillis() + 1000);
        ChatUtils.info("Pit spawn learned at %.0f, %.0f, %.0f (.pitspawn set to correct it).", spawn.x, spawn.y, spawn.z);
        LOG.info("Pit spawn learned at {}, {}, {}", Math.round(spawn.x), Math.round(spawn.y), Math.round(spawn.z));
    }
}
