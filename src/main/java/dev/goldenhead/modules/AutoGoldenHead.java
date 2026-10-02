package dev.goldenhead.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.List;
import java.util.Locale;

public class AutoGoldenHead extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> maxAbsorption = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-golden-hearts")
        .description("Use a head when your golden (absorption) hearts are at or below this. 0.5 = half a heart.")
        .defaultValue(0.5)
        .range(0, 10)
        .sliderRange(0, 4)
        .build()
    );

    private final Setting<List<String>> items = sgGeneral.add(new StringListSetting.Builder()
        .name("items")
        .description("Item names to use, highest priority first. Case and colors ignored.")
        .defaultValue(List.of("golden head", "fractured soul", "rage potato"))
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks to wait after using a head, so laggy servers don't make you eat two.")
        .defaultValue(20)
        .range(1, 200)
        .sliderRange(1, 60)
        .build()
    );

    private final Setting<Boolean> pullFromInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("pull-from-inventory")
        .description("Move a head into the hotbar if none is there.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> hotbarSlot = sgGeneral.add(new IntSetting.Builder()
        .name("hotbar-slot")
        .description("Hotbar slot (1-9) to pull heads into.")
        .defaultValue(9)
        .range(1, 9)
        .visible(pullFromInventory::get)
        .build()
    );

    private final Setting<Boolean> pauseWhileUsing = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-while-using")
        .description("Don't interrupt eating, drinking, bow drawing or blocking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing your hand when using a head.")
        .defaultValue(true)
        .build()
    );

    private int timer;

    public AutoGoldenHead() {
        super(Categories.Combat, "auto-golden-head", "Right-clicks Golden Heads, Fractured Souls and Rage Potatoes when your golden hearts run out.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (timer > 0) {
            timer--;
            return;
        }

        if (mc.player.isDead()) return;
        // Absorption is stored in HP: 1 heart = 2 HP.
        if (mc.player.getAbsorptionAmount() > maxAbsorption.get() * 2) return;
        if (pauseWhileUsing.get() && mc.player.isUsingItem()) return;

        FindItemResult head = findItem();
        if (head == null) return;

        if (!head.isHotbar() && !head.isOffhand()) {
            if (!pullFromInventory.get()) return;
            InvUtils.move().from(head.slot()).toHotbar(hotbarSlot.get() - 1);
            timer = 2; // let the server process the move before using it
            return;
        }

        Hand hand;
        boolean swapped = false;
        if (head.isOffhand()) hand = Hand.OFF_HAND;
        else {
            hand = Hand.MAIN_HAND;
            if (!head.isMainHand()) swapped = InvUtils.swap(head.slot(), true);
            if (!head.isMainHand() && !swapped) return;
        }

        // interactItem = right-click in the air, so the head never gets placed as a block.
        mc.interactionManager.interactItem(mc.player, hand);
        if (swing.get()) mc.player.swingHand(hand);
        if (swapped) InvUtils.swapBack();

        timer = delay.get();
    }

    // Hotbar/offhand first (in priority order), so we only pull from the inventory when the hotbar has nothing.
    private FindItemResult findItem() {
        FindItemResult fallback = null;
        for (String name : items.get()) {
            String key = name.toLowerCase(Locale.ROOT).trim();
            if (key.isEmpty()) continue;
            FindItemResult result = InvUtils.find(stack -> matches(stack, key));
            if (!result.found()) continue;
            if (result.isHotbar() || result.isOffhand()) return result;
            if (fallback == null) fallback = result;
        }
        return fallback;
    }

    private boolean matches(ItemStack stack) {
        for (String name : items.get()) {
            String key = name.toLowerCase(Locale.ROOT).trim();
            if (!key.isEmpty() && matches(stack, key)) return true;
        }
        return false;
    }

    // Name only, not lore: Pit lore often mentions other items ("heals like a Golden Head").
    private static boolean matches(ItemStack stack, String key) {
        return !stack.isEmpty() && clean(stack.getName()).contains(key);
    }

    private static String clean(Text text) {
        String s = Formatting.strip(text.getString());
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    @Override
    public String getInfoString() {
        return String.valueOf(InvUtils.find(this::matches).count());
    }
}
