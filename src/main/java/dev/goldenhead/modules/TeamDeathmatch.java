package dev.goldenhead.modules;

import dev.goldenhead.GoldenHeadAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Style;
import net.minecraft.text.TextColor;

import java.util.Optional;

/**
 * Team filter for the Team Deathmatch event. Your team comes from the hat (head slot) you wear;
 * while you have a red or blue hat, ZAimbot only targets players of the other team.
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

    public TeamDeathmatch() {
        super(GoldenHeadAddon.PIT, "team-deathmatch", "During TDM, detects your team from your hat and makes ZAimbot target only the other team.");
    }

    /** Called from ZAimbot's target check. Outside TDM (no team hat on you) it changes nothing. */
    public static boolean allowTarget(Entity entity) {
        TeamDeathmatch module = Modules.get().get(TeamDeathmatch.class);
        if (module == null || !module.isActive() || module.mc.player == null) return true;
        if (!(entity instanceof PlayerEntity player)) return true;

        Team own = fromHat(module.mc.player);
        if (own == Team.None) return true;

        Team theirs = fromHat(player);
        if (theirs == Team.None && module.useNameColor.get()) theirs = fromName(player);
        if (theirs == Team.None) return module.targetUnknown.get();
        return theirs != own;
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

    // Color of the part of the display name holding the username, so level/rank prefixes don't count.
    private static Team fromName(PlayerEntity player) {
        String username = player.getName().getString();
        Optional<TextColor> color = player.getDisplayName().visit((style, part) ->
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
        if (hue >= 190 && hue <= 265) return Team.Blue;
        return Team.None;
    }

    @Override
    public String getInfoString() {
        if (mc.player == null) return null;
        Team own = fromHat(mc.player);
        return own == Team.None ? "no event" : own.name();
    }
}
