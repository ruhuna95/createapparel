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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
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
        event.register(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "mirror_tilt"), new com.rexhub.createapparel.client.mirror.MirrorTiltModelLoader());
    }

    @SubscribeEvent
    public static void registerMirrorShader(net.neoforged.neoforge.client.event.RegisterShadersEvent event) {
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

        });
    }

    @SubscribeEvent
    public static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
            event.<com.rexhub.createapparel.menu.WardrobeMenu, WardrobeScreen<com.rexhub.createapparel.menu.WardrobeMenu>>register(ModMenuTypes.WARDROBE.get(), WardrobeScreen::new);
            event.<com.rexhub.createapparel.menu.MannequinMenu, WardrobeScreen<com.rexhub.createapparel.menu.MannequinMenu>>register(ModMenuTypes.MANNEQUIN.get(), WardrobeScreen::new);
            event.register(ModMenuTypes.CITIZEN.get(), com.rexhub.createapparel.client.screen.CitizenScreen::new);
            event.register(ModMenuTypes.ROUTE_MARKER.get(), com.rexhub.createapparel.client.screen.RouteMarkerScreen::new);
            event.register(ModMenuTypes.CITIZEN_GAUGE.get(), com.rexhub.createapparel.client.screen.CitizenGaugeScreen::new);
            event.<com.rexhub.createapparel.photo.PhotographyMenu, net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<com.rexhub.createapparel.photo.PhotographyMenu>>register(
                    com.rexhub.createapparel.photo.PhotoRegistries.PHOTOGRAPHY.get(), (menu, inventory, title) ->
                    menu.mode() == com.rexhub.createapparel.photo.PhotographyMenu.Mode.EDIT
                            ? new com.rexhub.createapparel.client.advertisement.PhotoEditScreen(menu, inventory, title)
                            : new com.rexhub.createapparel.client.advertisement.PhotographyScreen(menu, inventory, title));
            event.register(com.rexhub.createapparel.advertisement.AdvertisementRegistries.CONTROL.get(), com.rexhub.createapparel.client.advertisement.AdvertisementControlScreen::new);
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ModPartialModels.CITIZEN_GAUGE_PANEL.modelLocation()));
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(com.rexhub.createapparel.client.renderer.HangerDisplay.MODEL));
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(com.rexhub.createapparel.client.renderer.HangerDisplay.CLIP_MODEL));
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ModPartialModels.SPINNING_MACHINE_ROTATING.modelLocation()));
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ModPartialModels.REARING_ITEM.modelLocation()));
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ModPartialModels.REARING_FEEDING.modelLocation()));
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ModPartialModels.SPINNING_MACHINE_BOBBIN_THREAD.modelLocation()));
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ModPartialModels.LOOM_SLIDE.modelLocation()));
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ModPartialModels.LOOM_SLIDE_WITH_THREAD.modelLocation()));
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ModPartialModels.SEWING_MACHINE_ROTATING.modelLocation()));
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ModPartialModels.SEWING_MACHINE_PISTON.modelLocation()));
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ModPartialModels.SEWING_MACHINE_THREAD.modelLocation()));
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ModPartialModels.WARDROBE_LOWER_LEFT_DOOR.modelLocation()));
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ModPartialModels.WARDROBE_LOWER_RIGHT_DOOR.modelLocation()));
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ModPartialModels.WARDROBE_UPPER_LEFT_DOOR.modelLocation()));
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ModPartialModels.WARDROBE_UPPER_RIGHT_DOOR.modelLocation()));
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
        event.registerBlockEntityRenderer(com.rexhub.createapparel.advertisement.AdvertisementRegistries.PANEL_ENTITY.get(), com.rexhub.createapparel.client.advertisement.AdvertisementPanelRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CITIZEN_GAUGE.get(), com.rexhub.createapparel.client.renderer.CitizenGaugeRenderer::new);
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
        for (var skin : event.getSkins()) {
            net.minecraft.client.renderer.entity.player.PlayerRenderer renderer = event.getSkin(skin);
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
                        ? FabricAttributeMap.color(stack).map(color -> 0xFF000000 | color.tintRgb()).orElse(0xFFFFFFFF)
                        : 0xFFFFFFFF,
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
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) resourceManager -> {
            com.rexhub.createapparel.client.renderer.GaugeWireRouter.resetCaches();
            com.rexhub.createapparel.client.citizen.ClientGaugeView.resetRendering();
        });
    }

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.HOTBAR, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "watch"), WatchHud::render);
    }
}
