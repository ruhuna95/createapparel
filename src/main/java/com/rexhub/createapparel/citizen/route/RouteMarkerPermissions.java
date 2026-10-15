package com.rexhub.createapparel.citizen.route;

import com.rexhub.createapparel.block.CitizenRouteMarkerBlock;
import com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity;
import com.rexhub.createapparel.item.SmartPhoneItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import java.util.UUID;

public final class RouteMarkerPermissions {
    private RouteMarkerPermissions() { }

    public static boolean holdingPhone(Player player) {
        return player.getMainHandItem().getItem() instanceof SmartPhoneItem || player.getOffhandItem().getItem() instanceof SmartPhoneItem;
    }

    public static boolean canEditRoutes(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND && player.getMainHandItem().getItem() instanceof SmartPhoneItem;
    }

    public static boolean canModify(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        return !player.isSpectator() && player.mayBuild() && level.isInWorldBounds(pos) && level.getWorldBorder().isWithinBounds(pos)
                && com.rexhub.createapparel.citizen.CitizenNavigation.hasLoadedRegion(level, pos, 0) && level.mayInteract(player, pos)
                && !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())
                && player.mayUseItemAt(pos, net.minecraft.core.Direction.UP,
                    player.getMainHandItem().getItem() instanceof SmartPhoneItem ? player.getMainHandItem() : player.getOffhandItem());
    }

    public static boolean canEdit(ServerPlayer player, UUID id, BlockPos pos) {
        return canModify(player, pos) && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64
                && player.level().getBlockEntity(pos) instanceof CitizenRouteMarkerBlockEntity entity && id.equals(entity.markerId());
    }

    public static boolean aimingAt(ServerPlayer player, BlockPos pos) {
        var hit = pick(player);
        return hit != null && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    @javax.annotation.Nullable public static BlockHitResult pick(Player player) {
        if (!com.rexhub.createapparel.citizen.CitizenNavigation.hasLoadedRegion(player.level(), player.blockPosition(), (int) Math.ceil(player.getBlockReach()))) return null;
        var eye = player.getEyePosition();
        return player.level().clip(new net.minecraft.world.level.ClipContext(eye, eye.add(player.getLookAngle().scale(player.getBlockReach())),
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
    }

    public static boolean canPlace(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        var pos = hit.getBlockPos().above();
        var level = player.serverLevel();
        return hit.getDirection() == net.minecraft.core.Direction.UP && player.isShiftKeyDown()
                && canEditRoutes(player, hand) && canModify(player, hit.getBlockPos())
                && canModify(player, pos) && aimingAt(player, hit.getBlockPos()) && topFace(player)
                && level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null && level.getFluidState(pos).isEmpty()
                && CitizenRouteMarkerBlock.supported(level, pos);
    }

    private static boolean topFace(ServerPlayer player) {
        var hit = pick(player);
        return hit != null && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getDirection() == net.minecraft.core.Direction.UP;
    }
}
