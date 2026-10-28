package com.rexhub.createapparel.advertisement;

import com.rexhub.createapparel.CreateApparel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class AdvertisementNetwork {
    private static SimpleChannel network;
    private static final Map<UUID, Map<String, Long>> subscriptions = new HashMap<>();
    private static final Map<UUID, Long> nextRequest = new HashMap<>();
    private AdvertisementNetwork() { }
    public static void initialize() {
        network = NetworkRegistry.ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "advertisement"))
                .networkProtocolVersion(() -> "2").clientAcceptedVersions("2"::equals).serverAcceptedVersions("2"::equals).simpleChannel();
        network.messageBuilder(Request.class, 0, NetworkDirection.PLAY_TO_SERVER).encoder(Request::encode).decoder(Request::decode)
                .consumerMainThread((packet, context) -> { var player = context.get().getSender(); if (player != null) packet.handle(player); }).add();
        network.messageBuilder(Update.class, 1, NetworkDirection.PLAY_TO_CLIENT).encoder(Update::encode).decoder(Update::decode)
                .consumerMainThread((packet, context) -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> com.rexhub.createapparel.client.advertisement.ClientAdvertisementChannels.accept(packet))).add();
        network.messageBuilder(Control.class, 2, NetworkDirection.PLAY_TO_SERVER).encoder(Control::encode).decoder(Control::decode)
                .consumerMainThread((packet, context) -> {
                    var player = context.get().getSender();
                    if (player != null && player.containerMenu instanceof AdvertisementControlMenu menu && menu.containerId == packet.containerId())
                        menu.configure(player, packet.channel(), packet.scene(), packet.action());
                }).add();
        network.messageBuilder(MenuState.class, 3, NetworkDirection.PLAY_TO_CLIENT).encoder(MenuState::encode).decoder(MenuState::decode)
                .consumerMainThread((packet, context) -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> packet.handleClient())).add();
    }
    public static void request(BlockPos pos, long revision) { network.sendToServer(new Request(pos, revision)); }
    public static void control(int menu, String channel, int scene, int action) { network.sendToServer(new Control(menu, channel, scene, action)); }
    public static void sendMenuState(ServerPlayer player, AdvertisementControlMenu menu) {
        network.send(PacketDistributor.PLAYER.with(() -> player), new MenuState(menu.containerId, menu.position(), menu.channel(), menu.scenes()));
    }
    public static void publish(MinecraftServer server, String id, AdvertisementNetworkSavedData.Channel channel) {
        for (var player : server.getPlayerList().getPlayers()) {
            if (subscriptions.getOrDefault(player.getUUID(), Map.of()).getOrDefault(id, -1L) >= server.overworld().getGameTime())
                network.send(PacketDistributor.PLAYER.with(() -> player), new Update(id, channel.revision(), channel.scenes()));
        }
    }
    public record Request(BlockPos pos, long revision) {
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
            if (channel.revision() != revision) network.send(PacketDistributor.PLAYER.with(() -> player), new Update(id, channel.revision(), channel.scenes()));
        }
    }
    public record Update(String channel, long revision, List<AdvertisementSnapshot> scenes) {
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
    public record Control(int containerId, String channel, int scene, int action) {
        void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(containerId); buffer.writeUtf(channel, 48); buffer.writeVarInt(scene); buffer.writeVarInt(action); }
        static Control decode(FriendlyByteBuf buffer) { return new Control(buffer.readVarInt(), buffer.readUtf(48), buffer.readVarInt(), buffer.readVarInt()); }
    }
    public record MenuState(int containerId, BlockPos position, String channel, List<AdvertisementSnapshot> scenes) {
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
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        subscriptions.remove(event.getEntity().getUUID()); nextRequest.remove(event.getEntity().getUUID());
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent event) { subscriptions.clear(); nextRequest.clear(); }
}
