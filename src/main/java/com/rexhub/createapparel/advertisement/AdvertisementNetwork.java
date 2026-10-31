package com.rexhub.createapparel.advertisement;

import com.rexhub.createapparel.CreateApparel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.*;
import java.util.*;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class AdvertisementNetwork {
    private static final Map<UUID, Map<String, Long>> subscriptions = new HashMap<>();
    private static final Map<UUID, Long> nextRequest = new HashMap<>();
    private AdvertisementNetwork() { }
    public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("3");
        registrar.playToServer(Request.TYPE, Request.STREAM_CODEC, (packet, context) -> packet.handle((ServerPlayer) context.player()));
        registrar.playToClient(Update.TYPE, Update.STREAM_CODEC,
                (packet, context) -> com.rexhub.createapparel.client.advertisement.ClientAdvertisementChannels.accept(packet));
        registrar.playToServer(Control.TYPE, Control.STREAM_CODEC, (packet, context) -> {
            var player = (ServerPlayer) context.player();
            if (player.containerMenu instanceof AdvertisementControlMenu menu && menu.containerId == packet.containerId())
                menu.configure(player, packet.channel(), packet.scene(), packet.action());
        });
        registrar.playToClient(MenuState.TYPE, MenuState.STREAM_CODEC, (packet, context) -> packet.handleClient());
    }
    public static void request(BlockPos pos, long revision) { PacketDistributor.sendToServer(new Request(pos, revision)); }
    public static void control(int menu, String channel, int scene, int action) { PacketDistributor.sendToServer(new Control(menu, channel, scene, action)); }
    public static void sendMenuState(ServerPlayer player, AdvertisementControlMenu menu) {
        PacketDistributor.sendToPlayer(player, new MenuState(menu.containerId, menu.position(), menu.channel(), menu.scenes()));
    }
    public static void publish(MinecraftServer server, String id, AdvertisementNetworkSavedData.Channel channel) {
        for (var player : server.getPlayerList().getPlayers()) {
            if (subscriptions.getOrDefault(player.getUUID(), Map.of()).getOrDefault(id, -1L) >= server.overworld().getGameTime())
                PacketDistributor.sendToPlayer(player, new Update(id, channel.revision(), channel.scenes()));
        }
    }
    public record Request(BlockPos pos, long revision) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID,
                "advertisement_request"));
        public static final net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), Request::decode);
        @Override public Type<Request> type() { return TYPE; }

        void encode(FriendlyByteBuf buffer) { buffer.writeBlockPos(pos); buffer.writeLong(revision); }
        static Request decode(FriendlyByteBuf buffer) { return new Request(buffer.readBlockPos(), buffer.readLong()); }
        void handle(ServerPlayer player) {
            long now = player.getServer().overworld().getGameTime();
            if (now < nextRequest.getOrDefault(player.getUUID(), 0L)) return;
            nextRequest.put(player.getUUID(), now + 2);
            if (!player.level().hasChunk(pos.getX() >> 4, pos.getZ() >> 4) || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 128 * 128
                    || !(player.level().getBlockEntity(pos) instanceof AdvertisementPanelBlockEntity panel)) return;
            var controller = panel.controller(); if (controller == null || controller.kind() != AdvertisementPanelBlock.Kind.SIGNAGE) return;
            var id = controller.channel(); if (id.isEmpty()) return;
            var watched = subscriptions.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashMap<>());
            watched.entrySet().removeIf(entry -> entry.getValue() < now);
            if (watched.size() >= 128 && !watched.containsKey(id)) watched.remove(watched.keySet().iterator().next());
            watched.put(id, now + 1200);
            var channel = AdvertisementNetworkSavedData.get(player.serverLevel()).channel(id);
            if (channel.revision() != revision) PacketDistributor.sendToPlayer(player, new Update(id, channel.revision(), channel.scenes()));
        }
    }
    public record Update(String channel, long revision, List<AdvertisementSnapshot> scenes) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final Type<Update> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID,
                "advertisement_update"));
        public static final net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, Update> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), Update::decode);
        @Override public Type<Update> type() { return TYPE; }

        public Update { scenes = AdvertisementScenes.validate(scenes); }
        public Update(String channel, long revision, AdvertisementSnapshot snapshot) { this(channel, revision, List.of(snapshot)); }
        public AdvertisementSnapshot snapshot() { return scenes.get(0); }
        public void encode(FriendlyByteBuf buffer) { buffer.writeUtf(channel, 48); buffer.writeLong(revision); var tag = new net.minecraft.nbt.CompoundTag(); tag.put("Scenes", AdvertisementScenes.save(scenes)); buffer.writeNbt(tag); }
        public static Update decode(FriendlyByteBuf buffer) {
            var id = buffer.readUtf(48); long revision = buffer.readLong(); var tag = buffer.readNbt();
            if (!AdvertisementNetworkSavedData.validChannel(id) || revision < 0 || tag == null || tag.sizeInBytes() > AdvertisementScenes.MAX_BYTES) throw new IllegalArgumentException("Invalid channel update");
            return new Update(id, revision, AdvertisementScenes.load(tag.getList("Scenes", net.minecraft.nbt.Tag.TAG_COMPOUND)));
        }
    }
    public record Control(int containerId, String channel, int scene, int action) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final Type<Control> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID,
                "advertisement_control"));
        public static final net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, Control> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), Control::decode);
        @Override public Type<Control> type() { return TYPE; }

        void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(containerId); buffer.writeUtf(channel, 48); buffer.writeVarInt(scene); buffer.writeVarInt(action); }
        static Control decode(FriendlyByteBuf buffer) { return new Control(buffer.readVarInt(), buffer.readUtf(48), buffer.readVarInt(), buffer.readVarInt()); }
    }
    public record MenuState(int containerId, BlockPos position, String channel, List<AdvertisementSnapshot> scenes) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final Type<MenuState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID,
                "advertisement_menu_state"));
        public static final net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, MenuState> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), MenuState::decode);
        @Override public Type<MenuState> type() { return TYPE; }

        void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(containerId); buffer.writeBlockPos(position); buffer.writeUtf(channel, 48);
            var tag = new net.minecraft.nbt.CompoundTag(); tag.put("Scenes", AdvertisementScenes.save(scenes)); buffer.writeNbt(tag);
        }
        static MenuState decode(FriendlyByteBuf buffer) {
            int id = buffer.readVarInt(); var pos = buffer.readBlockPos(); String channel = buffer.readUtf(48); var tag = buffer.readNbt();
            if (tag == null || tag.sizeInBytes() > AdvertisementScenes.MAX_BYTES) throw new IllegalArgumentException("Invalid advertisement menu state");
            return new MenuState(id, pos, channel, AdvertisementScenes.load(tag.getList("Scenes", net.minecraft.nbt.Tag.TAG_COMPOUND)));
        }
        private void handleClient() {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof AdvertisementControlMenu menu && menu.containerId == containerId && menu.position().equals(position))
                menu.accept(channel, scenes);
        }
    }
    @net.neoforged.bus.api.SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        subscriptions.remove(event.getEntity().getUUID()); nextRequest.remove(event.getEntity().getUUID());
    }
    @net.neoforged.bus.api.SubscribeEvent public static void stop(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { subscriptions.clear(); nextRequest.clear(); }
}
