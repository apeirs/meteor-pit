package dev.goldenhead.utils;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalYLevel;
import net.minecraft.util.math.BlockPos;

/**
 * Thin wrapper around Baritone, only class-loaded when Baritone is installed (check BaritoneUtils.IS_AVAILABLE).
 * While active it switches Baritone to Pit rules: never mine or place, any fall height is fine (no fall damage),
 * parkour and diagonal moves on for the shortest route. The user's own values are restored on {@link #release()}.
 */
public class BaritonePather {
    private Boolean allowBreak, allowPlace, allowParkour, allowDiagonalAscend, allowDiagonalDescend, autoTool, allowInventory;
    private Integer maxFall;
    private boolean applied;
    private BlockPos goal;

    private static IBaritone baritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    public void pathTo(BlockPos pos, int range) {
        apply();
        if (pos.equals(goal) && isActive()) return;
        goal = pos;
        baritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(pos, range));
    }

    /** Path to any spot at or below this Y (used to drop out of spawn). */
    public void pathDownTo(int y) {
        apply();
        BlockPos marker = new BlockPos(0, y, 0);
        if (marker.equals(goal) && isActive()) return;
        goal = marker;
        baritone().getCustomGoalProcess().setGoalAndPath(new GoalYLevel(y));
    }

    public boolean isActive() {
        return baritone().getCustomGoalProcess().isActive();
    }

    public void stop() {
        if (goal == null) return;
        goal = null;
        baritone().getPathingBehavior().cancelEverything();
    }

    /** Stop pathing and give Baritone its settings back. */
    public void release() {
        stop();
        if (!applied) return;
        Settings s = BaritoneAPI.getSettings();
        s.allowBreak.value = allowBreak;
        s.allowPlace.value = allowPlace;
        s.allowParkour.value = allowParkour;
        s.allowDiagonalAscend.value = allowDiagonalAscend;
        s.allowDiagonalDescend.value = allowDiagonalDescend;
        s.autoTool.value = autoTool;
        s.allowInventory.value = allowInventory;
        s.maxFallHeightNoWater.value = maxFall;
        applied = false;
    }

    private void apply() {
        if (applied) return;
        Settings s = BaritoneAPI.getSettings();
        allowBreak = s.allowBreak.value;
        allowPlace = s.allowPlace.value;
        allowParkour = s.allowParkour.value;
        allowDiagonalAscend = s.allowDiagonalAscend.value;
        allowDiagonalDescend = s.allowDiagonalDescend.value;
        autoTool = s.autoTool.value;
        allowInventory = s.allowInventory.value;
        maxFall = s.maxFallHeightNoWater.value;

        s.allowBreak.value = false;
        s.allowPlace.value = false;
        s.autoTool.value = false;
        s.allowInventory.value = false;
        s.allowParkour.value = true;
        s.allowDiagonalAscend.value = true;
        s.allowDiagonalDescend.value = true;
        s.maxFallHeightNoWater.value = 256;
        applied = true;
    }
}
