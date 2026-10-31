package com.rexhub.createapparel.gametest;

import com.mojang.authlib.GameProfile;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.citizen.CitizenCoverage;
import com.rexhub.createapparel.citizen.SmartPhoneEvents;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenAge;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.menu.CitizenMenu;
import com.rexhub.createapparel.network.CitizenActionPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitizenMenuGameTests {
    private CitizenMenuGameTests() { }

    private record Fixture(Citizen citizen, ServerPlayer player, CitizenMenu menu) { }

    private static Fixture fixture(GameTestHelper helper) {
        Citizen citizen = ModEntities.CITIZEN.get().create(helper.getLevel());
        if (citizen == null) throw new IllegalStateException("Citizen factory failed");
        citizen.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        citizen.setNoGravity(true);
        helper.getLevel().addFreshEntity(citizen);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "citizen-menu"), net.minecraft.server.level.ClientInformation.createDefault());
        player.setPos(citizen.position());
        var menu = new CitizenMenu(19, player.getInventory(), citizen);
        player.containerMenu = menu;
        return new Fixture(citizen, player, menu);
    }

    private static CompoundTag inventory(Fixture fixture) {
        var tag = new CompoundTag();
        tag.put("Items", fixture.player().getInventory().save(new net.minecraft.nbt.ListTag()));
        tag.put("Carried", fixture.menu().getCarried().saveOptional(com.rexhub.createapparel.TestRegistries.lookup()));
        return tag;
    }

    private static void unchanged(GameTestHelper helper, Fixture fixture, CompoundTag state, CompoundTag items) {
        helper.assertTrue(state.equals(fixture.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup())), "Rejected action keeps Citizen state and styles");
        helper.assertTrue(items.equals(inventory(fixture)), "Rejected action keeps cursor and every player inventory slot");
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void criticalRemovalsAreRejectedOnEveryClickRoute(GameTestHelper helper) {
        var f = fixture(helper);
        for (GarmentSlot slot : List.of(GarmentSlot.TOP, GarmentSlot.BOTTOM)) {
            for (ClickType type : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.SWAP, ClickType.THROW)) {
                for (int button : new int[]{0, 1}) {
                    var before = f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup());
                    var items = inventory(f);
                    f.menu().clicked(slot.ordinal(), button, type, f.player());
                    unchanged(helper, f, before, items);
                }
            }
        }
        f.player().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE));
        var before = f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup());
        var items = inventory(f);
        for (int index : new int[]{-200, 999}) for (ClickType type : ClickType.values()) {
            f.menu().clicked(index, 0, type, f.player());
        }
        f.menu().clicked(GarmentSlot.TOP.ordinal(), 40, ClickType.SWAP, f.player());
        f.menu().clicked(GarmentSlot.TOP.ordinal(), 39, ClickType.SWAP, f.player());
        f.menu().clicked(-1, 40, ClickType.SWAP, f.player());
        f.menu().quickMoveStack(f.player(), GarmentSlot.TOP.ordinal());
        unchanged(helper, f, before, items);
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void directAndHotbarReplacementCommitOnceAndPreserveItemNbt(GameTestHelper helper) {
        var f = fixture(helper);
        var previous = f.menu().apparelStack(GarmentSlot.TOP);
        var shirt = new ItemStack(ModItems.SHIRT_LONGSLEEVES.get());
        com.rexhub.createapparel.item.ItemData.update(shirt, tagData -> tagData.putString("CustomMetadata", "retained"));
        f.menu().setCarried(shirt.copy());
        long revision = f.citizen().citizenState().revision();
        f.menu().clicked(GarmentSlot.TOP.ordinal(), 0, ClickType.PICKUP, f.player());
        helper.assertTrue(ItemStack.matches(shirt, f.menu().apparelStack(GarmentSlot.TOP))
                && ItemStack.matches(previous, f.menu().getCarried()), "Direct replacement swaps actual stacks");
        helper.assertTrue(f.citizen().citizenState().revision() == revision + 1, "Replacement publishes exactly one complete state");
        f.menu().setCarried(ItemStack.EMPTY);
        f.player().getInventory().setItem(2, previous.copy());
        f.menu().clicked(GarmentSlot.TOP.ordinal(), 2, ClickType.SWAP, f.player());
        helper.assertTrue(ItemStack.matches(shirt, f.player().getInventory().getItem(2))
                && ItemStack.matches(previous, f.menu().apparelStack(GarmentSlot.TOP)), "Number-key replacement preserves both stacks");
        f.player().setItemInHand(InteractionHand.OFF_HAND, shirt.copy());
        f.menu().clicked(GarmentSlot.TOP.ordinal(), 40, ClickType.SWAP, f.player());
        helper.assertTrue(ItemStack.matches(shirt, f.menu().apparelStack(GarmentSlot.TOP))
                && ItemStack.matches(previous, f.player().getOffhandItem()), "Offhand swap uses actual offhand");
        f.menu().setCarried(new ItemStack(Items.STONE));
        var before = f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup());
        var items = inventory(f);
        f.menu().clicked(GarmentSlot.TOP.ordinal(), 0, ClickType.PICKUP, f.player());
        unchanged(helper, f, before, items);
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void shiftTransferAndUnderwearPermitCoveredRemoval(GameTestHelper helper) {
        var f = fixture(helper);
        f.player().getInventory().setItem(9, new ItemStack(ModItems.BRIEFS.get()));
        f.menu().clicked(CitizenMenu.PLAYER_INVENTORY_START, 0, ClickType.QUICK_MOVE, f.player());
        helper.assertTrue(f.player().getInventory().getItem(9).isEmpty()
                && !f.menu().apparelStack(GarmentSlot.INNER_BOTTOM).isEmpty(), "Shift-click equips compatible underwear");
        var pants = f.menu().apparelStack(GarmentSlot.BOTTOM);
        f.menu().clicked(GarmentSlot.BOTTOM.ordinal(), 1, ClickType.PICKUP, f.player());
        helper.assertTrue(f.menu().apparelStack(GarmentSlot.BOTTOM).isEmpty() && ItemStack.matches(pants, f.menu().getCarried())
                && CitizenCoverage.isCovered(f.menu().loadout()), "Covered removal puts real trousers on cursor");
        f.menu().clicked(GarmentSlot.BOTTOM.ordinal(), 1, ClickType.PICKUP, f.player());
        helper.assertTrue(f.menu().getCarried().isEmpty() && ItemStack.matches(pants, f.menu().apparelStack(GarmentSlot.BOTTOM)), "Right-click places one garment");
        f.menu().clicked(GarmentSlot.INNER_BOTTOM.ordinal(), 0, ClickType.QUICK_MOVE, f.player());
        helper.assertTrue(f.menu().apparelStack(GarmentSlot.INNER_BOTTOM).isEmpty()
                && f.player().getInventory().items.stream().filter(stack -> stack.is(ModItems.BRIEFS.get())).mapToInt(ItemStack::getCount).sum() == 1,
                "Shift removal transfers exactly once");
        for (int i = 0; i < 36; i++) f.player().getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        var before = f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup());
        var items = inventory(f);
        f.menu().clicked(GarmentSlot.FEET.ordinal(), 0, ClickType.QUICK_MOVE, f.player());
        unchanged(helper, f, before, items);
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void taggedDressReplacementAndAutomationRespectCompleteCoverage(GameTestHelper helper) {
        var f = fixture(helper);
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("createapparel:shirt_dress_midi"));
        helper.assertTrue(item != null && item != Items.AIR, "Registered lower-covering dress exists");
        f.menu().setCarried(new ItemStack(item));
        f.menu().clicked(GarmentSlot.TOP.ordinal(), 0, ClickType.PICKUP, f.player());
        f.menu().setCarried(ItemStack.EMPTY);
        f.menu().clicked(GarmentSlot.BOTTOM.ordinal(), 0, ClickType.PICKUP, f.player());
        helper.assertTrue(!f.menu().getCarried().isEmpty() && CitizenCoverage.isCovered(f.menu().loadout()), "Dress supplies lower coverage without trousers");
        f.menu().setCarried(new ItemStack(ModItems.T_SHIRT.get()));
        var before = f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup());
        var items = inventory(f);
        f.menu().clicked(GarmentSlot.TOP.ordinal(), 0, ClickType.PICKUP, f.player());
        unchanged(helper, f, before, items);
        var loadout = f.menu().loadout();
        helper.assertTrue(loadout.extractItem(GarmentSlot.TOP.ordinal(), 1, true).isEmpty()
                && loadout.extractItem(GarmentSlot.TOP.ordinal(), 1, false).isEmpty(), "Automation cannot remove last cover");
        loadout.setStackInSlot(GarmentSlot.TOP.ordinal(), ItemStack.EMPTY);
        loadout.getStack(GarmentSlot.TOP).shrink(1);
        com.rexhub.createapparel.item.ItemData.update(loadout.getStack(GarmentSlot.TOP), tag -> tag.putString("Tamper", "copy"));
        helper.assertTrue(loadout.removeAll().isEmpty() && before.equals(f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup())), "Bulk removal and reference mutation cannot bypass coverage");
        var legacy = new com.rexhub.createapparel.entity.CitizenState();
        var saved = before.copy();
        saved.put("ApparelSlots", new com.rexhub.createapparel.apparel.ApparelLoadout().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup()));
        legacy.deserializeNBT(com.rexhub.createapparel.TestRegistries.lookup(), saved);
        helper.assertTrue(legacy.loadout().getStack(GarmentSlot.TOP).isEmpty(), "Legacy uncovered saves remain loadable");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void hiddenTabsRejectAllSlotRoutesAndClearPendingDrag(GameTestHelper helper) {
        var f = fixture(helper);
        f.menu().setCarried(new ItemStack(Items.STONE, 8));
        f.menu().clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, f.player());
        f.menu().clicked(CitizenMenu.PLAYER_INVENTORY_START, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, f.player());
        for (var tab : List.of(CitizenMenu.Tab.APPEARANCE, CitizenMenu.Tab.BEHAVIOR)) {
            CitizenActionPacket.tab(f.menu(), tab).handle(f.player());
            helper.assertTrue(f.menu().slots.stream().noneMatch(net.minecraft.world.inventory.Slot::isActive), "All invisible slots inactive");
            var before = f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup());
            var items = inventory(f);
            for (ClickType type : ClickType.values()) for (int index : new int[]{0, GarmentSlot.TOP.ordinal(), CitizenMenu.PLAYER_INVENTORY_START, -999}) {
                f.menu().clicked(index, 0, type, f.player());
            }
            f.menu().quickMoveStack(f.player(), GarmentSlot.FEET.ordinal());
            unchanged(helper, f, before, items);
        }
        CitizenActionPacket.tab(f.menu(), CitizenMenu.Tab.GARMENT).handle(f.player());
        f.menu().clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, f.player());
        helper.assertTrue(f.player().getInventory().getItem(9).isEmpty() && f.menu().getCarried().getCount() == 8, "Tab switch clears old drag selection");
        f.menu().clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, f.player());
        f.menu().clicked(CitizenMenu.PLAYER_INVENTORY_START, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, f.player());
        f.menu().clicked(CitizenMenu.PLAYER_INVENTORY_START + 1, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, f.player());
        f.menu().clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, f.player());
        helper.assertTrue(f.player().getInventory().getItem(9).getCount() == 4 && f.player().getInventory().getItem(10).getCount() == 4
                && f.menu().getCarried().isEmpty(), "Visible player inventory retains normal drag distribution");
        f.menu().setCarried(new ItemStack(Items.STONE));
        f.menu().clicked(CitizenMenu.PLAYER_INVENTORY_START + 2, 0, ClickType.PICKUP_ALL, f.player());
        helper.assertTrue(f.menu().getCarried().getCount() == 9 && f.player().getInventory().getItem(9).isEmpty()
                && f.player().getInventory().getItem(10).isEmpty(), "Double-click collects player items without loss");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void appearancePaletteStylesAndChildSaveAreServerAuthoritative(GameTestHelper helper) {
        var f = fixture(helper);
        f.citizen().citizenState().setAge(CitizenAge.CHILD);
        f.menu().setCarried(new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()));
        f.menu().clicked(GarmentSlot.TOP.ordinal(), 0, ClickType.PICKUP, f.player());
        CitizenActionPacket.style(f.menu(), GarmentSlot.TOP, "sleeves", "rolled").handle(f.player());
        helper.assertTrue("rolled".equals(f.menu().loadout().styleState(GarmentSlot.TOP).styles().get("sleeves")), "Citizen uses shared style constraints");
        CitizenActionPacket.tab(f.menu(), CitizenMenu.Tab.APPEARANCE).handle(f.player());
        for (var color : FabricColor.values()) {
            CitizenActionPacket.select(f.menu(), CitizenActionPacket.Action.HAIR_COLOR, color.getSerializedName()).handle(f.player());
            CitizenActionPacket.select(f.menu(), CitizenActionPacket.Action.EYE_COLOR, color.getSerializedName()).handle(f.player());
            helper.assertTrue(f.citizen().citizenState().appearance().hairColorRgb() == color.tintRgb()
                    && f.citizen().citizenState().appearance().eyeColorRgb() == color.tintRgb(), "Every dye uses shared RGB: " + color);
        }
        for (var tone : SkinTone.values()) {
            CitizenActionPacket.select(f.menu(), CitizenActionPacket.Action.SKIN_TONE, tone.getSerializedName()).handle(f.player());
            helper.assertTrue(f.citizen().citizenState().appearance().skinTone() == tone, "Every skin tone is selectable");
        }
        for (var name : com.rexhub.createapparel.citizen.CitizenHairStyles.BUILT_IN) {
            CitizenActionPacket.select(f.menu(), CitizenActionPacket.Action.HAIR_STYLE, "createapparel:" + name).handle(f.player());
            helper.assertTrue(f.citizen().citizenState().appearance().hairStyleId().getPath().equals(name), "Every built-in hair is selectable");
        }
        CitizenActionPacket.select(f.menu(), CitizenActionPacket.Action.ARM_PROFILE, "slim").handle(f.player());
        CitizenActionPacket.select(f.menu(), CitizenActionPacket.Action.CHEST_PROFILE, "bust").handle(f.player());
        helper.assertTrue(f.citizen().citizenState().appearance().armProfile() == ArmProfile.SLIM
                && f.citizen().citizenState().appearance().chestProfile() == ChestProfile.BUST && f.citizen().isBaby(), "Body editing preserves child age");
        f.citizen().getEntityData().packDirty();
        long revision = f.citizen().citizenState().revision();
        CitizenActionPacket.select(f.menu(), CitizenActionPacket.Action.ARM_PROFILE, "slim").handle(f.player());
        helper.assertTrue(f.citizen().citizenState().revision() == revision && f.citizen().getEntityData().packDirty() == null, "Unchanged selection sends no full state");
        var saved = f.citizen().saveWithoutId(new CompoundTag());
        var restored = ModEntities.CITIZEN.get().create(helper.getLevel());
        if (restored == null) throw new IllegalStateException("Citizen factory failed");
        restored.load(saved);
        helper.assertTrue(restored.citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup()).equals(f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup()))
                && saved.getString("BaseMode").equals("stand"), "Edited child appearance, clothes and read-only behavior persist");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void malformedPacketsAndStaleMenusCannotEditCitizen(GameTestHelper helper) {
        var f = fixture(helper);
        CitizenActionPacket.tab(f.menu(), CitizenMenu.Tab.APPEARANCE).handle(f.player());
        var before = f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup());
        for (var action : CitizenActionPacket.Action.values()) {
            for (String value : List.of("missing", "#FFFFFF", "BLUE", "other:short", "createapparel:missing", "x".repeat(65))) {
                new CitizenActionPacket(19, f.citizen().getId(), action, -1, "", value).handle(f.player());
            }
        }
        for (var packet : List.of(new CitizenActionPacket(20, f.citizen().getId(), CitizenActionPacket.Action.SKIN_TONE, 0, "", "fair"),
                new CitizenActionPacket(19, f.citizen().getId() + 1, CitizenActionPacket.Action.SKIN_TONE, 0, "", "fair"),
                new CitizenActionPacket(19, f.citizen().getId(), null, 0, "", "fair"))) packet.handle(f.player());
        helper.assertTrue(before.equals(f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup())), "Malformed fields, entity and menu IDs rejected");
        for (var action : List.of(CitizenActionPacket.Action.HAIR_STYLE, CitizenActionPacket.Action.HAIR_COLOR,
                CitizenActionPacket.Action.EYE_COLOR, CitizenActionPacket.Action.SKIN_TONE)) {
            CitizenActionPacket.select(f.menu(), action, "missing").handle(f.player());
        }
        helper.assertTrue(before.equals(f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup())), "Invalid values with valid indices rejected");
        f.player().getAbilities().mayBuild = false;
        CitizenActionPacket.select(f.menu(), CitizenActionPacket.Action.SKIN_TONE, "fair").handle(f.player());
        f.player().getAbilities().mayBuild = true;
        f.player().setPos(f.citizen().position().add(9, 0, 0));
        helper.assertTrue(!f.menu().stillValid(f.player()), "Eight-block distance enforced");
        CitizenActionPacket.select(f.menu(), CitizenActionPacket.Action.SKIN_TONE, "fair").handle(f.player());
        f.player().setPos(f.citizen().position());
        f.player().containerMenu = f.player().inventoryMenu;
        CitizenActionPacket.select(f.menu(), CitizenActionPacket.Action.SKIN_TONE, "fair").handle(f.player());
        helper.assertTrue(before.equals(f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup())), "Permissions, distance and closed menu rejected");
        f.player().containerMenu = f.menu();
        f.citizen().setHealth(0);
        helper.assertTrue(!f.menu().stillValid(f.player()), "Death invalidates open menu");
        f.citizen().discard();
        helper.assertTrue(!f.menu().stillValid(f.player()), "Removal invalidates open menu");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), com.rexhub.createapparel.TestRegistries.lookup(), net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        try {
            buffer.writeVarInt(Integer.MAX_VALUE);
            var missing = new CitizenMenu(21, f.player().getInventory(), buffer);
            helper.assertTrue(missing.citizen() == null && !missing.stillValid(f.player()), "Missing tracked entity safely invalidates client menu");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void exclusiveMenusRejectOtherPlayersAndPhoneRequiresActualHand(GameTestHelper helper) {
        var f = fixture(helper);
        var otherPlayer = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "citizen-other"), net.minecraft.server.level.ClientInformation.createDefault());
        otherPlayer.setPos(f.citizen().position());
        var other = new CitizenMenu(20, otherPlayer.getInventory(), f.citizen());
        otherPlayer.containerMenu = other;
        var before = f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup());
        var items = inventory(f);
        other.setCarried(new ItemStack(ModItems.SWEATER.get()));
        other.clicked(GarmentSlot.TOP.ordinal(), 0, ClickType.PICKUP, otherPlayer);
        unchanged(helper, f, before, items);
        helper.assertTrue(!other.stillValid(otherPlayer) && other.getCarried().is(ModItems.SWEATER.get())
                && f.citizen().interaction().isViewer(f.player(), f.menu()), "A second menu cannot acquire or edit an occupied Citizen");
        var mock = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        for (var hand : InteractionHand.values()) {
            mock.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            mock.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            mock.setItemInHand(hand, new ItemStack(ModItems.SMART_PHONE.get()));
            var unrelated = new PlayerInteractEvent.EntityInteract(mock,
                    hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, f.citizen());
            SmartPhoneEvents.interact(unrelated);
            helper.assertTrue(!unrelated.isCanceled(), "Phone in other hand cannot trigger Citizen GUI");
            var event = new PlayerInteractEvent.EntityInteract(mock, hand, f.citizen());
            SmartPhoneEvents.interact(event);
            helper.assertTrue(event.isCanceled() && mock.getItemInHand(hand).getCount() == 1, "Actual-hand Citizen interaction consumes no phone");
        }
        helper.assertTrue(f.menu().stillValid(f.player()), "Phone is required only at opening");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void permittedDropCloneAndCapacityQueriesKeepCoverageAndStyles(GameTestHelper helper) {
        var f = fixture(helper);
        f.menu().setCarried(new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()));
        f.menu().clicked(GarmentSlot.TOP.ordinal(), 0, ClickType.PICKUP, f.player());
        f.menu().setCarried(ItemStack.EMPTY);
        f.menu().loadout().setStyle(GarmentSlot.TOP, "sleeves", "rolled");
        var before = f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup());
        long revision = f.citizen().citizenState().revision();
        helper.assertTrue(f.menu().slots.get(GarmentSlot.TOP.ordinal()).getMaxStackSize(new ItemStack(ModItems.SHIRT_LONGSLEEVES.get())) == 1,
                "Capacity query uses fixed apparel limit");
        helper.assertTrue(before.equals(f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup())) && revision == f.citizen().citizenState().revision(),
                "Read-only capacity query cannot clear stack, reset style or notify");
        f.menu().clicked(GarmentSlot.TOP.ordinal(), 2, ClickType.CLONE, f.player());
        helper.assertTrue(f.menu().getCarried().isEmpty(), "Survival cannot clone equipment");
        f.player().getAbilities().instabuild = true;
        f.menu().clicked(GarmentSlot.TOP.ordinal(), 2, ClickType.CLONE, f.player());
        helper.assertTrue(f.menu().getCarried().is(ModItems.SHIRT_LONGSLEEVES.get()) && before.equals(f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup())),
                "Creative pick copies equipment without touching Citizen");
        f.menu().setCarried(ItemStack.EMPTY);
        f.player().getAbilities().instabuild = false;
        var shoes = f.menu().apparelStack(GarmentSlot.FEET);
        f.menu().clicked(GarmentSlot.FEET.ordinal(), 1, ClickType.THROW, f.player());
        var dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                f.player().getBoundingBox().inflate(2), entity -> ItemStack.matches(entity.getItem(), shoes));
        helper.assertTrue(f.menu().apparelStack(GarmentSlot.FEET).isEmpty() && dropped.size() == 1
                && CitizenCoverage.isCovered(f.menu().loadout()), "Permitted throw produces exactly one real item after commit");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen_reload", timeoutTicks = 400)
    public static void editedAppearanceAndGarmentsSurviveRealDatapackReload(GameTestHelper helper) {
        var f = fixture(helper);
        f.menu().setCarried(new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()));
        f.menu().clicked(GarmentSlot.TOP.ordinal(), 0, ClickType.PICKUP, f.player());
        CitizenActionPacket.style(f.menu(), GarmentSlot.TOP, "sleeves", "rolled").handle(f.player());
        CitizenActionPacket.tab(f.menu(), CitizenMenu.Tab.APPEARANCE).handle(f.player());
        CitizenActionPacket.select(f.menu(), CitizenActionPacket.Action.HAIR_COLOR, "navy").handle(f.player());
        CitizenActionPacket.select(f.menu(), CitizenActionPacket.Action.HAIR_STYLE, "createapparel:bald").handle(f.player());
        var before = f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup());
        var server = helper.getLevel().getServer();
        var reload = server.reloadResources(server.getPackRepository().getSelectedIds());
        helper.succeedWhen(() -> {
            helper.assertTrue(reload.isDone(), "Reload completed");
            reload.join();
            helper.assertTrue(before.equals(f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup())) && CitizenCoverage.isCovered(f.menu().loadout()),
                    "Reload preserves edited appearance, actual clothes and equipped styles");
        });
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void appearanceWheelStepsUseCurrentServerValuesAndWrapEveryOption(GameTestHelper helper) {
        var f = fixture(helper);
        CitizenActionPacket.tab(f.menu(), CitizenMenu.Tab.APPEARANCE).handle(f.player());
        var options = new java.util.EnumMap<CitizenActionPacket.Action, List<String>>(CitizenActionPacket.Action.class);
        options.put(CitizenActionPacket.Action.ARM_PROFILE, List.of("classic", "slim"));
        options.put(CitizenActionPacket.Action.CHEST_PROFILE, List.of("flat", "bust"));
        options.put(CitizenActionPacket.Action.SKIN_TONE, java.util.Arrays.stream(SkinTone.values()).map(SkinTone::getSerializedName).toList());
        options.put(CitizenActionPacket.Action.HAIR_STYLE, com.rexhub.createapparel.citizen.CitizenHairStyles.BUILT_IN.stream()
                .map(name -> "createapparel:" + name).toList());
        options.put(CitizenActionPacket.Action.HAIR_COLOR, java.util.Arrays.stream(FabricColor.values()).map(FabricColor::getSerializedName).toList());
        options.put(CitizenActionPacket.Action.EYE_COLOR, options.get(CitizenActionPacket.Action.HAIR_COLOR));
        for (var entry : options.entrySet()) {
            var field = entry.getKey();
            var values = entry.getValue();
            CitizenActionPacket.select(f.menu(), field, values.get(0)).handle(f.player());
            for (int click = 0; click < 3; click++) CitizenActionPacket.step(f.menu(), field, 1).handle(f.player());
            helper.assertTrue(appearanceOption(f.citizen(), field).equals(values.get(3 % values.size())), "Rapid steps accumulate from server state: " + field);
            for (int click = 0; click < 2; click++) CitizenActionPacket.step(f.menu(), field, -1).handle(f.player());
            helper.assertTrue(appearanceOption(f.citizen(), field).equals(values.get(1)), "Upward wheel reverses exactly one step: " + field);
            for (int click = 0; click < values.size(); click++) CitizenActionPacket.step(f.menu(), field, 1).handle(f.player());
            helper.assertTrue(appearanceOption(f.citizen(), field).equals(values.get(1)), "Full cycle visits every option and wraps: " + field);
            CitizenActionPacket.select(f.menu(), field, values.get(0)).handle(f.player());
            CitizenActionPacket.step(f.menu(), field, -1).handle(f.player());
            helper.assertTrue(appearanceOption(f.citizen(), field).equals(values.get(values.size() - 1)), "Negative step wraps at start: " + field);
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void appearanceWheelRejectsInvalidDeltasAndHiddenTabsAndHandlesLegacyColors(GameTestHelper helper) {
        var f = fixture(helper);
        CitizenActionPacket.tab(f.menu(), CitizenMenu.Tab.APPEARANCE).handle(f.player());
        var before = f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup());
        for (int delta : new int[]{0, 2, -2, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            CitizenActionPacket.step(f.menu(), CitizenActionPacket.Action.HAIR_COLOR, delta).handle(f.player());
        }
        new CitizenActionPacket(19, f.citizen().getId(), CitizenActionPacket.Action.HAIR_COLOR, 1, "", "navy").handle(f.player());
        new CitizenActionPacket(20, f.citizen().getId(), CitizenActionPacket.Action.HAIR_COLOR, 1, "", "").handle(f.player());
        helper.assertTrue(before.equals(f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup())), "Invalid delta, mixed absolute value and stale menu rejected");
        for (var tab : List.of(CitizenMenu.Tab.GARMENT, CitizenMenu.Tab.BEHAVIOR)) {
            CitizenActionPacket.tab(f.menu(), tab).handle(f.player());
            CitizenActionPacket.step(f.menu(), CitizenActionPacket.Action.HAIR_COLOR, 1).handle(f.player());
            helper.assertTrue(before.equals(f.citizen().citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup())), "Hidden appearance buttons cannot change values");
        }
        CitizenActionPacket.tab(f.menu(), CitizenMenu.Tab.APPEARANCE).handle(f.player());
        for (int direction : new int[]{1, -1}) {
            var appearance = f.citizen().citizenState().appearance();
            f.citizen().citizenState().setAppearance(new com.rexhub.createapparel.entity.CitizenAppearance(appearance.skinTone(),
                    appearance.armProfile(), appearance.chestProfile(), appearance.hairStyleId(), 0x123456, appearance.eyeColorRgb()));
            CitizenActionPacket.step(f.menu(), CitizenActionPacket.Action.HAIR_COLOR, direction).handle(f.player());
            helper.assertTrue(f.citizen().citizenState().appearance().hairColorRgb()
                    == (direction > 0 ? FabricColor.WHITE : FabricColor.PLUM).tintRgb(), "Legacy custom RGB enters palette at requested edge");
        }
        helper.succeed();
    }

    private static String appearanceOption(Citizen citizen, CitizenActionPacket.Action action) {
        var appearance = citizen.citizenState().appearance();
        return switch (action) {
            case ARM_PROFILE -> appearance.armProfile().getSerializedName();
            case CHEST_PROFILE -> appearance.chestProfile().getSerializedName();
            case SKIN_TONE -> appearance.skinTone().getSerializedName();
            case HAIR_STYLE -> appearance.hairStyleId().toString();
            case HAIR_COLOR, EYE_COLOR -> java.util.Arrays.stream(FabricColor.values())
                    .filter(color -> color.tintRgb() == (action == CitizenActionPacket.Action.HAIR_COLOR ? appearance.hairColorRgb() : appearance.eyeColorRgb()))
                    .findFirst().orElseThrow().getSerializedName();
            default -> throw new IllegalArgumentException("Not an appearance field: " + action);
        };
    }
}
