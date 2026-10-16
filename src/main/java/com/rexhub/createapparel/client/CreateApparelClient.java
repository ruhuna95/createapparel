package com.rexhub.createapparel.client;

import com.rexhub.createapparel.client.model.MannequinModel;
import com.rexhub.createapparel.client.renderer.MannequinRenderer;
import com.rexhub.createapparel.menu.MannequinMenu;
import com.rexhub.createapparel.menu.WardrobeMenu;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import com.rexhub.createapparel.client.model.ModPartialModels;
import com.rexhub.createapparel.client.model.SilkwormCocoonModel;
import com.rexhub.createapparel.client.model.SilkwormModel;
import com.rexhub.createapparel.client.renderer.LoomRenderer;
import com.rexhub.createapparel.client.renderer.FirstPersonApparelRenderer;
import com.rexhub.createapparel.client.renderer.PlayerApparelLayers;
import com.rexhub.createapparel.client.renderer.SewingMachineRenderer;
import com.rexhub.createapparel.client.renderer.SilkwormRenderer;
import com.rexhub.createapparel.client.renderer.SpinningMachineRenderer;
import com.rexhub.createapparel.client.renderer.WardrobeRenderer;
import com.rexhub.createapparel.client.renderer.WatchHud;
import com.rexhub.createapparel.client.screen.SewingPatternScreen;
import com.rexhub.createapparel.client.screen.WardrobeScreen;
import com.rexhub.createapparel.init.ModBlockEntities;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModMenuTypes;
import com.rexhub.createapparel.material.FabricAttributeMap;
import com.rexhub.createapparel.material.FabricColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CreateApparelClient {
    public static final ModelLayerLocation CITIZEN_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "citizen"), "main");
    public static final ModelLayerLocation MANNEQUIN_CLASSIC_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "mannequin_classic"), "main");
    public static final ModelLayerLocation MANNEQUIN_SLIM_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "mannequin_slim"), "main");
    public static final ModelLayerLocation SILKWORM_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "silkworm"),
            "main"
    );
    public static final ModelLayerLocation SILKWORM_COCOON_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "silkworm_cocoon"),
            "main"
    );

    private CreateApparelClient() {
    }

    @SubscribeEvent
    public static void registerMirrorGeometry(ModelEvent.RegisterGeometryLoaders event) {
        event.register("mirror_tilt", new com.rexhub.createapparel.client.mirror.MirrorTiltModelLoader());
    }

    @SubscribeEvent
    public static void registerMirrorShader(net.minecraftforge.client.event.RegisterShadersEvent event) {
        try {
            event.registerShader(new net.minecraft.client.renderer.ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "mirror"),
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION),
                    com.rexhub.createapparel.client.mirror.MirrorRenderManager.INSTANCE::setShader);
        } catch (java.io.IOException exception) {
            com.rexhub.createapparel.client.mirror.MirrorRenderManager.INSTANCE.setShader(null);
            com.mojang.logging.LogUtils.getLogger().warn("Mirror shader unavailable; using fallback mirror surfaces", exception);
        }
    }

    public static void openSewingPatternScreen(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new SewingPatternScreen(hand));
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.rexhub.createapparel.init.ModFluids.MIRROR_COATING_FLUID.get(), net.minecraft.client.renderer.RenderType.translucent());
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.rexhub.createapparel.init.ModFluids.FLOWING_MIRROR_COATING_FLUID.get(), net.minecraft.client.renderer.RenderType.translucent());
            com.rexhub.createapparel.client.guide.ApparelItemDescriptions.register();
            net.createmod.ponder.foundation.PonderIndex.addPlugin(
                    new com.rexhub.createapparel.client.ponder.ApparelPonderPlugin());
            MenuScreens.<WardrobeMenu, WardrobeScreen<WardrobeMenu>>
                    register(ModMenuTypes.WARDROBE.get(), WardrobeScreen::new);
            MenuScreens.<MannequinMenu, WardrobeScreen<MannequinMenu>>
                    register(ModMenuTypes.MANNEQUIN.get(), WardrobeScreen::new);
            MenuScreens.register(ModMenuTypes.CITIZEN.get(), com.rexhub.createapparel.client.screen.CitizenScreen::new);
            MenuScreens.register(ModMenuTypes.ROUTE_MARKER.get(), com.rexhub.createapparel.client.screen.RouteMarkerScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(com.rexhub.createapparel.client.renderer.HangerDisplay.MODEL);
        event.register(com.rexhub.createapparel.client.renderer.HangerDisplay.CLIP_MODEL);
        event.register(ModPartialModels.SPINNING_MACHINE_ROTATING.modelLocation());
        event.register(ModPartialModels.REARING_ITEM.modelLocation());
        event.register(ModPartialModels.REARING_FEEDING.modelLocation());
        event.register(ModPartialModels.SPINNING_MACHINE_BOBBIN_THREAD.modelLocation());
        event.register(ModPartialModels.LOOM_SLIDE.modelLocation());
        event.register(ModPartialModels.LOOM_SLIDE_WITH_THREAD.modelLocation());
        event.register(ModPartialModels.SEWING_MACHINE_ROTATING.modelLocation());
        event.register(ModPartialModels.SEWING_MACHINE_PISTON.modelLocation());
        event.register(ModPartialModels.SEWING_MACHINE_THREAD.modelLocation());
        event.register(ModPartialModels.WARDROBE_LOWER_LEFT_DOOR.modelLocation());
        event.register(ModPartialModels.WARDROBE_LOWER_RIGHT_DOOR.modelLocation());
        event.register(ModPartialModels.WARDROBE_UPPER_LEFT_DOOR.modelLocation());
        event.register(ModPartialModels.WARDROBE_UPPER_RIGHT_DOOR.modelLocation());
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CITIZEN_LAYER, com.rexhub.createapparel.client.model.CitizenModel::createBodyLayer);
        event.registerLayerDefinition(MANNEQUIN_CLASSIC_LAYER,
                () -> MannequinModel.createBodyLayer(false));
        event.registerLayerDefinition(MANNEQUIN_SLIM_LAYER,
                () -> MannequinModel.createBodyLayer(true));
        event.registerLayerDefinition(SILKWORM_LAYER, SilkwormModel::createBodyLayer);
        event.registerLayerDefinition(SILKWORM_COCOON_LAYER, SilkwormCocoonModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CITIZEN.get(), com.rexhub.createapparel.client.renderer.CitizenRenderer::new);
        event.registerBlockEntityRenderer(com.rexhub.createapparel.init.ModMirrors.BLOCK_ENTITY.get(),
                com.rexhub.createapparel.client.mirror.MirrorBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CLOTHING_RACK.get(),
                com.rexhub.createapparel.client.renderer.ClothingRackRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WALL_HANGER.get(),
                com.rexhub.createapparel.client.renderer.WallHangerRenderer::new);
        ModEntities.mannequins().values().forEach(type ->
                event.registerEntityRenderer(type.get(), MannequinRenderer::new));
        event.registerEntityRenderer(ModEntities.SILKWORM.get(), SilkwormRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SPINNING_MACHINE.get(), SpinningMachineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SILKWORM_REARING_TRAY.get(),
                com.rexhub.createapparel.client.renderer.SilkwormRearingTrayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LOOM.get(), LoomRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SEWING_MACHINE.get(), SewingMachineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WARDROBE.get(), WardrobeRenderer::new);
    }

    @SubscribeEvent
    public static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        FirstPersonApparelRenderer.reloadModels(event.getEntityModels());
        for (String skin : event.getSkins()) {
            net.minecraft.client.renderer.entity.player.PlayerRenderer renderer = event.getPlayerSkin(skin);
            if (renderer != null) {
                PlayerApparelLayers.add(renderer);
            }
        }
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> tintIndex == 0
                        ? FabricColor.fromDye(stack).map(color -> 0xFF000000 | color.tintRgb()).orElse(0xFFFFFFFF)
                        : 0xFFFFFFFF,
                ModItems.apparelDyes().values().stream().map(item -> item.get()).toArray(Item[]::new)
        );
        event.register(
                (stack, tintIndex) -> tintIndex == 0
                        ? FabricAttributeMap.color(stack).map(color -> color.tintRgb()).orElse(0xFFFFFF)
                        : 0xFFFFFF,
                ModItems.fabricItems().entrySet().stream()
                        .filter(entry -> entry.getValue().isDyed())
                        .map(entry -> entry.getKey().get())
                        .toArray(Item[]::new)
        );
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager ->
                com.rexhub.createapparel.client.mirror.MirrorRenderManager.INSTANCE.reset());
        event.registerReloadListener(ClientGarmentAssets.INSTANCE);
        event.registerReloadListener(com.rexhub.createapparel.client.citizen.ClientCitizenAssets.INSTANCE);
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                resourceManager -> com.rexhub.createapparel.client.renderer.SilkwormRearingTrayContentsRenderer.resetModel());
    }

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "watch", WatchHud::render);
    }
}
