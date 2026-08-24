package com.rexhub.createapparel.garment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.network.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class GarmentDefinitionManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final GarmentDefinitionManager INSTANCE = new GarmentDefinitionManager();
    private static volatile Map<ResourceLocation, GarmentDefinition> definitions = Map.of();

    private GarmentDefinitionManager() {
        super(GSON, "garments");
    }

    public static Optional<GarmentDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(definitions.get(id));
    }

    private static List<GarmentDefinition> all() {
        return List.copyOf(definitions.values());
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> objects,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        LinkedHashMap<ResourceLocation, GarmentDefinition> loaded = new LinkedHashMap<>();
        objects.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation id = entry.getKey();
                    Item item = ForgeRegistries.ITEMS.getValue(id);
                    if (!(item instanceof GarmentItem)) {
                        throw new IllegalStateException("Garment definition has no matching GarmentItem: " + id);
                    }
                    GarmentDefinition definition = GarmentDefinition.fromJson(id, entry.getValue().getAsJsonObject());
                    loaded.put(id, definition);
                });
        definitions = Collections.unmodifiableMap(loaded);
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @SubscribeEvent
    public static void synchronize(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            ModNetwork.sendGarmentDefinitions(event.getPlayer(), all());
            return;
        }
        event.getPlayerList().getPlayers()
                .forEach(player -> ModNetwork.sendGarmentDefinitions(player, all()));
    }
}
