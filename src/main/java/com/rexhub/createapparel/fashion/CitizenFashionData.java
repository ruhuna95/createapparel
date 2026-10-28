package com.rexhub.createapparel.fashion;

import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

public final class CitizenFashionData {
    private boolean forceFashion;
    private long cooldownUntil;
    private final Map<GarmentSlot, PurchasedRegisterRef> purchases = new EnumMap<>(GarmentSlot.class);
    @Nullable private PurchasedRegisterRef lastRegister;
    private ItemStack desiredGarment = ItemStack.EMPTY;
    @Nullable private GarmentSlot desiredSlot;
    @Nullable private PurchasedRegisterRef desiredRegister;
    public boolean forceFashion() { return forceFashion; }
    public void setForceFashion(boolean value) { forceFashion = value; if (value) clearDesire(); }
    public long cooldownUntil() { return cooldownUntil; }
    public void cooldown(long until) { cooldownUntil = until; }
    @Nullable public PurchasedRegisterRef purchasedRegister(GarmentSlot slot) { return purchases.getOrDefault(slot, lastRegister); }
    public void clearPurchase(GarmentSlot slot) { purchases.remove(slot); }
    public ItemStack desiredGarment() { return desiredGarment.copy(); }
    @Nullable public GarmentSlot desiredSlot() { return desiredSlot; }
    @Nullable public PurchasedRegisterRef desiredRegister() { return desiredRegister; }
    public void desire(ItemStack garment, GarmentSlot slot, PurchasedRegisterRef register) {
        desiredGarment = garment.copyWithCount(1); desiredSlot = slot; desiredRegister = register;
    }
    public void clearDesire() { desiredGarment = ItemStack.EMPTY; desiredSlot = null; desiredRegister = null; }
    public void purchased(GarmentSlot slot, PurchasedRegisterRef register) { purchases.put(slot, register); lastRegister = register; }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("ForceFashion", forceFashion);
        tag.putLong("CooldownUntil", cooldownUntil);
        if (lastRegister != null) tag.put("LastRegister", lastRegister.save());
        CompoundTag slots = new CompoundTag();
        purchases.forEach((slot, register) -> slots.put(slot.getSerializedName(), register.save()));
        tag.put("Purchases", slots);
        return tag;
    }
    public void load(CompoundTag tag) {
        forceFashion = tag.getBoolean("ForceFashion"); cooldownUntil = Math.max(0, tag.getLong("CooldownUntil"));
        lastRegister = PurchasedRegisterRef.load(tag.getCompound("LastRegister"));
        purchases.clear(); clearDesire();
        for (var slot : GarmentSlot.values()) {
            var reference = PurchasedRegisterRef.load(tag.getCompound("Purchases").getCompound(slot.getSerializedName()));
            if (reference != null) purchases.put(slot, reference);
        }
    }
}
