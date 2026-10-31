package com.rexhub.createapparel.client.ponder;

import com.rexhub.createapparel.advertisement.AdvertisementPanelBlockEntity;
import com.rexhub.createapparel.advertisement.AdvertisementRegistries;
import com.rexhub.createapparel.fashion.CashRegisterBlockEntity;
import com.rexhub.createapparel.fashion.FashionRegistries;
import com.rexhub.createapparel.guide.RetailGuide;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public final class ApparelRetailScenes {
    private ApparelRetailScenes() { }

    public static void build(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title(RetailGuide.ID, RetailGuide.title(false));
        scene.configureBasePlate(1, 1, 5);
        scene.scaleSceneView(0.85F);
        scene.showBasePlate();
        BlockPos register = util.grid().at(3, 2, 3), seat = register.north(), storage = util.grid().at(1, 1, 3);
        scene.world().setBlock(register.below(), Blocks.SMOOTH_STONE.defaultBlockState(), false);
        scene.world().setBlock(register, FashionRegistries.CASH_REGISTER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), false);
        scene.world().setBlock(storage, Blocks.CHEST.defaultBlockState(), false);
        scene.world().showSection(util.select().position(register.below()), Direction.DOWN);
        scene.world().showSection(util.select().position(register), Direction.DOWN);
        scene.world().showSection(util.select().position(storage), Direction.DOWN);
        text(scene, util, 0, register);

        scene.world().setBlock(seat.below(), Blocks.SMOOTH_STONE.defaultBlockState(), false);
        scene.world().setBlock(seat, AllBlocks.SEATS.get(DyeColor.WHITE).getDefaultState(), false);
        scene.world().showSection(util.select().fromTo(seat.below(), seat), Direction.DOWN);
        text(scene, util, 1, seat);
        scene.world().setBlock(seat, Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(seat.below(), AllBlocks.SEATS.get(DyeColor.WHITE).getDefaultState(), false);
        SeatEntity[] cashierSeat = new SeatEntity[1];
        scene.world().createEntity(level -> {
            var entity = new SeatEntity(com.simibubi.create.AllEntityTypes.SEAT.get(), level);
            entity.setPos(Vec3.atBottomCenterOf(seat.below()));
            cashierSeat[0] = entity;
            return entity;
        });
        scene.world().createEntity(level -> {
            var cashier = Objects.requireNonNull(EntityType.VILLAGER.create(level));
            cashier.setNoAi(true);
            cashier.setNoGravity(true);
            cashier.setPos(Vec3.atBottomCenterOf(seat.below()));
            var vehicle = Objects.requireNonNull(cashierSeat[0]);
            cashier.startRiding(vehicle, true);
            vehicle.positionRider(cashier);
            cashier.setOldPosAndRot();
            return cashier;
        });
        scene.world().modifyBlockEntity(register, CashRegisterBlockEntity.class, entity -> entity.linkStorage(storage));
        scene.effects().indicateSuccess(register);
        text(scene, util, 2, seat.below());

        var phone = new ItemStack(ModItems.SMART_PHONE.get());
        scene.overlay().showControls(util.vector().topOf(register), Pointing.DOWN, 50).rightClick().withItem(phone);
        text(scene, util, 3, register);
        scene.overlay().showControls(util.vector().topOf(storage), Pointing.DOWN, 50).rightClick().whileSneaking().withItem(phone);
        scene.world().modifyBlockEntity(storage, net.minecraft.world.level.block.entity.ChestBlockEntity.class,
                chest -> chest.setItem(0, shirt().copyWithCount(16)));
        text(scene, util, 4, storage);

        BlockPos display = util.grid().at(5, 1, 3), advertisement = util.grid().at(4, 2, 5);
        scene.world().createEntity(level -> {
            var mannequin = Objects.requireNonNull(ModEntities.FULL_MANNEQUIN_WHITE_CLASSIC.get().create(level));
            mannequin.setPos(Vec3.atBottomCenterOf(display));
            mannequin.mannequinState().loadout().setStackInSlot(com.rexhub.createapparel.garment.GarmentSlot.TOP.ordinal(), shirt());
            if (level.getBlockEntity(register) instanceof CashRegisterBlockEntity entity) mannequin.mannequinState().setLinkedRegister(entity.reference());
            return mannequin;
        });
        scene.world().setBlock(advertisement, AdvertisementRegistries.BILLBOARD_PANEL.get().defaultBlockState(), false);
        scene.world().showSection(util.select().position(advertisement), Direction.DOWN);
        scene.world().modifyBlockEntity(advertisement, AdvertisementPanelBlockEntity.class, panel -> {
            if (panel.getLevel().getBlockEntity(register) instanceof CashRegisterBlockEntity entity) panel.link(entity.reference());
        });
        scene.overlay().showControls(util.vector().topOf(display), Pointing.DOWN, 50).rightClick().whileSneaking().withItem(phone);
        text(scene, util, 5, display);
        scene.markAsFinished();
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, int index, BlockPos target) {
        scene.overlay().showText(90).attachKeyFrame().text(RetailGuide.text(index, false)).pointAt(util.vector().topOf(target)).placeNearTarget();
        scene.idle(100);
    }

    private static ItemStack shirt() {
        var stack = new ItemStack(ModItems.T_SHIRT.get());
        new com.rexhub.createapparel.garment.GarmentData(com.rexhub.createapparel.material.FabricMaterial.COTTON, "plain",
                com.rexhub.createapparel.material.FabricColor.BLUE, java.util.Optional.empty(), java.util.Optional.empty()).write(stack);
        return stack;
    }
}
