package com.rexhub.createapparel.client.fashion;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.entity.Mannequin;
import com.rexhub.createapparel.advertisement.AdvertisementPanelBlockEntity;
import com.rexhub.createapparel.fashion.CashRegisterBlockEntity;
import com.rexhub.createapparel.fashion.RetailDisplayIndex;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.network.ModNetwork;
import com.rexhub.createapparel.network.RetailInspectionPacket;
import com.rexhub.createapparel.network.RetailInspectionRequestPacket;
import com.rexhub.createapparel.network.RetailLinkSessionPacket;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.*;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class ClientRetailView {
    @Nullable private static CashRegisterBlockEntity selected;
    @Nullable private static RetailLinkSessionPacket session;
    @Nullable private static SettingOutline hovered;
    @Nullable private static RetailDisplayIndex.Bucket bucket;
    private static long revision = -1;
    private static Iterator<Object> sources = Collections.emptyIterator();
    private static final Set<Object> outlines = new HashSet<>();
    private static final String SEAT = "apparel_retail_seat";
    private static final String LOWER_SEAT = "apparel_retail_lower_seat";
    record LinkOutline(Object source) { }
    record SettingOutline(Object source) { }

    private ClientRetailView() { }

    public static void accept(RetailInspectionPacket packet) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.dimension().location().equals(packet.dimension())
                && mc.level.getBlockEntity(packet.pos()) instanceof CashRegisterBlockEntity register
                && register.registerId().equals(packet.registerId())) register.acceptInspection(packet.status());
    }

    public static void accept(RetailLinkSessionPacket packet) {
        clearHovered();
        session = packet.register() == null ? null : packet;
    }

    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (session != null && (mc.player == null || mc.level == null || session.register() == null
                || !mc.level.dimension().location().equals(session.register().dimension())
                || mc.player.getInventory().selected != session.slot() || !mc.player.getItemInHand(session.hand()).is(ModItems.SMART_PHONE.get()))) {
            session = null;
            clearHovered();
        }
        if (mc.level == null || mc.player == null || mc.screen != null || mc.options.hideGui
                || !mc.player.isHolding(ModItems.SMART_PHONE.get()) && !GogglesItem.isWearingGoggles(mc.player)) {
            clear();
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !(mc.level.getBlockEntity(hit.getBlockPos()) instanceof CashRegisterBlockEntity register)) {
            if (selected != null || !outlines.isEmpty()) clear();
            showSettingTarget(mc);
            return;
        }
        clearHovered();
        if (selected != register) {
            clear(); selected = register;
            ModNetwork.requestRetailInspection(new RetailInspectionRequestPacket(register.getBlockPos()));
        }
        if (!mc.player.isHolding(ModItems.SMART_PHONE.get())) { clearOutlines(); return; }
        var linked = RetailDisplayIndex.get(mc.level).linked(register.reference());
        if (bucket != linked || linked != null && revision != linked.revision()) {
            clearOutlines();
            bucket = linked;
            revision = linked == null ? -1 : linked.revision();
            sources = linked == null ? Collections.emptyIterator() : linked.sources().iterator();
        }
        var seats = register.cashierSeatPositions();
        showSeat(SEAT, seats.get(0));
        showSeat(LOWER_SEAT, seats.get(1));
        var bounds = register.linkedStorageBounds();
        for (int index = 0; index < bounds.size(); index++) {
            var box = bounds.get(index);
            var pos = BlockPos.containing(box.minX, box.minY, box.minZ);
            if (mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4))
                outline(new StorageOutline(index), box, 0x42B8E8, 2);
        }
        if (linked == null) return;
        if (!sources.hasNext()) sources = linked.sources().iterator();
        int ttl = Math.max(2, (linked.sources().size() + 63) / 64 + 2);
        for (int count = 0; count < 64 && sources.hasNext(); count++) {
            var source = sources.next();
            outline(source, RetailDisplayIndex.bounds(source), source instanceof Mannequin ? 0xD886E8 : 0xF3B840, ttl);
        }
    }

    record StorageOutline(int index) { }

    private static void showSettingTarget(Minecraft mc) {
        var level = mc.level;
        if (level == null || session == null || session.register() == null) { clearHovered(); return; }
        var reference = session.register();
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Mannequin mannequin
                && mannequin.isAlive() && reference.equals(mannequin.mannequinState().linkedRegister())) {
            showHovered(mannequin, RetailDisplayIndex.bounds(mannequin), 0xD886E8);
            return;
        }
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            var pos = hit.getBlockPos();
            if (level.getBlockEntity(pos) instanceof AdvertisementPanelBlockEntity panel) {
                var controller = panel.controller();
                if (controller != null && reference.equals(controller.linkedRegister())) {
                    showHovered(controller, RetailDisplayIndex.bounds(controller), 0xF3B840);
                    return;
                }
            }
            var registerPos = reference.lastKnownPos();
            if (level.hasChunk(registerPos.getX() >> 4, registerPos.getZ() >> 4)
                    && level.getBlockEntity(registerPos) instanceof CashRegisterBlockEntity register
                    && reference.equals(register.reference())) {
                var bounds = register.linkedStorageBounds(pos);
                if (bounds != null) { showHovered(bounds, bounds, 0x42B8E8); return; }
                if (register.cashierSeatPositions().contains(pos) && level.getBlockState(pos).getBlock() instanceof SeatBlock) {
                    showHovered(pos, new AABB(pos), 0x75D36B);
                    return;
                }
            }
        }
        clearHovered();
    }

    private static void showHovered(Object source, AABB bounds, int color) {
        var key = new SettingOutline(source);
        if (!key.equals(hovered)) clearHovered();
        hovered = key;
        Outliner.getInstance().showAABB(key, bounds.inflate(0.02), 2).colored(color).lineWidth(1 / 32F);
    }

    private static void clearHovered() {
        if (hovered != null) Outliner.getInstance().remove(hovered);
        hovered = null;
    }

    private static void showSeat(Object key, BlockPos pos) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && level.getBlockState(pos).getBlock() instanceof SeatBlock)
            outline(key, new AABB(pos), 0x75D36B, 2);
    }

    private static void outline(Object source, AABB box, int color, int ttl) {
        var key = new LinkOutline(source);
        outlines.add(key);
        Outliner.getInstance().showAABB(key, box.inflate(0.02), ttl).colored(color).lineWidth(1 / 32F);
    }

    private static void clearOutlines() {
        outlines.forEach(Outliner.getInstance()::remove);
        outlines.clear();
    }

    private static void clear() {
        if (selected != null && Minecraft.getInstance().getConnection() != null)
            ModNetwork.requestRetailInspection(new RetailInspectionRequestPacket(selected.getBlockPos(), false));
        clearOutlines(); clearHovered(); selected = null; bucket = null; revision = -1; sources = Collections.emptyIterator();
    }

    @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) { clear(); session = null; }
    }
}
