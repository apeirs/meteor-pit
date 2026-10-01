package dev.goldenhead;

import dev.goldenhead.modules.AutoGoldenHead;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class GoldenHeadAddon extends MeteorAddon {
    @Override
    public void onInitialize() {
        Modules.get().add(new AutoGoldenHead());
    }

    @Override
    public String getPackage() {
        return "dev.goldenhead";
    }
}
