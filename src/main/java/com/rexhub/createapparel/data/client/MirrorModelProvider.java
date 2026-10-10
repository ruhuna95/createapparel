package com.rexhub.createapparel.data.client;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.MirrorFurnitureBlock;
import com.rexhub.createapparel.init.ModFluids;
import com.rexhub.createapparel.init.ModMirrors;
import com.rexhub.createapparel.mirror.MirrorWood;
import com.rexhub.createapparel.mirror.MirrorFurnitureGeometry;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.common.data.ExistingFileHelper;

public final class MirrorModelProvider extends BlockStateProvider {
    public MirrorModelProvider(PackOutput output, ExistingFileHelper helper) { super(output, CreateApparel.MOD_ID, helper); }
    @Override public String getName() { return "Create: Apparel mirror models"; }

    @Override protected void registerStatesAndModels() {
        var panel = base("mirror_panel", "oak");
        cube(panel, 0, 0, 15, 16, 16, 16, "mirror");
        furnitureStates(ModMirrors.MIRROR_PANEL.get(), panel, panel);
        var panelItem = base("mirror_panel_item", "oak");
        cube(panelItem, 0, 0, 7.5F, 16, 16, 8.5F, "mirror");
        simpleBlockItem(ModMirrors.MIRROR_PANEL.get(), panelItem);
        simpleBlockWithItem(ModMirrors.MIRROR_BLOCK.get(), models().cubeAll("mirror_block", modLoc("block/mirror_surface")));
        mirroredDoor(ModMirrors.MIRRORED_DOOR.get(), "mirrored_door", "iron_block");
        furnitureModels(ModMirrors.IRON_FULL_LENGTH_MIRROR.get(), ModMirrors.IRON_TABLE_MIRROR.get(), "iron", "iron_block");
        for (var wood : MirrorWood.values()) {
            furnitureModels(ModMirrors.FULL_LENGTH_MIRRORS.get(wood).get(), ModMirrors.TABLE_MIRRORS.get(wood).get(), wood.id(), wood.id() + "_planks");
            mirroredDoor(ModMirrors.MIRRORED_DOORS.get(wood).get(), wood.id() + "_mirrored_door", wood.id() + "_planks");
        }
        simpleBlock(ModFluids.MIRROR_COATING_BLOCK.get(), models().getBuilder("mirror_coating_fluid")
                .texture("particle", mcLoc("block/water_still")));
        itemModels().getBuilder("mirror_coating_fluid_bucket")
                .parent(itemModels().getExistingFile(mcLoc("item/generated")))
                .texture("layer0", modLoc("item/mirror_coating_fluid_bucket"));
    }

    private void furnitureModels(Block fullBlock, Block tableBlock, String material, String frameTexture) {
        String full = material + "_full_length_mirror";
        var lower = furnitureBase(full + "_lower", frameTexture);
        var upper = furnitureBase(full + "_upper", frameTexture);
        fullLength(lower, false, 0);
        fullLength(upper, true, 0);
        lower = tilted(lower).rotation(0, supportAngle(true), 0, 15)
                .rotation(1, supportAngle(true), 0, 15).rotation(2, supportAngle(true), 0, 15)
                .rotation(3, MirrorFurnitureGeometry.FULL_TILT, 0, 9).rotation(4, MirrorFurnitureGeometry.FULL_TILT, 0, 9).end();
        upper = tilted(upper).rotation(0, MirrorFurnitureGeometry.FULL_TILT, -16, 9)
                .rotation(1, MirrorFurnitureGeometry.FULL_TILT, -16, 9).end();
        furnitureStates(fullBlock, lower, upper);
        var item = furnitureBase(full + "_item", frameTexture);
        fullLength(item, false, 0);
        fullLength(item, true, 16);
        tallItemTransforms(item);
        item = tilted(item).rotation(0, supportAngle(true), 0, 15)
                .rotation(1, supportAngle(true), 0, 15).rotation(2, supportAngle(true), 0, 15)
                .rotation(3, MirrorFurnitureGeometry.FULL_TILT, 0, 9).rotation(4, MirrorFurnitureGeometry.FULL_TILT, 0, 9)
                .rotation(5, MirrorFurnitureGeometry.FULL_TILT, 0, 9).rotation(6, MirrorFurnitureGeometry.FULL_TILT, 0, 9).end();
        simpleBlockItem(fullBlock, item);
        String table = material + "_table_mirror";
        var model = furnitureBase(table, frameTexture);
        support(model, false);
        cube(model, 3, 0, 7, 13, 10, 9, "wood");
        cube(model, 4, 1, 6.999F, 12, 9, 7.999F, "mirror");
        model = tilted(model)
                .rotation(0, supportAngle(false), 0, 12)
                .rotation(1, supportAngle(false), 0, 12).rotation(2, supportAngle(false), 0, 12)
                .rotation(3, MirrorFurnitureGeometry.TABLE_TILT, 0, 9)
                .rotation(4, MirrorFurnitureGeometry.TABLE_TILT, 0, 9).end();
        furnitureStates(tableBlock, model, model);
        simpleBlockItem(tableBlock, model);
    }

    private void furnitureStates(Block block, BlockModelBuilder lower, BlockModelBuilder upper) {
        getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(state.getValue(MirrorFurnitureBlock.HALF) == DoubleBlockHalf.UPPER ? upper : lower)
                .rotationY(((int) state.getValue(MirrorFurnitureBlock.FACING).toYRot() + 180) % 360).build());
    }

    private BlockModelBuilder base(String name, String wood) {
        return models().getBuilder(name).parent(models().getExistingFile(mcLoc("block/block")))
                .texture("wood", mcLoc("block/" + wood + "_planks"))
                .texture("mirror", modLoc("block/mirror_surface")).texture("particle", mcLoc("block/" + wood + "_planks"));
    }

    private BlockModelBuilder furnitureBase(String name, String frameTexture) {
        return new BlockModelBuilder(modLoc("block/" + name), models().existingFileHelper)
                .parent(models().getExistingFile(mcLoc("block/block")))
                .texture("wood", mcLoc("block/" + frameTexture))
                .texture("mirror", modLoc("block/mirror_surface")).texture("particle", mcLoc("block/" + frameTexture));
    }

    private MirrorTiltModelBuilder tilted(BlockModelBuilder geometry) {
        return models().getBuilder(geometry.getLocation().getPath()).customLoader(MirrorTiltModelBuilder::begin).geometry(geometry.toJson());
    }

    private void tallItemTransforms(BlockModelBuilder model) {
        model.transforms().transform(net.minecraft.world.item.ItemDisplayContext.GUI)
                .rotation(15, 225, 0).translation(0, -4, 0).scale(0.45F).end()
                .transform(net.minecraft.world.item.ItemDisplayContext.GROUND).translation(0, 2, 0).scale(0.25F).end()
                .transform(net.minecraft.world.item.ItemDisplayContext.FIXED).translation(0, -4, 0).scale(0.5F).end();
    }

    private void fullLength(BlockModelBuilder model, boolean upper, int y) {
        if (!upper) support(model, true);
        float textureY = upper ? y - 2 : 0;
        cube(model, 1, y + (upper ? -2 : 0), 7, 15, y + 14, 9, "wood", textureY);
        cube(model, 2, y + (upper ? -2 : 1), 6.999F, 14, y + (upper ? 13 : 14), 7.999F, "mirror", textureY);
    }

    private void support(BlockModelBuilder model, boolean full) {
        var attachment = MirrorFurnitureGeometry.supportAttachment(full);
        double length = Math.hypot(attachment.y, attachment.z - (full ? 15 : 12));
        float left = full ? 1 : 3, right = full ? 15 : 13;
        float z = full ? 15 : 12;
        cube(model, left, 0, z - 1, left + 1, (float) length, z + 1, "wood");
        cube(model, right - 1, 0, z - 1, right, (float) length, z + 1, "wood");
        cube(model, left + 1, 0, z - 1, right - 1, 1, z + 1, "wood");
    }

    private float supportAngle(boolean full) {
        var attachment = MirrorFurnitureGeometry.supportAttachment(full);
        return (float) Math.toDegrees(Math.atan2(attachment.z - (full ? 15 : 12), attachment.y));
    }

    private void mirroredDoor(Block block, String name, String frameTexture) {
        var lower = doorModel(name + "_lower", frameTexture);
        var upper = doorModel(name + "_upper", frameTexture);
        doorHalf(lower, false, 0);
        doorHalf(upper, true, 0);
        getVariantBuilder(block).forAllStatesExcept(state -> {
            Direction facing = state.getValue(DoorBlock.FACING);
            if (state.getValue(DoorBlock.OPEN)) facing = state.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT
                    ? facing.getCounterClockWise() : facing.getClockWise();
            return ConfiguredModel.builder().modelFile(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? upper : lower)
                    .rotationY(((int) facing.toYRot() + 180) % 360).build();
        }, DoorBlock.POWERED);
        var item = doorModel(name + "_item", frameTexture);
        doorHalf(item, false, 0);
        doorHalf(item, true, 16);
        tallItemTransforms(item);
        simpleBlockItem(block, item);
    }

    private BlockModelBuilder doorModel(String name, String frameTexture) {
        return models().getBuilder(name).parent(models().getExistingFile(mcLoc("block/block")))
                .texture("frame", mcLoc("block/" + frameTexture)).texture("mirror", modLoc("block/mirror_surface"))
                .texture("particle", mcLoc("block/" + frameTexture));
    }

    private void doorHalf(BlockModelBuilder model, boolean upper, int y) {
        cube(model, 0, y, 13, 16, y + 16, 16, "frame");
        float minY = y + (upper ? 0 : 2), maxY = y + (upper ? 14 : 16);
        cube(model, 2, minY, 12.999F, 14, maxY, 13.5F, "mirror");
        cube(model, 2, minY, 15.5F, 14, maxY, 16.001F, "mirror");
    }

    private void cube(BlockModelBuilder model, float x1, float y1, float z1, float x2, float y2, float z2, String texture) {
        cube(model, x1, y1, z1, x2, y2, z2, texture, y1 >= 16 ? 16 : 0);
    }

    private void cube(BlockModelBuilder model, float x1, float y1, float z1, float x2, float y2, float z2, String texture, float textureY) {
        var element = model.element().from(x1, y1, z1).to(x2, y2, z2);
        float localY1 = y1 - textureY;
        float localY2 = y2 - textureY;
        for (Direction face : Direction.values()) {
            var builder = element.face(face).texture("#" + texture);
            switch (face) {
                case DOWN -> builder.uvs(x1, 16 - z2, x2, 16 - z1);
                case UP -> builder.uvs(x1, z1, x2, z2);
                case NORTH -> builder.uvs(16 - x2, 16 - localY2, 16 - x1, 16 - localY1);
                case SOUTH -> builder.uvs(x1, 16 - localY2, x2, 16 - localY1);
                case WEST -> builder.uvs(z1, 16 - localY2, z2, 16 - localY1);
                case EAST -> builder.uvs(16 - z2, 16 - localY2, 16 - z1, 16 - localY1);
            }
            builder.end();
        }
    }
}
