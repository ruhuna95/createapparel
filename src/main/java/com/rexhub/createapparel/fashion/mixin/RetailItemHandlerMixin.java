package com.rexhub.createapparel.fashion.mixin;

import com.rexhub.createapparel.fashion.RetailInspection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemStackHandler.class, remap = false)
public abstract class RetailItemHandlerMixin {
    @Inject(method = "setStackInSlot", at = @At("RETURN"))
    private void createapparel$slotChanged(int slot, ItemStack stack, CallbackInfo callback) {
        RetailInspection.handlerChanged((ItemStackHandler) (Object) this);
    }
    @Inject(method = "insertItem", at = @At("RETURN"))
    private void createapparel$inserted(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> callback) {
        if (!simulate && callback.getReturnValue().getCount() < stack.getCount()) RetailInspection.handlerChanged((ItemStackHandler) (Object) this);
    }
    @Inject(method = "extractItem", at = @At("RETURN"))
    private void createapparel$extracted(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> callback) {
        if (!simulate && !callback.getReturnValue().isEmpty()) RetailInspection.handlerChanged((ItemStackHandler) (Object) this);
    }
    @Inject(method = "deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void createapparel$loaded(CompoundTag tag, CallbackInfo callback) {
        RetailInspection.handlerChanged((ItemStackHandler) (Object) this);
    }
}
