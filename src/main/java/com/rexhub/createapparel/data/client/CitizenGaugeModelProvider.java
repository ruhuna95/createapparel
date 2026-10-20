package com.rexhub.createapparel.data.client;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

public final class CitizenGaugeModelProvider extends BlockStateProvider {
    public CitizenGaugeModelProvider(PackOutput output, ExistingFileHelper helper) { super(output, CreateApparel.MOD_ID, helper); }
    @Override public String getName() { return "Create: Apparel Citizen Gauge Models"; }
    @Override protected void registerStatesAndModels() {
        var model = models().withExistingParent("citizen_gauge", net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "block/factory_gauge/block"));
        simpleBlock(ModBlocks.CITIZEN_GAUGE.get(), model);
        models().withExistingParent("block/citizen_gauge/panel", net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "block/factory_gauge/panel"))
                .texture("0", CitizenGaugeTextureProvider.TEXTURE).texture("particle", CitizenGaugeTextureProvider.TEXTURE);
        itemModels().withExistingParent("citizen_gauge", net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "block/factory_gauge/item"))
                .texture("0", CitizenGaugeTextureProvider.TEXTURE).texture("particle", CitizenGaugeTextureProvider.TEXTURE);
    }
}
