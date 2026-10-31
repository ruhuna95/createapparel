package com.rexhub.createapparel.photo.mixin;

import com.rexhub.createapparel.photo.ManualCraftingInput;
import net.minecraft.world.item.crafting.CraftingInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CraftingInput.class)
abstract class CraftingInputMixin implements ManualCraftingInput {
    @Unique private boolean createapparel$manual;

    @Override public boolean createapparel$isManual() { return createapparel$manual; }
    @Override public void createapparel$setManual(boolean manual) { createapparel$manual = manual; }
}
