package com.rexhub.createapparel.gametest;

import com.mojang.authlib.GameProfile;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.advertisement.*;
import com.rexhub.createapparel.entity.*;
import com.rexhub.createapparel.fashion.*;
import com.rexhub.createapparel.garment.*;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.photo.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FashionRetailGameTests {
    private FashionRetailGameTests() { }
    private static Citizen citizen(GameTestHelper helper) {
        var citizen = Objects.requireNonNull(ModEntities.CITIZEN.get().create(helper.getLevel()));
        citizen.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        helper.getLevel().addFreshEntity(citizen); return citizen;
    }
    private static ServerPlayer player(GameTestHelper helper, Citizen citizen) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "fashion-test"));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
        };
        player.setPos(citizen.position()); player.getInventory().selected = 0; player.getInventory().setItem(0, new ItemStack(PhotoRegistries.CAMERA.get())); return player;
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void revisedFashionTagsLoadOnTheServerAndKeepDesignSpecificEligibility(GameTestHelper helper) {
        for (var name : List.of("t_shirt", "t_shirt_u_neck", "t_shirt_v_neck", "t_shirt_longsleeves", "t_shirt_longsleeves_u_neck", "t_shirt_longsleeves_v_neck")) {
            var definition = GarmentDefinitionManager.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, name)).orElseThrow();
            var tags = FashionTagResolver.resolve(definition, "logo_creeper");
            helper.assertTrue(!tags.contains(FashionTag.KIDS) && FashionEvaluator.eligible(CitizenAge.ADULT, com.rexhub.createapparel.apparel.ChestProfile.FLAT, tags), "Creeper T-shirts are eligible for adults: " + name);
        }
        var backpack = GarmentDefinitionManager.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "backpack")).orElseThrow();
        helper.assertTrue(!FashionEvaluator.eligible(CitizenAge.ADULT, com.rexhub.createapparel.apparel.ChestProfile.FLAT, FashionTagResolver.resolve(backpack, "logo_creeper")), "The shared Creeper design still restricts other garments to children");
        var expected = Map.of(
                "ankle_socks/argyle", Set.of(FashionTag.CASUAL, FashionTag.FLASHY, FashionTag.THIN),
                "knee_high_socks/stripe", Set.of(FashionTag.CASUAL, FashionTag.CUTE),
                "thigh_high_socks/toe_heel", Set.of(FashionTag.CASUAL, FashionTag.FLASHY),
                "sweater/argyle", Set.of(FashionTag.CASUAL, FashionTag.FORMAL, FashionTag.THICK),
                "panties/contrast_bow", Set.of(FashionTag.CUTE, FashionTag.FEMININE, FashionTag.SIMPLE),
                "pullover_hoodie/plain", Set.of(FashionTag.CASUAL, FashionTag.SIMPLE),
                "a_line_skirt_maxi/plain", Set.of(FashionTag.FEMININE, FashionTag.SIMPLE),
                "backpack/plain", Set.of(FashionTag.SIMPLE),
                "tight_skirt_midi/gingham_check", Set.of(FashionTag.CASUAL, FashionTag.CUTE, FashionTag.FEMININE, FashionTag.FORMAL),
                "tights/stripe", Set.of(FashionTag.CASUAL, FashionTag.FEMININE));
        expected.forEach((key, tags) -> {
            var parts = key.split("/");
            var definition = GarmentDefinitionManager.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, parts[0])).orElseThrow();
            helper.assertTrue(FashionTagResolver.resolve(definition, parts[1]).equals(tags), "Server-loaded fashion tags: " + key);
        });
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void defaultAdvertisementsUseWorldPhotosAndPostersDoNotOpenMenus(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen);
        for (int index = 0; index < 3; index++) {
            var snapshot = AdvertisementScenes.DEFAULTS.get(index);
            helper.assertTrue(snapshot.photo() == null && !snapshot.displayPhoto().garments().isEmpty(), "Default advertisements render bundled photos without becoming printed items");
            helper.assertTrue(snapshot.displayPhoto().layout().bodyYaw() == (index == 1 ? 345 : 30), "Defaults retain the world barrel photo body directions");
        }
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var state = AdvertisementRegistries.POSTER.get().defaultBlockState();
        helper.getLevel().setBlockAndUpdate(pos.south(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos.south().above(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, state);
        state.getBlock().setPlacedBy(helper.getLevel(), pos, state, player, new ItemStack(AdvertisementRegistries.POSTER_ITEM.get()));
        var panel = (AdvertisementPanelBlockEntity) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(AdvertisementScenes.DEFAULTS.contains(panel.localSnapshot()), "An empty poster chooses one of the three photo defaults");
        var before = player.containerMenu;
        var result = state.use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        helper.assertTrue(result == net.minecraft.world.InteractionResult.PASS && player.containerMenu == before, "Regular poster use never opens an editor");
        var signage = AdvertisementRegistries.SIGNAGE_PANEL.get().defaultBlockState();
        helper.assertTrue(signage.getLightEmission(helper.getLevel(), pos) == 15, "Signage emits level fifteen light");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void broadcasterStoresNinePhotosPublishesOnCloseAndReturnsItems(GameTestHelper helper) throws ReflectiveOperationException {
        var citizen = citizen(helper); var player = player(helper, citizen); var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, AdvertisementRegistries.SIGNAGE_BROADCASTER.get().defaultBlockState());
        var broadcaster = (SignageBroadcasterBlockEntity) level.getBlockEntity(pos); broadcaster.initialize(player);
        helper.assertTrue(broadcaster.channel().isEmpty(), "Broadcasters do not default to a global channel");
        var duplicate = new ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get()); AdvertisementScenes.DEFAULTS.get(0).displayPhoto().write(duplicate);
        broadcaster.photos().setItem(2, duplicate.copy()); broadcaster.photos().setItem(8, duplicate.copy());
        var sparse = new SignageBroadcasterBlockEntity(pos, broadcaster.getBlockState()); sparse.load(broadcaster.saveWithoutMetadata());
        helper.assertTrue(sparse.photos().getItem(0).isEmpty() && sparse.photos().getItem(2).getCount() == 1
                && sparse.photos().getItem(8).getCount() == 1, "Sparse and identical photo scenes retain separate slot positions on reload");
        broadcaster.photos().clearContent();
        var photos = new ArrayList<AdvertisingPhotoData>();
        for (int index = 0; index < 9; index++) {
            var photo = AdvertisementScenes.DEFAULTS.get(index % 3).displayPhoto().copyIdentity(); photos.add(photo);
            var stack = new ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get()); photo.write(stack); broadcaster.photos().setItem(index, stack);
        }
        AdvertisementControlMenu.open(player, pos, net.minecraft.world.InteractionHand.MAIN_HAND);
        var menu = (AdvertisementControlMenu) player.containerMenu;
        helper.assertTrue(menu.slots.size() == 45 && menu.getSlot(0).getMaxStackSize() == 1
                && !menu.getSlot(0).mayPlace(new ItemStack(Items.STONE)), "The broadcaster exposes nine single-photo slots and inventory");
        var network = AdvertisementNetworkSavedData.get(level);
        helper.assertTrue(menu.configure(player, "nine_scene_test", 0, -1) && network.channel("nine_scene_test").revision() == 0, "Channel edits are staged until close");
        player.closeContainer();
        var published = network.channel("nine_scene_test");
        helper.assertTrue(published.scenes().size() == 9 && published.owner().equals(player.getUUID()), "Closing publishes nine owned scenes");
        for (int index = 0; index < 9; index++) helper.assertTrue(published.scenes().get(index).photo().save().equals(photos.get(index).save())
                && Objects.requireNonNull(AdvertisingPhotoData.read(broadcaster.photos().getItem(index))).save().equals(photos.get(index).save()), "Publication preserves both scene values and physical masters");
        var restored = new SignageBroadcasterBlockEntity(pos, broadcaster.getBlockState()); restored.load(broadcaster.saveWithoutMetadata());
        helper.assertTrue(restored.channel().equals("nine_scene_test") && restored.photos().getContainerSize() == 9
                && ItemStack.matches(restored.photos().getItem(8), broadcaster.photos().getItem(8)), "Photo storage and channel survive NBT reload");
        var loader = AdvertisementNetworkSavedData.class.getDeclaredMethod("load", CompoundTag.class); loader.setAccessible(true);
        var restoredNetwork = (AdvertisementNetworkSavedData) loader.invoke(null, network.save(new CompoundTag()));
        helper.assertTrue(restoredNetwork.channel("nine_scene_test").equals(published), "All nine scenes, revision and channel owner survive saved data reload");
        var stranger = player(helper, citizen);
        helper.assertTrue(!network.publish(stranger, "nine_scene_test", AdvertisementScenes.DEFAULTS), "Another player cannot replace this playlist");
        level.destroyBlock(pos, true);
        var dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(1))
                .stream().filter(entity -> entity.getItem().is(PhotoRegistries.ADVERTISING_PHOTO.get())).mapToInt(entity -> entity.getItem().getCount()).sum();
        helper.assertTrue(dropped == 9 && network.channel("nine_scene_test").equals(published), "Breaking returns nine masters without erasing the published playlist");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void panelLayoutsAreStagedAndSignageSceneEditsDoNotRewriteBroadcasts(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen); var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, AdvertisementRegistries.BILLBOARD_PANEL.get().defaultBlockState());
        var panel = (AdvertisementPanelBlockEntity) level.getBlockEntity(pos); panel.initialize(player, new CompoundTag());
        var before = panel.localSnapshot();
        AdvertisementControlMenu.open(player, pos, net.minecraft.world.InteractionHand.MAIN_HAND);
        var menu = (AdvertisementControlMenu) player.containerMenu;
        helper.assertTrue(menu.slots.isEmpty() && menu.configure(player, "", 0, 1) && panel.localSnapshot().equals(before), "Billboard edits affect only a slot-free menu preview before close");
        helper.assertTrue(!menu.configure(player, "", 0, 7), "Captured body direction is not a panel setting");
        player.closeContainer();
        helper.assertTrue(panel.localSnapshot().photo() == null && panel.localSnapshot().displayPhoto().layout().framing() == PhotoLayout.Framing.UPPER_BODY
                && panel.localSnapshot().displayPhoto().layout().bodyYaw() == before.displayPhoto().layout().bodyYaw(), "Closing saves framing while preserving default media and captured direction");
        var signagePos = helper.absolutePos(new BlockPos(3, 2, 2));
        level.setBlockAndUpdate(signagePos, AdvertisementRegistries.SIGNAGE_PANEL.get().defaultBlockState());
        var signage = (AdvertisementPanelBlockEntity) level.getBlockEntity(signagePos); signage.initialize(player, new CompoundTag());
        var scenes = java.util.stream.IntStream.range(0, 9).mapToObj(index -> AdvertisementSnapshot.photo(AdvertisementScenes.DEFAULTS.get(index % 3).displayPhoto().copyIdentity())).toList();
        var network = AdvertisementNetworkSavedData.get(level); network.publish(player, "layout_scene_test", scenes);
        AdvertisementControlMenu.open(player, signagePos, net.minecraft.world.InteractionHand.MAIN_HAND);
        var signageMenu = (AdvertisementControlMenu) player.containerMenu;
        helper.assertTrue(!menu.configure(player, "", 0, 1), "Old menu actions cannot modify a replacement menu");
        helper.assertTrue(signageMenu.configure(player, "layout_scene_test", 8, 1) && signage.channel().isEmpty(), "Scene changes and channel selection are staged");
        player.closeContainer();
        helper.assertTrue(signage.channel().equals("layout_scene_test") && signage.sceneLayouts().size() == 1 && signage.sceneLayouts().containsKey(8), "Only the edited scene stores a local layout override");
        helper.assertTrue(signage.currentScenes().get(8).displayPhoto().layout().framing() == PhotoLayout.Framing.UPPER_BODY
                && network.channel("layout_scene_test").scenes().get(8).displayPhoto().layout().framing() == PhotoLayout.Framing.FULL_BODY, "Panel edits do not change another display's broadcast content");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void legacyAdvertisementChannelsAndUnconfiguredGlobalPanelsMigrate(GameTestHelper helper) throws ReflectiveOperationException {
        var photo = AdvertisementScenes.DEFAULTS.get(0).displayPhoto();
        var legacy = new CompoundTag(); var entry = new CompoundTag(); var channels = new net.minecraft.nbt.ListTag();
        entry.putString("Id", "legacy_scene"); entry.putLong("Revision", 5); entry.putUUID("Owner", UUID.randomUUID());
        entry.put("Snapshot", AdvertisementSnapshot.photo(photo).save()); channels.add(entry); legacy.put("Channels", channels);
        var loader = AdvertisementNetworkSavedData.class.getDeclaredMethod("load", CompoundTag.class); loader.setAccessible(true);
        var restored = (AdvertisementNetworkSavedData) loader.invoke(null, legacy);
        helper.assertTrue(restored.channel("legacy_scene").scenes().size() == 1 && restored.channel("legacy_scene").snapshot().photo().save().equals(photo.save())
                && restored.channel("legacy_scene").revision() == 5, "Single-scene broadcasts retain their photo, owner and revision");
        var pos = helper.absolutePos(new BlockPos(2, 2, 2)); var state = AdvertisementRegistries.SIGNAGE_PANEL.get().defaultBlockState();
        var panel = new AdvertisementPanelBlockEntity(pos, state); var tag = panel.saveWithoutMetadata(); tag.putInt("Version", 1);
        tag.getCompound("Content").putString("Channel", "global"); panel.load(tag);
        helper.assertTrue(panel.channel().isEmpty() && panel.currentScenes().size() == 3, "Legacy implicit global signage becomes an unconfigured three-photo loop");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void citizenPhotographyChargesOnceAndPreservesModelAndOwnedItems(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen); var before = citizen.citizenState().serializeNBT();
        int quote = PhotographyService.quote(citizen); helper.assertTrue(quote == PhotographyService.quote(citizen), "Daily quote is stable");
        player.getInventory().setItem(1, new ItemStack(Items.EMERALD, quote));
        var menu = PhotographyMenu.capture(15, player.getInventory(), PhotographyService.snapshot(citizen), citizen.getId(), 0, quote); player.containerMenu = menu;
        menu.clicked(PhotographyMenu.INVENTORY_START + 28, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
        helper.assertTrue(PhotographyService.issue(player, menu), "Photography succeeds");
        helper.assertTrue(menu.getSlot(PhotographyMenu.PAYMENT_SLOT).getItem().isEmpty(), "Exact model fee consumed from the input slot");
        helper.assertTrue(before.equals(citizen.citizenState().serializeNBT()), "Model apparel unchanged");
        ItemStack photo = player.getInventory().items.stream().filter(stack -> stack.is(PhotoRegistries.ADVERTISING_PHOTO.get())).findFirst().orElseThrow();
        var data = Objects.requireNonNull(AdvertisingPhotoData.read(photo));
        helper.assertTrue(data.playerId() == null && !data.garments().isEmpty(), "Citizen appearance stored without entity reference");
        helper.assertTrue(!PhotographyService.issue(player, menu), "Insufficient remaining emeralds cannot create another photo");
        var saved = data.save(); var copy = data.copyIdentity();
        helper.assertTrue(!copy.photoId().equals(data.photoId()) && copy.captureId().equals(data.captureId()), "Copy identity is independent");
        copy.withLayout(new PhotoLayout(com.rexhub.createapparel.material.FabricColor.RED, data.layout().framing(), data.layout().aspect(), data.layout().horizontal(), data.layout().vertical(), true));
        helper.assertTrue(saved.equals(data.save()), "Editing copies preserves original");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void fullInventoryAndMissingGarmentsFailWithoutPayment(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen); int quote = PhotographyService.quote(citizen);
        player.getInventory().setItem(1, new ItemStack(Items.EMERALD, quote));
        for (int index = 2; index < 36; index++) player.getInventory().setItem(index, new ItemStack(Items.STONE, 64));
        var menu = PhotographyMenu.capture(16, player.getInventory(), PhotographyService.snapshot(citizen), citizen.getId(), 0, quote); player.containerMenu = menu;
        menu.getSlot(PhotographyMenu.PAYMENT_SLOT).set(new ItemStack(Items.EMERALD, quote));
        player.getInventory().setItem(1, new ItemStack(Items.STONE, 64));
        helper.assertTrue(!PhotographyService.issue(player, menu) && menu.getSlot(PhotographyMenu.PAYMENT_SLOT).getItem().getCount() == quote, "Full inventory leaves fee untouched");
        player.getInventory().setItem(2, ItemStack.EMPTY);
        menu.loadout().setStackInSlot(GarmentSlot.TOP.ordinal(), new ItemStack(ModItems.SWEATER.get()));
        helper.assertTrue(!PhotographyService.issue(player, menu) && menu.getSlot(PhotographyMenu.PAYMENT_SLOT).getItem().getCount() == quote, "Unowned garment leaves fee untouched");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void photographyTradeUsesInputAndCommitsOnlyOnResultPickup(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen); int quote = PhotographyService.quote(citizen);
        var menu = PhotographyMenu.capture(17, player.getInventory(), PhotographyService.snapshot(citizen), citizen.getId(), 0, quote);
        player.containerMenu = menu;
        player.getInventory().setItem(1, new ItemStack(Items.EMERALD, 64));
        menu.broadcastChanges();
        helper.assertTrue(!menu.getSlot(PhotographyMenu.RESULT_SLOT).hasItem(), "Inventory emeralds cannot pay for a trade");
        helper.assertTrue(!menu.getSlot(PhotographyMenu.PAYMENT_SLOT).mayPlace(new ItemStack(Items.DIAMOND)), "Input rejects other items");
        menu.getSlot(PhotographyMenu.PAYMENT_SLOT).set(new ItemStack(Items.EMERALD, quote - 1)); menu.broadcastChanges();
        menu.clicked(PhotographyMenu.RESULT_SLOT, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && !menu.getSlot(PhotographyMenu.RESULT_SLOT).hasItem(), "Underpayment cannot create a photo");
        menu.getSlot(PhotographyMenu.PAYMENT_SLOT).set(new ItemStack(Items.EMERALD, quote + 1)); menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(PhotographyMenu.RESULT_SLOT).hasItem(), "Payment reveals the output preview");
        helper.assertTrue(menu.getSlot(PhotographyMenu.PAYMENT_SLOT).getItem().getCount() == quote + 1, "Preview does not charge");
        helper.assertTrue(!menu.clickMenuButton(player, 8), "The removed capture button cannot bypass the trade");
        menu.clickMenuButton(player, 7);
        menu.clicked(PhotographyMenu.RESULT_SLOT, 1, net.minecraft.world.inventory.ClickType.PICKUP, player);
        var photo = Objects.requireNonNull(AdvertisingPhotoData.read(menu.getCarried()));
        helper.assertTrue(photo.layout().bodyYaw() == 15, "Pickup captures the latest rotation");
        helper.assertTrue(!photo.photoId().equals(new UUID(0, 0)) && !photo.captureId().equals(new UUID(0, 0)), "Only issued photos receive capture identities");
        helper.assertTrue(menu.getSlot(PhotographyMenu.PAYMENT_SLOT).getItem().getCount() == 1, "Exactly one fee is charged");
        helper.assertTrue(player.getInventory().getItem(1).getCount() == 64, "Unsubmitted emeralds stay untouched");
        menu.clicked(PhotographyMenu.RESULT_SLOT, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getCount() == 1 && menu.getSlot(PhotographyMenu.PAYMENT_SLOT).getItem().getCount() == 1, "A repeated packet cannot duplicate the result");
        menu.setCarried(ItemStack.EMPTY); menu.removed(player);
        helper.assertTrue(player.getInventory().items.stream().filter(stack -> stack.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum() == 65,
                "Closing returns the unspent input");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void photographyResultProtectsCursorPreviewAndInvalidSubjects(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen); int quote = PhotographyService.quote(citizen);
        var menu = PhotographyMenu.capture(18, player.getInventory(), PhotographyService.snapshot(citizen), citizen.getId(), 0, quote);
        player.containerMenu = menu; menu.getSlot(PhotographyMenu.PAYMENT_SLOT).set(new ItemStack(Items.EMERALD, quote)); menu.broadcastChanges();
        for (var type : new net.minecraft.world.inventory.ClickType[]{net.minecraft.world.inventory.ClickType.SWAP,
                net.minecraft.world.inventory.ClickType.CLONE, net.minecraft.world.inventory.ClickType.THROW, net.minecraft.world.inventory.ClickType.PICKUP_ALL})
            menu.clicked(PhotographyMenu.RESULT_SLOT, 0, type, player);
        helper.assertTrue(menu.getCarried().isEmpty() && menu.getSlot(PhotographyMenu.PAYMENT_SLOT).getItem().getCount() == quote, "Unsupported output clicks do not issue or charge");
        menu.setCarried(new ItemStack(Items.DIAMOND));
        menu.clicked(PhotographyMenu.RESULT_SLOT, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.DIAMOND) && menu.getSlot(PhotographyMenu.PAYMENT_SLOT).getItem().getCount() == quote, "Occupied cursor is not overwritten or charged");
        menu.setCarried(ItemStack.EMPTY);
        for (var garment : GarmentSlot.values()) {
            var position = com.rexhub.createapparel.menu.ApparelMenu.apparelSlotPosition(garment);
            var slot = menu.getSlot(garment.ordinal());
            helper.assertTrue(slot.x == position.x() + PhotographyMenuLayout.GARMENT_X_OFFSET && slot.y == position.y(), "Clothing arrangement matches Wardrobe within the centered garment area");
            helper.assertTrue(slot.mayPickup(player) == slot.hasItem() && slot.mayPlace(new ItemStack(ModItems.T_SHIRT.get())) == menu.loadout().isItemValid(garment.ordinal(), new ItemStack(ModItems.T_SHIRT.get())),
                    "Trial garments can be picked up and only accept the matching clothing type");
        }
        citizen.discard(); menu.broadcastChanges();
        helper.assertTrue(!menu.getSlot(PhotographyMenu.RESULT_SLOT).hasItem(), "An invalid subject clears the result");
        helper.assertTrue(!PhotographyService.issue(player, menu) && menu.getSlot(PhotographyMenu.PAYMENT_SLOT).getItem().getCount() == quote, "A missing model never charges");
        menu.removed(player); helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void photographyClothingDragAndDropIsTemporaryAndPreservesPaidPhotos(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen);
        var modelTop = new ItemStack(ModItems.SWEATER.get()); modelTop.getOrCreateTag().putString("TrialOwner", "model");
        var ownedTop = new ItemStack(ModItems.T_SHIRT.get()); ownedTop.getOrCreateTag().putString("TrialOwner", "photographer");
        citizen.citizenState().loadout().setStackInSlot(GarmentSlot.TOP.ordinal(), modelTop.copy());
        player.getInventory().setItem(9, ownedTop.copy());
        var originalOutfit = citizen.citizenState().loadout().serializeNBT().copy();
        int quote = PhotographyService.quote(citizen);
        var menu = PhotographyMenu.capture(28, player.getInventory(), PhotographyService.snapshot(citizen), citizen.getId(), 0, quote);
        player.containerMenu = menu;
        menu.clicked(PhotographyMenu.INVENTORY_START, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(ItemStack.matches(menu.getCarried(), ownedTop) && !menu.getSlot(PhotographyMenu.INVENTORY_START).hasItem(), "Picking clothing up uses the ordinary carried cursor");
        menu.clicked(GarmentSlot.TOP.ordinal(), 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(ItemStack.matches(menu.loadout().getStack(GarmentSlot.TOP), ownedTop) && menu.getCarried().is(ModItems.SWEATER.get()), "Dropping clothing on the matching slot swaps the outfit");
        menu.clicked(PhotographyMenu.INVENTORY_START + 1, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && menu.getSlot(PhotographyMenu.INVENTORY_START + 1).getItem().is(ModItems.SWEATER.get())
                && player.getInventory().getItem(10).isEmpty(), "Recovered model clothing stays in the trial inventory only");
        menu.clicked(GarmentSlot.TOP.ordinal(), 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        menu.clicked(PhotographyMenu.INVENTORY_START, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(menu.loadout().getStack(GarmentSlot.TOP).isEmpty() && ItemStack.matches(menu.getSlot(PhotographyMenu.INVENTORY_START).getItem(), ownedTop), "Trial clothing can be recovered into its original inventory slot");
        menu.clicked(PhotographyMenu.INVENTORY_START, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        menu.clicked(GarmentSlot.TOP.ordinal(), 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        menu.getSlot(PhotographyMenu.PAYMENT_SLOT).set(new ItemStack(Items.EMERALD, quote + 3));
        helper.assertTrue(PhotographyService.issue(player, menu), "Trial clothing remains eligible for a paid photo");
        var photo = player.getInventory().items.stream().map(AdvertisingPhotoData::read).filter(Objects::nonNull).findFirst().orElseThrow();
        helper.assertTrue(photo.loadout(GarmentDefinitionManager::get).getStack(GarmentSlot.TOP).is(ModItems.T_SHIRT.get()), "The issued photo captures the trial outfit");
        menu.clicked(PhotographyMenu.INVENTORY_START + 1, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        menu.removed(player);
        helper.assertTrue(menu.getCarried().isEmpty() && ItemStack.matches(player.getInventory().getItem(9), ownedTop)
                && player.getInventory().getItem(10).isEmpty(), "Closing with borrowed clothing on the cursor restores the real inventory without copies");
        helper.assertTrue(originalOutfit.equals(citizen.citizenState().loadout().serializeNBT()), "The real model outfit never changes");
        helper.assertTrue(player.getInventory().items.stream().filter(stack -> stack.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum() == 3,
                "Closing preserves photo payment and refunds only the unused emeralds");
        helper.assertTrue(player.getInventory().items.stream().filter(stack -> stack.is(PhotoRegistries.ADVERTISING_PHOTO.get())).count() == 1,
                "Closing never rolls the issued photo back");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void photographyQuickCraftCanDressAndRecoverTrialClothing(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen);
        var owned = new ItemStack(ModItems.T_SHIRT.get()); player.getInventory().setItem(9, owned.copy());
        var menu = PhotographyMenu.capture(30, player.getInventory(), PhotographyService.snapshot(citizen), citizen.getId(), 0, PhotographyService.quote(citizen));
        player.containerMenu = menu;
        menu.loadout().setStackInSlot(GarmentSlot.TOP.ordinal(), ItemStack.EMPTY);
        for (int[] transfer : new int[][]{{PhotographyMenu.INVENTORY_START, GarmentSlot.TOP.ordinal()},
                {GarmentSlot.TOP.ordinal(), PhotographyMenu.INVENTORY_START}}) {
            menu.clicked(transfer[0], 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().is(ModItems.T_SHIRT.get()) && !menu.getSlot(transfer[1]).hasItem() && menu.getSlot(transfer[1]).mayPlace(menu.getCarried())
                    && menu.canDragTo(menu.getSlot(transfer[1])), "The drag source, target validity and drag permission are correct: " + Arrays.toString(transfer));
            menu.clicked(-999, net.minecraft.world.inventory.AbstractContainerMenu.getQuickcraftMask(0, 0), net.minecraft.world.inventory.ClickType.QUICK_CRAFT, player);
            menu.clicked(transfer[1], net.minecraft.world.inventory.AbstractContainerMenu.getQuickcraftMask(1, 0), net.minecraft.world.inventory.ClickType.QUICK_CRAFT, player);
            menu.clicked(-999, net.minecraft.world.inventory.AbstractContainerMenu.getQuickcraftMask(2, 0), net.minecraft.world.inventory.ClickType.QUICK_CRAFT, player);
            helper.assertTrue(menu.getCarried().isEmpty() && menu.getSlot(transfer[1]).getItem().is(ModItems.T_SHIRT.get()),
                    "Vanilla drag packets can move a trial garment between the inventory and clothing slot: " + Arrays.toString(transfer)
                            + "; carried=" + menu.getCarried() + "; target=" + menu.getSlot(transfer[1]).getItem());
        }
        helper.assertTrue(menu.loadout().getStack(GarmentSlot.TOP).isEmpty() && ItemStack.matches(player.getInventory().getItem(9), owned),
                "Drag recovery never moves the real garment");
        menu.removed(player); helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void photographyTrialClothingCannotEscapeAndTracksExternalInventoryChanges(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen);
        var ownedTop = new ItemStack(ModItems.T_SHIRT.get()); ownedTop.getOrCreateTag().putInt("Preserved", 7);
        citizen.citizenState().loadout().setStackInSlot(GarmentSlot.TOP.ordinal(), new ItemStack(ModItems.SWEATER.get()));
        player.getInventory().setItem(9, ownedTop.copy());
        var menu = PhotographyMenu.capture(29, player.getInventory(), PhotographyService.snapshot(citizen), citizen.getId(), 0, PhotographyService.quote(citizen));
        player.containerMenu = menu;
        var area = citizen.getBoundingBox().inflate(4);
        int drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area).size();
        menu.clicked(GarmentSlot.TOP.ordinal(), 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        menu.clicked(net.minecraft.world.inventory.AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(ModItems.SWEATER.get()), "Borrowed clothing cannot be thrown outside the screen");
        menu.clicked(PhotographyMenu.PAYMENT_SLOT, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(ModItems.SWEATER.get()) && !menu.getSlot(PhotographyMenu.PAYMENT_SLOT).hasItem(), "Clothing cannot enter the real payment slot");
        menu.clicked(PhotographyMenu.INVENTORY_START + 1, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        for (var type : new net.minecraft.world.inventory.ClickType[]{net.minecraft.world.inventory.ClickType.THROW,
                net.minecraft.world.inventory.ClickType.CLONE, net.minecraft.world.inventory.ClickType.SWAP})
            menu.clicked(PhotographyMenu.INVENTORY_START + 1, 0, type, player);
        helper.assertTrue(menu.getCarried().isEmpty() && menu.getSlot(PhotographyMenu.INVENTORY_START + 1).getItem().is(ModItems.SWEATER.get()), "Drop, clone and hotbar swap cannot export a trial item");
        helper.assertTrue(!menu.getSlot(PhotographyMenu.INVENTORY_START).mayPlace(new ItemStack(Items.DIAMOND)), "Real clothing reservations cannot be overwritten by real items");
        menu.quickMoveStack(player, PhotographyMenu.INVENTORY_START + 1);
        helper.assertTrue(menu.loadout().getStack(GarmentSlot.TOP).is(ModItems.SWEATER.get()), "Shift equips the recovered trial clothing");
        menu.clicked(GarmentSlot.TOP.ordinal(), 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        menu.clicked(PhotographyMenu.INVENTORY_START + 1, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        player.getInventory().setItem(10, new ItemStack(Items.DIAMOND)); menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(PhotographyMenu.INVENTORY_START + 1).getItem().is(Items.DIAMOND), "An external real inventory update wins over a borrowed display item");
        helper.assertTrue(!menu.getSlot(PhotographyMenu.INVENTORY_START + 1).mayPlace(ownedTop), "Real non-clothing items cannot be overwritten by trial clothes");
        menu.clicked(PhotographyMenu.INVENTORY_START, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        player.setHealth(0); menu.removed(player);
        helper.assertTrue(menu.getCarried().isEmpty() && ItemStack.matches(player.getInventory().getItem(9), ownedTop)
                && player.getInventory().getItem(10).is(Items.DIAMOND), "Death closes the trial without dropping copies or reverting unrelated inventory changes");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area).size() == drops,
                "No trial clothing became a world item");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void photographyTrialClothingCannotEnterOrExtractRealBundleContents(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen);
        var bundle = new ItemStack(Items.BUNDLE); var contents = new net.minecraft.nbt.ListTag();
        contents.add(new ItemStack(ModItems.T_SHIRT.get()).save(new CompoundTag())); bundle.getOrCreateTag().put("Items", contents);
        player.getInventory().setItem(9, bundle.copy());
        var emptyBundle = new ItemStack(Items.BUNDLE); player.getInventory().setItem(11, emptyBundle.copy());
        var menu = PhotographyMenu.capture(31, player.getInventory(), PhotographyService.snapshot(citizen), citizen.getId(), 0, PhotographyService.quote(citizen));
        player.containerMenu = menu;
        menu.clicked(PhotographyMenu.INVENTORY_START, 1, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && ItemStack.matches(player.getInventory().getItem(9), bundle), "A real bundle cannot supply clothes to the virtual cursor");
        menu.clicked(PhotographyMenu.INVENTORY_START, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        menu.clicked(PhotographyMenu.INVENTORY_START + 1, 1, net.minecraft.world.inventory.ClickType.PICKUP, player);
        for (int phase = 0; phase <= 2; phase++) menu.clicked(phase == 1 ? PhotographyMenu.INVENTORY_START + 1 : -999,
                net.minecraft.world.inventory.AbstractContainerMenu.getQuickcraftMask(phase, 1), net.minecraft.world.inventory.ClickType.QUICK_CRAFT, player);
        helper.assertTrue(ItemStack.matches(menu.getCarried(), bundle) && !menu.getSlot(PhotographyMenu.INVENTORY_START + 1).hasItem(), "Right click and drag cannot unpack real bundle clothes into trial slots");
        menu.clicked(PhotographyMenu.INVENTORY_START, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        menu.clicked(GarmentSlot.TOP.ordinal(), 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        var borrowed = menu.getCarried().copy();
        helper.assertTrue(!borrowed.isEmpty(), "The model provides a virtual garment");
        menu.clicked(PhotographyMenu.INVENTORY_START + 2, 1, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(ItemStack.matches(menu.getCarried(), borrowed) && ItemStack.matches(player.getInventory().getItem(11), emptyBundle), "A virtual garment cannot be packed into an empty real bundle");
        menu.removed(player);
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(9), bundle) && ItemStack.matches(player.getInventory().getItem(11), emptyBundle)
                && menu.getCarried().isEmpty(), "Closing preserves real bundle contents exactly");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void photographyStartsWithTheModelsWornStyles(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen);
        helper.getLevel().addFreshEntity(player);
        for (var target : new net.minecraft.world.entity.LivingEntity[]{citizen, player}) {
            var outfit = PhotographyService.loadout(target);
            outfit.setStackInSlot(GarmentSlot.TOP.ordinal(), new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()));
            helper.assertTrue(outfit.setStyle(GarmentSlot.TOP, "tuck", "tucked") && outfit.setStyle(GarmentSlot.TOP, "sleeves", "rolled")
                    && outfit.setStyle(GarmentSlot.TOP, "open", "opened"), "Models wear a non-default style before photography");
            var worn = outfit.serializeNBT().copy();
            var expected = outfit.styleState(GarmentSlot.TOP);
            var snapshot = Objects.requireNonNull(AdvertisingPhotoData.load(PhotographyService.snapshot(target).save()));
            helper.assertTrue(snapshot.loadout(GarmentDefinitionManager::get).styleState(GarmentSlot.TOP).equals(expected), "The opening snapshot retains worn styles");
            int cost = target == citizen ? PhotographyService.quote(citizen) : 0;
            var menu = PhotographyMenu.capture(44, player.getInventory(), snapshot, target.getId(), 0, cost);
            player.containerMenu = menu;
            menu.sendAllDataToRemote(); menu.broadcastFullState();
            helper.assertTrue(menu.loadout().styleState(GarmentSlot.TOP).equals(expected), "Initial and full synchronization preserve every worn style");
            if (cost > 0) menu.getSlot(PhotographyMenu.PAYMENT_SLOT).set(new ItemStack(Items.EMERALD, cost));
            var photo = Objects.requireNonNull(AdvertisingPhotoData.read(menu.takePhoto(player, false)));
            helper.assertTrue(photo.loadout(GarmentDefinitionManager::get).styleState(GarmentSlot.TOP).equals(expected), "An unedited photo captures the original worn styles");
            menu.setCarried(ItemStack.EMPTY); menu.removed(player);
            helper.assertTrue(outfit.serializeNBT().equals(worn), "Photography does not change the real model's styles");
        }
        player.discard(); helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void photographyStylesAreTemporaryAndCapturedInThePhoto(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen);
        citizen.citizenState().loadout().setStackInSlot(GarmentSlot.TOP.ordinal(), new ItemStack(ModItems.T_SHIRT.get()));
        var before = citizen.citizenState().loadout().serializeNBT().copy();
        int quote = PhotographyService.quote(citizen);
        var menu = PhotographyMenu.capture(42, player.getInventory(), PhotographyService.snapshot(citizen), citizen.getId(), 0, quote);
        player.containerMenu = menu;
        helper.assertTrue(menu.clickMenuButton(player, PhotographyMenu.SELECT_SLOT_ACTION + GarmentSlot.TOP.ordinal()), "Garment selection can be scrolled independently of item movement");
        helper.assertTrue(menu.clickMenuButton(player, PhotographyMenu.STYLE_ACTION), "The selected garment style can be changed");
        helper.assertTrue("tucked".equals(menu.loadout().styleState(GarmentSlot.TOP).styles().get("tuck")), "Forward cycling selects the next allowed style");
        helper.assertTrue(menu.clickMenuButton(player, PhotographyMenu.REVERSE_STYLE_ACTION), "Styles support reverse scrolling");
        helper.assertTrue("untucked".equals(menu.loadout().styleState(GarmentSlot.TOP).styles().get("tuck")), "Reverse cycling restores the original style");
        helper.assertTrue(!menu.clickMenuButton(player, PhotographyMenu.STYLE_ACTION + 15), "Unknown style categories are rejected");
        helper.assertTrue(!menu.clickMenuButton(player, PhotographyMenu.SELECT_SLOT_ACTION + PhotographyMenu.GARMENT_SLOTS), "Invalid garment selection is rejected");
        menu.clickMenuButton(player, PhotographyMenu.STYLE_ACTION);
        menu.getSlot(PhotographyMenu.PAYMENT_SLOT).set(new ItemStack(Items.EMERALD, quote));
        var photo = Objects.requireNonNull(AdvertisingPhotoData.read(menu.takePhoto(player, false)));
        helper.assertTrue("tucked".equals(photo.loadout(GarmentDefinitionManager::get).styleState(GarmentSlot.TOP).styles().get("tuck")), "The paid photo retains the trial garment style");
        menu.setCarried(ItemStack.EMPTY);
        menu.removed(player);
        helper.assertTrue(before.equals(citizen.citizenState().loadout().serializeNBT()), "Closing never changes the real model styles");
        var next = PhotographyMenu.capture(43, player.getInventory(), PhotographyService.snapshot(citizen), citizen.getId(), 0, quote);
        player.containerMenu = next;
        helper.assertTrue(!menu.clickMenuButton(player, PhotographyMenu.STYLE_ACTION), "Replaced menus cannot change styles");
        helper.assertTrue("untucked".equals(next.loadout().styleState(GarmentSlot.TOP).styles().get("tuck")), "A new session starts from the real model style");
        next.removed(player); helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void photographySettingsRoundTripYawAndRejectStaleQuote(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen); int quote = PhotographyService.quote(citizen);
        var menu = PhotographyMenu.capture(19, player.getInventory(), PhotographyService.snapshot(citizen), citizen.getId(), 0, quote);
        player.containerMenu = menu;
        for (int action = 0; action <= 7; action++) {
            int setting = action == 7 ? 8 : action;
            int before = menu.setting(setting);
            helper.assertTrue(menu.clickMenuButton(player, action) && menu.clickMenuButton(player, PhotographyMenu.REVERSE_ACTION + action), "Settings support both scroll directions");
            helper.assertTrue(menu.setting(setting) == before, "Reverse scrolling restores the previous value");
        }
        menu.clickMenuButton(player, PhotographyMenu.REVERSE_ACTION + 7);
        helper.assertTrue(menu.preview().layout().bodyYaw() == 345, "Rotation wraps backwards");
        menu.clickMenuButton(player, PhotographyMenu.COLOR_ACTION + com.rexhub.createapparel.material.FabricColor.RED.ordinal());
        helper.assertTrue(menu.preview().layout().background() == com.rexhub.createapparel.material.FabricColor.RED, "The color selector can choose an exact swatch");
        var saved = menu.preview().save();
        helper.assertTrue(Objects.requireNonNull(AdvertisingPhotoData.load(saved)).layout().bodyYaw() == 345, "Rotation survives photo serialization");
        saved.getCompound("Layout").remove("BodyYaw");
        helper.assertTrue(Objects.requireNonNull(AdvertisingPhotoData.load(saved)).layout().bodyYaw() == 0, "Legacy photos face forward");
        saved.getCompound("Layout").putInt("BodyYaw", 360);
        helper.assertTrue(AdvertisingPhotoData.load(saved) == null, "Invalid photo rotation is rejected");
        menu.getSlot(PhotographyMenu.PAYMENT_SLOT).set(new ItemStack(Items.EMERALD, quote));
        long time = helper.getLevel().getDayTime(); helper.getLevel().setDayTime(time + 24000);
        try {
            menu.broadcastChanges(); menu.clicked(PhotographyMenu.RESULT_SLOT, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().isEmpty() && !menu.getSlot(PhotographyMenu.RESULT_SLOT).hasItem()
                    && menu.getSlot(PhotographyMenu.PAYMENT_SLOT).getItem().getCount() == quote, "A stale quote clears output without charging");
        } finally { helper.getLevel().setDayTime(time); menu.removed(player); }
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void photographyShiftPickupIsOneTradeAndSelfPortraitNeedsNoPayment(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen); int quote = PhotographyService.quote(citizen);
        var menu = PhotographyMenu.capture(20, player.getInventory(), PhotographyService.snapshot(citizen), citizen.getId(), 0, quote);
        player.containerMenu = menu; menu.getSlot(PhotographyMenu.PAYMENT_SLOT).set(new ItemStack(Items.EMERALD, quote * 2)); menu.broadcastChanges();
        menu.clicked(PhotographyMenu.RESULT_SLOT, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
        helper.assertTrue(player.getInventory().items.stream().filter(stack -> stack.is(PhotoRegistries.ADVERTISING_PHOTO.get())).mapToInt(ItemStack::getCount).sum() == 1,
                "One Shift click issues exactly one photo even when a second trade is affordable");
        helper.assertTrue(menu.getSlot(PhotographyMenu.PAYMENT_SLOT).getItem().getCount() == quote, "Shift pickup charges once");
        menu.removed(player);
        var self = PhotographyMenu.capture(21, player.getInventory(), PhotographyService.snapshot(player), player.getId(), 0, 0);
        player.containerMenu = self; helper.getLevel().addFreshEntity(player); self.broadcastChanges();
        helper.assertTrue(self.getSlot(PhotographyMenu.RESULT_SLOT).hasItem(), "Free self portrait has a result without payment");
        self.clicked(PhotographyMenu.RESULT_SLOT, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(Objects.requireNonNull(AdvertisingPhotoData.read(self.getCarried())).playerId().equals(player.getUUID()), "Free result references the photographer");
        helper.assertTrue(self.getSlot(PhotographyMenu.PAYMENT_SLOT).getItem().isEmpty(), "A free portrait consumes no input");
        self.setCarried(ItemStack.EMPTY); self.removed(player); player.discard(); helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void photoEditingPreservesCapturedBodyRotationAndOutfit(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen); var original = PhotographyService.snapshot(citizen).copyIdentity();
        var layout = original.layout(); original = original.withLayout(new PhotoLayout(layout.background(), layout.framing(), layout.aspect(),
                layout.horizontal(), layout.vertical(), layout.mirror(), 75));
        var stack = new ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get(), 2); original.write(stack);
        player.getInventory().setItem(0, stack); PhotographyMenu.openEdit(player, original, 0);
        var menu = (PhotographyMenu) player.containerMenu;
        helper.assertTrue(menu.mode() == PhotographyMenu.Mode.EDIT && menu.slots.isEmpty(), "Editing has neither clothing, inventory nor trade slots");
        helper.assertTrue(!menu.clickMenuButton(player, 6), "Editing cannot replace the captured pose");
        helper.assertTrue(!menu.clickMenuButton(player, PhotographyMenu.STYLE_ACTION)
                && !menu.clickMenuButton(player, PhotographyMenu.REVERSE_STYLE_ACTION), "Editing cannot change captured garment styles");
        helper.assertTrue(!menu.clickMenuButton(player, 7) && !menu.clickMenuButton(player, PhotographyMenu.REVERSE_ACTION + 7), "Editing cannot rotate the captured body in either direction");
        menu.setData(8, 210);
        helper.assertTrue(menu.clickMenuButton(player, 1), "Framing remains editable");
        helper.assertTrue(menu.clickMenuButton(player, 8), "Saving an edited photo succeeds");
        var edited = Objects.requireNonNull(AdvertisingPhotoData.read(stack));
        helper.assertTrue(edited.layout().bodyYaw() == original.layout().bodyYaw() && stack.getCount() == 2, "Editing preserves captured rotation even if menu data is changed directly");
        helper.assertTrue(edited.layout().framing() != original.layout().framing(), "The whole photo stack receives the edited framing");
        helper.assertTrue(edited.withLayout(original.layout()).save().equals(original.save()), "Editing preserves all captured appearance, garments, pose and identities");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void photographyHighConfiguredFeeSupportsRefundableStackDeposits(GameTestHelper helper) {
        var config = com.rexhub.createapparel.config.ModConfigs.COMMON.fashion;
        int base = config.photographyBaseCost.get(), extra = config.photographyExtra.get();
        config.photographyBaseCost.set(100); config.photographyExtra.set(0);
        try {
            var citizen = citizen(helper); var player = player(helper, citizen);
            var emeralds = new ItemStack(Items.EMERALD, 64); emeralds.setHoverName(net.minecraft.network.chat.Component.literal("Refundable deposit"));
            player.getInventory().setItem(1, emeralds.copy());
            var menu = PhotographyMenu.capture(22, player.getInventory(), PhotographyService.snapshot(citizen), citizen.getId(), 0, PhotographyService.quote(citizen));
            player.containerMenu = menu;
            menu.clicked(PhotographyMenu.INVENTORY_START + 28, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
            helper.assertTrue(menu.remainingCost() == 36 && !menu.getSlot(PhotographyMenu.PAYMENT_SLOT).hasItem()
                    && !menu.getSlot(PhotographyMenu.RESULT_SLOT).hasItem(), "Large fees accept a stack as a refundable deposit without oversized item stacks");
            menu.removed(player);
            helper.assertTrue(ItemStack.matches(emeralds, player.getInventory().getItem(1)), "Closing refunds the deposited stack with its complete tags");
            player.getInventory().setItem(2, new ItemStack(Items.EMERALD, 36));
            menu = PhotographyMenu.capture(23, player.getInventory(), PhotographyService.snapshot(citizen), citizen.getId(), 0, PhotographyService.quote(citizen));
            player.containerMenu = menu;
            menu.clicked(PhotographyMenu.INVENTORY_START + 28, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
            menu.clicked(PhotographyMenu.INVENTORY_START + 29, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
            helper.assertTrue(menu.getSlot(PhotographyMenu.RESULT_SLOT).hasItem(), "The remaining payment unlocks the result");
            menu.clicked(PhotographyMenu.RESULT_SLOT, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
            helper.assertTrue(AdvertisingPhotoData.read(menu.getCarried()) != null && !menu.getSlot(PhotographyMenu.PAYMENT_SLOT).hasItem()
                    && menu.remainingCost() == 100, "Photo pickup commits the entire fee and resets the next offer");
            menu.setCarried(ItemStack.EMPTY); menu.removed(player);
            helper.assertTrue(player.getInventory().items.stream().noneMatch(stack -> stack.is(Items.EMERALD)), "Completed deposits are not refunded a second time");
        } finally { config.photographyBaseCost.set(base); config.photographyExtra.set(extra); }
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void photographyAndEditingStayOpenWhileOnlyConsentExpires(GameTestHelper helper) throws ReflectiveOperationException {
        var citizen = citizen(helper); var photographer = player(helper, citizen);
        var editor = player(helper, citizen); var subject = player(helper, citizen);
        helper.getLevel().addFreshEntity(photographer); helper.getLevel().addFreshEntity(subject);
        var snapshot = PhotographyService.snapshot(citizen);
        var capture = PhotographyMenu.capture(24, photographer.getInventory(), snapshot, citizen.getId(), 0, PhotographyService.quote(citizen));
        photographer.containerMenu = capture;
        capture.getSlot(PhotographyMenu.PAYMENT_SLOT).set(new ItemStack(Items.EMERALD, capture.cost()));
        var photo = new ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get()); snapshot.write(photo);
        editor.getInventory().setItem(0, photo); PhotographyMenu.openEdit(editor, snapshot, 0);
        var edit = (PhotographyMenu) editor.containerMenu;
        var consent = PhotographyMenu.consent(25, subject.getInventory(), snapshot, photographer.getId(), 0);
        subject.containerMenu = consent;
        var deadline = PhotographyMenu.class.getDeclaredField("expiresAt"); deadline.setAccessible(true);
        try {
            helper.assertTrue(capture.stillValid(photographer) && edit.stillValid(editor) && consent.stillValid(subject), "All three menus start valid");
            for (var menu : new PhotographyMenu[]{capture, edit, consent}) deadline.setLong(menu, helper.getLevel().getGameTime() - 1);
            helper.assertTrue(capture.stillValid(photographer) && edit.stillValid(editor), "Photography and editing do not close after sixty seconds");
            helper.assertTrue(!consent.stillValid(subject) && !consent.clickMenuButton(subject, 8), "An expired approval request remains invalid");
            helper.assertTrue(capture.clickMenuButton(photographer, 7) && PhotographyService.issue(photographer, capture), "The long-open capture remains usable");
            helper.assertTrue(edit.clickMenuButton(editor, 1) && edit.clickMenuButton(editor, 8), "The long-open editor can save");
            helper.assertTrue(Objects.requireNonNull(AdvertisingPhotoData.read(photo)).layout().framing() == PhotoLayout.Framing.UPPER_BODY, "The late edit was saved");
            photographer.setPos(citizen.position().add(20, 0, 0));
            helper.assertTrue(!capture.stillValid(photographer), "Removing timeout does not remove the model distance check");
        } finally {
            capture.removed(photographer); consent.removed(subject);
            photographer.discard(); subject.discard();
        }
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void registerRequiresSeatedLivingCashierAndExtractsExactlyOne(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, FashionRegistries.CASH_REGISTER.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        var register = (CashRegisterBlockEntity) level.getBlockEntity(pos); var reference = register.reference();
        var storagePos = pos.east(); level.setBlockAndUpdate(storagePos, Blocks.CHEST.defaultBlockState());
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(storagePos);
        var garment = new ItemStack(ModItems.T_SHIRT.get(), 2); GarmentData.defaultFor(GarmentDefinitionManager.get(ModItems.T_SHIRT.getId()).orElseThrow()).write(garment);
        chest.setItem(0, garment); helper.assertTrue(register.linkStorage(storagePos) && !register.isOpen(), "Storage alone stays closed");
        var seatPos = pos.north(); level.setBlockAndUpdate(seatPos, com.simibubi.create.AllBlocks.SEATS.get(net.minecraft.world.item.DyeColor.WHITE).getDefaultState());
        var seat = new com.simibubi.create.content.contraptions.actors.seat.SeatEntity(level, seatPos); seat.setPos(Vec3.atBottomCenterOf(seatPos)); level.addFreshEntity(seat);
        var cow = Objects.requireNonNull(net.minecraft.world.entity.EntityType.COW.create(level)); cow.setPos(Vec3.atBottomCenterOf(seatPos)); level.addFreshEntity(cow); cow.startRiding(seat, true);
        var citizen = citizen(helper);
        helper.assertTrue(register.isOpen(), "Cow seated on Create Seat opens register");
        var candidate = RetailPurchase.select(citizen, register, GarmentSlot.TOP, false);
        helper.assertTrue(candidate != null && RetailPurchase.purchase(citizen, register, candidate), "Purchase succeeds");
        helper.assertTrue(chest.getItem(0).getCount() == 1 && ItemStack.isSameItemSameTags(citizen.citizenState().loadout().getStack(GarmentSlot.TOP), garment), "One real garment extracted and worn");
        helper.assertTrue(reference.equals(citizen.citizenState().fashion().purchasedRegister(GarmentSlot.TOP)), "Purchase origin recorded");
        citizen.citizenState().fashion().setForceFashion(true);
        helper.assertTrue(RetailPurchase.select(citizen, register, GarmentSlot.TOP, false) == null, "Locked Citizen does not purchase");
        level.removeBlock(pos, false); level.setBlockAndUpdate(pos, FashionRegistries.CASH_REGISTER.get().defaultBlockState());
        helper.assertTrue(reference.resolve(level) == null, "Replacement register cannot resolve old purchase origin"); helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void rectangularPanelsShareOneSnapshotAndSplitWithoutLosingIt(GameTestHelper helper) {
        var level = helper.getLevel(); var origin = helper.absolutePos(new BlockPos(2, 2, 2)); var citizen = citizen(helper); var player = player(helper, citizen);
        var snapshot = AdvertisementSnapshot.photo(PhotographyService.snapshot(citizen));
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) {
            var pos = origin.west(x).above(y); level.setBlockAndUpdate(pos, AdvertisementRegistries.BILLBOARD_PANEL.get().defaultBlockState());
            var panel = (AdvertisementPanelBlockEntity) level.getBlockEntity(pos); panel.initialize(player, new CompoundTag());
            if (x == 0 && y == 0) panel.content(snapshot); PanelGroupManager.merge(level, panel);
        }
        var panel = (AdvertisementPanelBlockEntity) level.getBlockEntity(origin); var controller = Objects.requireNonNull(panel.controller());
        helper.assertTrue(controller.width() == 2 && controller.height() == 2, "Panels form a 2 by 2 rectangle");
        var group = controller.groupId(); var content = controller.localSnapshot().save();
        for (var pos : PanelGroupManager.members(controller)) {
            var member = (AdvertisementPanelBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(member.groupId().equals(group), "One group identity");
            helper.assertTrue(member.isController() == member.saveWithoutMetadata().contains("Content"), "Only controller saves content");
        }
        var removed = controller.getBlockPos(); level.destroyBlock(removed, false);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) {
            var pos = origin.west(x).above(y); if (pos.equals(removed)) continue;
            var survivor = (AdvertisementPanelBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(survivor.controller() != null && survivor.controller().localSnapshot().save().equals(content), "Every remaining rectangle keeps content");
        }
        helper.assertTrue(java.util.List.of(origin.west(), origin.above(), origin.west().above()).stream()
                .map(pos -> (AdvertisementPanelBlockEntity) level.getBlockEntity(pos)).filter(AdvertisementPanelBlockEntity::isController).count() == 2,
                "An L-shaped remainder is partitioned into two rectangles rather than three individual panels");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void bothPanelTypesPreserveRectanglesAndRejoinAfterRefillingAHole(GameTestHelper helper) {
        var level = helper.getLevel(); var citizen = citizen(helper); var player = player(helper, citizen);
        var origin = helper.absolutePos(new BlockPos(4, 2, 4));
        var snapshot = AdvertisementSnapshot.photo(PhotographyService.snapshot(citizen));
        var layout = snapshot.displayPhoto().layout();
        var reference = new PurchasedRegisterRef(UUID.randomUUID(), level.dimension().location(), origin.below());
        for (var block : List.of(AdvertisementRegistries.BILLBOARD_PANEL.get(), AdvertisementRegistries.SIGNAGE_PANEL.get()).stream().map(value -> (AdvertisementPanelBlock) value).toList()) {
            for (var facing : Direction.Plane.HORIZONTAL) {
                var right = facing.getCounterClockWise(); var state = block.defaultBlockState().setValue(AdvertisementPanelBlock.FACING, facing);
                for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) {
                    var pos = origin.relative(right, x).above(y); level.setBlockAndUpdate(pos, state);
                    var panel = (AdvertisementPanelBlockEntity) level.getBlockEntity(pos); panel.initialize(player, new CompoundTag());
                    if (x == 0 && y == 0) {
                        if (block.kind() == AdvertisementPanelBlock.Kind.SIGNAGE) panel.configureScenes("rectangle_test", Map.of(2, layout));
                        else panel.content(snapshot);
                        panel.link(reference);
                    }
                    PanelGroupManager.merge(level, panel);
                }
                var first = (AdvertisementPanelBlockEntity) level.getBlockEntity(origin);
                var expectedContent = Objects.requireNonNull(first.controller()).content();
                helper.assertTrue(first.width() == 3 && first.height() == 3, "Both panel types form a complete 3 by 3 rectangle in every facing");
                var hole = origin.relative(right).above(); level.destroyBlock(hole, false);
                Set<UUID> groups = new HashSet<>();
                for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) {
                    var pos = origin.relative(right, x).above(y); if (pos.equals(hole)) continue;
                    var controller = Objects.requireNonNull(((AdvertisementPanelBlockEntity) level.getBlockEntity(pos)).controller());
                    helper.assertTrue(controller.content().equals(expectedContent), "Every surviving rectangle retains photo or channel, layout, owner, register and merge family");
                    groups.add(controller.groupId());
                }
                helper.assertTrue(groups.size() == 4, "The eight-panel ring is partitioned into four rectangles, not eight individual panels");
                level.setBlockAndUpdate(hole, state);
                var replacement = (AdvertisementPanelBlockEntity) level.getBlockEntity(hole); replacement.initialize(player, new CompoundTag());
                PanelGroupManager.merge(level, replacement);
                var restored = Objects.requireNonNull(replacement.controller());
                helper.assertTrue(restored.width() == 3 && restored.height() == 3 && restored.content().equals(expectedContent), "Replacing the missing panel rejoins matching fragments into the original full rectangle");
                for (int x = 0; x < 3; x++) level.destroyBlock(origin.relative(right, x).above(2), false);
                var survivor = Objects.requireNonNull(((AdvertisementPanelBlockEntity) level.getBlockEntity(origin)).controller());
                helper.assertTrue(survivor.width() == 3 && survivor.height() == 2, "Removing an edge leaves the connected 3 by 2 rectangle intact");
                for (int x = 0; x < 3; x++) for (int y = 0; y < 2; y++) level.destroyBlock(origin.relative(right, x).above(y), false);
            }
        }
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion", timeoutTicks = 80)
    public static void panelPlacementTicksGrowRectanglesAndBuildIndependentAdjacentRectangles(GameTestHelper helper) {
        var level = helper.getLevel(); var citizen = citizen(helper); var player = player(helper, citizen);
        var origins = List.of(helper.absolutePos(new BlockPos(5, 2, 2)), helper.absolutePos(new BlockPos(5, 2, 5)));
        var blocks = List.of(AdvertisementRegistries.BILLBOARD_PANEL.get(), AdvertisementRegistries.SIGNAGE_PANEL.get()).stream().map(value -> (AdvertisementPanelBlock) value).toList();
        for (int index = 0; index < 2; index++) {
            var block = blocks.get(index); var origin = origins.get(index);
            for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) {
                var pos = origin.west(x).above(y); placeTestPanel(level, pos, block, player, x == 0 && y == 0);
                PanelGroupManager.merge(level, (AdvertisementPanelBlockEntity) level.getBlockEntity(pos));
            }
            placeTestPanel(level, origin.west(2), block, player, false);
        }
        helper.runAfterDelay(4, () -> {
            for (int index = 0; index < 2; index++) {
                var origin = origins.get(index);
                var controller = Objects.requireNonNull(((AdvertisementPanelBlockEntity) level.getBlockEntity(origin)).controller());
                helper.assertTrue(controller.width() == 2 && controller.height() == 2, "An adjacent incomplete column does not disassemble the existing rectangle");
                placeTestPanel(level, origin.west(2).above(), blocks.get(index), player, false);
            }
            helper.runAfterDelay(4, () -> {
                for (int index = 0; index < 2; index++) {
                    var origin = origins.get(index);
                    var controller = Objects.requireNonNull(((AdvertisementPanelBlockEntity) level.getBlockEntity(origin)).controller());
                    helper.assertTrue(controller.width() == 3 && controller.height() == 2, "Scheduled placement updates grow the whole surface without a manual repair call");
                    placeTestPanel(level, origin.west(3), blocks.get(index), player, true);
                    placeTestPanel(level, origin.west(3).above(), blocks.get(index), player, false);
                }
                helper.runAfterDelay(4, () -> {
                    for (var origin : origins) {
                        var original = Objects.requireNonNull(((AdvertisementPanelBlockEntity) level.getBlockEntity(origin)).controller());
                        var adjacent = Objects.requireNonNull(((AdvertisementPanelBlockEntity) level.getBlockEntity(origin.west(3))).controller());
                        helper.assertTrue(original.width() == 3 && original.height() == 2 && adjacent.width() == 1 && adjacent.height() == 2
                                && !original.groupId().equals(adjacent.groupId()), "A new independently configured rectangle forms beside an existing rectangle without mixing their content");
                    }
                    helper.succeed();
                });
            });
        });
    }
    private static void placeTestPanel(net.minecraft.server.level.ServerLevel level, BlockPos pos, AdvertisementPanelBlock block, ServerPlayer player, boolean independent) {
        var state = block.defaultBlockState(); level.setBlockAndUpdate(pos, state);
        block.setPlacedBy(level, pos, state, player, new ItemStack(block));
        if (independent) {
            var panel = (AdvertisementPanelBlockEntity) level.getBlockEntity(pos);
            if (block.kind() == AdvertisementPanelBlock.Kind.SIGNAGE) panel.channel("separate_" + Math.abs(pos.asLong()));
            else panel.content(AdvertisementScenes.DEFAULTS.get(0));
        }
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void panelMergeLimitsDoNotPreventANewAdjacentRectangle(GameTestHelper helper) {
        var level = helper.getLevel(); var citizen = citizen(helper); var player = player(helper, citizen);
        var origin = helper.absolutePos(new BlockPos(5, 2, 2));
        var limit = com.rexhub.createapparel.config.ModConfigs.COMMON.fashion.maxPanels; int previous = limit.get();
        try {
            limit.set(4);
            for (var block : List.of(AdvertisementRegistries.BILLBOARD_PANEL.get(), AdvertisementRegistries.SIGNAGE_PANEL.get()).stream().map(value -> (AdvertisementPanelBlock) value).toList()) {
                for (int x = 0; x < 3; x++) for (int y = 0; y < 2; y++) {
                    var pos = origin.west(x).above(y); placeTestPanel(level, pos, block, player, false);
                    PanelGroupManager.merge(level, (AdvertisementPanelBlockEntity) level.getBlockEntity(pos));
                }
                var first = Objects.requireNonNull(((AdvertisementPanelBlockEntity) level.getBlockEntity(origin)).controller());
                var adjacent = Objects.requireNonNull(((AdvertisementPanelBlockEntity) level.getBlockEntity(origin.west(2))).controller());
                helper.assertTrue(first.width() == 2 && first.height() == 2 && adjacent.width() == 1 && adjacent.height() == 2, "A connected component over the group limit still forms valid neighboring rectangles");
                var foreign = player(helper, citizen); var foreignPos = origin.west(3);
                placeTestPanel(level, foreignPos, block, foreign, false);
                var foreignPanel = (AdvertisementPanelBlockEntity) level.getBlockEntity(foreignPos); PanelGroupManager.merge(level, foreignPanel);
                helper.assertTrue(foreignPanel.width() == 1 && foreignPanel.height() == 1 && foreignPanel.owner().equals(foreign.getUUID()), "Panels owned by another player never join");
                for (int x = 0; x < 4; x++) for (int y = 0; y < 2; y++) level.destroyBlock(origin.west(x).above(y), false);
            }
        } finally { limit.set(previous); }
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void panelMultiwayRectanglesAndEditedFragmentsRespectTheirMergeFamilies(GameTestHelper helper) {
        var level = helper.getLevel(); var citizen = citizen(helper); var player = player(helper, citizen);
        var origin = helper.absolutePos(new BlockPos(4, 2, 2));
        for (var block : List.of(AdvertisementRegistries.BILLBOARD_PANEL.get(), AdvertisementRegistries.SIGNAGE_PANEL.get())) {
            for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) placeTestPanel(level, origin.west(x).above(y), (AdvertisementPanelBlock) block, player, false);
            var first = (AdvertisementPanelBlockEntity) level.getBlockEntity(origin);
            if (first.kind() == AdvertisementPanelBlock.Kind.SIGNAGE) first.channel("multiway_test"); else first.content(AdvertisementScenes.DEFAULTS.get(0));
            var content = first.content(); var family = first.mergeFamily();
            var rectangles = new int[][]{{0, 0, 2, 1}, {2, 0, 1, 2}, {1, 2, 2, 1}, {0, 1, 1, 2}, {1, 1, 1, 1}};
            for (var rectangle : rectangles) {
                var start = origin.west(rectangle[0]).above(rectangle[1]); var id = UUID.randomUUID();
                for (int x = 0; x < rectangle[2]; x++) for (int y = 0; y < rectangle[3]; y++)
                    ((AdvertisementPanelBlockEntity) level.getBlockEntity(start.west(x).above(y))).group(id, start, rectangle[2], rectangle[3], content);
            }
            var center = (AdvertisementPanelBlockEntity) level.getBlockEntity(origin.west().above()); PanelGroupManager.merge(level, center);
            var controller = Objects.requireNonNull(center.controller());
            helper.assertTrue(controller.width() == 3 && controller.height() == 3 && controller.mergeFamily().equals(family), "A full rectangle tiled without any pairwise rectangular union still joins as one surface");
            var saved = controller.saveWithoutMetadata(); controller.load(saved);
            helper.assertTrue(controller.mergeFamily().equals(family), "Merge family survives NBT round-trip");
            level.destroyBlock(origin.west().above(), false);
            var edited = Objects.requireNonNull(((AdvertisementPanelBlockEntity) level.getBlockEntity(origin.above())).controller());
            if (edited.kind() == AdvertisementPanelBlock.Kind.SIGNAGE) edited.channel("edited_fragment"); else edited.content(AdvertisementScenes.DEFAULTS.get(1));
            helper.assertTrue(!edited.mergeFamily().equals(family), "Editing a split fragment starts an independent merge family");
            placeTestPanel(level, origin.west().above(), (AdvertisementPanelBlock) block, player, false);
            PanelGroupManager.merge(level, (AdvertisementPanelBlockEntity) level.getBlockEntity(origin.west().above()));
            helper.assertTrue(!Objects.requireNonNull(((AdvertisementPanelBlockEntity) level.getBlockEntity(origin)).controller()).groupId().equals(Objects.requireNonNull(edited.controller()).groupId()), "Refilling a hole cannot overwrite a separately edited fragment");
            for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) level.destroyBlock(origin.west(x).above(y), false);
        }
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void signageDeferredRemovalRecoversChannelAndTheRemainingRectangle(GameTestHelper helper) {
        var level = helper.getLevel(); var citizen = citizen(helper); var player = player(helper, citizen);
        var origin = helper.absolutePos(new BlockPos(4, 2, 2)); var group = UUID.randomUUID();
        for (int x = 1; x < 3; x++) {
            var pos = origin.west(x); level.setBlockAndUpdate(pos, AdvertisementRegistries.SIGNAGE_PANEL.get().defaultBlockState());
            ((AdvertisementPanelBlockEntity) level.getBlockEntity(pos)).group(group, origin, 3, 1, null);
        }
        level.destroyBlock(origin.west(2), false);
        var data = AdvertisementNetworkSavedData.get(level); String key = level.dimension().location() + "/" + group;
        helper.assertTrue(data.recovery(key) != null && data.recovery(key).getBoolean("Pending"), "Signage journals removal while its controller is unavailable");
        level.setBlockAndUpdate(origin, AdvertisementRegistries.SIGNAGE_PANEL.get().defaultBlockState());
        var controller = (AdvertisementPanelBlockEntity) level.getBlockEntity(origin); controller.initialize(player, new CompoundTag());
        var layouts = Map.of(1, AdvertisementScenes.DEFAULTS.get(1).displayPhoto().layout()); controller.configureScenes("deferred_signage", layouts);
        controller.group(group, origin, 3, 1, controller.content()); PanelGroupManager.repair(level, controller);
        var follower = (AdvertisementPanelBlockEntity) level.getBlockEntity(origin.west());
        helper.assertTrue(controller.width() == 2 && controller.height() == 1 && follower.controller() == controller
                && controller.channel().equals("deferred_signage") && controller.sceneLayouts().equals(layouts) && data.recovery(key) == null, "Deferred recovery preserves the remaining signage rectangle, channel and scene layouts");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void billboardRemovalKeepsThePhotoUntilTheLastPanel(GameTestHelper helper) {
        var level = helper.getLevel(); var citizen = citizen(helper); var player = player(helper, citizen);
        var origin = helper.absolutePos(new BlockPos(4, 2, 2));
        var snapshot = AdvertisementSnapshot.photo(PhotographyService.snapshot(citizen));
        var printed = new ItemStack(AdvertisementRegistries.BILLBOARD_PANEL_ITEM.get()); printed.getOrCreateTag().put("Advertisement", snapshot.save());
        for (int x = 0; x < 3; x++) {
            var pos = origin.west(x); var state = AdvertisementRegistries.BILLBOARD_PANEL.get().defaultBlockState();
            level.setBlockAndUpdate(pos, state); state.getBlock().setPlacedBy(level, pos, state, player,
                    x == 0 ? printed : new ItemStack(AdvertisementRegistries.BILLBOARD_PANEL_ITEM.get()));
            PanelGroupManager.merge(level, (AdvertisementPanelBlockEntity) level.getBlockEntity(pos));
        }
        for (int x = 0; x < 3; x++) {
            level.destroyBlock(origin.west(x), true);
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(origin).inflate(5),
                    item -> item.getItem().is(AdvertisementRegistries.BILLBOARD_PANEL_ITEM.get()));
            helper.assertTrue(drops.stream().mapToInt(item -> item.getItem().getCount()).sum() == 1, "Each removed panel drops exactly one item");
            var drop = drops.get(0).getItem();
            helper.assertTrue(drop.hasFoil() == (x == 2), "Only the final collected panel has a photo and glint");
            if (x < 2) {
                helper.assertTrue(!drop.hasTag(), "Earlier panels are completely blank and reusable");
                var remaining = (AdvertisementPanelBlockEntity) level.getBlockEntity(origin.west(x + 1));
                helper.assertTrue(remaining.isController() && remaining.width() == 2 - x && remaining.height() == 1
                        && remaining.localSnapshot().save().equals(snapshot.save()), "The remaining rectangle inherits the photograph");
            } else helper.assertTrue(AdvertisementSnapshot.load(drop.getOrCreateTag().getCompound("Advertisement")).save().equals(snapshot.save()), "The final item preserves the original photo");
            drops.forEach(net.minecraft.world.entity.Entity::discard);
        }
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void billboardBlankItemsExtendTheClickedSurface(GameTestHelper helper) {
        var level = helper.getLevel(); var citizen = citizen(helper); var player = player(helper, citizen);
        var origin = helper.absolutePos(new BlockPos(4, 2, 4));
        var block = AdvertisementRegistries.BILLBOARD_PANEL.get(); var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        var snapshot = AdvertisementSnapshot.photo(PhotographyService.snapshot(citizen));
        for (var facing : Direction.Plane.HORIZONTAL) {
            var state = block.defaultBlockState().setValue(AdvertisementPanelBlock.FACING, facing);
            level.setBlockAndUpdate(origin, state);
            var first = (AdvertisementPanelBlockEntity) level.getBlockEntity(origin); first.initialize(player, new CompoundTag()); first.content(snapshot);
            var blank = new ItemStack(AdvertisementRegistries.BILLBOARD_PANEL_ITEM.get(), 2); player.setItemInHand(hand, blank);
            var side = facing.getCounterClockWise();
            var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(origin), side, origin, false);
            helper.assertTrue(state.use(level, player, hand, hit) == net.minecraft.world.InteractionResult.PASS,
                    "Holding another panel places it instead of opening the advertisement settings");
            var placed = ((net.minecraft.world.item.BlockItem) blank.getItem()).place(new net.minecraft.world.item.context.BlockPlaceContext(player, hand, blank, hit));
            var nextPos = origin.relative(side);
            helper.assertTrue(placed.consumesAction() && blank.getCount() == 1 && level.getBlockState(nextPos).getValue(AdvertisementPanelBlock.FACING) == facing,
                    "Side placement inherits the existing surface direction and consumes only the blank panel");
            PanelGroupManager.merge(level, (AdvertisementPanelBlockEntity) level.getBlockEntity(nextPos));
            var controller = Objects.requireNonNull(first.controller());
            helper.assertTrue(controller.width() == 2 && controller.height() == 1 && controller.localSnapshot().save().equals(snapshot.save()),
                    "The blank addition extends the original photograph as one rectangle");
            level.destroyBlock(nextPos, false); level.destroyBlock(origin, false);
        }
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void billboardRecoveryFollowsRepeatedRemovalsBeforeFollowersReload(GameTestHelper helper) {
        var level = helper.getLevel(); var citizen = citizen(helper); var player = player(helper, citizen);
        var origin = helper.absolutePos(new BlockPos(3, 2, 2));
        UUID oldGroup = UUID.randomUUID(), middleGroup = UUID.randomUUID(), finalGroup = UUID.randomUUID();
        var snapshot = AdvertisementSnapshot.photo(PhotographyService.snapshot(citizen));
        var content = new CompoundTag(); content.put("Advertisement", snapshot.save()); content.putUUID("Owner", player.getUUID()); content.putBoolean("Independent", true);
        var positions = new long[]{origin.asLong(), origin.west().asLong()};
        for (long encoded : positions) {
            var pos = BlockPos.of(encoded); level.setBlockAndUpdate(pos, AdvertisementRegistries.BILLBOARD_PANEL.get().defaultBlockState());
            ((AdvertisementPanelBlockEntity) level.getBlockEntity(pos)).group(oldGroup, origin.east(2), 4, 1, null);
        }
        var data = AdvertisementNetworkSavedData.get(level);
        for (int index = 0; index < 2; index++) {
            var group = new CompoundTag(); group.putUUID("Group", index == 0 ? middleGroup : finalGroup);
            group.putLong("Origin", (index == 0 ? origin.east() : origin).asLong()); group.putInt("Width", index == 0 ? 3 : 2); group.putInt("Height", 1); group.putLongArray("Members", positions);
            var groups = new net.minecraft.nbt.ListTag(); groups.add(group);
            var recovery = new CompoundTag(); recovery.put("Content", content); recovery.put("Groups", groups); recovery.putLongArray("Remaining", positions);
            data.recovery(level.dimension().location() + "/" + (index == 0 ? oldGroup : middleGroup), recovery);
        }
        var controller = (AdvertisementPanelBlockEntity) level.getBlockEntity(origin);
        var follower = (AdvertisementPanelBlockEntity) level.getBlockEntity(origin.west());
        PanelGroupManager.repair(level, controller); PanelGroupManager.repair(level, follower);
        helper.assertTrue(controller.isController() && controller.groupId().equals(finalGroup) && controller.width() == 2
                && follower.controller() == controller && controller.localSnapshot().save().equals(snapshot.save()), "Reload follows successive recovery records to the latest surviving rectangle");
        helper.assertTrue(data.recovery(level.dimension().location() + "/" + oldGroup) == null
                && data.recovery(level.dimension().location() + "/" + middleGroup) == null, "Completed recovery records are removed");
        helper.assertTrue(!AdvertisementPanelBlock.drop(follower).hasFoil(), "A recovered multi-panel group still returns blank components");
        level.destroyBlock(origin, false);
        helper.assertTrue(AdvertisementPanelBlock.drop(follower).hasFoil(), "The final recovered panel retains the photo");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void billboardCanBeRemovedBeforeALoadedFollowerRunsItsRepairTick(GameTestHelper helper) {
        var level = helper.getLevel(); var citizen = citizen(helper); var player = player(helper, citizen);
        var origin = helper.absolutePos(new BlockPos(3, 2, 2)); UUID oldGroup = UUID.randomUUID(), nextGroup = UUID.randomUUID();
        var snapshot = AdvertisementSnapshot.photo(PhotographyService.snapshot(citizen));
        var content = new CompoundTag(); content.put("Advertisement", snapshot.save()); content.putUUID("Owner", player.getUUID());
        var positions = new long[]{origin.asLong(), origin.west().asLong()};
        for (long encoded : positions) {
            var pos = BlockPos.of(encoded); level.setBlockAndUpdate(pos, AdvertisementRegistries.BILLBOARD_PANEL.get().defaultBlockState());
            ((AdvertisementPanelBlockEntity) level.getBlockEntity(pos)).group(oldGroup, origin.east(), 3, 1, null);
        }
        var group = new CompoundTag(); group.putUUID("Group", nextGroup); group.putLong("Origin", origin.asLong());
        group.putInt("Width", 2); group.putInt("Height", 1); group.putLongArray("Members", positions);
        var groups = new net.minecraft.nbt.ListTag(); groups.add(group);
        var recovery = new CompoundTag(); recovery.put("Content", content); recovery.put("Groups", groups); recovery.putLongArray("Remaining", positions);
        AdvertisementNetworkSavedData.get(level).recovery(level.dimension().location() + "/" + oldGroup, recovery);
        PanelGroupManager.repair(level, (AdvertisementPanelBlockEntity) level.getBlockEntity(origin));
        level.destroyBlock(origin, false);
        var follower = (AdvertisementPanelBlockEntity) level.getBlockEntity(origin.west());
        PanelGroupManager.repair(level, follower);
        helper.assertTrue(follower.isController() && follower.width() == 1 && follower.localSnapshot().save().equals(snapshot.save())
                && AdvertisementPanelBlock.drop(follower).hasFoil(), "An already loaded but not yet repaired follower keeps the final photograph");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void billboardRemovalWaitsForAnUnavailableControllerWithoutLosingThePhoto(GameTestHelper helper) {
        var level = helper.getLevel(); var citizen = citizen(helper); var player = player(helper, citizen);
        var origin = helper.absolutePos(new BlockPos(4, 2, 2)); var group = UUID.randomUUID();
        for (int x = 1; x < 3; x++) {
            var pos = origin.west(x); level.setBlockAndUpdate(pos, AdvertisementRegistries.BILLBOARD_PANEL.get().defaultBlockState());
            ((AdvertisementPanelBlockEntity) level.getBlockEntity(pos)).group(group, origin, 3, 1, null);
        }
        level.destroyBlock(origin.west(2), true);
        var data = AdvertisementNetworkSavedData.get(level); String key = level.dimension().location() + "/" + group;
        helper.assertTrue(data.recovery(key) != null && data.recovery(key).getBoolean("Pending"), "Missing controller removals are journaled rather than silently forgotten");
        var snapshot = AdvertisementSnapshot.photo(PhotographyService.snapshot(citizen));
        level.setBlockAndUpdate(origin, AdvertisementRegistries.BILLBOARD_PANEL.get().defaultBlockState());
        var controller = (AdvertisementPanelBlockEntity) level.getBlockEntity(origin); controller.initialize(player, new CompoundTag()); controller.content(snapshot);
        controller.group(group, origin, 3, 1, controller.content()); PanelGroupManager.repair(level, controller);
        var follower = (AdvertisementPanelBlockEntity) level.getBlockEntity(origin.west());
        helper.assertTrue(controller.width() == 2 && follower.controller() == controller && controller.localSnapshot().save().equals(snapshot.save())
                && data.recovery(key) == null, "Loading the controller restores only the remaining rectangle and preserves its photograph");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void deployerPrintingCopiesActualPhotoAndDoesNotConsumeTheMaster(GameTestHelper helper) {
        var level = helper.getLevel(); var citizen = citizen(helper);
        var photo = new ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get(), 7); PhotographyService.snapshot(citizen).write(photo);
        photo.getOrCreateTag().putString("PrintMaster", "preserved"); var before = photo.copy();
        var pos = helper.absolutePos(new BlockPos(2, 5, 2));
        level.setBlockAndUpdate(pos, com.simibubi.create.AllBlocks.DEPLOYER.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.DOWN));
        level.setBlockAndUpdate(pos.below(2), com.simibubi.create.AllBlocks.DEPOT.getDefaultState());
        var deployer = (com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity) level.getBlockEntity(pos); deployer.initialize();
        var depot = (com.simibubi.create.content.logistics.depot.DepotBlockEntity) level.getBlockEntity(pos.below(2)); depot.initialize();
        deployer.getPlayer().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, photo);
        for (var media : java.util.List.of(AdvertisementRegistries.POSTER_ITEM, AdvertisementRegistries.BILLBOARD_PANEL_ITEM)) {
            var blank = new ItemStack(media.get(), 3); blank.getOrCreateTag().putString("MediaMetadata", "keep");
            var recipe = deployer.getRecipe(blank);
            helper.assertTrue(recipe instanceof com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe application && application.shouldKeepHeldItem(), "Native deployer search finds a non-consuming print recipe");
            var transported = new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(blank.copy());
            depot.getBehaviour(com.simibubi.create.content.logistics.depot.DepotBehaviour.TYPE).setHeldItem(transported);
            var handler = depot.getBehaviour(com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TYPE);
            com.simibubi.create.content.kinetics.deployer.BeltDeployerCallbacks.activate(transported, handler, deployer, recipe);
            var inventory = depot.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElseThrow(AssertionError::new);
            int blanks = 0, prints = 0;
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                var output = inventory.getStackInSlot(slot);
                if (output.isEmpty()) continue;
                helper.assertTrue(output.is(media.get()), "Printing does not change media type");
                if (output.hasFoil()) {
                    prints += output.getCount();
                    helper.assertTrue(AdvertisementSnapshot.load(output.getOrCreateTag().getCompound("Advertisement")).photo().save()
                            .equals(Objects.requireNonNull(AdvertisingPhotoData.read(photo)).save()), "The actual master values, not a placeholder photo, are printed");
                    helper.assertTrue(output.getOrCreateTag().getString("MediaMetadata").equals("keep") && deployer.getRecipe(output) == null, "Media metadata survives and already printed items cannot be overwritten");
                } else blanks += output.getCount();
            }
            helper.assertTrue(blanks == 2 && prints == 1 && ItemStack.matches(before, deployer.getPlayer().getMainHandItem()), "One media is printed per operation and the whole photo stack is untouched");
            for (int slot = 0; slot < inventory.getSlots(); slot++) inventory.extractItem(slot, 64, false);
            var registered = (AdPrintingDeployerRecipe) level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("createapparel:deploying/" + media.getId().getPath() + "_printing")).orElseThrow();
            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                AdvertisementRegistries.AD_PRINTING.get().toNetwork(buffer, registered);
                var synced = AdvertisementRegistries.AD_PRINTING.get().fromNetwork(registered.getId(), buffer);
                helper.assertTrue(synced != null && synced.shouldKeepHeldItem() && synced.getSerializer() == AdvertisementRegistries.AD_PRINTING.get(), "Printing recipes retain their custom serializer and non-consumption after sync");
            } finally { buffer.release(); }
        }
        deployer.getPlayer().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get()));
        helper.assertTrue(deployer.getRecipe(new ItemStack(AdvertisementRegistries.POSTER_ITEM.get())) == null, "A photo without captured data cannot print");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void posterPlacesVerticallyOnSupportedWallsAndFallsAsOneItem(GameTestHelper helper) {
        var level = helper.getLevel(); var citizen = citizen(helper); var player = player(helper, citizen);
        var origin = helper.absolutePos(new BlockPos(2, 2, 2));
        var block = AdvertisementRegistries.POSTER.get();
        for (var facing : Direction.Plane.HORIZONTAL) {
            var wall = origin.relative(facing.getOpposite());
            var stack = new ItemStack(AdvertisementRegistries.POSTER_ITEM.get(), 2);
            stack.getOrCreateTag().put("Advertisement", AdvertisementSnapshot.photo(PhotographyService.snapshot(citizen)).save());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            java.util.function.Supplier<net.minecraft.world.item.context.BlockPlaceContext> context = () -> new net.minecraft.world.item.context.BlockPlaceContext(player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, stack, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(wall), facing, wall, false));
            helper.assertTrue(block.getStateForPlacement(context.get()) == null, "A poster cannot float without wall support");
            level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState()); level.setBlockAndUpdate(wall.above(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(origin.above(), Blocks.STONE.defaultBlockState());
            helper.assertTrue(block.getStateForPlacement(context.get()) == null, "An obstructed upper half prevents placement");
            level.setBlockAndUpdate(origin.above(), Blocks.AIR.defaultBlockState());
            var result = ((net.minecraft.world.item.BlockItem) stack.getItem()).place(context.get());
            helper.assertTrue(result.consumesAction() && stack.getCount() == 1, "Wall placement consumes exactly one poster");
            var panel = (AdvertisementPanelBlockEntity) level.getBlockEntity(origin);
            helper.assertTrue(panel.width() == 1 && panel.height() == 2 && level.getBlockState(origin.above()).is(block), "The poster is one block wide and two blocks tall");
            helper.assertTrue(level.getBlockState(origin).getCollisionShape(level, origin).isEmpty(), "The hanging poster has no physical collision");
            level.destroyBlock(wall.above(), false);
            helper.assertTrue(level.getBlockState(origin).isAir() && level.getBlockState(origin.above()).isAir(), "Removing either wall support removes the whole poster");
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(origin).inflate(3), item -> item.getItem().is(stack.getItem()));
            helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().getCount() == 1 && drops.get(0).getItem().hasFoil(), "Falling posters retain their photo and drop only once");
            drops.forEach(net.minecraft.world.entity.Entity::discard); level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void advertisementMediaCraftingUsesPaperAndGlassAndCannotPrintPhotos(GameTestHelper helper) {
        var level = helper.getLevel(); var citizen = citizen(helper); var player = player(helper, citizen);
        var grid = new net.minecraft.world.inventory.TransientCraftingContainer(new net.minecraft.world.inventory.CraftingMenu(34, player.getInventory()), 2, 2);
        var poster = level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("createapparel:poster")).orElseThrow();
        var billboard = level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("createapparel:billboard_panel")).orElseThrow();
        for (int index = 0; index < 4; index++) grid.setItem(index, new ItemStack(Items.PAPER));
        var type = net.minecraft.world.item.crafting.RecipeType.CRAFTING;
        var recipe = level.getRecipeManager().getRecipeFor(type, grid, level).orElseThrow();
        var output = recipe.assemble(grid, level.registryAccess());
        helper.assertTrue(recipe.getId().equals(poster.getId()) && output.is(AdvertisementRegistries.POSTER_ITEM.get()) && output.getCount() == 1, "Four papers make one poster in the player grid");
        grid.setItem(3, ItemStack.EMPTY);
        helper.assertTrue(level.getRecipeManager().getRecipeFor(type, grid, level).isEmpty(), "Three papers cannot make a poster");
        grid.clearContent(); grid.setItem(0, new ItemStack(AdvertisementRegistries.POSTER_ITEM.get())); grid.setItem(3, new ItemStack(Items.GLASS_PANE));
        recipe = level.getRecipeManager().getRecipeFor(type, grid, level).orElseThrow(); output = recipe.assemble(grid, level.registryAccess());
        helper.assertTrue(recipe.getId().equals(billboard.getId()) && output.is(AdvertisementRegistries.BILLBOARD_PANEL_ITEM.get()) && output.getCount() == 2, "A poster and glass pane make the existing two-panel output");
        grid.setItem(3, new ItemStack(Items.GLASS)); helper.assertTrue(level.getRecipeManager().getRecipeFor(type, grid, level).isEmpty(), "Glass blocks cannot replace glass panes");
        var photo = new ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get()); PhotographyService.snapshot(citizen).write(photo);
        for (var media : java.util.List.of(AdvertisementRegistries.POSTER_ITEM, AdvertisementRegistries.BILLBOARD_PANEL_ITEM)) {
            grid.clearContent(); grid.setItem(0, photo); grid.setItem(1, new ItemStack(media.get()));
            helper.assertTrue(level.getRecipeManager().getRecipeFor(type, grid, level).isEmpty(), "Crafting photos with either media is no longer supported");
        }
        helper.assertTrue(level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("createapparel:ad_print_manual_only")).isEmpty(), "The manual printing recipe is absent");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void photoCraftingCopiesTheCompleteNbtAndConsumesTheInput(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen); var data = PhotographyService.snapshot(citizen).copyIdentity();
        var input = new ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get()); data.write(input);
        var menu = new net.minecraft.world.inventory.CraftingMenu(18, player.getInventory());
        var grid = new net.minecraft.world.inventory.TransientCraftingContainer(menu, 3, 3);
        input.getOrCreateTag().putString("CustomPhotoMetadata", "preserve exactly");
        grid.setItem(0, input);
        var recipe = new PhotoCopyRecipe(net.minecraft.resources.ResourceLocation.parse("createapparel:test_photo_copy"), net.minecraft.world.item.crafting.CraftingBookCategory.MISC);
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Manual table supported");
        var a = recipe.assemble(grid, helper.getLevel().registryAccess()); var b = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(ItemStack.isSameItemSameTags(a, b), "Preview does not generate random UUIDs");
        helper.assertTrue(a.getCount() == 2 && ItemStack.isSameItemSameTags(a, input), "One input yields two photos with exactly the original NBT");
        helper.assertTrue(recipe.getRemainingItems(grid).stream().allMatch(ItemStack::isEmpty), "Copying does not return a third master photo");
        a.getOrCreateTag().putString("CustomPhotoMetadata", "edited copy");
        helper.assertTrue("preserve exactly".equals(input.getOrCreateTag().getString("CustomPhotoMetadata")), "Copy NBT is independent of the input");
        grid.setItem(1, new ItemStack(Items.PAPER));
        helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Extra ingredients are rejected");
        grid.setItem(1, new ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get()));
        helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Multiple photo slots are rejected");
        grid.setItem(1, new ItemStack(AdvertisementRegistries.POSTER_ITEM.get()));
        helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, helper.getLevel()).isEmpty(),
                "Advertising photos cannot be printed onto media in a crafting grid");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void channelsShareWorldStateAndStaticSnapshotsRemainIndependent(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen); var network = AdvertisementNetworkSavedData.get(helper.getLevel());
        String channel = "test_" + Math.abs(player.getUUID().hashCode());
        var data = PhotographyService.snapshot(citizen); var staticSnapshot = AdvertisementSnapshot.photo(data);
        helper.assertTrue(network.publish(player, channel, staticSnapshot), "Publish succeeds");
        var revision = network.channel(channel).revision();
        helper.assertTrue(network.publish(player, channel, AdvertisementSnapshot.DEFAULT) && network.channel(channel).revision() > revision, "Revision advances on republish");
        helper.assertTrue(staticSnapshot.photo() != null, "Static snapshot remains unchanged");
        var other = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if (other != null) helper.assertTrue(AdvertisementNetworkSavedData.get(other) == network, "Dimensions share one saved data instance");
        var hostile = player(helper, citizen); helper.assertTrue(!network.publish(hostile, channel, staticSnapshot), "Another owner cannot overwrite channel");
        helper.assertTrue(!AdvertisementNetworkSavedData.validChannel("../bad"), "Invalid channel rejected"); helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "fashion")
    public static void broadcasterFrontFacesThePlacingPlayerInAllFourDirections(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen);
        var block = AdvertisementRegistries.SIGNAGE_BROADCASTER.get();
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        for (var facing : Direction.Plane.HORIZONTAL) {
            player.setYRot(facing.getOpposite().toYRot());
            var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            var context = new net.minecraft.world.item.context.BlockPlaceContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(AdvertisementRegistries.SIGNAGE_BROADCASTER_ITEM.get()), hit);
            var state = Objects.requireNonNull(block.getStateForPlacement(context));
            helper.assertTrue(state.getValue(HorizontalDirectionalBlock.FACING) == facing, "The front points back toward the placing player");
            helper.assertTrue(state.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90)
                    .getValue(HorizontalDirectionalBlock.FACING) == facing.getClockWise(), "Rotation follows the front");
            helper.assertTrue(state.mirror(net.minecraft.world.level.block.Mirror.LEFT_RIGHT)
                    .getValue(HorizontalDirectionalBlock.FACING) == (facing.getAxis() == Direction.Axis.Z ? facing.getOpposite() : facing), "Mirroring preserves directional front semantics");
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "fashion", timeoutTicks = 200)
    public static void phoneAndCameraPauseTheSameIntentAndResumeAfterClosing(GameTestHelper helper) {
        var citizen = citizen(helper); citizen.setNoGravity(false);
        citizen.setPos(citizen.position().add(0, 80, 0));
        var player = player(helper, citizen); player.setPos(citizen.position().add(0, 0, -3));
        var level = helper.getLevel(); var start = citizen.position(); var state = citizen.citizenState().behavior();
        for (int x = -3; x <= 6; x++) for (int z = -3; z <= 3; z++)
            level.setBlockAndUpdate(citizen.blockPosition().offset(x, -1, z), Blocks.STONE.defaultBlockState());
        var destination = start.add(3, 0, 0); long now = level.getGameTime();
        var intent = new com.rexhub.createapparel.citizen.population.CitizenIntent(
                com.rexhub.createapparel.citizen.population.CitizenIntent.Source.PLAYER,
                com.rexhub.createapparel.citizen.population.CitizenIntent.DestinationType.POSITION, destination, null, now, now + 40, 0);
        helper.assertTrue(citizen.intents().request(intent), "Original movement intent accepted");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
        var interaction = new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(
                player, net.minecraft.world.InteractionHand.MAIN_HAND, citizen);
        ((com.rexhub.createapparel.item.SmartPhoneItem) ModItems.SMART_PHONE.get()).interact(interaction);
        helper.assertTrue(player.containerMenu instanceof com.rexhub.createapparel.menu.CitizenMenu && citizen.interaction().active(), "Phone opens a paused session");
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(citizen.position().distanceToSqr(start) < 0.0001 && !citizen.isNoAi() && !citizen.isEffectiveAi(), "The Citizen stands still without changing NoAI");
            helper.assertTrue(citizen.getLookAngle().dot(player.getEyePosition().subtract(citizen.getEyePosition()).normalize()) > 0.99, "The Citizen faces the interacting player");
            helper.assertTrue(citizen.citizenState().behavior().equals(state) && destination.equals(citizen.routeMovement().currentDestination()), "Behavior and destination are preserved");
            player.closeContainer();
            helper.assertTrue(!citizen.interaction().active() && citizen.isEffectiveAi()
                    && citizen.intents().active() != null && citizen.intents().active().retryCount() == 0
                    && citizen.intents().active().expiresAt() > level.getGameTime(), "Closing resumes the original intent without menu-time expiry");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(PhotoRegistries.CAMERA.get()));
            PhotographyService.open(player, citizen, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(player.containerMenu instanceof PhotographyMenu && citizen.interaction().active(), "Camera also pauses the Citizen");
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(citizen.position().distanceToSqr(start) < 0.0001, "The capture session keeps the Citizen stopped");
                player.closeContainer();
                helper.assertTrue(citizen.isEffectiveAi() && state.equals(citizen.citizenState().behavior())
                        && destination.equals(citizen.routeMovement().currentDestination()), "Capture close restores movement without changing settings");
                helper.runAfterDelay(20, () -> {
                    helper.assertTrue(citizen.getX() > start.x + 0.1, "The Citizen actually walks again after closing the menu: start=" + start
                            + " position=" + citizen.position() + " destination=" + citizen.routeMovement().currentDestination()
                            + " state=" + citizen.routeMovement().state() + " requests=" + citizen.routeMovement().navigationRequests()
                            + " path=" + citizen.getNavigation().getPath() + " intent=" + citizen.intents().active()
                            + " effectiveAi=" + citizen.isEffectiveAi());
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "apparel_state", batch = "fashion", timeoutTicks = 100)
    public static void exclusiveCitizenInteractionReleasesInvalidOwners(GameTestHelper helper) {
        var citizen = citizen(helper); citizen.setNoGravity(true); citizen.setNoAi(true);
        var first = player(helper, citizen); var second = player(helper, citizen);
        var firstMenu = new com.rexhub.createapparel.menu.CitizenMenu(30, first.getInventory(), citizen); first.containerMenu = firstMenu;
        PhotographyService.open(second, citizen, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(second.containerMenu == second.inventoryMenu && firstMenu.stillValid(first), "An occupied Citizen rejects a second camera viewer");
        first.setPos(citizen.position().add(20, 0, 0));
        PhotographyService.open(second, citizen, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(second.containerMenu instanceof PhotographyMenu && citizen.interaction().isViewer(second, second.containerMenu)
                && !firstMenu.stillValid(first), "A distant owner is released immediately when another player interacts");
        firstMenu.removed(first);
        helper.assertTrue(citizen.interaction().isViewer(second, second.containerMenu), "Removing the old menu cannot release the new owner");
        second.setHealth(0);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!citizen.interaction().active() && citizen.isNoAi(), "Invalid owners are pruned and the original NoAI flag is untouched");
            helper.succeed();
        });
    }

    private static void openCitizenInteraction(ServerPlayer player, Citizen citizen, boolean camera) {
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(camera ? PhotoRegistries.CAMERA.get() : ModItems.SMART_PHONE.get()));
        if (camera) PhotographyService.open(player, citizen, net.minecraft.world.InteractionHand.MAIN_HAND);
        else ((com.rexhub.createapparel.item.SmartPhoneItem) ModItems.SMART_PHONE.get()).interact(
                new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND, citizen));
    }

    @GameTest(template = "apparel_state", batch = "fashion")
    public static void phoneAndCameraShareOneExclusiveCitizenSession(GameTestHelper helper) {
        for (boolean firstCamera : new boolean[]{false, true}) for (boolean secondCamera : new boolean[]{false, true}) {
            var citizen = citizen(helper); citizen.setNoAi(true);
            var first = player(helper, citizen); var second = player(helper, citizen);
            second.getInventory().setItem(1, new ItemStack(Items.EMERALD, 64));
            openCitizenInteraction(first, citizen, firstCamera);
            var firstMenu = first.containerMenu;
            helper.assertTrue(firstMenu != first.inventoryMenu && citizen.interaction().isViewer(first, firstMenu), "The first phone or camera acquires the Citizen");
            openCitizenInteraction(second, citizen, secondCamera);
            helper.assertTrue(second.containerMenu == second.inventoryMenu && firstMenu.stillValid(first)
                    && citizen.interaction().isViewer(first, firstMenu) && second.getInventory().getItem(1).getCount() == 64,
                    "Every phone/camera combination rejects the second player without closing or charging either player");
            first.closeContainer();
            openCitizenInteraction(second, citizen, secondCamera);
            helper.assertTrue(second.containerMenu != second.inventoryMenu && second.containerMenu.stillValid(second)
                    && citizen.interaction().isViewer(second, second.containerMenu), "Closing hands the Citizen to the next interacting player");
            firstMenu.removed(first);
            helper.assertTrue(citizen.interaction().isViewer(second, second.containerMenu) && !firstMenu.stillValid(first), "A delayed old close cannot revoke the new session");
            second.closeContainer(); citizen.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "fashion")
    public static void invalidCitizenSessionsReleaseForDisconnectReplacementAndDimensionChange(GameTestHelper helper) {
        for (int reason = 0; reason < 6; reason++) {
            var citizen = citizen(helper); citizen.setNoAi(true);
            var first = player(helper, citizen); var second = player(helper, citizen);
            openCitizenInteraction(first, citizen, true);
            var original = first.containerMenu;
            switch (reason) {
                case 0 -> first.disconnect();
                case 1 -> first.containerMenu = first.inventoryMenu;
                case 2 -> first.setHealth(0);
                case 3 -> first.setPos(citizen.position().add(20, 0, 0));
                case 4 -> first.setServerLevel(Objects.requireNonNull(helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER)));
                case 5 -> first.discard();
                default -> throw new IllegalArgumentException();
            }
            openCitizenInteraction(second, citizen, false);
            helper.assertTrue(second.containerMenu instanceof com.rexhub.createapparel.menu.CitizenMenu
                    && citizen.interaction().isViewer(second, second.containerMenu) && !original.stillValid(first), "An invalid owner releases the exclusive session: " + reason);
            original.removed(first);
            helper.assertTrue(second.containerMenu.stillValid(second), "A stale removal cannot affect the replacement session: " + reason);
            second.closeContainer(); citizen.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "fashion")
    public static void directCameraMenusCannotBypassAnOccupiedCitizenOrCharge(GameTestHelper helper) {
        var citizen = citizen(helper); citizen.setNoAi(true);
        var first = player(helper, citizen); var second = player(helper, citizen);
        openCitizenInteraction(first, citizen, false);
        var ownerMenu = first.containerMenu;
        int quote = PhotographyService.quote(citizen);
        var denied = PhotographyMenu.capture(32, second.getInventory(), PhotographyService.snapshot(citizen), citizen.getId(), 0, quote);
        second.containerMenu = denied;
        denied.getSlot(PhotographyMenu.PAYMENT_SLOT).set(new ItemStack(Items.EMERALD, quote));
        helper.assertTrue(!denied.stillValid(second) && !PhotographyService.issue(second, denied)
                && denied.getSlot(PhotographyMenu.PAYMENT_SLOT).getItem().getCount() == quote, "A directly constructed unauthorized camera menu cannot issue or charge");
        denied.removed(second);
        helper.assertTrue(citizen.interaction().isViewer(first, ownerMenu) && ownerMenu.stillValid(first), "An unauthorized camera cannot release the phone owner");
        var otherCitizen = citizen(helper); otherCitizen.setNoAi(true);
        second.containerMenu = second.inventoryMenu; openCitizenInteraction(second, otherCitizen, true);
        helper.assertTrue(second.containerMenu instanceof PhotographyMenu && otherCitizen.interaction().isViewer(second, second.containerMenu)
                && citizen.interaction().isViewer(first, ownerMenu), "Different Citizens can be used independently");
        second.closeContainer(); first.closeContainer(); helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "fashion")
    public static void replacingOwnCitizenMenuKeepsTheNewMenuExclusive(GameTestHelper helper) {
        var citizen = citizen(helper); citizen.setNoAi(true);
        var first = player(helper, citizen); var second = player(helper, citizen);
        openCitizenInteraction(first, citizen, false); var oldMenu = first.containerMenu;
        first.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(PhotoRegistries.CAMERA.get()));
        var replacement = PhotographyMenu.capture(33, first.getInventory(), PhotographyService.snapshot(citizen), citizen.getId(), 0, PhotographyService.quote(citizen));
        first.containerMenu = replacement;
        oldMenu.removed(first);
        helper.assertTrue(replacement.stillValid(first) && citizen.interaction().isViewer(first, replacement)
                && !oldMenu.stillValid(first), "The same player can replace a menu without leaving the old menu authorized");
        openCitizenInteraction(second, citizen, true);
        helper.assertTrue(second.containerMenu == second.inventoryMenu && citizen.interaction().isViewer(first, replacement), "The replacement remains exclusive to its owner");
        first.closeContainer(); helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void eitherPosterHalfDropsExactlyOnePreservedPosterInEveryDirection(GameTestHelper helper) {
        var level = helper.getLevel(); var citizen = citizen(helper); var player = player(helper, citizen);
        var photo = PhotographyService.snapshot(citizen); var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        for (var facing : Direction.Plane.HORIZONTAL) for (int part = 0; part < 2; part++) for (boolean legacy : new boolean[]{false, true}) {
            var state = AdvertisementRegistries.POSTER.get().defaultBlockState().setValue(AdvertisementPanelBlock.FACING, facing);
            var stack = new ItemStack(AdvertisementRegistries.POSTER_ITEM.get()); stack.getOrCreateTag().put("Advertisement", AdvertisementSnapshot.photo(photo).save());
            level.setBlockAndUpdate(origin, state);
            if (!legacy) state.getBlock().setPlacedBy(level, origin, state, player, stack);
            var other = origin.above();
            if (legacy) {
                other = origin.relative(facing.getCounterClockWise());
                level.setBlockAndUpdate(other, state.setValue(AdvertisementPanelBlock.PART, 1));
                var controller = (AdvertisementPanelBlockEntity) level.getBlockEntity(origin);
                controller.initialize(player, stack.getOrCreateTag());
                var content = controller.content(); var id = controller.groupId();
                controller.group(id, origin, 2, 1, content);
                ((AdvertisementPanelBlockEntity) level.getBlockEntity(other)).group(id, origin, 2, 1, null);
            }
            level.destroyBlock(part == 0 ? origin : other, true);
            helper.assertTrue(!level.getBlockState(origin).is(AdvertisementRegistries.POSTER.get()) && !level.getBlockState(other).is(AdvertisementRegistries.POSTER.get()), "Both poster halves removed");
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(origin).inflate(3),
                    item -> item.getItem().is(AdvertisementRegistries.POSTER_ITEM.get()));
            helper.assertTrue(drops.stream().mapToInt(item -> item.getItem().getCount()).sum() == 1, "Exactly one poster dropped");
            var snapshot = AdvertisementSnapshot.load(drops.get(0).getItem().getOrCreateTag().getCompound("Advertisement"));
            helper.assertTrue(snapshot.photo() != null && snapshot.photo().save().equals(photo.save()), "Printed content preserved on drop");
            drops.forEach(net.minecraft.world.entity.Entity::discard);
        }
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void framingAndFillExcludeHiddenSlotsFromFashionSources(GameTestHelper helper) {
        var citizen = citizen(helper); var outfit = citizen.citizenState().loadout();
        var upper = new PhotoLayout(PhotoLayout.DEFAULT.background(), PhotoLayout.Framing.UPPER_BODY, PhotoLayout.Aspect.FIT,
                PhotoLayout.Horizontal.CENTER, PhotoLayout.Vertical.CENTER, false);
        helper.assertTrue(com.rexhub.createapparel.photo.PhotoVisibility.visible(GarmentSlot.TOP, upper, 2, 1, outfit), "Upper body can advertise a top");
        helper.assertTrue(!com.rexhub.createapparel.photo.PhotoVisibility.visible(GarmentSlot.FEET, upper, 2, 1, outfit), "Upper body cannot advertise footwear");
        helper.assertTrue(!com.rexhub.createapparel.photo.PhotoVisibility.visible(GarmentSlot.INNER_TOP, PhotoLayout.DEFAULT, 2, 1, outfit), "A covered inner top is not advertised");
        var fill = new PhotoLayout(PhotoLayout.DEFAULT.background(), PhotoLayout.Framing.FULL_BODY, PhotoLayout.Aspect.FILL,
                PhotoLayout.Horizontal.CENTER, PhotoLayout.Vertical.CENTER, false);
        helper.assertTrue(!com.rexhub.createapparel.photo.PhotoVisibility.visible(GarmentSlot.FEET, fill, 2, 1, outfit), "Wide fill crops footwear"); helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void actualPickupAndShiftCraftingDuplicateUnchangedNbtInBothGrids(GameTestHelper helper) {
        var citizen = citizen(helper); var level = helper.getLevel(); var data = PhotographyService.snapshot(citizen).copyIdentity();
        for (int width : new int[]{2, 3}) for (boolean shift : new boolean[]{false, true}) {
            var player = player(helper, citizen); player.getInventory().clearContent();
            net.minecraft.world.inventory.AbstractContainerMenu menu;
            if (width == 2) menu = player.inventoryMenu;
            else {
                var pos = helper.absolutePos(new BlockPos(1, 1, 1)); level.setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
                menu = new net.minecraft.world.inventory.CraftingMenu(22, player.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(level, pos));
            }
            player.containerMenu = menu;
            var grid = (net.minecraft.world.inventory.CraftingContainer) menu.getSlot(1).container;
            var input = new ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get(), 2); data.write(input);
            input.getOrCreateTag().putString("CustomPhotoMetadata", "keep");
            var master = input.copy();
            grid.setItem(0, input);
            for (int iteration = 0; iteration < 2; iteration++) {
                if (shift) menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
                else {
                    menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
                    helper.assertTrue(menu.getCarried().getCount() == (iteration + 1) * 2
                            && ItemStack.isSameItemSameTags(menu.getCarried(), master), "Normal pickup creates two identical-NBT photos per consumed input");
                }
            }
            if (shift) {
                var copies = player.getInventory().items.stream().filter(stack -> stack.is(PhotoRegistries.ADVERTISING_PHOTO.get())).toList();
                helper.assertTrue(copies.stream().mapToInt(ItemStack::getCount).sum() == 4, "Shift turns two inputs into four outputs");
                helper.assertTrue(copies.stream().allMatch(stack -> ItemStack.isSameItemSameTags(stack, master)), "Every Shift output keeps the entire NBT");
            }
            helper.assertTrue(grid.getItem(0).isEmpty(), "Both source photos are consumed without a remainder");
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "fashion")
    public static void fullInventoryShiftCopyDoesNotConsumeOrMutateItsSource(GameTestHelper helper) {
        var citizen = citizen(helper); var player = player(helper, citizen);
        for (int index = 0; index < player.getInventory().items.size(); index++) player.getInventory().setItem(index, new ItemStack(Items.STONE, 64));
        var menu = player.inventoryMenu; player.containerMenu = menu;
        var grid = (net.minecraft.world.inventory.CraftingContainer) menu.getSlot(1).container;
        var input = new ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get(), 2); PhotographyService.snapshot(citizen).write(input);
        var before = input.copy(); grid.setItem(0, input);
        menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
        helper.assertTrue(ItemStack.matches(before, grid.getItem(0)), "A failed Shift transfer keeps the source stack and NBT unchanged");
        helper.assertTrue(menu.getSlot(0).getItem().getCount() == 2
                && ItemStack.isSameItemSameTags(before, menu.getSlot(0).getItem()), "The unchanged two-photo preview remains available");
        helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion")
    public static void anotherPlayerMustApproveBeforeAFreePhotoCanBeCreated(GameTestHelper helper) {
        var citizen = citizen(helper); var photographer = player(helper, citizen); var subject = player(helper, citizen);
        helper.getLevel().addFreshEntity(photographer); helper.getLevel().addFreshEntity(subject);
        PhotographyService.open(photographer, subject, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(subject.containerMenu instanceof PhotographyMenu consent && consent.mode() == PhotographyMenu.Mode.CONSENT, "Subject receives an approval menu");
        helper.assertTrue(!(photographer.containerMenu instanceof PhotographyMenu), "No capture session before approval");
        var consent = (PhotographyMenu) subject.containerMenu;
        helper.assertTrue(consent.clickMenuButton(subject, 8), "Subject approval succeeds");
        helper.assertTrue(photographer.containerMenu instanceof PhotographyMenu capture && capture.mode() == PhotographyMenu.Mode.CAPTURE && capture.cost() == 0,
                "Approval opens a free session for the photographer");
        helper.assertTrue(PhotographyService.issue(photographer, (PhotographyMenu) photographer.containerMenu), "Approved capture succeeds");
        var photo = photographer.getInventory().items.stream().filter(stack -> stack.is(PhotoRegistries.ADVERTISING_PHOTO.get())).findFirst().orElseThrow();
        helper.assertTrue(Objects.requireNonNull(AdvertisingPhotoData.read(photo)).playerId().equals(subject.getUUID()), "Photo references the approved subject UUID");
        subject.discard(); photographer.discard(); helper.succeed();
    }
    @GameTest(template = "apparel_state", batch = "fashion_observation", timeoutTicks = 260)
    public static void lockedSourceSpreadsItsPurchasedGarmentThroughAnActualFashionIntent(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(1, 98, 1));
        for (int x = -3; x <= 5; x++) for (int z = -3; z <= 5; z++)
            level.setBlockAndUpdate(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, FashionRegistries.CASH_REGISTER.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        var register = (CashRegisterBlockEntity) level.getBlockEntity(pos);
        level.setBlockAndUpdate(pos.east(), Blocks.CHEST.defaultBlockState());
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(pos.east());
        var garment = new ItemStack(ModItems.SWEATER.get());
        GarmentData.defaultFor(GarmentDefinitionManager.get(ModItems.SWEATER.getId()).orElseThrow()).write(garment);
        chest.setItem(0, garment.copy()); register.linkStorage(pos.east());
        var seatPos = pos.south(); level.setBlockAndUpdate(seatPos, com.simibubi.create.AllBlocks.SEATS.get(net.minecraft.world.item.DyeColor.WHITE).getDefaultState());
        var seat = new com.simibubi.create.content.contraptions.actors.seat.SeatEntity(level, seatPos); seat.setPos(Vec3.atBottomCenterOf(seatPos)); level.addFreshEntity(seat);
        var cow = Objects.requireNonNull(net.minecraft.world.entity.EntityType.COW.create(level)); cow.setPos(Vec3.atBottomCenterOf(seatPos)); level.addFreshEntity(cow); cow.startRiding(seat, true);
        var observer = citizen(helper); observer.setPos(Vec3.atBottomCenterOf(pos.south(2))); observer.setOnGround(true);
        var source = citizen(helper); source.setPos(observer.position().add(1, 0, 0)); source.setNoAi(true); source.setNoGravity(true);
        helper.assertTrue(register.isOpen(), "The test shop has a cashier in front and a connected inventory");
        source.citizenState().loadout().setStackInSlot(GarmentSlot.TOP.ordinal(), garment.copy());
        source.citizenState().fashion().purchased(GarmentSlot.TOP, register.reference()); source.citizenState().fashion().setForceFashion(true);
        observer.setYRot(-90); observer.yHeadRot = observer.yBodyRot = -90;
        var config = com.rexhub.createapparel.config.ModConfigs.COMMON.fashion;
        int interval = config.observationTicks.get(); double points = config.otherCitizenPoints.get(), scale = config.pointScale.get(), shopping = config.shoppingChance.get();
        config.observationTicks.set(20); config.otherCitizenPoints.set(1000.0); config.pointScale.set(1.0); config.shoppingChance.set(0.0);
        helper.runAfterDelay(220, () -> {
            try {
                helper.assertTrue(ItemStack.isSameItemSameTags(observer.citizenState().loadout().getStack(GarmentSlot.TOP), garment),
                        "Observer purchased and wore the advertised garment: position=" + observer.position() + " intent=" + observer.intents().active()
                                + " desired=" + observer.citizenState().fashion().desiredRegister() + " state=" + observer.routeMovement().state()
                                + " failures=" + observer.routeMovement().consecutiveFailures() + " open=" + register.isOpen());
                helper.assertTrue(chest.getItem(0).isEmpty(), "The actual shop stock was consumed");
                helper.assertTrue(register.reference().equals(observer.citizenState().fashion().purchasedRegister(GarmentSlot.TOP)), "Purchase source can propagate further");
                helper.assertTrue(source.citizenState().fashion().forceFashion(), "Locked Citizen remains a fashion source"); helper.succeed();
            } finally {
                config.observationTicks.set(interval); config.otherCitizenPoints.set(points); config.pointScale.set(scale); config.shoppingChance.set(shopping);
                observer.discard(); source.discard(); cow.discard(); seat.discard();
            }
        });
    }
}
