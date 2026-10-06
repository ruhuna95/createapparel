package com.rexhub.createapparel.data.client;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.WallHangerBlock;
import com.rexhub.createapparel.init.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelBuilder;
import net.minecraftforge.common.data.ExistingFileHelper;

public final class FurnitureModelProvider extends BlockStateProvider {
    public FurnitureModelProvider(PackOutput output, ExistingFileHelper helper) {
        super(output, CreateApparel.MOD_ID, helper);
    }

    @Override
    protected void registerStatesAndModels() {
        hanger(model("common_hanger"));
        BlockModelBuilder wall = model("wall_hanger");
        hanger(wall);
        cube(wall, 6, 11, 15, 10, 16, 16, "metal");
        cube(wall, 7.5F, 14.5F, 10.5F, 8.5F, 15.5F, 16, "metal");
        getVariantBuilder(ModBlocks.WALL_HANGER.get()).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(wall).rotationY(rotation(state.getValue(WallHangerBlock.FACING))).build());
        simpleBlockItem(ModBlocks.WALL_HANGER.get(), wall);
    }

    private BlockModelBuilder model(String name) {
        return models().getBuilder(name).parent(models().getExistingFile(mcLoc("block/block")))
                .texture("wood", mcLoc("block/oak_planks"))
                .texture("metal", mcLoc("block/iron_block"))
                .texture("particle", mcLoc("block/oak_planks"));
    }

    private void hanger(BlockModelBuilder model) {
        cube(model, 3, 10.5F, 10.5F, 13, 11.25F, 11.5F, "wood");
        cube(model, 8, 13, 10.5F, 13.25F, 13.75F, 11.5F, "wood")
                .rotation().origin(8, 13, 11).axis(Direction.Axis.Z).angle(-22.5F).end();
        cube(model, 2.75F, 13, 10.5F, 8, 13.75F, 11.5F, "wood")
                .rotation().origin(8, 13, 11).axis(Direction.Axis.Z).angle(22.5F).end();
        cube(model, 7.65F, 13, 10.65F, 8.35F, 15.5F, 11.35F, "metal");
        cube(model, 8, 15, 10.65F, 9.25F, 15.5F, 11.35F, "metal");
        cube(model, 8.75F, 14.5F, 10.65F, 9.25F, 15.5F, 11.35F, "metal");
    }

    private ModelBuilder<BlockModelBuilder>.ElementBuilder cube(BlockModelBuilder model,
            float x1, float y1, float z1, float x2, float y2, float z2, String texture) {
        var element = model.element().from(x1, y1, z1).to(x2, y2, z2);
        for (Direction direction : Direction.values()) element.face(direction).texture("#" + texture).end();
        return element;
    }

    private int rotation(Direction facing) { return ((int) facing.toYRot() + 180) % 360; }
}
