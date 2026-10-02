package dev.goldenhead.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.*;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.regex.Pattern;

public final class PitUtils {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private PitUtils() {
    }

    public static String clean(Text text) {
        String s = Formatting.strip(text.getString());
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /** Whether the sidebar title or any sidebar line contains {@code key} (lowercase, colors ignored). */
    public static boolean sidebarContains(String key) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || key.isEmpty()) return false;

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective sidebar = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (sidebar == null) return false;
        if (clean(sidebar.getDisplayName()).contains(key)) return true;

        for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(sidebar)) {
            Text line = Team.decorateName(scoreboard.getScoreHolderTeam(entry.owner()), entry.name());
            if (clean(line).contains(key)) return true;
        }
        return false;
    }

    /**
     * Real players in the tab list, including you. Skips the fake entries servers use for NPCs and tab
     * decorations: names that aren't valid usernames, and version-2 UUIDs (Hypixel's NPC marker).
     */
    public static int realPlayerCount() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return 0;

        int count = 0;
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile().id().version() == 2) continue;
            if (!USERNAME.matcher(entry.getProfile().name()).matches()) continue;
            count++;
        }
        return count;
    }
}
