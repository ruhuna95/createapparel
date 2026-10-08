package com.rexhub.createapparel.data.client;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.SilkwormRearingTrayBlock;
import com.rexhub.createapparel.init.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

public final class SilkwormRearingTrayModelProvider extends BlockStateProvider {
    public SilkwormRearingTrayModelProvider(PackOutput output, ExistingFileHelper helper) {
        super(output, CreateApparel.MOD_ID, helper);
    }

    @Override
    public String getName() { return "Silkworm Rearing Tray Models: " + CreateApparel.MOD_ID; }

    @Override
    protected void registerStatesAndModels() {
        var body = body("block");
        getVariantBuilder(ModBlocks.SILKWORM_REARING_TRAY.get()).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(body).rotationY(((int) state.getValue(SilkwormRearingTrayBlock.HORIZONTAL_FACING).toYRot() + 180) % 360)
                .build());
        var item = body("item");
        cube(item, 0, 6, 6, 16, 10, 10, "metal");
        itemModels().getBuilder("silkworm_rearing_tray")
                .parent(new ModelFile.UncheckedModelFile(mcLoc("builtin/entity")))
                .texture("particle", mcLoc("block/oak_planks"))
                .transforms()
                .transform(ItemDisplayContext.GUI).rotation(30, 225, 0).scale(0.625F).end()
                .transform(ItemDisplayContext.GROUND).translation(0, 3, 0).scale(0.25F).end()
                .transform(ItemDisplayContext.FIXED).scale(0.5F).end()
                .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                    .rotation(75, 45, 0).translation(0, 2.5F, 0).scale(0.375F).end()
                .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                    .rotation(75, 225, 0).translation(0, 2.5F, 0).scale(0.375F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).rotation(0, 45, 0).scale(0.4F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND).rotation(0, 225, 0).scale(0.4F).end();
        var feeding = model("feeding");
        cube(feeding, 4, 13, 4, 12, 14, 12, "food");
    }

    private BlockModelBuilder body(String name) {
        var body = model(name);
        var gearbox = body.element().from(4, 4, 4).to(12, 12, 12);
        for (Direction face : Direction.values()) {
            gearbox.face(face).texture(face.getAxis() == Direction.Axis.X ? "#opening" : "#casing").end();
        }
        for (int x : new int[]{2, 12}) for (int z : new int[]{2, 12})
            cube(body, x, 0, z, x + 2, 12, z + 2, "casing");
        cube(body, 1, 12, 1, 15, 13, 15, "wood");
        cube(body, 1, 13, 1, 3, 16, 15, "wood");
        cube(body, 13, 13, 1, 15, 16, 15, "wood");
        cube(body, 3, 13, 1, 13, 16, 3, "wood");
        cube(body, 3, 13, 13, 13, 16, 15, "wood");
        return body;
    }

    private BlockModelBuilder model(String name) {
        return models().getBuilder("block/silkworm_rearing_tray/" + name)
                .parent(models().getExistingFile(mcLoc("block/block")))
                .texture("casing", net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "block/andesite_casing"))
                .texture("opening", net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "block/gearbox"))
                .texture("metal", net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "block/andesite_block"))
                .texture("wood", mcLoc("block/oak_planks"))
                .texture("food", mcLoc("block/moss_block"))
                .texture("particle", "#wood");
    }

    private void cube(BlockModelBuilder model, float x1, float y1, float z1,
                      float x2, float y2, float z2, String texture) {
        var element = model.element().from(x1, y1, z1).to(x2, y2, z2);
        for (Direction face : Direction.values()) element.face(face).texture("#" + texture).end();
    }
}
