package dev.goldenhead.modules;

import dev.goldenhead.MeteorPitAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.Optional;

/**
 * Team filter for the Team Deathmatch event. Your team comes from your hat, or from your name color while
 * other players wear team hats; ZAimbot then only targets players of the other team.
 */
public class TeamDeathmatch extends Module {
    public enum Team { Red, Blue, None }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> useNameColor = sgGeneral.add(new BoolSetting.Builder()
        .name("use-name-color")
        .description("If a player's hat can't be read, fall back to the color of their name.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> targetUnknown = sgGeneral.add(new BoolSetting.Builder()
        .name("target-unknown")
        .description("During TDM, still target players whose team can't be detected.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> minHats = sgGeneral.add(new IntSetting.Builder()
        .name("min-hats")
        .description("Without a hat yourself, TDM counts as running once this many other players wear team hats.")
        .defaultValue(2)
        .range(1, 20)
        .sliderRange(1, 10)
        .build()
    );

    public TeamDeathmatch() {
        super(MeteorPitAddon.PIT, "team-deathmatch", "During TDM, detects your team (hat or name color) and makes ZAimbot target only the other team.");
    }

    private Team ownTeam = Team.None;

    @Override
    public void onDeactivate() {
        ownTeam = Team.None;
    }

    // Your team: your own hat, otherwise (servers that only hat other players) your name color,
    // but only trusted while TDM is visibly running, i.e. other players wear team hats.
    @EventHandler
    private void onTick(TickEvent.Post event) {
        ownTeam = Team.None;
        if (mc.world == null || mc.player == null) return;

        ownTeam = fromHat(mc.player);
        if (ownTeam != Team.None) return;

        int hatted = 0;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player && fromHat(player) != Team.None) hatted++;
        }
        if (hatted >= minHats.get()) ownTeam = fromName(mc.player);
    }

    /** Called from ZAimbot's target check. Outside TDM (your team unknown) it changes nothing. */
    public static boolean allowTarget(Entity entity) {
        TeamDeathmatch module = Modules.get().get(TeamDeathmatch.class);
        if (module == null || !module.isActive() || module.ownTeam == Team.None) return true;
        if (!(entity instanceof PlayerEntity player)) return true;

        Team theirs = fromHat(player);
        if (theirs == Team.None && module.useNameColor.get()) theirs = fromName(player);
        if (theirs == Team.None) return module.targetUnknown.get();
        return theirs != module.ownTeam;
    }

    public static Team fromHat(PlayerEntity player) {
        ItemStack hat = player.getEquippedStack(EquipmentSlot.HEAD);
        if (hat.isEmpty()) return Team.None;

        DyedColorComponent dyed = hat.get(DataComponentTypes.DYED_COLOR);
        if (dyed != null) return fromRgb(dyed.rgb());

        // red_wool, blue_wool, light_blue_stained_glass, red_banner, ...
        String id = Registries.ITEM.getId(hat.getItem()).getPath();
        if (id.startsWith("red_")) return Team.Red;
        if (id.startsWith("blue_") || id.startsWith("light_blue_")) return Team.Blue;
        return Team.None;
    }

    /** Username color: scoreboard team color, then tab-list name, then display name. */
    public static Team fromName(PlayerEntity player) {
        net.minecraft.scoreboard.Team team = player.getScoreboardTeam();
        if (team != null && team.getColor().getColorValue() != null) {
            Team fromTeam = fromRgb(team.getColor().getColorValue());
            if (fromTeam != Team.None) return fromTeam;
        }

        String username = player.getName().getString();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (entry != null && entry.getDisplayName() != null) {
                Team fromTab = colorOf(entry.getDisplayName(), username);
                if (fromTab != Team.None) return fromTab;
            }
        }
        return colorOf(player.getDisplayName(), username);
    }

    // Color of the text segment holding the username, so level/rank prefixes don't count.
    private static Team colorOf(Text text, String username) {
        Optional<TextColor> color = text.visit((style, part) ->
            part.contains(username) && style.getColor() != null ? Optional.of(style.getColor()) : Optional.empty(),
            Style.EMPTY);
        return color.map(c -> fromRgb(c.getRgb())).orElse(Team.None);
    }

    private static Team fromRgb(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        int delta = max - min;
        if (max < 40 || delta < 40) return Team.None; // black, white, gray

        float hue;
        if (max == r) hue = 60f * (((g - b) / (float) delta) % 6);
        else if (max == g) hue = 60f * ((b - r) / (float) delta + 2);
        else hue = 60f * ((r - g) / (float) delta + 4);
        if (hue < 0) hue += 360;

        if (hue <= 25 || hue >= 335) return Team.Red;
        if (hue >= 175 && hue <= 265) return Team.Blue; // includes aqua (§b, §3) team colors
        return Team.None;
    }

    @Override
    public String getInfoString() {
        return ownTeam == Team.None ? "no event" : ownTeam.name();
    }
}
