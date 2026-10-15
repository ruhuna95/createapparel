package com.rexhub.createapparel.block.entity;

import com.rexhub.createapparel.citizen.route.CitizenRouteManager;
import com.rexhub.createapparel.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;
import java.util.UUID;

public final class CitizenRouteMarkerBlockEntity extends BlockEntity {
    @Nullable private UUID markerId;

    public CitizenRouteMarkerBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.CITIZEN_ROUTE_MARKER.get(), pos, state); }
    @Nullable public UUID markerId() { return markerId; }
    public void setMarkerId(UUID id) { if (!id.equals(markerId)) { markerId = id; setChanged(); } }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) CitizenRouteManager.get(server).register(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (markerId != null) tag.putUUID("MarkerId", markerId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        markerId = tag.hasUUID("MarkerId") ? tag.getUUID("MarkerId") : null;
    }
}
