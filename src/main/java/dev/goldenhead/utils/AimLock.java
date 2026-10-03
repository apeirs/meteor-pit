package dev.goldenhead.utils;

import net.minecraft.entity.Entity;

/**
 * Who ZAimbot is aiming at this frame. Cleared at the start of its render and set again only if it
 * actually aims, so a lost target does not linger. Auto Clicker refuses to click without this.
 */
public final class AimLock {
    private static Entity target;
    private static long at;

    private AimLock() {
    }

    public static void set(Entity entity) {
        target = entity;
        at = System.currentTimeMillis();
    }

    public static void clear() {
        target = null;
    }

    /** The entity ZAimbot is locked onto, or null if it is not aiming at one right now. */
    public static Entity get() {
        if (target == null || target.isRemoved() || !target.isAlive()) return null;
        // ZAimbot updates this every rendered frame. A stale lock means it stopped.
        if (System.currentTimeMillis() - at > 200) return null;
        return target;
    }
}
