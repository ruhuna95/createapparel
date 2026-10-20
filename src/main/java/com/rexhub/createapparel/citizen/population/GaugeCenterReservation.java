package com.rexhub.createapparel.citizen.population;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

public record GaugeCenterReservation(UUID id, String dimension, BlockPos center, int reservedItemCount,
                                     long createdAt, long lastVerifiedAt, State state, String lastObservedDimension, int lastObservedItems) {
    public enum State { UNVERIFIED, OBSERVED, CONFLICT, DIMENSION_MISMATCH }
    public GaugeCenterReservation(UUID id, String dimension, BlockPos center, int reservedItemCount, long createdAt, long lastVerifiedAt, State state) {
        this(id, dimension, center, reservedItemCount, createdAt, lastVerifiedAt, state, "", -1);
    }
    public boolean conflicted() { return state == State.CONFLICT || state == State.DIMENSION_MISMATCH; }
    public GaugeCenterReservation verified(String observedDimension, int observedItems, long now) {
        if (observedItems < 0) throw new IllegalArgumentException("Observed item count cannot be negative");
        boolean mismatch = !dimension.isEmpty() && !observedDimension.isEmpty() && !dimension.equals(observedDimension);
        var next = mismatch || state == State.DIMENSION_MISMATCH ? State.DIMENSION_MISMATCH
                : state == State.CONFLICT || observedItems > reservedItemCount && reservedItemCount > 0 ? State.CONFLICT
                : observedItems > 0 ? State.OBSERVED : state;
        return new GaugeCenterReservation(id, dimension, center, reservedItemCount, createdAt, now, next, observedDimension, observedItems);
    }
    public GaugeCenterReservation remaining(int count) {
        return new GaugeCenterReservation(id, dimension, center, count, createdAt, lastVerifiedAt, state, lastObservedDimension, lastObservedItems);
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putUUID("Id", id); tag.putLong("Center", center.asLong());
        tag.putString("Dimension", dimension); tag.putInt("Items", reservedItemCount);
        tag.putLong("CreatedAt", createdAt); tag.putLong("LastVerifiedAt", lastVerifiedAt); tag.putString("State", state.name());
        tag.putString("LastObservedDimension", lastObservedDimension); tag.putInt("LastObservedItems", lastObservedItems); return tag;
    }
    public static GaugeCenterReservation load(CompoundTag tag) {
        State state;
        try { state = State.valueOf(tag.getString("State")); } catch (IllegalArgumentException ignored) { state = State.UNVERIFIED; }
        return new GaugeCenterReservation(tag.getUUID("Id"), tag.getString("Dimension"), BlockPos.of(tag.getLong("Center")),
                tag.getInt("Items") > 0 ? tag.getInt("Items") : -1, tag.getLong("CreatedAt"), tag.getLong("LastVerifiedAt"), state,
                tag.getString("LastObservedDimension"), tag.contains("LastObservedItems") ? Math.max(-1, tag.getInt("LastObservedItems")) : -1);
    }
}
