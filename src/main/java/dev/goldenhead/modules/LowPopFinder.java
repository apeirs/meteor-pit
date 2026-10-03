package dev.goldenhead.modules;

import dev.goldenhead.MeteorPitAddon;
import dev.goldenhead.utils.PitUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundEvents;

import java.util.Locale;

/**
 * Hops Pit lobbies until one has at most {@link #maxPlayers} real players: join Pit, let the tab list fill,
 * count, and if it's too full go back to the lobby and try again. Commands are spaced out so the server
 * doesn't kick for spam, and the attempt cap stops it from looping forever.
 */
public class LowPopFinder extends Module {
    private enum State { Check, Joining, Measuring, Leaving, Cooldown }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    private final Setting<Integer> maxPlayers = sgGeneral.add(new IntSetting.Builder()
        .name("max-players")
        .description("Stop in the first Pit lobby with this many real players or fewer (you included).")
        .defaultValue(15)
        .range(1, 200)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<String> joinCommand = sgGeneral.add(new StringSetting.Builder()
        .name("join-command")
        .description("Command that sends you to a Pit lobby.")
        .defaultValue("/play pit")
        .build()
    );

    private final Setting<String> leaveCommand = sgGeneral.add(new StringSetting.Builder()
        .name("leave-command")
        .description("Command that takes you out of Pit.")
        .defaultValue("/lobby")
        .build()
    );

    private final Setting<Integer> maxAttempts = sgGeneral.add(new IntSetting.Builder()
        .name("max-attempts")
        .description("Give up after this many joins. 0 = never.")
        .defaultValue(30)
        .range(0, 500)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<String> pitKeyword = sgAdvanced.add(new StringSetting.Builder()
        .name("pit-sidebar-keyword")
        .description("You're in Pit when the sidebar contains this.")
        .defaultValue("pit")
        .build()
    );

    private final Setting<Integer> settleMs = sgAdvanced.add(new IntSetting.Builder()
        .name("settle-ms")
        .description("Wait this long after arriving before counting, so the tab list is complete.")
        .defaultValue(2500)
        .range(500, 15000)
        .sliderRange(500, 6000)
        .build()
    );

    private final Setting<Integer> commandGapMs = sgAdvanced.add(new IntSetting.Builder()
        .name("command-gap-ms")
        .description("Wait between leaving and the next join, to stay under the server's command spam limit.")
        .defaultValue(2000)
        .range(0, 15000)
        .sliderRange(0, 6000)
        .build()
    );

    private final Setting<Integer> timeoutMs = sgAdvanced.add(new IntSetting.Builder()
        .name("timeout-ms")
        .description("If a join or leave hasn't happened after this long, try again.")
        .defaultValue(10000)
        .range(2000, 60000)
        .sliderRange(2000, 30000)
        .build()
    );

    private State state;
    private long stateSince;
    private int attempts;
    private int lastCount = -1;
    private ClientWorld lastWorld;

    public LowPopFinder() {
        super(MeteorPitAddon.PIT, "low-pop-finder", "Hops Pit lobbies (join, count, /lobby, repeat) until one has few enough players.");
    }

    @Override
    public void onActivate() {
        attempts = 0;
        lastCount = -1;
        lastWorld = mc.world;
        set(State.Check);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return; // mid server switch
        long now = System.currentTimeMillis();
        long inState = now - stateSince;

        boolean worldChanged = mc.world != lastWorld;
        lastWorld = mc.world;
        boolean inPit = PitUtils.sidebarContains(pitKeyword.get().toLowerCase(Locale.ROOT).trim());

        switch (state) {
            case Check -> {
                if (inPit) set(State.Measuring);
                else join();
            }
            case Joining -> {
                if (inPit) set(State.Measuring);
                else if (inState > timeoutMs.get()) join();
            }
            case Measuring -> {
                if (worldChanged) set(State.Measuring); // moved again (e.g. server sent us elsewhere): restart settle
                else if (!inPit) set(State.Check);
                else if (inState >= settleMs.get()) measure();
            }
            case Leaving -> {
                if (!inPit || inState > timeoutMs.get()) set(State.Cooldown);
            }
            case Cooldown -> {
                if (inState >= commandGapMs.get()) join();
            }
        }
    }

    private void measure() {
        lastCount = PitUtils.realPlayerCount();
        if (lastCount <= maxPlayers.get()) {
            info("Found a lobby with %d players (limit %d) after %d joins.", lastCount, maxPlayers.get(), attempts);
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_LEVELUP, 1f));
            toggle();
            return;
        }

        info("%d players, over %d. Trying another lobby.", lastCount, maxPlayers.get());
        ChatUtils.sendPlayerMsg(leaveCommand.get(), false);
        set(State.Leaving);
    }

    private void join() {
        if (maxAttempts.get() > 0 && attempts >= maxAttempts.get()) {
            warning("No lobby with %d or fewer players after %d joins. Stopping.", maxPlayers.get(), attempts);
            toggle();
            return;
        }
        attempts++;
        ChatUtils.sendPlayerMsg(joinCommand.get(), false);
        set(State.Joining);
    }

    private void set(State next) {
        state = next;
        stateSince = System.currentTimeMillis();
    }

    @Override
    public String getInfoString() {
        if (state == null) return null;
        return lastCount >= 0 ? state.name() + " " + attempts + " (" + lastCount + ")" : state.name() + " " + attempts;
    }
}
