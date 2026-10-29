package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.init.GarmentCreativePresets;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModRegistries;
import com.rexhub.createapparel.init.ModTags;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import com.rexhub.createapparel.villager.ModVillagers;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TailorGameTests {
    private static final ResourceLocation RECYCLING = ResourceLocation.parse("createapparel:crushing/garments_to_old_cloth");
    private static final List<Item> THREADS = List.of(ModItems.COTTON_THREAD.get(), ModItems.LINEN_THREAD.get(),
            ModItems.HEMP_THREAD.get(), ModItems.WOOL_THREAD.get(), ModItems.SILK_THREAD.get(), ModItems.SYNTHETIC_THREAD.get());
    private static final List<Item> FABRICS = java.util.Arrays.stream(FabricMaterial.values())
            .map(material -> ModItems.undyedFabric(material).get()).toList();

    private TailorGameTests() { }

    @GameTest(template = "apparel_state", batch = "tailor")
    public static void recyclingTagExactlyTracksGarmentsAndIgnoresNbt(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(RECYCLING).orElseThrow();
        helper.assertTrue(recipe.getType() == AllRecipeTypes.CRUSHING.getType(), "Native Create crushing recipe");
        var crushing = (com.simibubi.create.content.kinetics.crusher.CrushingRecipe) recipe;
        helper.assertTrue(crushing.getProcessingDuration() == 100, "100 tick duration");
        for (var entry : ModRegistries.ITEMS.getEntries()) {
            var stack = new ItemStack(entry.get());
            boolean recyclable = entry.get() instanceof GarmentItem;
            helper.assertTrue(stack.is(ModTags.Items.RECYCLABLE_GARMENTS) == recyclable, "Exact recyclable tag: " + entry.getId());
            var inventory = new ItemStackHandler(1);
            inventory.setStackInSlot(0, stack);
            var wrapper = new RecipeWrapper(inventory);
            helper.assertTrue(crushing.matches(wrapper, helper.getLevel()) == recyclable, "Exact crushing input: " + entry.getId());
            if (!recyclable) continue;
            for (var input : List.of(stack, GarmentCreativePresets.create(entry))) {
                inventory.setStackInSlot(0, input.copy());
                inventory.getStackInSlot(0).getOrCreateTag().putString("RecyclingTest", "discard");
                helper.assertTrue(crushing.matches(wrapper, helper.getLevel()), "NBT does not restrict crushing");
                var outputs = crushing.rollResults();
                helper.assertTrue(outputs.size() == 1 && outputs.get(0).is(ModItems.OLD_CLOTH.get())
                        && outputs.get(0).getCount() == 1 && !outputs.get(0).hasTag(), "One clean scrap at 100% chance");
                helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(
                        AllRecipeTypes.MILLING.getType(), wrapper, helper.getLevel()).isEmpty(), "No millstone recycling");
            }
        }
        helper.assertTrue(new ItemStack(ModItems.OLD_CLOTH.get()).getMaxStackSize() == 64, "Scraps stack to 64");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "tailor")
    public static void nativeCrusherConsumesGarmentsAndProducesCleanScraps(GameTestHelper helper) {
        var pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, AllBlocks.CRUSHING_WHEEL_CONTROLLER.getDefaultState());
        var crusher = (CrushingWheelControllerBlockEntity) helper.getBlockEntity(pos);
        crusher.crushingspeed = 4;
        for (var garment : List.of(ModItems.T_SHIRT, ModItems.TRENCH_COAT, ModItems.SNEAKERS)) {
            var stack = GarmentCreativePresets.create(garment);
            stack.getOrCreateTag().putString("OwnerHistory", "discard");
            helper.assertTrue(crusher.inventory.insertItem(0, stack, false).isEmpty(), "Crusher accepts garment");
            helper.assertTrue(crusher.findRecipe().orElseThrow().getId().equals(RECYCLING), "Native recipe selection");
            for (int tick = 0; tick < 100 && !crusher.inventory.appliedRecipe; tick++) crusher.tick();
            helper.assertTrue(crusher.inventory.appliedRecipe, "Native crusher applied its recipe");
            helper.assertTrue(crusher.inventory.getStackInSlot(0).isEmpty(), "Garment consumed");
            var result = crusher.inventory.getStackInSlot(1);
            helper.assertTrue(result.is(ModItems.OLD_CLOTH.get()) && result.getCount() == 1 && !result.hasTag(), "Native clean output");
            crusher.inventory.clear();
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "tailor")
    public static void everySewingStateIsAClaimableTailorPoi(GameTestHelper helper) {
        var poi = ModVillagers.TAILOR_POI.get();
        var pos = new BlockPos(1, 2, 1);
        for (var state : ModBlocks.SEWING_MACHINE.get().getStateDefinition().getPossibleStates()) {
            helper.assertTrue(PoiTypes.forState(state).orElseThrow().is(ModVillagers.TAILOR_POI.getKey()), "Forge POI state map");
            helper.setBlock(pos, state);
            var holder = helper.getLevel().getPoiManager().getType(helper.absolutePos(pos)).orElseThrow();
            helper.assertTrue(holder.value() == poi, "Placed sewing machine creates POI");
            helper.assertTrue(VillagerProfession.NONE.acquirableJobSite().test(holder), "Unemployed villagers can acquire POI");
            helper.assertTrue(ModVillagers.TAILOR.get().heldJobSite().test(holder)
                    && ModVillagers.TAILOR.get().acquirableJobSite().test(holder), "Tailor holds and reacquires sewing machine");
            helper.assertTrue(helper.getBlockEntity(pos) instanceof com.rexhub.createapparel.block.entity.SewingMachineBlockEntity,
                    "Sewing machine entity preserved");
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "tailor", timeoutTicks = 600)
    public static void unemployedAdultFindsSewingMachineThroughVanillaAi(GameTestHelper helper) {
        GameTestFixtures.dryRoom(helper, 0, 0, 7);
        var machine = new BlockPos(3, 1, 3);
        helper.setBlock(machine, ModBlocks.SEWING_MACHINE.get().defaultBlockState());
        var villager = new Villager(EntityType.VILLAGER, helper.getLevel());
        villager.setAge(0);
        villager.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 3))));
        helper.getLevel().addFreshEntity(villager);
        villager.refreshBrain(helper.getLevel());
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.TAILOR.get(), "Vanilla AI acquired Tailor");
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElseThrow().pos()
                    .equals(helper.absolutePos(machine)), "Vanilla job site memory");
            helper.assertTrue(villager.getOffers().size() == 2, "Novice has two real offers");
        });
    }

    @GameTest(template = "apparel_state", batch = "tailor")
    public static void offersHaveExactPoolsCountsExperienceAndIndependentSelection(GameTestHelper helper) {
        var listings = VillagerTrades.TRADES.get(ModVillagers.TAILOR.get());
        helper.assertTrue(listings != null && listings.size() == 5, "Five trade levels");
        var trader = new Villager(EntityType.VILLAGER, helper.getLevel());
        var random = RandomSource.create(94712);
        var xp = List.of(2, 5, 10, 15, 20);
        for (int level = 1; level <= 5; level++) {
            var pair = listings.get(level);
            helper.assertTrue(pair.length == 2, "One buying and one selling listing per level");
            Set<Item> bought = new HashSet<>(), sold = new HashSet<>();
            int[] threadCount = new int[2];
            boolean buyThreadSellFabric = false, buyFabricSellThread = false;
            for (int sample = 0; sample < 4096; sample++) {
                var buy = pair[0].getOffer(trader, random);
                var sell = pair[1].getOffer(trader, random);
                checkOffer(helper, buy, true, level, xp.get(level - 1));
                checkOffer(helper, sell, false, level, xp.get(level - 1));
                var buyItem = buy.getBaseCostA().getItem();
                var sellItem = sell.getResult().getItem();
                bought.add(buyItem);
                sold.add(sellItem);
                boolean buyThread = THREADS.contains(buyItem), sellThread = THREADS.contains(sellItem);
                if (buyThread) threadCount[0]++;
                if (sellThread) threadCount[1]++;
                buyThreadSellFabric |= buyThread && !sellThread;
                buyFabricSellThread |= !buyThread && sellThread;
            }
            Set<Item> materials = new HashSet<>(THREADS);
            materials.addAll(FABRICS);
            helper.assertTrue(bought.equals(level == 1 ? Set.of(ModItems.OLD_CLOTH.get()) : materials), "Exact buy whitelist");
            helper.assertTrue(sold.equals(level == 1 ? Set.copyOf(THREADS) : materials), "Exact sell whitelist");
            if (level > 1) {
                helper.assertTrue(threadCount[0] > 1850 && threadCount[0] < 2250
                        && threadCount[1] > 1850 && threadCount[1] < 2250, "50% category selection");
                helper.assertTrue(buyThreadSellFabric && buyFabricSellThread, "Independent category draws");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "tailor")
    public static void tenVanillaOffersPersistAcrossRestockAndEntityReload(GameTestHelper helper) {
        var villager = new AdvancingVillager(helper.getLevel());
        villager.setVillagerData(villager.getVillagerData().setProfession(ModVillagers.TAILOR.get()));
        helper.assertTrue(villager.getOffers().size() == 2, "Two novice offers");
        for (int level = 2; level <= 5; level++) villager.advance();
        helper.assertTrue(villager.getOffers().size() == 10, "Ten offers at master");
        var before = villager.getOffers().stream().map(offer -> List.of(offer.getBaseCostA().copy(), offer.getResult().copy())).toList();
        for (var offer : villager.getOffers()) for (int use = 0; use < offer.getMaxUses(); use++) offer.increaseUses();
        villager.restock();
        for (int index = 0; index < 10; index++) {
            var offer = villager.getOffers().get(index);
            helper.assertTrue(offer.getUses() == 0, "Vanilla restock restores uses");
            helper.assertTrue(ItemStack.matches(before.get(index).get(0), offer.getBaseCostA())
                    && ItemStack.matches(before.get(index).get(1), offer.getResult()), "Restock never rerolls items");
        }
        var saved = villager.saveWithoutId(new CompoundTag());
        var restored = new Villager(EntityType.VILLAGER, helper.getLevel());
        restored.load(saved);
        helper.assertTrue(restored.getVillagerData().getProfession() == ModVillagers.TAILOR.get()
                && restored.getVillagerData().getLevel() == 5, "Profession and level persist");
        helper.assertTrue(restored.getOffers().createTag().equals(villager.getOffers().createTag()), "Exact persistent offers");
        helper.assertTrue(restored.getDisplayName().getString().equals("Tailor"), "Forge profession translation key");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "tailor")
    public static void purchasedMaterialsCannotProfitThroughRecyclingAtBasePrices(GameTestHelper helper) {
        var trader = new Villager(EntityType.VILLAGER, helper.getLevel());
        var random = RandomSource.create(81624);
        var listings = VillagerTrades.TRADES.get(ModVillagers.TAILOR.get());
        var recyclingOffer = listings.get(1)[0].getOffer(trader, random);
        int clothPerEmerald = recyclingOffer.getBaseCostA().getCount();
        helper.assertTrue(clothPerEmerald == 2 && recyclingOffer.getResult().getCount() == 1, "Two scraps buy one emerald");
        var sales = new java.util.HashMap<Item, Integer>();
        var purchases = new java.util.HashMap<Item, Integer>();
        for (int draw = 0; draw < 2048; draw++) {
            var sale = listings.get(2)[1].getOffer(trader, random);
            sales.put(sale.getResult().getItem(), sale.getResult().getCount());
            var purchase = listings.get(2)[0].getOffer(trader, random);
            purchases.put(purchase.getBaseCostA().getItem(), purchase.getBaseCostA().getCount());
        }
        helper.assertTrue(sales.size() == 13 && purchases.keySet().equals(sales.keySet()), "All traded materials covered");
        for (var item : sales.keySet()) {
            helper.assertTrue(sales.get(item) <= purchases.get(item), "Buying then reselling cannot create emeralds");
        }
        int threadPerEmerald = THREADS.stream().mapToInt(sales::get).max().orElseThrow();
        int fabricPerEmerald = FABRICS.stream().mapToInt(sales::get).max().orElseThrow();
        int denominator = threadPerEmerald * fabricPerEmerald;
        var recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(com.rexhub.createapparel.recipe.SewingRecipe.recipeType());
        helper.assertTrue(!recipes.isEmpty(), "Loaded production recipes");
        for (var recipe : recipes) {
            int materialCost = recipe.getFabricCount() * threadPerEmerald + recipe.getThreadCount() * fabricPerEmerald;
            helper.assertTrue(materialCost * clothPerEmerald >= denominator,
                    "Purchased sewing inputs cost at least the recycled result: " + recipe.getId());
        }
        helper.assertTrue(4 / clothPerEmerald < 2 + 1, "Four gloves return two emeralds for three spent on fabric and thread");
        helper.succeed();
    }

    private static void checkOffer(GameTestHelper helper, MerchantOffer offer, boolean buying, int level, int xp) {
        var material = buying ? offer.getBaseCostA() : offer.getResult();
        var emerald = buying ? offer.getResult() : offer.getBaseCostA();
        boolean oldCloth = buying && level == 1;
        boolean thread = THREADS.contains(material.getItem());
        helper.assertTrue(oldCloth ? material.is(ModItems.OLD_CLOTH.get()) : thread || FABRICS.contains(material.getItem()), "Whitelisted material");
        helper.assertTrue(material.getCount() == (oldCloth ? 2 : thread ? (buying ? 8 : 4) : buying ? 4 : 2), "Base material quantity");
        helper.assertTrue(emerald.is(Items.EMERALD) && emerald.getCount() == 1 && offer.getCostB().isEmpty(), "One emerald, no second cost");
        helper.assertTrue(offer.getMaxUses() == (oldCloth ? 16 : 12) && offer.getXp() == xp
                && offer.getPriceMultiplier() == 0.05F, "Uses, experience and vanilla pricing");
        helper.assertTrue(!material.hasTag(), "Trades use plain item stacks");
    }

    private static final class AdvancingVillager extends Villager {
        private AdvancingVillager(ServerLevel level) {
            super(EntityType.VILLAGER, level);
        }

        private void advance() {
            setVillagerData(getVillagerData().setLevel(getVillagerData().getLevel() + 1));
            updateTrades();
        }
    }
}
