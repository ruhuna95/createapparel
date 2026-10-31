package com.rexhub.createapparel.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

public final class SilkwormRearingRecipe implements Recipe<RecipeWrapper> {
    private final Ingredient food;
    private final int foodCount;
    private final ItemStack result;
    private final int processingTime;

    public SilkwormRearingRecipe(Ingredient food, int foodCount,
                                ItemStack result, int processingTime) {
        if (food.isEmpty() || foodCount < 1 || foodCount > 64 || result.isEmpty()
                || result.getCount() > result.getMaxStackSize() || processingTime < 1) {
            throw new JsonSyntaxException("Invalid silkworm rearing recipe");
        }
        this.food = food;
        this.foodCount = foodCount;
        this.result = result.copy();
        this.processingTime = processingTime;
    }

    public int foodCount() { return foodCount; }

    public int processingTime() { return processingTime; }

    @Override
    public boolean matches(RecipeWrapper inventory, Level level) {
        return inventory.getItem(0).getCount() >= foodCount && food.test(inventory.getItem(0));
    }

    @Override
    public ItemStack assemble(RecipeWrapper inventory, net.minecraft.core.HolderLookup.Provider registries) { return result.copy(); }

    @Override
    public boolean canCraftInDimensions(int width, int height) { return true; }

    @Override
    public ItemStack getResultItem(net.minecraft.core.HolderLookup.Provider registries) { return result.copy(); }


    @Override
    public RecipeSerializer<?> getSerializer() { return ModRecipeTypes.SILKWORM_REARING.getSerializer(); }

    @Override
    public RecipeType<?> getType() { return ModRecipeTypes.SILKWORM_REARING.getType(); }

    @Override
    public boolean isSpecial() { return true; }

    @Override
    public net.minecraft.core.NonNullList<Ingredient> getIngredients() {
        return net.minecraft.core.NonNullList.of(Ingredient.EMPTY, food);
    }

    public static final class Serializer implements RecipeSerializer<SilkwormRearingRecipe> {
        private static final com.mojang.serialization.MapCodec<SilkwormRearingRecipe> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Ingredient.CODEC_NONEMPTY.fieldOf("food").forGetter(recipe -> recipe.food),
                        com.mojang.serialization.Codec.intRange(1, 64).optionalFieldOf("food_count", 1).forGetter(recipe -> recipe.foodCount),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
                        com.mojang.serialization.Codec.intRange(1, Integer.MAX_VALUE).fieldOf("processing_time").forGetter(recipe -> recipe.processingTime)
                ).apply(instance, SilkwormRearingRecipe::new));
        private static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, SilkwormRearingRecipe> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.of((buffer, recipe) -> {
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.food);
                    buffer.writeVarInt(recipe.foodCount);
                    ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
                    buffer.writeVarInt(recipe.processingTime);
                }, buffer -> new SilkwormRearingRecipe(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),
                        buffer.readVarInt(), ItemStack.STREAM_CODEC.decode(buffer), buffer.readVarInt()));

        @Override public com.mojang.serialization.MapCodec<SilkwormRearingRecipe> codec() { return CODEC; }
        @Override public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, SilkwormRearingRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
