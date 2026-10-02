package dev.goldenhead.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.goldenhead.modules.Beast;
import dev.goldenhead.modules.TeamDeathmatch;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import dev.goldenhead.mixin.TextDisplayAccessor;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.*;
import net.minecraft.text.Text;

import java.util.Comparator;

/**
 * .pitinfo - prints what the server tells the client about the player under your crosshair (or the nearest one)
 * and the sidebar, so we can see how an event marks the beast / teams on this server.
 */
public class PitInfoCommand extends Command {
    public PitInfoCommand() {
        super("pitinfo", "Shows how the server marks the player you look at (name, tab, team, health, armor, holograms) and the sidebar.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            PlayerEntity target = mc.targetedEntity instanceof PlayerEntity p ? p : mc.world.getPlayers().stream()
                .filter(p -> p != mc.player)
                .min(Comparator.comparingDouble(p -> p.squaredDistanceTo(mc.player)))
                .orElse(null);

            sidebar();
            if (target == null) {
                warning("No other player nearby.");
                return SINGLE_SUCCESS;
            }

            info("--- %s ---", target.getName().getString());
            info("Display name: %s", target.getDisplayName().getString());
            Team team = target.getScoreboardTeam();
            if (team != null) info("Team: %s, color %s, prefix '%s', suffix '%s'", team.getName(), team.getColor().getName(), team.getPrefix().getString(), team.getSuffix().getString());

            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(target.getUuid());
            if (entry != null && entry.getDisplayName() != null) info("Tab name: %s", entry.getDisplayName().getString());

            info("Health %.1f / %.1f, absorption %.1f", target.getHealth(), target.getMaxHealth(), target.getAbsorptionAmount());
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND}) {
                var stack = target.getEquippedStack(slot);
                if (!stack.isEmpty()) info("%s: %s \"%s\"", slot.getName(), Registries.ITEM.getId(stack.getItem()).getPath(), stack.getName().getString());
            }
            info("TDM team (hat): %s, beast now: %s", TeamDeathmatch.fromHat(target), Beast.isBeast(target));

            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof PlayerEntity || entity.squaredDistanceTo(target) > 9) continue;
                Text label = entity instanceof DisplayEntity.TextDisplayEntity display ? ((TextDisplayAccessor) display).goldenhead$getText() : entity.getCustomName();
                if (label != null) info("Hologram (%s): %s", Registries.ENTITY_TYPE.getId(entity.getType()).getPath(), label.getString());
            }
            return SINGLE_SUCCESS;
        });
    }

    private void sidebar() {
        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective sidebar = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (sidebar == null) return;

        info("--- Sidebar: %s ---", sidebar.getDisplayName().getString());
        for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(sidebar)) {
            info(Team.decorateName(scoreboard.getScoreHolderTeam(entry.owner()), entry.name()).getString());
        }
    }
}
