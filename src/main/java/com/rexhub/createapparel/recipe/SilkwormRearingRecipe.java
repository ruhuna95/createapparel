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
import net.minecraftforge.items.wrapper.RecipeWrapper;

public final class SilkwormRearingRecipe implements Recipe<RecipeWrapper> {
    private final ResourceLocation id;
    private final Ingredient food;
    private final int foodCount;
    private final ItemStack result;
    private final int processingTime;

    public SilkwormRearingRecipe(ResourceLocation id, Ingredient food, int foodCount,
                                ItemStack result, int processingTime) {
        if (food.isEmpty() || foodCount < 1 || foodCount > 64 || result.isEmpty()
                || result.getCount() > result.getMaxStackSize() || processingTime < 1) {
            throw new JsonSyntaxException("Invalid silkworm rearing recipe: " + id);
        }
        this.id = id;
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
    public ItemStack assemble(RecipeWrapper inventory, RegistryAccess registries) { return result.copy(); }

    @Override
    public boolean canCraftInDimensions(int width, int height) { return true; }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) { return result.copy(); }

    @Override
    public ResourceLocation getId() { return id; }

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
        @Override
        public SilkwormRearingRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new SilkwormRearingRecipe(id, Ingredient.fromJson(json.get("food")),
                    GsonHelper.getAsInt(json, "food_count", 1),
                    ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result")),
                    GsonHelper.getAsInt(json, "processing_time"));
        }

        @Override
        public SilkwormRearingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new SilkwormRearingRecipe(id, Ingredient.fromNetwork(buffer), buffer.readVarInt(),
                    buffer.readItem(), buffer.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, SilkwormRearingRecipe recipe) {
            recipe.food.toNetwork(buffer);
            buffer.writeVarInt(recipe.foodCount);
            buffer.writeItem(recipe.result);
            buffer.writeVarInt(recipe.processingTime);
        }
    }
}
