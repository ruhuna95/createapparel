package com.rexhub.createapparel.advertisement;

import com.rexhub.createapparel.photo.AdvertisingPhotoData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class SignageBroadcasterBlockEntity extends BlockEntity {
    private String channel = "";
    private UUID owner;
    private final SimpleContainer photos = new SimpleContainer(AdvertisementScenes.MAX_SCENES) {
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return AdvertisingPhotoData.read(stack) != null; }
        @Override public void setChanged() { super.setChanged(); SignageBroadcasterBlockEntity.this.setChanged(); }
    };

    public SignageBroadcasterBlockEntity(BlockPos pos, BlockState state) { super(AdvertisementRegistries.BROADCASTER_ENTITY.get(), pos, state); }
    public String channel() { return channel; }
    public SimpleContainer photos() { return photos; }
    public void initialize(Player player) { if (owner == null) { owner = player.getUUID(); setChanged(); } }
    public boolean mayEdit(Player player) {
        return player.getAbilities().mayBuild && !player.isSpectator() && (owner == null || owner.equals(player.getUUID()) || player.isCreative() || player.hasPermissions(2));
    }
    public boolean configure(net.minecraft.server.level.ServerPlayer player, String channel) {
        if (!mayEdit(player) || !channel.isEmpty() && !AdvertisementNetworkSavedData.validChannel(channel)) return false;
        var scenes = new java.util.ArrayList<AdvertisementSnapshot>();
        for (int index = 0; index < photos.getContainerSize(); index++) {
            var photo = AdvertisingPhotoData.read(photos.getItem(index));
            if (photo != null) scenes.add(AdvertisementSnapshot.photo(photo));
        }
        if (!channel.isEmpty() && !AdvertisementNetworkSavedData.get(player.serverLevel()).publish(player, channel,
                scenes.isEmpty() ? AdvertisementScenes.DEFAULTS : scenes)) return false;
        this.channel = channel; initialize(player); setChanged(); return true;
    }
    @Override protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putInt("Version", 1); tag.putString("Channel", channel);
        var items = new net.minecraft.nbt.ListTag();
        for (int slot = 0; slot < photos.getContainerSize(); slot++) if (!photos.getItem(slot).isEmpty()) {
            var item = (CompoundTag) photos.getItem(slot).save(registries); item.putByte("Slot", (byte) slot); items.add(item);
        }
        tag.put("Photos", items); if (owner != null) tag.putUUID("Owner", owner);
    }
    @Override protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); channel = AdvertisementNetworkSavedData.validChannel(tag.getString("Channel")) ? tag.getString("Channel") : "";
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        photos.clearContent();
        var items = tag.getList("Photos", CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(items.size(), AdvertisementScenes.MAX_SCENES); index++) {
            var item = items.getCompound(index); int slot = item.getByte("Slot") & 255;
            if (slot < photos.getContainerSize() && photos.getItem(slot).isEmpty()) photos.setItem(slot, com.rexhub.createapparel.item.ItemData.load(registries, item));
        }
    }
}
