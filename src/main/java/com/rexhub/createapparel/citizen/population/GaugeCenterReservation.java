package com.rexhub.createapparel.citizen.population;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

public record GaugeCenterReservation(UUID id, String dimension, BlockPos center, int reservedItemCount,
                                     long createdAt, long lastVerifiedAt, State state) {
    public enum State { UNVERIFIED, OBSERVED, CONFLICT }
    public GaugeCenterReservation verified(String dimension, int items, long now) {
        return new GaugeCenterReservation(id, dimension, center, reservedItemCount, createdAt, now,
                items > reservedItemCount && reservedItemCount > 0 || state == State.CONFLICT ? State.CONFLICT : State.OBSERVED);
    }
    public GaugeCenterReservation remaining(int count) {
        return new GaugeCenterReservation(id, dimension, center, count, createdAt, lastVerifiedAt, state);
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putUUID("Id", id); tag.putLong("Center", center.asLong());
        tag.putString("Dimension", dimension); tag.putInt("Items", reservedItemCount);
        tag.putLong("CreatedAt", createdAt); tag.putLong("LastVerifiedAt", lastVerifiedAt); tag.putString("State", state.name()); return tag;
    }
    public static GaugeCenterReservation load(CompoundTag tag) {
        State state;
        try { state = State.valueOf(tag.getString("State")); } catch (IllegalArgumentException ignored) { state = State.UNVERIFIED; }
        return new GaugeCenterReservation(tag.getUUID("Id"), tag.getString("Dimension"), BlockPos.of(tag.getLong("Center")),
                tag.getInt("Items") > 0 ? tag.getInt("Items") : -1, tag.getLong("CreatedAt"), tag.getLong("LastVerifiedAt"), state);
    }
}
