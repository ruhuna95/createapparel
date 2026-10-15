package com.rexhub.createapparel.citizen.route;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.UUID;

public record RouteMarker(UUID id, BlockPos pos, RouteMarkerType type, double horizontalRadius,
                          double verticalTolerance, @Nullable UUID districtId, CrossingCondition condition) {
    public RouteMarker {
        pos = pos.immutable();
        if (!RouteSettings.validRegion(horizontalRadius, verticalTolerance)) throw new IllegalArgumentException("Invalid marker region");
        if (type != RouteMarkerType.GATEWAY) districtId = null;
        if (type != RouteMarkerType.CROSSING) condition = CrossingCondition.UNSET;
    }

    public static RouteMarker create(UUID id, BlockPos pos, RouteMarkerType type) {
        return new RouteMarker(id, pos, type, RouteSettings.DEFAULT_RADIUS, RouteSettings.DEFAULT_VERTICAL_TOLERANCE,
                null, CrossingCondition.UNSET);
    }

    public Vec3 center() { return Vec3.atBottomCenterOf(pos); }

    public boolean contains(Vec3 point) {
        var delta = point.subtract(center());
        return delta.x * delta.x + delta.z * delta.z <= horizontalRadius * horizontalRadius
                && Math.abs(delta.y) <= verticalTolerance;
    }

    public RouteMarker withType(RouteMarkerType next) {
        return new RouteMarker(id, pos, next, horizontalRadius, verticalTolerance, null, CrossingCondition.UNSET);
    }

    public RouteMarker withRegion(double radius, double tolerance) {
        return new RouteMarker(id, pos, type, radius, tolerance, districtId, condition);
    }

    public RouteMarker withDistrict(@Nullable UUID district) {
        return new RouteMarker(id, pos, type, horizontalRadius, verticalTolerance, district, condition);
    }

    public RouteMarker withCondition(CrossingCondition next) {
        return new RouteMarker(id, pos, type, horizontalRadius, verticalTolerance, districtId, next);
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putLong("Pos", pos.asLong());
        tag.putString("Type", type.name());
        tag.putDouble("HorizontalRadius", horizontalRadius);
        tag.putDouble("VerticalTolerance", verticalTolerance);
        if (districtId != null) tag.putUUID("DistrictId", districtId);
        tag.put("Condition", condition.save());
        return tag;
    }

    public static RouteMarker load(CompoundTag tag) {
        return new RouteMarker(tag.getUUID("Id"), BlockPos.of(tag.getLong("Pos")), RouteMarkerType.valueOf(tag.getString("Type")),
                tag.getDouble("HorizontalRadius"), tag.getDouble("VerticalTolerance"),
                tag.hasUUID("DistrictId") ? tag.getUUID("DistrictId") : null, CrossingCondition.load(tag.getCompound("Condition")));
    }
}
