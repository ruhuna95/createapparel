package com.rexhub.createapparel.network;

import com.rexhub.createapparel.fashion.CashRegisterBlockEntity;
import com.rexhub.createapparel.fashion.RetailInspection;
import com.rexhub.createapparel.init.ModItems;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public record RetailInspectionRequestPacket(BlockPos pos, boolean observe) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<RetailInspectionRequestPacket> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.rexhub.createapparel.CreateApparel.MOD_ID, "retail_inspection_request_packet"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, RetailInspectionRequestPacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), RetailInspectionRequestPacket::decode);

    @Override
    public Type<RetailInspectionRequestPacket> type() {
        return TYPE;
    }

    private static final java.util.Map<ServerPlayer, RetailInspectionRequestPacket> PENDING = new java.util.WeakHashMap<>();
    public RetailInspectionRequestPacket(BlockPos pos) { this(pos, true); }
    public void encode(FriendlyByteBuf buffer) { buffer.writeBlockPos(pos); buffer.writeBoolean(observe); }
    public static RetailInspectionRequestPacket decode(FriendlyByteBuf buffer) { return new RetailInspectionRequestPacket(buffer.readBlockPos(), buffer.readBoolean()); }

    public void handle(ServerPlayer player) {
        PENDING.put(player, this);
    }
    public static void flush(ServerPlayer player) {
        var request = PENDING.remove(player);
        if (request == null) return;
        RetailInspection.unwatch(player);
        if (request.observe() && canObserve(player, request.pos())
                && player.level().getBlockEntity(request.pos()) instanceof CashRegisterBlockEntity register)
            RetailInspection.watch(player, register);
    }
    public static void forget(ServerPlayer player) { PENDING.remove(player); RetailInspection.unwatch(player); }
    public static boolean canObserve(ServerPlayer player, BlockPos pos) {
        if (!player.isAlive() || player.hasDisconnected() || !player.isHolding(ModItems.SMART_PHONE.get()) && !GogglesItem.isWearingGoggles(player)) return false;
        var level = player.serverLevel();
        return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) <= 16 * 16
                && player.pick(16, 1, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(pos);
    }
}
