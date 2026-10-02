package dev.goldenhead.modules;

import dev.goldenhead.GoldenHeadAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import dev.goldenhead.mixin.TextDisplayAccessor;
import dev.goldenhead.utils.PitUtils;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Finds the beast(s) during the Beast event. ZAimbot aims at them first and Nametags draws their name in
 * {@link #nametagColor}. Detection signals, strongest first:
 * 1. the keyword in their name, team prefix/suffix, tab-list name, or a hologram above their head;
 * 2. while the sidebar mentions the event: max health at or above a threshold (beasts get extra hearts);
 * 3. optional: the beast kit (diamond chestplate + boots + diamond sword).
 */
public class Beast extends Module {
    private final SettingGroup sgDetect = settings.getDefaultGroup();
    private final SettingGroup sgActions = settings.createGroup("Actions");

    private final Setting<String> keyword = sgDetect.add(new StringSetting.Builder()
        .name("keyword")
        .description("Text that marks a beast in names, tab list, holograms and the sidebar. Case and colors ignored.")
        .defaultValue("beast")
        .build()
    );

    private final Setting<Boolean> byHealth = sgDetect.add(new BoolSetting.Builder()
        .name("detect-by-health")
        .description("While the sidebar shows the event, treat players with very high max health as beasts.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> minMaxHealth = sgDetect.add(new DoubleSetting.Builder()
        .name("min-max-health")
        .description("Max health (in HP, 2 per heart) that counts as a beast.")
        .defaultValue(40)
        .range(21, 1000)
        .sliderRange(21, 200)
        .visible(byHealth::get)
        .build()
    );

    private final Setting<Boolean> byKit = sgDetect.add(new BoolSetting.Builder()
        .name("detect-by-kit")
        .description("While the sidebar shows the event, treat diamond chestplate + boots + diamond sword as a beast. Can misfire on normal players.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> prioritize = sgActions.add(new BoolSetting.Builder()
        .name("zaimbot-prioritize")
        .description("ZAimbot aims at a valid beast before anyone else.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> colorNametag = sgActions.add(new BoolSetting.Builder()
        .name("color-nametag")
        .description("Draw beasts' names in the Nametags module with the color below.")
        .defaultValue(true)
        .build()
    );

    public final Setting<SettingColor> nametagColor = sgActions.add(new ColorSetting.Builder()
        .name("nametag-color")
        .description("Name color for beasts in Nametags.")
        .defaultValue(new SettingColor(255, 0, 0))
        .visible(colorNametag::get)
        .build()
    );

    private final Set<UUID> beasts = new HashSet<>();
    private boolean eventActive;

    public Beast() {
        super(GoldenHeadAddon.PIT, "beast", "Detects the beast in the Beast event: ZAimbot targets them first and Nametags shows them in red.");
    }

    @Override
    public void onDeactivate() {
        beasts.clear();
        eventActive = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        beasts.clear();
        if (mc.world == null || mc.player == null) return;

        String key = keyword.get().toLowerCase(Locale.ROOT).trim();
        if (key.isEmpty()) return;
        eventActive = PitUtils.sidebarContains(key);

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (namedBeast(player, key) || (eventActive && statBeast(player))) beasts.add(player.getUuid());
        }

        // Holograms: armor stands / text displays floating over a player's head.
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity) continue;
            Text label = entity instanceof DisplayEntity.TextDisplayEntity display ? ((TextDisplayAccessor) display).goldenhead$getText() : entity.getCustomName();
            if (label == null || !clean(label).contains(key)) continue;

            Box above = entity.getBoundingBox().expand(0.8, 0, 0.8).stretch(0, -3.5, 0);
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player.getBoundingBox().intersects(above)) beasts.add(player.getUuid());
            }
        }
    }

    private boolean namedBeast(PlayerEntity player, String key) {
        if (clean(player.getDisplayName()).contains(key)) return true; // includes team prefix/suffix

        if (mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            return entry != null && entry.getDisplayName() != null && clean(entry.getDisplayName()).contains(key);
        }
        return false;
    }

    private boolean statBeast(PlayerEntity player) {
        if (byHealth.get() && player.getMaxHealth() >= minMaxHealth.get()) return true;
        return byKit.get()
            && player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.DIAMOND_CHESTPLATE)
            && player.getEquippedStack(EquipmentSlot.FEET).isOf(Items.DIAMOND_BOOTS)
            && player.getMainHandStack().isOf(Items.DIAMOND_SWORD);
    }

    public static String clean(Text text) {
        return PitUtils.clean(text);
    }

    private static Beast get() {
        Beast module = Modules.get().get(Beast.class);
        return module != null && module.isActive() ? module : null;
    }

    public static boolean isBeast(Entity entity) {
        Beast module = get();
        return module != null && module.beasts.contains(entity.getUuid());
    }

    /** ZAimbot should prefer beasts: module on, setting on, and we aren't a beast ourselves. */
    public static boolean shouldPrioritize() {
        Beast module = get();
        return module != null && module.prioritize.get() && !module.beasts.isEmpty()
            && module.mc.player != null && !module.beasts.contains(module.mc.player.getUuid());
    }

    public static SettingColor nametagColorFor(PlayerEntity player) {
        Beast module = get();
        return module != null && module.colorNametag.get() && module.beasts.contains(player.getUuid()) ? module.nametagColor.get() : null;
    }

    public boolean isEventActive() {
        return eventActive;
    }

    @Override
    public String getInfoString() {
        return beasts.isEmpty() ? null : String.valueOf(beasts.size());
    }
}
