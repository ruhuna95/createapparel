package com.rexhub.createapparel.garment;

import com.rexhub.createapparel.entity.Mannequin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.network.ModNetwork;
import com.rexhub.createapparel.apparel.ApparelCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID)
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

    public static List<GarmentDefinition> all() {
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
                    Item item = BuiltInRegistries.ITEM.get(id);
                    if (!(item instanceof GarmentItem)) {
                        throw new IllegalStateException("Garment definition has no matching GarmentItem: " + id);
                    }
                    GarmentDefinition definition = GarmentDefinition.fromJson(id, entry.getValue().getAsJsonObject());
                    loaded.put(id, definition);
                });
        Map<String, com.rexhub.createapparel.fashion.FashionDesignModifier> modifiers = new LinkedHashMap<>();
        resourceManager.getResource(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "fashion/design_modifiers.json")).ifPresent(resource -> {
            try (var reader = resource.openAsReader()) {
                var json = GSON.fromJson(reader, com.google.gson.JsonObject.class);
                var designs = loaded.values().stream().flatMap(definition -> definition.designs().stream()).collect(java.util.stream.Collectors.toSet());
                json.entrySet().forEach(entry -> {
                    if (!designs.contains(entry.getKey())) throw new IllegalArgumentException("Unknown fashion design: " + entry.getKey());
                    modifiers.put(entry.getKey(), com.rexhub.createapparel.fashion.FashionDesignModifier.parse(entry.getValue().getAsJsonObject()));
                });
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Cannot read fashion design modifiers", exception);
            }
        });
        definitions = Collections.unmodifiableMap(loaded);
        com.rexhub.createapparel.fashion.FashionTagResolver.acceptModifiers(modifiers);
        loaded.values().forEach(definition -> definition.designs().forEach(design -> {
            var tags = com.rexhub.createapparel.fashion.FashionTagResolver.resolve(definition, design);
            if (tags.contains(com.rexhub.createapparel.fashion.FashionTag.FEMININE) && tags.contains(com.rexhub.createapparel.fashion.FashionTag.MASCULINE))
                com.mojang.logging.LogUtils.getLogger().warn("Garment {} / {} has conflicting fashion targets; automatic purchases are disabled", definition.id(), design);
        }));
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
        event.addListener(new com.rexhub.createapparel.citizen.CitizenPresetManager(
                event.getServerResources().getRecipeManager(), event.getConditionContext()));
    }

    private static void synchronizePlayer(net.minecraft.server.level.ServerPlayer player) {
        ModNetwork.sendGarmentDefinitions(player, all());
        ApparelCapabilities.get(player).ifPresent(inventory -> {
            inventory.reconcileStyles();
            ModNetwork.sendApparelInventory(player, inventory);
        });
    }

    @SubscribeEvent
    public static void synchronize(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            synchronizePlayer(event.getPlayer());
            return;
        }
        event.getPlayerList().getPlayers()
                .forEach(GarmentDefinitionManager::synchronizePlayer);
        for (var level : event.getPlayerList().getServer().getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof Mannequin mannequin) {
                    mannequin.mannequinState().loadout().reconcileStyles();
                    mannequin.syncState();
                }
            }
        }
    }
}
