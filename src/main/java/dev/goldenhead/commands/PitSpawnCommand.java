package dev.goldenhead.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.goldenhead.utils.SpawnArea;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.util.math.Vec3d;

/** .pitspawn - show the learned spawn point; .pitspawn set - use where you stand; .pitspawn clear - learn again. */
public class PitSpawnCommand extends Command {
    public PitSpawnCommand() {
        super("pitspawn", "Shows, sets (where you stand) or clears the Pit spawn used to skip spawn targets.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            Vec3d spawn = SpawnArea.get();
            if (spawn == null) warning("Spawn not known yet. It's learned on your next join/respawn, or use .pitspawn set while standing in spawn.");
            else info("Spawn: %.1f, %.1f, %.1f (radius %.0f, counts down to y %.1f). You are %sin spawn.",
                spawn.x, spawn.y, spawn.z, SpawnArea.RADIUS, spawn.y - SpawnArea.BELOW, SpawnArea.isInSpawn(mc.player) ? "" : "not ");
            return SINGLE_SUCCESS;
        });

        builder.then(literal("set").executes(context -> {
            SpawnArea.set(mc.player.getEntityPos());
            info("Spawn set to where you stand.");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(context -> {
            SpawnArea.set(null);
            info("Spawn cleared; it will be learned on your next join/respawn.");
            return SINGLE_SUCCESS;
        }));
    }
}
