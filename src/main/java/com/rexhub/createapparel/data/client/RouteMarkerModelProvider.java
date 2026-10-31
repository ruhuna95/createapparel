package com.rexhub.createapparel.data.client;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModBlocks;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public final class RouteMarkerModelProvider extends BlockStateProvider {
    public RouteMarkerModelProvider(PackOutput output, ExistingFileHelper helper) { super(output, CreateApparel.MOD_ID, helper); }
    @Override public String getName() { return "Citizen Route Marker Models"; }
    @Override protected void registerStatesAndModels() {
        var empty = models().getBuilder("route_marker").texture("particle", mcLoc("block/stone"));
        simpleBlock(ModBlocks.CITIZEN_ROUTE_MARKER.get(), empty);
        simpleBlock(ModBlocks.GATEWAY_ROUTE_MARKER.get(), empty);
        simpleBlock(ModBlocks.CROSSING_ROUTE_MARKER.get(), empty);
    }
}
