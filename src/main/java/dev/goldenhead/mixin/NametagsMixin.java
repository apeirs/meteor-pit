package dev.goldenhead.mixin;

import dev.goldenhead.modules.Beast;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.systems.modules.render.Nametags;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Beasts get their own name color. nameColor is the first Color local in renderNametagPlayer.
@Mixin(value = Nametags.class, remap = false)
public abstract class NametagsMixin {
    @Unique
    private PlayerEntity goldenhead$player;

    @Inject(method = "renderNametagPlayer", at = @At("HEAD"))
    private void goldenhead$capturePlayer(Render2DEvent event, PlayerEntity player, boolean shadow, CallbackInfo ci) {
        goldenhead$player = player;
    }

    @ModifyVariable(method = "renderNametagPlayer", at = @At("STORE"), ordinal = 0)
    private Color goldenhead$beastColor(Color color) {
        if (goldenhead$player == null) return color;
        SettingColor beast = Beast.nametagColorFor(goldenhead$player);
        return beast != null ? beast : color;
    }
}
