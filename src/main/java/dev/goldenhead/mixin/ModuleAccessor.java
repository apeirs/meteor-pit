package dev.goldenhead.mixin;

import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Module.class, remap = false)
public interface ModuleAccessor {
    @Mutable
    @Accessor("category")
    void setCategory(Category category);
}
