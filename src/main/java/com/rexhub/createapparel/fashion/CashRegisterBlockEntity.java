package com.rexhub.createapparel.fashion;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CashRegisterBlockEntity extends BlockEntity implements com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    private UUID registerId = UUID.randomUUID();
    private final List<RetailStorageLink> storages = new ArrayList<>();
    private boolean storagesDirty = true;
    @Nullable private RetailInspection.Status inspection;
    public CashRegisterBlockEntity(BlockPos pos, BlockState state) { super(FashionRegistries.CASH_REGISTER_ENTITY.get(), pos, state); }
    public UUID registerId() { return registerId; }
    public PurchasedRegisterRef reference() {
        if (level == null) throw new IllegalStateException("Unattached register");
        return new PurchasedRegisterRef(registerId, level.dimension().location(), worldPosition);
    }
    public boolean linkStorage(BlockPos pos) {
        if (pos.equals(worldPosition) || pos.distSqr(worldPosition) > 64 * 64 || level == null || !level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        refreshStorages();
        var target = RetailStorageLink.at(pos);
        if (!target.refresh(level)) return false;
        if (storages.removeIf(link -> link.overlaps(target))) {
            RetailStorageLinks.track(this); changed(); return true;
        }
        if (target.handler() == null || target.handler().getSlots() > com.rexhub.createapparel.config.ModConfigs.COMMON.fashion.storageSlotLimit.get() || storages.size() >= 8) return false;
        storages.add(target);
        RetailStorageLinks.track(this);
        changed(); return true;
    }
    public List<BlockPos> linkedStoragePositions() { return storages.stream().flatMap(link -> link.positions().stream()).distinct().toList(); }
    public List<AABB> linkedStorageBounds() { return storages.stream().filter(link -> !link.positions().isEmpty()).map(RetailStorageLink::bounds).toList(); }
    @Nullable public AABB linkedStorageBounds(BlockPos pos) {
        for (var link : storages) if (link.positions().contains(pos)) return link.bounds();
        return null;
    }
    public void invalidateStorages() {
        storagesDirty = true;
        RetailInspection.storageChanged(this);
    }
    public void refreshStorages() {
        if (!storagesDirty || level == null) return;
        storagesDirty = false;
        var before = storageTag();
        storages.removeIf(link -> link.positions().isEmpty() || !link.refresh(level));
        for (int index = 0; index < storages.size(); index++) {
            var link = storages.get(index);
            for (int other = storages.size() - 1; other > index; other--)
                if (link.overlaps(storages.get(other))) storages.remove(other);
        }
        RetailStorageLinks.track(this);
        if (!before.equals(storageTag())) changed();
    }
    private ListTag storageTag() {
        var result = new ListTag();
        for (var link : storages) result.add(link.save());
        return result;
    }
    public List<IItemHandler> storages() {
        List<IItemHandler> handlers = new ArrayList<>();
        if (level == null) return handlers;
        refreshStorages();
        for (var link : storages) {
            var handler = link.handler();
            if (handler != null && handler.getSlots() <= com.rexhub.createapparel.config.ModConfigs.COMMON.fashion.storageSlotLimit.get()
                    && !handlers.contains(handler)) handlers.add(handler);
        }
        return handlers;
    }
    public boolean isOpen() {
        return level != null && !storages().isEmpty() && hasCashier();
    }
    public List<BlockPos> cashierSeatPositions() {
        BlockPos front = worldPosition.relative(getBlockState().getValue(HorizontalDirectionalBlock.FACING));
        return List.of(front, front.below());
    }
    public boolean hasCashier() {
        return level != null && cashierSeatPositions().stream().anyMatch(this::occupiedSeat);
    }
    private boolean occupiedSeat(BlockPos seatPos) {
        if (level == null) return false;
        if (!level.hasChunk(seatPos.getX() >> 4, seatPos.getZ() >> 4) || !(level.getBlockState(seatPos).getBlock() instanceof com.simibubi.create.content.contraptions.actors.seat.SeatBlock)) return false;
        return !level.getEntitiesOfClass(LivingEntity.class, new AABB(seatPos).inflate(0.1, 1, 0.1), entity ->
                entity.isAlive() && entity.getVehicle() instanceof com.simibubi.create.content.contraptions.actors.seat.SeatEntity
                        && entity.getVehicle().blockPosition().equals(seatPos)).isEmpty();
    }
    public void acceptInspection(RetailInspection.Status value) {
        inspection = value;
    }
    @Override public boolean addToGoggleTooltip(List<net.minecraft.network.chat.Component> tooltip, boolean isPlayerSneaking) {
        var lines = new ArrayList<net.minecraft.network.chat.Component>();
        if (inspection != null) inspection.addTooltip(lines);
        else lines.add(net.minecraft.network.chat.Component.translatable("tooltip.createapparel.register.checking"));
        for (var line : lines) new net.createmod.catnip.lang.LangBuilder(com.rexhub.createapparel.CreateApparel.MOD_ID).add(line).forGoggles(tooltip);
        return true;
    }
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
    @Override protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putUUID("RegisterId", registerId);
        tag.put("Storages", storageTag());
    }
    @Override protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        registerId = tag.hasUUID("RegisterId") ? tag.getUUID("RegisterId") : UUID.randomUUID();
        storages.clear();
        var links = tag.getList("Storages", CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(8, links.size()); index++) {
            var link = RetailStorageLink.load(links.getCompound(index), worldPosition);
            if (!link.positions().isEmpty() && !link.positions().contains(worldPosition)) storages.add(link);
        }
        storagesDirty = true;
        if (level instanceof ServerLevel) RetailStorageLinks.track(this);
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) FashionWorldIndex.get(server).addRegister(worldPosition);
        RetailStorageLinks.track(this);
    }
    @Override public void setRemoved() {
        RetailInspection.registerRemoved(this);
        RetailStorageLinks.remove(this);
        if (level instanceof ServerLevel server) FashionWorldIndex.get(server).removeRegister(worldPosition);
        super.setRemoved();
    }
}
