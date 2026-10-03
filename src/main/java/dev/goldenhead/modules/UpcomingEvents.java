package dev.goldenhead.modules;

import com.google.gson.reflect.TypeToken;
import dev.goldenhead.GoldenHeadAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the next Pit events in the top left, from the schedule brookeafk.com uses
 * (github.com/BrookeAFK/brookeafk-api, events.js: a JSON list of {event, timestamp, type}).
 * The list is fetched off-thread on activation and then every few minutes.
 */
public class UpcomingEvents extends Module {
    private static final String URL = "https://raw.githubusercontent.com/BrookeAFK/brookeafk-api/main/events.js";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> count = sgGeneral.add(new IntSetting.Builder()
        .name("count")
        .description("How many upcoming events to show per list.")
        .defaultValue(3)
        .range(1, 20)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Boolean> majors = sgGeneral.add(new BoolSetting.Builder()
        .name("majors")
        .description("Show upcoming major events.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> minors = sgGeneral.add(new BoolSetting.Builder()
        .name("minors")
        .description("Show upcoming minor events.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> refreshMinutes = sgGeneral.add(new IntSetting.Builder()
        .name("refresh-minutes")
        .description("How often to fetch the schedule again.")
        .defaultValue(10)
        .range(1, 120)
        .sliderRange(1, 60)
        .build()
    );

    private final Setting<Integer> x = sgRender.add(new IntSetting.Builder()
        .name("x")
        .description("Distance from the left edge of the screen.")
        .defaultValue(2)
        .range(0, 2000)
        .sliderRange(0, 500)
        .build()
    );

    private final Setting<Integer> y = sgRender.add(new IntSetting.Builder()
        .name("y")
        .description("Distance from the top edge of the screen.")
        .defaultValue(2)
        .range(0, 2000)
        .sliderRange(0, 500)
        .build()
    );

    private final Setting<Double> scale = sgRender.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Text size.")
        .defaultValue(1)
        .range(0.5, 3)
        .sliderRange(0.5, 2)
        .build()
    );

    private final Setting<SettingColor> titleColor = sgRender.add(new ColorSetting.Builder()
        .name("title-color")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> majorColor = sgRender.add(new ColorSetting.Builder()
        .name("major-color")
        .defaultValue(new SettingColor(255, 85, 85))
        .build()
    );

    private final Setting<SettingColor> minorColor = sgRender.add(new ColorSetting.Builder()
        .name("minor-color")
        .defaultValue(new SettingColor(85, 255, 255))
        .build()
    );

    private final Setting<SettingColor> timeColor = sgRender.add(new ColorSetting.Builder()
        .name("time-color")
        .defaultValue(new SettingColor(170, 170, 170))
        .build()
    );

    private static class Entry {
        String event;
        long timestamp;
        String type;
    }

    private volatile List<Entry> schedule = List.of();
    private volatile String error;
    private volatile boolean fetching;
    private long lastFetch;

    public UpcomingEvents() {
        super(GoldenHeadAddon.PIT, "upcoming-events", "Shows the next minor and major Pit events in the top left (schedule from brookeafk.com).");
    }

    @Override
    public void onActivate() {
        lastFetch = 0;
    }

    private void fetchIfDue() {
        long now = System.currentTimeMillis();
        if (fetching || now - lastFetch < refreshMinutes.get() * 60_000L) return;
        lastFetch = now;
        fetching = true;
        MeteorExecutor.execute(() -> {
            try {
                List<Entry> list = Http.get(URL + "?v=" + now).sendJson(new TypeToken<List<Entry>>() {}.getType());
                if (list == null) error = "couldn't reach brookeafk";
                else {
                    schedule = list;
                    error = null;
                }
            } catch (Exception e) {
                error = "couldn't read schedule";
            } finally {
                fetching = false;
            }
        });
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        fetchIfDue();
        if (mc.options.hudHidden) return;

        List<Line> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (majors.get()) section(lines, "Major events", "major", majorColor.get(), now);
        if (minors.get()) section(lines, "Minor events", "minor", minorColor.get(), now);
        if (lines.isEmpty()) return;

        var matrices = event.drawContext.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x.get(), y.get());
        matrices.scale(scale.get().floatValue(), scale.get().floatValue());

        int lineY = 0;
        for (Line line : lines) {
            int lineX = 0;
            for (int i = 0; i < line.parts.size(); i++) {
                String text = line.parts.get(i);
                event.drawContext.drawTextWithShadow(mc.textRenderer, text, lineX, lineY, line.colors.get(i).getPacked());
                lineX += mc.textRenderer.getWidth(text);
            }
            lineY += mc.textRenderer.fontHeight + 1;
        }
        matrices.popMatrix();
    }

    private void section(List<Line> lines, String title, String type, SettingColor color, long now) {
        if (!lines.isEmpty()) lines.add(new Line());
        lines.add(new Line().add(title, titleColor.get()));

        int shown = 0;
        for (Entry e : schedule) {
            if (shown >= count.get()) break;
            if (e.event == null || !type.equalsIgnoreCase(e.type) || e.timestamp <= now) continue;
            lines.add(new Line().add(e.event + " ", color).add(formatIn(e.timestamp - now), timeColor.get()));
            shown++;
        }
        if (shown == 0) lines.add(new Line().add(error != null ? error : fetching || schedule.isEmpty() ? "loading..." : "none scheduled", timeColor.get()));
    }

    private static String formatIn(long ms) {
        long s = ms / 1000;
        long h = s / 3600, m = s / 60 % 60, sec = s % 60;
        if (h > 0) return String.format("%dh %02dm", h, m);
        return String.format("%dm %02ds", m, sec);
    }

    private static class Line {
        final List<String> parts = new ArrayList<>();
        final List<SettingColor> colors = new ArrayList<>();

        Line add(String text, SettingColor color) {
            parts.add(text);
            colors.add(color);
            return this;
        }
    }

    @Override
    public String getInfoString() {
        long now = System.currentTimeMillis();
        for (Entry e : schedule) if (e.timestamp > now && "major".equalsIgnoreCase(e.type)) return e.event + " " + formatIn(e.timestamp - now);
        return null;
    }
}
