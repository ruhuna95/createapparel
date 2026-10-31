package com.rexhub.createapparel.villager;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class TailorTrades {
    private static final int OLD_CLOTH_COUNT = 2;
    private static final int EMERALD_COUNT = 1;
    private static final int OLD_CLOTH_MAX_USES = 16;
    private static final int MATERIAL_MAX_USES = 12;
    private static final int THREAD_BUY_COUNT = 8;
    private static final int THREAD_SELL_COUNT = 4;
    private static final int FABRIC_BUY_COUNT = 4;
    private static final int FABRIC_SELL_COUNT = 2;
    private static final List<Integer> LEVEL_XP = List.of(2, 5, 10, 15, 20);
    private static final float PRICE_MULTIPLIER = 0.05F;
    private static final List<DeferredHolder<Item, Item>> THREADS = List.of(
            ModItems.COTTON_THREAD, ModItems.LINEN_THREAD, ModItems.HEMP_THREAD,
            ModItems.WOOL_THREAD, ModItems.SILK_THREAD, ModItems.SYNTHETIC_THREAD);
    private static final List<DeferredHolder<Item, Item>> FABRICS = java.util.Arrays.stream(FabricMaterial.values())
            .map(ModItems::undyedFabric).toList();

    private TailorTrades() { }

    @SubscribeEvent
    public static void addTrades(VillagerTradesEvent event) {
        if (event.getType() != ModVillagers.TAILOR.get()) return;
        for (int level = 1; level <= LEVEL_XP.size(); level++) {
            int xp = LEVEL_XP.get(level - 1);
            boolean novice = level == 1;
            var trades = event.getTrades().get(level);
            trades.add(novice ? (trader, random) -> new MerchantOffer(
                    new net.minecraft.world.item.trading.ItemCost(ModItems.OLD_CLOTH.get(), OLD_CLOTH_COUNT), new ItemStack(Items.EMERALD, EMERALD_COUNT),
                    OLD_CLOTH_MAX_USES, xp, PRICE_MULTIPLIER) : materialTrade(true, false, xp));
            trades.add(materialTrade(false, novice, xp));
        }
    }

    private static VillagerTrades.ItemListing materialTrade(boolean buying, boolean threadsOnly, int xp) {
        return (trader, random) -> {
            boolean thread = threadsOnly || random.nextBoolean();
            Item item = choose(thread ? THREADS : FABRICS, random);
            int count = thread ? (buying ? THREAD_BUY_COUNT : THREAD_SELL_COUNT)
                    : (buying ? FABRIC_BUY_COUNT : FABRIC_SELL_COUNT);
            var material = new ItemStack(item, count);
            var emerald = new ItemStack(Items.EMERALD, EMERALD_COUNT);
            return new MerchantOffer(buying ? new net.minecraft.world.item.trading.ItemCost(item, count) : new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, EMERALD_COUNT), buying ? emerald : material,
                    MATERIAL_MAX_USES, xp, PRICE_MULTIPLIER);
        };
    }

    private static Item choose(List<DeferredHolder<Item, Item>> candidates, RandomSource random) {
        return candidates.get(random.nextInt(candidates.size())).get();
    }
}
