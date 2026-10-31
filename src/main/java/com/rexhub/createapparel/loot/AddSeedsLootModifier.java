package com.rexhub.createapparel.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rexhub.createapparel.init.ModItems;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

public final class AddSeedsLootModifier extends LootModifier {
    public static final com.mojang.serialization.MapCodec<AddSeedsLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> codecStart(instance)
            .and(Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(modifier -> modifier.chance))
            .apply(instance, AddSeedsLootModifier::new));

    private final float chance;

    public AddSeedsLootModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (state != null && isSeedSource(state) && context.getRandom().nextFloat() < chance) {
            generatedLoot.add(new ItemStack(randomSeed(context)));
        }
        return generatedLoot;
    }

    private static boolean isSeedSource(BlockState state) {
        return state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN);
    }

    private static Item randomSeed(LootContext context) {
        return switch (context.getRandom().nextInt(3)) {
            case 0 -> ModItems.COTTON_SEEDS.get();
            case 1 -> ModItems.FLAX_SEEDS.get();
            default -> ModItems.HEMP_SEEDS.get();
        };
    }

    @Override
    public com.mojang.serialization.MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
