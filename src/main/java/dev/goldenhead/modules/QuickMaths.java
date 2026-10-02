package dev.goldenhead.modules;

import dev.goldenhead.GoldenHeadAddon;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Quick Maths event: reads the equation from the server's chat broadcast, solves it and types the answer
 * after a random delay. The trigger is anchored to the start of the line, so a player who types
 * "QUICK MATHS! Solve: ..." in chat (their line starts with their name) can't bait it.
 */
public class QuickMaths extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> trigger = sgGeneral.add(new StringSetting.Builder()
        .name("trigger")
        .description("Regex for the server's question line (colors stripped). Group 1 is the equation.")
        .defaultValue("^QUICK MATHS! Solve: (.+)$")
        .build()
    );

    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder()
        .name("min-delay-ms")
        .description("Shortest wait before answering.")
        .defaultValue(300)
        .range(0, 10000)
        .sliderRange(0, 3000)
        .build()
    );

    private final Setting<Integer> maxDelay = sgGeneral.add(new IntSetting.Builder()
        .name("max-delay-ms")
        .description("Longest wait before answering. Each wait is random between min and max.")
        .defaultValue(800)
        .range(0, 10000)
        .sliderRange(0, 3000)
        .build()
    );

    private String answer;
    private long sendAt;

    public QuickMaths() {
        super(GoldenHeadAddon.PIT, "quick-maths", "Solves the Quick Maths question and answers in chat after a random delay.");
    }

    @Override
    public void onActivate() {
        answer = null;
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        String line = Formatting.strip(event.getMessage().getString());
        if (line == null) return;

        Matcher matcher;
        try {
            matcher = Pattern.compile(trigger.get(), Pattern.CASE_INSENSITIVE).matcher(line.trim());
        } catch (PatternSyntaxException e) {
            return;
        }
        if (!matcher.find() || matcher.groupCount() < 1) return;

        Double value = Expr.eval(matcher.group(1));
        if (value == null) {
            warning("Couldn't solve: %s", matcher.group(1));
            return;
        }

        answer = format(value);
        int min = Math.min(minDelay.get(), maxDelay.get()), max = Math.max(minDelay.get(), maxDelay.get());
        sendAt = System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        trySend();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        trySend();
    }

    private void trySend() {
        if (answer == null || mc.player == null || System.currentTimeMillis() < sendAt) return;
        ChatUtils.sendPlayerMsg(answer, false);
        answer = null;
    }

    private static String format(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return Long.toString(Math.round(v));
        return new BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** Tiny recursive-descent evaluator: + - * / ^, parentheses, unary minus; x × ÷ accepted. */
    static final class Expr {
        private final String s;
        private int i;

        private Expr(String s) {
            this.s = s;
        }

        static Double eval(String input) {
            String norm = input.replaceAll("[xX×✕]", "*").replace('÷', '/').replaceAll("[=?\\s]", "").replace(",", "");
            if (norm.isEmpty()) return null;
            try {
                Expr e = new Expr(norm);
                double v = e.sum();
                return e.i == norm.length() && Double.isFinite(v) ? v : null;
            } catch (RuntimeException ex) {
                return null;
            }
        }

        private double sum() {
            double v = product();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '+') { i++; v += product(); }
                else if (c == '-') { i++; v -= product(); }
                else break;
            }
            return v;
        }

        private double product() {
            double v = power();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '*') { i++; v *= power(); }
                else if (c == '/') { i++; v /= power(); }
                else break;
            }
            return v;
        }

        private double power() {
            double base = unary();
            if (i < s.length() && s.charAt(i) == '^') {
                i++;
                return Math.pow(base, power());
            }
            return base;
        }

        private double unary() {
            if (i < s.length() && s.charAt(i) == '-') { i++; return -unary(); }
            if (i < s.length() && s.charAt(i) == '+') { i++; return unary(); }
            if (i < s.length() && s.charAt(i) == '(') {
                i++;
                double v = sum();
                if (i >= s.length() || s.charAt(i) != ')') throw new IllegalStateException("missing )");
                i++;
                return v;
            }
            int start = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            if (start == i) throw new IllegalStateException("number expected");
            return Double.parseDouble(s.substring(start, i));
        }
    }
}
