package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.advertisement.AdvertisementPanelBlock;
import com.rexhub.createapparel.advertisement.AdvertisementPanelBlockEntity;
import com.rexhub.createapparel.advertisement.AdvertisementRegistries;
import com.rexhub.createapparel.advertisement.AdvertisementSnapshot;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.fashion.CitizenFashionController;
import com.rexhub.createapparel.fashion.PurchasedRegisterRef;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.photo.PhotographyService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FashionObservationGameTests {
    private static final int OBSERVATION_HEIGHT = 48;
    private FashionObservationGameTests() { }

    private static Citizen observer(GameTestHelper helper) {
        var citizen = Objects.requireNonNull(ModEntities.CITIZEN.get().create(helper.getLevel()));
        citizen.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1 + OBSERVATION_HEIGHT, 1))));
        citizen.setNoGravity(true);
        citizen.setYRot(0);
        citizen.setYHeadRot(0);
        return citizen;
    }

    private static boolean observed(Citizen citizen, Object sourceId) {
        try {
            var controller = citizen.fashionController();
            var history = CitizenFashionController.class.getDeclaredField("observations");
            history.setAccessible(true);
            var observations = (Map<?, ?>) history.get(controller);
            observations.clear();
            var observe = CitizenFashionController.class.getDeclaredMethod("observe", net.minecraft.server.level.ServerLevel.class, long.class);
            observe.setAccessible(true);
            observe.invoke(controller, citizen.level(), citizen.level().getGameTime() + 1);
            return observations.containsKey(sourceId);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void screen(GameTestHelper helper, int z, Block block) {
        for (int x = 1; x < 7; x++) for (int y = 1; y < 5; y++) helper.setBlock(new BlockPos(x, y + OBSERVATION_HEIGHT, z), block);
    }

    private static void checkDisplayScreens(GameTestHelper helper, Citizen observer, Object sourceId) {
        helper.assertTrue(observed(observer, sourceId), "An unobstructed display is observed");
        for (var glass : List.of(Blocks.GLASS, Blocks.WHITE_STAINED_GLASS, Blocks.RED_STAINED_GLASS,
                Blocks.TINTED_GLASS, Blocks.GLASS_PANE, Blocks.BLUE_STAINED_GLASS_PANE)) {
            screen(helper, 3, glass);
            helper.assertTrue(observed(observer, sourceId), "Display is observed through " + glass);
            screen(helper, 4, Blocks.GLASS_PANE);
            helper.assertTrue(observed(observer, sourceId), "Multiple glass layers remain transparent for " + glass);
            screen(helper, 4, Blocks.STONE);
            helper.assertTrue(!observed(observer, sourceId), "A wall behind glass still occludes the display for " + glass);
            screen(helper, 4, Blocks.AIR);
        }
        for (var wall : List.of(Blocks.STONE, Blocks.IRON_BARS)) {
            screen(helper, 3, wall);
            helper.assertTrue(!observed(observer, sourceId), "Non-glass collision remains opaque: " + wall);
        }
        screen(helper, 3, Blocks.GLASS);
        observer.setYRot(180);
        observer.setYHeadRot(180);
        helper.assertTrue(!observed(observer, sourceId), "Glass does not bypass the observer's viewing direction");
        observer.setYRot(0);
        observer.setYHeadRot(0);
        screen(helper, 3, Blocks.AIR);
    }

    @GameTest(template = "apparel_state", batch = "fashion_observation")
    public static void fashionDisplaysObserveThroughGlassButWallsStillOcclude(GameTestHelper helper) {
        var observer = observer(helper);
        var level = helper.getLevel();
        var target = helper.absolutePos(new BlockPos(2, 1 + OBSERVATION_HEIGHT, 6));
        var mannequin = Objects.requireNonNull(ModEntities.FULL_MANNEQUIN_WHITE_CLASSIC.get().create(level));
        mannequin.setNoGravity(true);
        mannequin.setPos(Vec3.atBottomCenterOf(target));
        mannequin.mannequinState().setLinkedRegister(new PurchasedRegisterRef(UUID.randomUUID(), level.dimension().location(), target));
        level.addFreshEntity(mannequin);
        checkDisplayScreens(helper, observer, mannequin.getUUID());
        mannequin.discard();
        checkPanels(helper, observer, target, 0);
    }

    private static void checkPanels(GameTestHelper helper, Citizen observer, BlockPos target, int index) {
        var level = helper.getLevel();
        var blocks = List.of(AdvertisementRegistries.POSTER.get(), AdvertisementRegistries.BILLBOARD_PANEL.get(),
                AdvertisementRegistries.SIGNAGE_PANEL.get());
        if (index == blocks.size()) {
            checkOtherCitizen(helper, observer, target);
            return;
        }
        var snapshot = AdvertisementSnapshot.photo(PhotographyService.snapshot(observer));
        var block = blocks.get(index);
        var pos = target.above();
        level.setBlockAndUpdate(pos, block.defaultBlockState().setValue(AdvertisementPanelBlock.FACING, Direction.NORTH));
        var panel = (AdvertisementPanelBlockEntity) Objects.requireNonNull(level.getBlockEntity(pos));
        if (block == AdvertisementRegistries.SIGNAGE_PANEL.get()) {
            var network = com.rexhub.createapparel.advertisement.AdvertisementNetworkSavedData.get(level);
            var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "observation-test"), net.minecraft.server.level.ClientInformation.createDefault());
            String channel = "observation_" + Math.abs(panel.groupId().hashCode());
            helper.assertTrue(network.publish(player, channel, snapshot), "Signage photo channel is published");
            panel.channel(channel);
        } else panel.content(snapshot);
        helper.runAfterDelay(2, () -> {
            checkDisplayScreens(helper, observer, panel.groupId());
            level.removeBlock(pos, false);
            checkPanels(helper, observer, target, index + 1);
        });
    }

    private static void checkOtherCitizen(GameTestHelper helper, Citizen observer, BlockPos target) {
        var level = helper.getLevel();
        var other = Objects.requireNonNull(ModEntities.CITIZEN.get().create(level));
        other.setNoGravity(true);
        other.setPos(Vec3.atBottomCenterOf(target));
        level.addFreshEntity(other);
        helper.assertTrue(observed(observer, other.getUUID()), "Other Citizens remain observable without obstruction");
        screen(helper, 3, Blocks.GLASS);
        helper.assertTrue(!observed(observer, other.getUUID()), "The display-only glass exception does not change Citizen observation");
        other.discard();
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "fashion_observation")
    public static void fashionSightDoesNotLoadMissingChunks(GameTestHelper helper) {
        var observer = Objects.requireNonNull(ModEntities.CITIZEN.get().create(helper.getLevel()));
        observer.setPos(1000000.5, 64, 1000000.5);
        observer.setYRot(0);
        observer.setYHeadRot(0);
        var target = observer.getEyePosition().add(0, 0, 4);
        var pos = BlockPos.containing(target);
        helper.assertTrue(!helper.getLevel().hasChunk(pos.getX() >> 4, pos.getZ() >> 4), "The observation region starts unloaded");
        try {
            var visible = CitizenFashionController.class.getDeclaredMethod("visible", net.minecraft.server.level.ServerLevel.class, CitizenFashionController.Source.class);
            visible.setAccessible(true);
            for (boolean seeThroughGlass : new boolean[]{false, true}) {
                var source = new CitizenFashionController.Source(UUID.randomUUID(), target, observer.citizenState().loadout(),
                        slot -> null, 1, seeThroughGlass);
                helper.assertTrue(Boolean.FALSE.equals(visible.invoke(observer.fashionController(), helper.getLevel(), source)),
                        "Unloaded blocks conservatively occlude observation");
            }
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        helper.assertTrue(!helper.getLevel().hasChunk(pos.getX() >> 4, pos.getZ() >> 4), "Sight tracing does not force-load chunks");
        helper.succeed();
    }
}
