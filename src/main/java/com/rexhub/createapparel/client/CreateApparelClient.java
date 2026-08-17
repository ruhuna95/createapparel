package com.rexhub.createapparel.client;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.client.model.SilkwormCocoonModel;
import com.rexhub.createapparel.client.model.SilkwormModel;
import com.rexhub.createapparel.client.model.ModPartialModels;
import com.rexhub.createapparel.client.renderer.SilkwormRenderer;
import com.rexhub.createapparel.client.renderer.SpinningMachineRenderer;
import com.rexhub.createapparel.init.ModBlockEntities;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricAttributeMap;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CreateApparelClient {
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
    public static void onClientSetup(FMLClientSetupEvent event) {
        ModPartialModels.bootstrap();
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        ModPartialModels.bootstrap();
        event.register(ModPartialModels.SPINNING_MACHINE_ROTATING.modelLocation());
        event.register(ModPartialModels.SPINNING_MACHINE_BOBBIN_THREAD.modelLocation());
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SILKWORM_LAYER, SilkwormModel::createBodyLayer);
        event.registerLayerDefinition(SILKWORM_COCOON_LAYER, SilkwormCocoonModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SILKWORM.get(), SilkwormRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SPINNING_MACHINE.get(), SpinningMachineRenderer::new);
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
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
}
