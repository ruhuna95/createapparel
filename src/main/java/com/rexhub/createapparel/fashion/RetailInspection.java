package com.rexhub.createapparel.fashion;

import com.rexhub.createapparel.advertisement.AdvertisementPanelBlock;
import com.rexhub.createapparel.advertisement.AdvertisementPanelBlockEntity;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.network.ModNetwork;
import com.rexhub.createapparel.network.RetailInspectionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.*;

public final class RetailInspection {
    private static final Map<ServerLevel, RetailInspection> LEVELS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, CashRegisterBlockEntity> WATCHING = new IdentityHashMap<>();
    private static final Map<IItemHandler, Set<ServerLevel>> HANDLER_LEVELS = new WeakHashMap<>();
    private final Map<CashRegisterBlockEntity, Probe> probes = new LinkedHashMap<>();
    private final Map<PurchasedRegisterRef, CashRegisterBlockEntity> references = new HashMap<>();
    private final Map<BlockPos, Set<CashRegisterBlockEntity>> blocks = new HashMap<>();
    private final Map<Long, Set<CashRegisterBlockEntity>> chunks = new HashMap<>();
    private final Map<IItemHandler, Set<CashRegisterBlockEntity>> handlers = new IdentityHashMap<>();
    private final Map<String, Set<CashRegisterBlockEntity>> channels = new HashMap<>();
    private final ArrayDeque<CashRegisterBlockEntity> waiting = new ArrayDeque<>();

    private RetailInspection() { }
    public static RetailInspection get(ServerLevel level) { return LEVELS.computeIfAbsent(level, ignored -> new RetailInspection()); }
    public static void unload(ServerLevel level) {
        WATCHING.entrySet().removeIf(entry -> entry.getValue().getLevel() == level);
        LEVELS.remove(level);
    }
    public static void registerRemoved(CashRegisterBlockEntity register) {
        if (!(register.getLevel() instanceof ServerLevel level)) return;
        var service = LEVELS.get(level);
        if (service != null) service.remove(register);
    }

    public static void storageChanged(CashRegisterBlockEntity register) {
        if (!(register.getLevel() instanceof ServerLevel level)) return;
        var service = LEVELS.get(level);
        if (service != null) service.invalidate(register);
    }

    public static void unwatch(ServerPlayer player) {
        var register = WATCHING.remove(player);
        if (register == null || !(register.getLevel() instanceof ServerLevel level)) return;
        var service = LEVELS.get(level);
        var probe = service == null ? null : service.probes.get(register);
        if (probe != null && probe.watchers.remove(player) && probe.watchers.isEmpty() && probe.job != null) {
            probe.job = null;
            probe.dirty = true;
            service.waiting.remove(register);
        }
    }

    public static void watch(ServerPlayer player, CashRegisterBlockEntity register) {
        unwatch(player);
        var service = get(player.serverLevel());
        var status = service.inspect(register);
        service.probes.get(register).watchers.add(player);
        WATCHING.put(player, register);
        send(player, register, status);
    }

    private static void send(ServerPlayer player, CashRegisterBlockEntity register, Status status) {
        if (player.level() == register.getLevel())
            ModNetwork.sendRetailInspection(player, new RetailInspectionPacket(player.level().dimension().location(),
                    register.getBlockPos(), register.registerId(), status));
    }

    public static void blockChanged(@Nullable Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        RetailStorageLinks.changed(level, pos);
        var service = LEVELS.get(server);
        if (service != null) service.invalidate(service.blocks.get(pos));
    }

    public static void chunkChanged(Level level, long chunk) {
        if (!(level instanceof ServerLevel server)) return;
        RetailStorageLinks.chunkChanged(level, chunk);
        var service = LEVELS.get(server);
        if (service != null) service.invalidate(service.chunks.get(chunk));
    }

    public static void displayChanged(Level level, PurchasedRegisterRef reference) {
        if (!(level instanceof ServerLevel server)) return;
        var service = LEVELS.get(server);
        if (service == null) return;
        var register = service.references.get(reference);
        if (register != null) service.invalidate(register);
    }

    public static void channelChanged(String channel) {
        for (var service : LEVELS.values()) service.invalidate(service.channels.get(channel));
    }

    public static void handlerChanged(IItemHandler handler) {
        List<ServerLevel> levels;
        synchronized (HANDLER_LEVELS) {
            var owners = HANDLER_LEVELS.get(handler);
            if (owners == null) return;
            levels = List.copyOf(owners);
        }
        for (var level : levels) {
            var service = LEVELS.get(level);
            if (service != null) service.invalidate(service.handlers.get(handler));
        }
    }

    private void invalidate(@Nullable Set<CashRegisterBlockEntity> registers) {
        if (registers != null) for (var register : registers) invalidate(register);
    }

    private void invalidate(CashRegisterBlockEntity register) {
        var probe = probes.get(register);
        if (probe == null) return;
        probe.dirty = true;
        probe.version++;
        if ((!probe.watchers.isEmpty() || probe.job != null) && !waiting.contains(register)) waiting.addLast(register);
    }

    public Status inspect(CashRegisterBlockEntity register) {
        var probe = probes.get(register);
        if (probe == null) {
            if (probes.size() >= 256) {
                var idle = probes.entrySet().stream().filter(entry -> entry.getValue().watchers.isEmpty()).findFirst();
                idle.ifPresent(entry -> remove(entry.getKey()));
            }
            probe = new Probe();
            probes.put(register, probe);
            references.put(register.reference(), register);
        }
        if (probe.dirty && probe.job == null) start(register, probe);
        return probe.status;
    }

    private void start(CashRegisterBlockEntity register, Probe probe) {
        unbind(register, probe);
        var level = (ServerLevel) Objects.requireNonNull(register.getLevel());
        probe.storages = List.copyOf(register.storages());
        probe.positions.add(register.getBlockPos());
        probe.positions.addAll(register.cashierSeatPositions());
        probe.positions.addAll(register.linkedStoragePositions());
        for (var pos : probe.positions) {
            blocks.computeIfAbsent(pos, ignored -> new HashSet<>()).add(register);
            chunks.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), ignored -> new HashSet<>()).add(register);
        }
        for (var handler : probe.storages) {
            handlers.computeIfAbsent(handler, ignored -> new HashSet<>()).add(register);
            synchronized (HANDLER_LEVELS) {
                HANDLER_LEVELS.computeIfAbsent(handler, ignored -> Collections.newSetFromMap(new WeakHashMap<>())).add(level);
            }
        }
        probe.bucket = RetailDisplayIndex.get(level).linked(register.reference());
        probe.revision = probe.bucket == null ? -1 : probe.bucket.revision();
        probe.job = new Job(probe.storages, probe.bucket);
        probe.jobVersion = probe.version;
        update(register, probe, new Status(register.hasCashier(), probe.storages.size(), probe.status.missing(), true));
        if (!waiting.contains(register)) waiting.addLast(register);
    }

    private void unbind(CashRegisterBlockEntity register, Probe probe) {
        for (var pos : probe.positions) {
            remove(blocks, pos, register);
            remove(chunks, net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), register);
        }
        for (var handler : probe.storages) remove(handlers, handler, register);
        for (var channel : probe.channels) remove(channels, channel, register);
        probe.positions.clear();
        probe.channels.clear();
    }

    private static <K> void remove(Map<K, Set<CashRegisterBlockEntity>> index, K key, CashRegisterBlockEntity register) {
        var entries = index.get(key);
        if (entries != null) {
            entries.remove(register);
            if (entries.isEmpty()) index.remove(key);
        }
    }

    private void remove(CashRegisterBlockEntity register) {
        var probe = probes.remove(register);
        if (probe == null) return;
        unbind(register, probe);
        references.remove(register.reference(), register);
        for (var player : probe.watchers) WATCHING.remove(player);
        waiting.remove(register);
    }

    private void update(CashRegisterBlockEntity register, Probe probe, Status status) {
        if (status.equals(probe.status)) return;
        probe.status = status;
        var viewers = probe.watchers.iterator();
        while (viewers.hasNext()) {
            var player = viewers.next();
            if (!com.rexhub.createapparel.network.RetailInspectionRequestPacket.canObserve(player, register.getBlockPos())) {
                viewers.remove();
                WATCHING.remove(player);
            } else send(player, register, status);
        }
    }

    public void tick(ServerLevel level) {
        int slots = 1024, displays = 32;
        int turns = waiting.size();
        while (turns-- > 0 && !waiting.isEmpty() && slots > 0 && displays > 0) {
            var register = waiting.removeFirst();
            var probe = probes.get(register);
            if (probe == null) continue;
            if (register.isRemoved()) { remove(register); continue; }
            if (probe.job == null && probe.dirty) start(register, probe);
            var bucket = RetailDisplayIndex.get(level).linked(register.reference());
            if (probe.bucket != bucket || bucket != null && probe.revision != bucket.revision()) {
                probe.job = null;
                probe.dirty = true;
                if (!waiting.contains(register)) waiting.addLast(register);
                continue;
            }
            var job = probe.job;
            if (job == null) continue;
            slots -= job.scanStock(Math.min(256, slots));
            displays -= job.scanDisplays(Math.min(8, displays), source -> {
                if (source instanceof AdvertisementPanelBlockEntity panel && panel.kind() == AdvertisementPanelBlock.Kind.SIGNAGE
                        && probe.channels.add(panel.channel()))
                    channels.computeIfAbsent(panel.channel(), ignored -> new HashSet<>()).add(register);
            });
            if (job.done()) {
                probe.job = null;
                if (probe.jobVersion == probe.version) {
                    probe.dirty = false;
                    update(register, probe, new Status(register.hasCashier(), probe.storages.size(), job.missing, false));
                } else if (!waiting.contains(register)) waiting.addLast(register);
            } else if (!waiting.contains(register)) waiting.addLast(register);
        }
    }

    public record Status(boolean cashier, int storageCount, boolean missing, boolean checking) {
        public Status {
            if (storageCount < 0 || storageCount > 8) throw new IllegalArgumentException("Invalid linked storage count");
        }
        public boolean storage() { return storageCount > 0; }
        public boolean open() { return cashier && storage(); }
        public void addTooltip(List<Component> tooltip) {
            tooltip.add(Component.translatable("tooltip.createapparel.register." + (open() ? "open" : "closed"))
                    .withStyle(open() ? ChatFormatting.GREEN : ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.createapparel.register.cashier",
                    Component.translatable("tooltip.createapparel.register." + (cashier ? "present" : "absent")))
                    .withStyle(cashier ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.createapparel.register.storage",
                    Component.translatable("tooltip.createapparel.register." + (storage() ? "connected" : "disconnected"), storageCount))
                    .withStyle(storage() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            if (missing) tooltip.add(Component.translatable("tooltip.createapparel.register.missing_stock").withStyle(ChatFormatting.RED));
            if (checking) tooltip.add(Component.translatable("tooltip.createapparel.register.checking").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static final class Probe {
        @Nullable private RetailDisplayIndex.Bucket bucket;
        private List<IItemHandler> storages = List.of();
        private final Set<BlockPos> positions = new HashSet<>();
        private final Set<String> channels = new HashSet<>();
        private final Set<ServerPlayer> watchers = new HashSet<>();
        private long revision = -1, version, jobVersion;
        private boolean dirty = true;
        private Status status = new Status(false, 0, false, true);
        @Nullable private Job job;
    }

    private record StockKey(Item item, @Nullable CompoundTag tag) {
        static StockKey of(ItemStack stack) { return new StockKey(stack.getItem(), stack.getTag() == null ? null : stack.getTag().copy()); }
    }

    private static final class Job {
        private final List<IItemHandler> storages;
        private final Iterator<Object> sources;
        private final Set<StockKey> stock = new HashSet<>();
        private int storageIndex, slot;
        private boolean missing;

        private Job(List<IItemHandler> storages, @Nullable RetailDisplayIndex.Bucket bucket) {
            this.storages = List.copyOf(storages);
            sources = bucket == null ? Collections.emptyIterator() : bucket.sources().iterator();
            if (!sources.hasNext()) storageIndex = storages.size();
        }

        private int scanStock(int budget) {
            int used = 0;
            while (storageIndex < storages.size() && used < budget) {
                var storage = storages.get(storageIndex);
                if (slot >= storage.getSlots()) { storageIndex++; slot = 0; continue; }
                var stack = storage.getStackInSlot(slot);
                if (!stack.isEmpty() && stack.getItem() instanceof GarmentItem) {
                    var extracted = storage.extractItem(slot, 1, true);
                    if (extracted.getCount() == 1 && ItemStack.isSameItemSameTags(stack, extracted)) stock.add(StockKey.of(stack));
                }
                slot++;
                used++;
            }
            return used;
        }

        private int scanDisplays(int budget, java.util.function.Consumer<Object> visit) {
            if (storageIndex < storages.size()) return 0;
            int used = 0;
            while (sources.hasNext() && used++ < budget) {
                var source = sources.next();
                visit.accept(source);
                for (var outfit : RetailDisplayIndex.outfits(source)) for (int index = 0; index < outfit.getSlots(); index++) {
                    var garment = outfit.getStackInSlot(index);
                    if (!garment.isEmpty() && !stock.contains(StockKey.of(garment))) missing = true;
                }
            }
            return Math.min(used, budget);
        }

        private boolean done() { return storageIndex >= storages.size() && !sources.hasNext(); }
    }
}
