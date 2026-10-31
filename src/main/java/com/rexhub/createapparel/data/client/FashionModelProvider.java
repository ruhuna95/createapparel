package com.rexhub.createapparel.data.client;

import com.rexhub.createapparel.advertisement.AdvertisementRegistries;
import com.rexhub.createapparel.advertisement.AdvertisementPanelBlock;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public final class FashionModelProvider extends BlockStateProvider {
    public FashionModelProvider(PackOutput output, ExistingFileHelper helper) {
        super(output, "createapparel", helper);
        for (String name : java.util.List.of("billboard_panel", "signage_panel"))
            helper.trackGenerated(modLoc("item/" + name), net.minecraft.server.packs.PackType.CLIENT_RESOURCES, ".png", "textures");
    }
    @Override public String getName() { return "Create: Apparel fashion retail and advertisement models"; }
    @Override protected void registerStatesAndModels() {
        for (var block : java.util.List.of(AdvertisementRegistries.BILLBOARD_PANEL.get(), AdvertisementRegistries.SIGNAGE_PANEL.get())) {
            String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
            var model = models().getBuilder(name).texture("frame", block == AdvertisementRegistries.SIGNAGE_PANEL.get() ? "minecraft:block/black_concrete" : "minecraft:block/iron_block").texture("particle", "#frame");
            model.element().from(0, 0, 14).to(16, 16, 16).allFaces((direction, face) -> face.texture("#frame")).end();
            getVariantBuilder(block).forAllStatesExcept(state -> ConfiguredModel.builder().modelFile(model)
                    .rotationY((int) (state.getValue(AdvertisementPanelBlock.FACING).toYRot() + 180) % 360).build(), AdvertisementPanelBlock.PART);
            itemModels().withExistingParent(name, mcLoc("item/generated")).texture("layer0", modLoc("item/" + name));
        }
    }
}
