package com.rexhub.createapparel.apparel;

import com.rexhub.createapparel.network.ModNetwork;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;

final class ApparelInventoryProvider implements ICapabilitySerializable<CompoundTag> {
    private final ApparelInventory inventory = new ApparelInventory();
    private LazyOptional<ApparelInventory> optional = createOptional();

    ApparelInventoryProvider(Player player) {
        inventory.setChangeListener(() -> {
            if (player instanceof ServerPlayer serverPlayer) {
                ModNetwork.sendApparelInventory(serverPlayer, inventory);
            }
        });
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability != ApparelCapabilities.PLAYER_APPAREL) {
            return LazyOptional.empty();
        }
        if (!optional.isPresent()) {
            optional = createOptional();
        }
        return optional.cast();
    }

    @Override
    public CompoundTag serializeNBT() {
        return inventory.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        inventory.deserializeNBT(tag);
    }

    void invalidate() {
        optional.invalidate();
    }

    private LazyOptional<ApparelInventory> createOptional() {
        return LazyOptional.of(() -> inventory);
    }
}
