package dev.goldenhead.mixin;

import dev.goldenhead.modules.Beast;
import dev.goldenhead.modules.TeamDeathmatch;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static meteordevelopment.meteorclient.MeteorClient.mc;

// @Pseudo: Meteorist is optional; without it this mixin is simply never applied.
@Pseudo
@Mixin(targets = "zgoly.meteorist.modules.zaimbot.ZAimbot", remap = false)
public abstract class ZAimbotMixin {
    @Shadow
    @Final
    private Setting<SortPriority> priority;

    @Shadow
    private boolean entityCheck(Entity entity) {
        throw new AssertionError();
    }

    @Shadow
    private void aim(LivingEntity player, Entity target) {
        throw new AssertionError();
    }

    // TDM: drop teammates from the target list.
    @Inject(method = "entityCheck", at = @At("RETURN"), cancellable = true)
    private void goldenhead$teamFilter(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !TeamDeathmatch.allowTarget(entity)) cir.setReturnValue(false);
    }

    // Beast event: aim at a valid beast first; otherwise ZAimbot picks a target as usual.
    @Inject(method = "onRender3D", at = @At("HEAD"), cancellable = true)
    private void goldenhead$beastFirst(Render3DEvent event, CallbackInfo ci) {
        if (mc.player == null || mc.world == null || !Beast.shouldPrioritize()) return;

        Entity beast = TargetUtils.get(entity -> Beast.isBeast(entity) && entityCheck(entity), priority.get());
        if (beast == null) return;

        aim(mc.player, beast);
        ci.cancel();
    }
}
