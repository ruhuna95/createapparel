package com.rexhub.createapparel.data.client;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.WallHangerBlock;
import com.rexhub.createapparel.block.HangerGeometry;
import com.rexhub.createapparel.init.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
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
        hanger(model("common_hanger"), Vec3.ZERO);
        BlockModelBuilder wall = model("wall_hanger");
        hanger(wall, HangerGeometry.WALL_ANCHOR.subtract(HangerGeometry.MODEL_ANCHOR));
        cube(wall, 6, 11, 15, 10, 16, 16, "metal");
        var hook = HangerGeometry.WALL_HOOK;
        cube(wall, hook.x1(), hook.y1(), hook.z1(), hook.x2(), hook.y2(), hook.z2(), hook.texture());
        getVariantBuilder(ModBlocks.WALL_HANGER.get()).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(wall).rotationY(rotation(state.getValue(WallHangerBlock.FACING))).build());
        simpleBlockItem(ModBlocks.WALL_HANGER.get(), wall);
        rackModels();
    }

    private void rackModels() {
        BlockModelBuilder[][] floor = new BlockModelBuilder[2][4];
        for (int half = 0; half < 2; half++) for (int supports = 0; supports < 4; supports++) {
            var model = rackModel("clothing_rack_floor_" + half + "_" + supports);
            rackHalf(model, half == 1, (supports & 1) != 0, (supports & 2) != 0, 0);
            floor[half][supports] = model;
        }
        var wallLower = rackModel("clothing_rack_wall_lower");
        BlockModelBuilder[][] wallUpper = new BlockModelBuilder[2][4];
        for (int anchored = 0; anchored < 2; anchored++) for (int connections = 0; connections < 4; connections++) {
            var wall = rackModel(anchored == 1 && connections == 0 ? "clothing_rack_wall_upper"
                    : "clothing_rack_wall_upper_" + anchored + "_" + connections);
            cube(wall, 7, 13, 0, 9, 15, anchored == 1 ? 15 : 16, "metal");
            if (anchored == 1) cube(wall, 6, 12, 15, 10, 16, 16, "metal");
            wallUpper[anchored][connections] = wall;
        }
        getVariantBuilder(ModBlocks.CLOTHING_RACK.get()).forAllStates(state -> {
            boolean upper = state.getValue(com.rexhub.createapparel.block.ClothingRackBlock.HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
            boolean wall = state.getValue(com.rexhub.createapparel.block.ClothingRackBlock.MOUNT)
                    == com.rexhub.createapparel.block.RackMountType.WALL;
            int supports = (state.getValue(com.rexhub.createapparel.block.ClothingRackBlock.LEFT_CONNECTED) ? 0 : 1)
                    | (state.getValue(com.rexhub.createapparel.block.ClothingRackBlock.RIGHT_CONNECTED) ? 0 : 2);
            int anchored = state.getValue(com.rexhub.createapparel.block.ClothingRackBlock.WALL_ANCHORED) ? 1 : 0;
            return ConfiguredModel.builder().modelFile(wall ? upper ? wallUpper[anchored][3 ^ supports] : wallLower : floor[upper ? 1 : 0][supports])
                    .rotationY(rotation(state.getValue(com.rexhub.createapparel.block.ClothingRackBlock.FACING))).build();
        });
        var item = rackModel("clothing_rack_item");
        rackHalf(item, false, true, true, 0);
        rackHalf(item, true, true, true, 16);
        item.transforms().transform(net.minecraft.world.item.ItemDisplayContext.GUI)
                .rotation(30, 225, 0).translation(0, -3, 0).scale(0.45F).end()
                .transform(net.minecraft.world.item.ItemDisplayContext.GROUND).translation(0, 2, 0).scale(0.25F).end()
                .transform(net.minecraft.world.item.ItemDisplayContext.FIXED).translation(0, -4, 0).scale(0.5F).end();
        simpleBlockItem(ModBlocks.CLOTHING_RACK.get(), item);
    }

    private void rackHalf(BlockModelBuilder model, boolean upper, boolean left, boolean right, int y) {
        if (upper) cube(model, 0, y + 13, 7, 16, y + 15, 9, "metal");
        else cube(model, 0, y, 6, 16, y + 2, 10, "metal");
        for (int x : new int[]{2, 14}) {
            if (x == 14 ? !left : !right) continue;
            cube(model, x - 1, y + (upper ? 0 : 2), 7, x + 1, y + (upper ? 13 : 16), 9, "metal");
            if (!upper) {
                cube(model, x - 2, y, 2, x + 2, y + 2, 6, "metal");
                cube(model, x - 2, y, 10, x + 2, y + 2, 14, "metal");
            }
        }
    }

    private BlockModelBuilder model(String name) {
        return models().getBuilder(name).parent(models().getExistingFile(mcLoc("block/block")))
                .texture("wood", mcLoc("block/oak_planks"))
                .texture("metal", mcLoc("block/iron_block"))
                .texture("particle", mcLoc("block/oak_planks"));
    }

    private BlockModelBuilder rackModel(String name) { return model(name).texture("particle", mcLoc("block/iron_block")); }

    private void hanger(BlockModelBuilder model, Vec3 offset) {
        float x = (float) (offset.x * 16);
        float y = (float) (offset.y * 16);
        float z = (float) (offset.z * 16);
        for (var part : HangerGeometry.PARTS) {
            var element = cube(model, part.x1(), part.y1(), part.z1(), part.x2(), part.y2(), part.z2(), part.texture());
            element.from(part.x1() + x, part.y1() + y, part.z1() + z).to(part.x2() + x, part.y2() + y, part.z2() + z);
            if (part.angle() != 0) element.rotation().origin(8 + x, 13 + y, 11 + z).axis(Direction.Axis.Z).angle(part.angle()).end();
        }
    }

    private ModelBuilder<BlockModelBuilder>.ElementBuilder cube(BlockModelBuilder model,
            float x1, float y1, float z1, float x2, float y2, float z2, String texture) {
        var element = model.element().from(x1, y1, z1).to(x2, y2, z2);
        float localY1 = y1 >= 16 ? y1 - 16 : y1;
        float localY2 = y1 >= 16 ? y2 - 16 : y2;
        for (Direction direction : Direction.values()) {
            var face = element.face(direction).texture("#" + texture);
            switch (direction) {
                case DOWN -> face.uvs(x1, 16 - z2, x2, 16 - z1);
                case UP -> face.uvs(x1, z1, x2, z2);
                case NORTH -> face.uvs(16 - x2, 16 - localY2, 16 - x1, 16 - localY1);
                case SOUTH -> face.uvs(x1, 16 - localY2, x2, 16 - localY1);
                case WEST -> face.uvs(z1, 16 - localY2, z2, 16 - localY1);
                case EAST -> face.uvs(16 - z2, 16 - localY2, 16 - z1, 16 - localY1);
            }
            face.end();
        }
        return element;
    }

    private int rotation(Direction facing) { return ((int) facing.toYRot() + 180) % 360; }
}
