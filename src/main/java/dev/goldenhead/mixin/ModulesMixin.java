package dev.goldenhead.mixin;

import dev.goldenhead.GoldenHeadAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Re-files ZAimbot under Pit as Meteorist registers it, so it is moved rather than duplicated.
@Mixin(value = Modules.class, remap = false)
public abstract class ModulesMixin {
    @Inject(method = "add", at = @At("HEAD"))
    private void goldenhead$moveToPit(Module module, CallbackInfo ci) {
        if (module.getClass().getName().equals(GoldenHeadAddon.ZAIMBOT)) {
            ((ModuleAccessor) module).setCategory(GoldenHeadAddon.PIT);
            LoggerFactory.getLogger("golden-head-addon").info("Moved {} to the Pit category", module.name);
        }
    }
}
