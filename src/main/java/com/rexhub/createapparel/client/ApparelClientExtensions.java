package com.rexhub.createapparel.client;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.client.renderer.GarmentItemRenderer;
import com.rexhub.createapparel.client.renderer.SilkwormRearingTrayItemRenderer;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModFluids;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class ApparelClientExtensions {
    private ApparelClientExtensions() { }

    @SubscribeEvent
    public static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private GarmentItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    var minecraft = Minecraft.getInstance();
                    renderer = new GarmentItemRenderer(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
                }
                return renderer;
            }
        }, ModRegistries.ITEMS.getEntries().stream().map(holder -> holder.get())
                .filter(GarmentItem.class::isInstance).toArray(Item[]::new));
        event.registerItem(new IClientItemExtensions() {
            private SilkwormRearingTrayItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    var minecraft = Minecraft.getInstance();
                    renderer = new SilkwormRearingTrayItemRenderer(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
                }
                return renderer;
            }
        }, ModItems.SILKWORM_REARING_TRAY.get());
        event.registerBlock(new com.rexhub.createapparel.client.particle.DisplayFurnitureClientExtensions(),
                ModBlocks.CLOTHING_RACK.get(), ModBlocks.CLIP_CLOTHING_RACK.get(),
                ModBlocks.WALL_HANGER.get(), ModBlocks.CLIP_WALL_HANGER.get());
        event.registerFluidType(new com.rexhub.createapparel.client.mirror.MirrorFluidExtensions(), ModFluids.MIRROR_COATING_TYPE.get());
    }
}
