package dev.goldenhead;

import dev.goldenhead.commands.PitInfoCommand;
import dev.goldenhead.modules.AutoGoldenHead;
import dev.goldenhead.modules.Beast;
import dev.goldenhead.modules.CarePackage;
import dev.goldenhead.modules.DragonEgg;
import dev.goldenhead.modules.QuickMaths;
import dev.goldenhead.modules.TeamDeathmatch;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;

public class GoldenHeadAddon extends MeteorAddon {
    public static final Category PIT = new Category("Pit", Items.GOLDEN_APPLE.getDefaultStack());

    // Modules from other addons that get filed under Pit instead of their own category (see ModulesMixin).
    public static final String ZAIMBOT = "zgoly.meteorist.modules.zaimbot.ZAimbot";

    @Override
    public void onInitialize() {
        Modules.get().add(new AutoGoldenHead());
        Modules.get().add(new TeamDeathmatch());
        Modules.get().add(new Beast());
        Modules.get().add(new DragonEgg());
        Modules.get().add(new CarePackage());
        Modules.get().add(new QuickMaths());

        Commands.add(new PitInfoCommand());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(PIT);
    }

    @Override
    public String getPackage() {
        return "dev.goldenhead";
    }
}
