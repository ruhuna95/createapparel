package com.rexhub.createapparel.client.ponder;

import com.rexhub.createapparel.block.LoomBlock;
import com.rexhub.createapparel.block.entity.SewingMachineBlockEntity;
import com.rexhub.createapparel.block.entity.SilkwormRearingTrayBlockEntity;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.guide.ProductionGuide;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Optional;

public final class ApparelProductionScenes {
    private ApparelProductionScenes() { }

    public static void build(ProductionGuide guide, SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title(guide.id(), guide.title(false));
        scene.configureBasePlate(1, 1, 5);
        scene.scaleSceneView(0.95F);
        scene.showBasePlate();
        scene.idle(10);
        switch (guide) {
            case SPINNING, WEAVING -> manual(guide, scene, util);
            case SPINNING_AUTOMATION, WEAVING_AUTOMATION, SEWING_AUTOMATION -> automation(guide, scene, util);
            case PATTERN -> pattern(guide, scene, util);
            case SEWING -> sewing(guide, scene, util);
            case REARING, REARING_AUTOMATION -> rearing(guide, scene, util);
        }
        scene.markAsFinished();
    }

    private static void text(ProductionGuide guide, int index, CreateSceneBuilder scene,
                             SceneBuildingUtil util, BlockPos target) {
        scene.overlay().showText(70).attachKeyFrame().text(guide.text(index, false))
                .pointAt(util.vector().topOf(target)).placeNearTarget();
        scene.idle(80);
    }

    private static void place(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos,
                              net.minecraft.world.level.block.state.BlockState state) {
        scene.world().setBlock(pos, state, false);
        scene.world().showSection(util.select().position(pos), Direction.DOWN);
    }

    private static void machine(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, Block block,
                                Direction driveDirection) {
        place(scene, util, pos, block.defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos drivenCog = drivenCog(pos, driveDirection);
        for (int distance = 1; distance < pos.distManhattan(drivenCog); distance++) {
            place(scene, util, pos.relative(driveDirection, distance), AllBlocks.SHAFT.getDefaultState()
                    .setValue(BlockStateProperties.AXIS, driveDirection.getAxis()));
        }
        for (int depth = 0; depth <= drivenCog.getY(); depth++) {
            place(scene, util, drivenCog.below(depth), AllBlocks.COGWHEEL.getDefaultState()
                    .setValue(BlockStateProperties.AXIS, driveDirection.getAxis()));
        }
        driveSpeed(scene, util, pos, driveDirection, 0);
        scene.idle(15);
    }

    private static BlockPos drivenCog(BlockPos pos, Direction driveDirection) {
        return switch (driveDirection) {
            case EAST -> new BlockPos(6, pos.getY(), pos.getZ());
            case WEST -> new BlockPos(0, pos.getY(), pos.getZ());
            case SOUTH -> new BlockPos(pos.getX(), pos.getY(), 6);
            default -> throw new IllegalArgumentException("Unsupported Ponder drive direction: " + driveDirection);
        };
    }

    private static BlockPos sourceCog(BlockPos drivenCog) {
        return new BlockPos(drivenCog.getX(), 0, drivenCog.getZ());
    }

    private static void driveSpeed(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos,
                                   Direction driveDirection, float speed) {
        BlockPos drivenCog = drivenCog(pos, driveDirection);
        scene.world().setKineticSpeed(util.select().fromTo(pos, drivenCog), speed);
        for (int depth = 1; depth <= drivenCog.getY(); depth++) {
            scene.world().setKineticSpeed(util.select().position(drivenCog.below(depth)), depth % 2 == 0 ? speed : -speed);
        }
    }

    private static void power(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, Direction driveDirection) {
        driveSpeed(scene, util, pos, driveDirection, 64);
        scene.effects().rotationDirectionIndicator(sourceCog(drivenCog(pos, driveDirection)));
        scene.effects().indicateSuccess(pos);
    }

    private static void transfer(CreateSceneBuilder scene, SceneBuildingUtil util, ItemStack item,
                                 BlockPos from, BlockPos to) {
        Vec3 start = util.vector().centerOf(from);
        var entity = scene.world().createItemEntity(start,
                util.vector().centerOf(to).subtract(start).scale(0.07), item);
        scene.idle(15);
        scene.world().modifyEntity(entity, Entity::discard);
    }

    private static void insert(CreateSceneBuilder scene, BlockPos pos, ItemStack item) {
        scene.world().modifyBlockEntity(pos, com.simibubi.create.content.kinetics.base.KineticBlockEntity.class,
                machine -> machine.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                    var remaining = item.copy();
                    for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                        remaining = handler.insertItem(slot, remaining, false);
                    }
                }));
    }

    private static void clearInput(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, boolean sewing) {
        scene.world().modifyBlockEntityNBT(util.select().position(pos),
                com.simibubi.create.content.kinetics.base.KineticBlockEntity.class, tag -> {
                    tag.put(sewing ? "MaterialInventory" : "InputInventory",
                            new ItemStackHandler(sewing ? 2 : 1).serializeNBT());
                    tag.putBoolean("Processing", false);
                });
        if (!sewing) {
            scene.world().modifyBlock(pos, state -> state.hasProperty(LoomBlock.THREAD_VISIBLE)
                    ? state.setValue(LoomBlock.THREAD_VISIBLE, false) : state, false);
        }
    }

    private static ItemStack fabric(FabricColor color, int count) {
        return new ItemStack(ModItems.dyedFabric(FabricMaterial.COTTON, color).get(), count);
    }

    private static ItemStack shirt() {
        var stack = new ItemStack(ModItems.T_SHIRT.get());
        new GarmentData(FabricMaterial.COTTON, "plain", FabricColor.BLUE, Optional.empty(), Optional.empty()).write(stack);
        return stack;
    }

    private static ItemStack patternStack() {
        var stack = new ItemStack(ModItems.SEWING_PATTERN.get());
        new PatternData(ResourceLocation.fromNamespaceAndPath("createapparel", "t_shirt"), "plain",
                Optional.empty(), Optional.empty()).write(stack);
        return stack;
    }

    private static void setPattern(CreateSceneBuilder scene, BlockPos pos) {
        scene.world().modifyBlockEntity(pos, SewingMachineBlockEntity.class, machine -> {
            FilteringBehaviour filter = BlockEntityBehaviour.get(machine, FilteringBehaviour.TYPE);
            if (filter != null) filter.setFilter(patternStack());
        });
    }

    private static void manual(ProductionGuide guide, CreateSceneBuilder scene, SceneBuildingUtil util) {
        boolean weaving = guide == ProductionGuide.WEAVING;
        BlockPos pos = util.grid().at(3, 1, 3);
        machine(scene, util, pos, weaving ? ModBlocks.LOOM.get() : ModBlocks.SPINNING_MACHINE.get(), Direction.EAST);
        text(guide, 0, scene, util, pos);
        power(scene, util, pos, Direction.EAST);
        text(guide, 1, scene, util, pos.east());
        var input = new ItemStack(weaving ? ModItems.COTTON_THREAD.get() : ModItems.COTTON_FIBER.get(), weaving ? 4 : 1);
        scene.overlay().showControls(util.vector().topOf(pos), Pointing.DOWN, 40).rightClick().withItem(input);
        insert(scene, pos, input);
        if (weaving) scene.world().modifyBlock(pos, state -> state.setValue(LoomBlock.THREAD_VISIBLE, true), false);
        text(guide, 2, scene, util, pos);
        scene.idle(30);
        clearInput(scene, util, pos, false);
        var output = new ItemStack(weaving ? ModItems.COTTON_FABRIC.get() : ModItems.COTTON_THREAD.get());
        scene.world().createItemEntity(util.vector().topOf(pos).add(0, 0.15, 0), Vec3.ZERO, output);
        scene.effects().indicateSuccess(pos);
        text(guide, 3, scene, util, pos);
        if (!weaving) {
            insert(scene, pos, input);
            driveSpeed(scene, util, pos, Direction.EAST, 0);
            text(guide, 4, scene, util, pos.east());
        }
    }

    private static void automation(ProductionGuide guide, CreateSceneBuilder scene, SceneBuildingUtil util) {
        boolean weaving = guide == ProductionGuide.WEAVING_AUTOMATION;
        boolean sewing = guide == ProductionGuide.SEWING_AUTOMATION;
        BlockPos pos = util.grid().at(3, 1, 3);
        Direction driveDirection = sewing ? Direction.SOUTH : Direction.EAST;
        machine(scene, util, pos, sewing ? ModBlocks.SEWING_MACHINE.get()
                : weaving ? ModBlocks.LOOM.get() : ModBlocks.SPINNING_MACHINE.get(), driveDirection);
        place(scene, util, pos.above(), AllBlocks.CHUTE.getDefaultState());
        place(scene, util, pos.west(), AllBlocks.DEPOT.getDefaultState());
        power(scene, util, pos, driveDirection);
        if (sewing) setPattern(scene, pos);
        var input = sewing ? fabric(FabricColor.BLUE, 3)
                : new ItemStack(weaving ? ModItems.COTTON_THREAD.get() : ModItems.COTTON_FIBER.get(), weaving ? 4 : 1);
        transfer(scene, util, input, pos.above(2), pos);
        insert(scene, pos, input);
        if (sewing) {
            var thread = new ItemStack(ModItems.COTTON_THREAD.get());
            transfer(scene, util, thread, pos.above(2), pos);
            insert(scene, pos, thread);
        }
        if (weaving) scene.world().modifyBlock(pos, state -> state.setValue(LoomBlock.THREAD_VISIBLE, true), false);
        text(guide, 0, scene, util, pos.above());
        clearInput(scene, util, pos, sewing);
        ItemStack output = sewing ? shirt() : new ItemStack(weaving ? ModItems.COTTON_FABRIC.get() : ModItems.COTTON_THREAD.get());
        scene.world().createItemOnBeltLike(pos.west(), Direction.EAST, output);
        scene.effects().indicateSuccess(pos.west());
        text(guide, 1, scene, util, sewing ? pos : pos.west());
        if (!sewing) {
            scene.world().hideSection(util.select().position(pos.west()), Direction.WEST);
            scene.idle(15);
            scene.world().setBlock(pos.west(), Blocks.AIR.defaultBlockState(), false);
            transfer(scene, util, input, pos.above(2), pos);
            insert(scene, pos, input);
            scene.idle(35);
            clearInput(scene, util, pos, false);
            scene.world().createItemEntity(util.vector().topOf(pos).add(0, 0.15, 0), Vec3.ZERO, output);
        }
        scene.overlay().showOutline(net.createmod.ponder.api.PonderPalette.GREEN, new Object(), util.select().position(pos.west()), 60);
        text(guide, 2, scene, util, pos.west());
    }

    private static void pattern(ProductionGuide guide, CreateSceneBuilder scene, SceneBuildingUtil util) {
        BlockPos pos = util.grid().at(3, 1, 3);
        machine(scene, util, pos, ModBlocks.SEWING_MACHINE.get(), Direction.SOUTH);
        scene.overlay().showControls(util.vector().centerOf(pos.east()), Pointing.RIGHT, 50)
                .rightClick().withItem(new ItemStack(ModItems.SEWING_PATTERN.get()));
        text(guide, 0, scene, util, pos.east());
        text(guide, 1, scene, util, pos.east());
        scene.overlay().showControls(util.vector().blockSurface(pos, Direction.NORTH), Pointing.RIGHT, 50)
                .rightClick().withItem(patternStack());
        setPattern(scene, pos);
        text(guide, 2, scene, util, pos);
        scene.overlay().showControls(util.vector().topOf(pos), Pointing.DOWN, 45).withItem(fabric(FabricColor.BLUE, 3));
        text(guide, 3, scene, util, pos);
    }

    private static void sewing(ProductionGuide guide, CreateSceneBuilder scene, SceneBuildingUtil util) {
        BlockPos pos = util.grid().at(3, 1, 3);
        machine(scene, util, pos, ModBlocks.SEWING_MACHINE.get(), Direction.SOUTH);
        setPattern(scene, pos);
        text(guide, 0, scene, util, pos);
        power(scene, util, pos, Direction.SOUTH);
        text(guide, 1, scene, util, pos.south());
        scene.overlay().showControls(util.vector().topOf(pos), Pointing.DOWN, 40)
                .rightClick().withItem(fabric(FabricColor.BLUE, 3));
        insert(scene, pos, fabric(FabricColor.BLUE, 3));
        scene.idle(30);
        scene.overlay().showControls(util.vector().topOf(pos), Pointing.DOWN, 40)
                .rightClick().withItem(new ItemStack(ModItems.COTTON_THREAD.get()));
        insert(scene, pos, new ItemStack(ModItems.COTTON_THREAD.get()));
        text(guide, 2, scene, util, pos);
        clearInput(scene, util, pos, true);
        scene.world().createItemEntity(util.vector().topOf(pos).add(0, 0.2, 0), Vec3.ZERO, shirt());
        scene.effects().indicateSuccess(pos);
        text(guide, 3, scene, util, pos);
    }

    private static void tray(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, boolean captured,
                             ItemStack food, ItemStack output) {
        scene.world().modifyBlockEntityNBT(util.select().position(pos), SilkwormRearingTrayBlockEntity.class, tag -> {
            var data = new CompoundTag();
            data.putBoolean("HasSilkworm", captured);
            var inventory = new ItemStackHandler(2);
            inventory.setStackInSlot(0, food);
            inventory.setStackInSlot(1, output);
            data.put("Inventory", inventory.serializeNBT());
            data.putString("State", captured ? "READY" : "EMPTY");
            tag.put("TrayData", data);
        });
    }

    private static void rearing(ProductionGuide guide, CreateSceneBuilder scene, SceneBuildingUtil util) {
        boolean automated = guide == ProductionGuide.REARING_AUTOMATION;
        BlockPos pos = util.grid().at(3, automated ? 2 : 1, 3);
        machine(scene, util, pos, ModBlocks.SILKWORM_REARING_TRAY.get(), Direction.EAST);
        var food = new ItemStack(Blocks.OAK_LEAVES);
        var cocoon = new ItemStack(ModItems.COCOON.get());
        if (automated) {
            tray(scene, util, pos, true, ItemStack.EMPTY, ItemStack.EMPTY);
            place(scene, util, pos.above(), AllBlocks.CHUTE.getDefaultState());
            place(scene, util, pos.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
            BlockPos spinner = pos.below().west();
            machine(scene, util, spinner, ModBlocks.SPINNING_MACHINE.get(), Direction.WEST);
            place(scene, util, spinner.south(), AllBlocks.DEPOT.getDefaultState());
            power(scene, util, pos, Direction.EAST);
            power(scene, util, spinner, Direction.WEST);
            transfer(scene, util, food, pos.above(2), pos);
            tray(scene, util, pos, true, food, ItemStack.EMPTY);
            text(guide, 0, scene, util, pos.above());
            tray(scene, util, pos, true, ItemStack.EMPTY, cocoon);
            text(guide, 1, scene, util, pos.below());
            place(scene, util, pos.north(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            scene.world().modifyBlockEntity(pos, SilkwormRearingTrayBlockEntity.class, SilkwormRearingTrayBlockEntity::updateRedstone);
            scene.effects().indicateRedstone(pos);
            text(guide, 2, scene, util, pos.north());
            scene.world().setBlock(pos.north(), Blocks.AIR.defaultBlockState(), false);
            scene.world().modifyBlockEntity(pos, SilkwormRearingTrayBlockEntity.class, SilkwormRearingTrayBlockEntity::updateRedstone);
            tray(scene, util, pos, true, ItemStack.EMPTY, ItemStack.EMPTY);
            transfer(scene, util, cocoon, pos, spinner);
            insert(scene, spinner, cocoon);
            scene.idle(40);
            clearInput(scene, util, spinner, false);
            scene.world().createItemOnBeltLike(spinner.south(), Direction.NORTH, new ItemStack(ModItems.SILK_THREAD.get()));
            text(guide, 3, scene, util, spinner);
        } else {
            BlockPos larvaPos = pos.north();
            var larva = scene.world().createEntity(level -> {
                var worm = ModEntities.SILKWORM.get().create(level);
                if (worm != null) worm.moveTo(Vec3.atBottomCenterOf(larvaPos));
                return worm;
            });
            scene.overlay().showControls(util.vector().topOf(larvaPos), Pointing.RIGHT, 50)
                    .rightClick().withItem(new ItemStack(ModItems.SILKWORM_REARING_TRAY.get()));
            text(guide, 0, scene, util, larvaPos);
            scene.world().modifyEntity(larva, Entity::discard);
            tray(scene, util, pos, true, ItemStack.EMPTY, ItemStack.EMPTY);
            scene.overlay().showControls(util.vector().topOf(pos), Pointing.DOWN, 40).rightClick().withItem(new ItemStack(Items.LEAD));
            text(guide, 1, scene, util, pos);
            power(scene, util, pos, Direction.EAST);
            scene.overlay().showControls(util.vector().topOf(pos), Pointing.DOWN, 40).rightClick().withItem(food);
            tray(scene, util, pos, true, food, ItemStack.EMPTY);
            text(guide, 2, scene, util, pos);
            tray(scene, util, pos, true, ItemStack.EMPTY, cocoon);
            scene.effects().indicateSuccess(pos);
            text(guide, 3, scene, util, pos);
            scene.overlay().showControls(util.vector().topOf(pos), Pointing.DOWN, 45).rightClick();
            transfer(scene, util, cocoon, pos, pos.west());
            tray(scene, util, pos, true, ItemStack.EMPTY, ItemStack.EMPTY);
            scene.overlay().showControls(util.vector().topOf(pos), Pointing.DOWN, 45).rightClick().whileSneaking();
            tray(scene, util, pos, false, ItemStack.EMPTY, ItemStack.EMPTY);
            scene.world().createEntity(level -> {
                var worm = ModEntities.SILKWORM.get().create(level);
                if (worm != null) worm.moveTo(Vec3.atBottomCenterOf(pos.above()));
                return worm;
            });
            text(guide, 4, scene, util, pos.above());
        }
    }
}
