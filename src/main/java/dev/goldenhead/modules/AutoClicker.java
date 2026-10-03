package dev.goldenhead.modules;

import dev.goldenhead.GoldenHeadAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Presses the attack key on a random jitter-click gap and turns off when you die.
 * This is only a left-click input. The game handles the hit on its normal input tick.
 * Sending the attack packet directly rubberbands you.
 */
public class AutoClicker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder()
        .name("min-delay-ms")
        .description("Shortest time between left clicks.")
        .defaultValue(40)
        .range(1, 1000)
        .sliderRange(20, 200)
        .build()
    );

    private final Setting<Integer> maxDelay = sgGeneral.add(new IntSetting.Builder()
        .name("max-delay-ms")
        .description("Longest time between left clicks. Each wait is random between min and max.")
        .defaultValue(65)
        .range(1, 1000)
        .sliderRange(20, 200)
        .build()
    );

    private long nextClick;

    public AutoClicker() {
        super(GoldenHeadAddon.PIT, "auto-clicker", "Left-clicks at a random 40-65 ms interval. Turns off when you die.");
    }

    @Override
    public void onActivate() {
        nextClick = System.currentTimeMillis() + delay();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (mc.player.isDead() || mc.player.deathTime > 0 || mc.player.getHealth() <= 0) toggle();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (mc.player.isDead() || mc.player.deathTime > 0 || mc.player.getHealth() <= 0) return;
        if (System.currentTimeMillis() < nextClick) return;

        // Same edge a real click produces. handleInputEvents turns it into doAttack next tick.
        KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(mc.options.attackKey.getBoundKeyTranslationKey()));
        nextClick = System.currentTimeMillis() + delay();
    }

    private int delay() {
        int min = Math.min(minDelay.get(), maxDelay.get()), max = Math.max(minDelay.get(), maxDelay.get());
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
