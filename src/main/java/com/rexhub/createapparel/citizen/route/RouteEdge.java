package com.rexhub.createapparel.citizen.route;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

public record RouteEdge(UUID id, UUID a, UUID b, boolean conditional) {
    public RouteEdge {
        if (a.equals(b)) throw new IllegalArgumentException("Self edge");
    }

    public UUID other(UUID endpoint) {
        if (a.equals(endpoint)) return b;
        if (b.equals(endpoint)) return a;
        throw new IllegalArgumentException("Not an edge endpoint");
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("A", a);
        tag.putUUID("B", b);
        tag.putBoolean("Conditional", conditional);
        return tag;
    }

    public static RouteEdge load(CompoundTag tag) {
        return new RouteEdge(tag.getUUID("Id"), tag.getUUID("A"), tag.getUUID("B"), tag.getBoolean("Conditional"));
    }
}
