package com.rexhub.createapparel.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.material.FabricAttribute;
import com.rexhub.createapparel.material.FabricAttributeMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;

public final class SewingRecipe implements Recipe<RecipeWrapper> {
    public static final int PROCESSING_DURATION = 2000;
    public static final int FABRIC_SLOT = 0;
    public static final int THREAD_SLOT = 1;
    public static final int PATTERN_SLOT = 2;
    private static final int MAX_TAG_COUNT = 256;

    private final ItemRequirement fabric;
    private final ItemRequirement thread;
    private final ResourceLocation patternItemId;
    private final ResourceLocation resultId;
    @javax.annotation.Nullable
    private ResourceLocation recipeId;

    public SewingRecipe(
            ItemRequirement fabric,
            ItemRequirement thread,
            ResourceLocation patternItemId,
            ResourceLocation resultId
    ) {
        this.fabric = fabric;
        this.thread = thread;
        this.patternItemId = patternItemId;
        this.resultId = resultId;
    }

    void bindId(ResourceLocation id) {
        recipeId = id;
    }

    @Override
    public boolean matches(RecipeWrapper inventory, Level level) {
        if (inventory.size() <= PATTERN_SLOT) {
            return false;
        }
        ItemStack fabricStack = inventory.getItem(FABRIC_SLOT);
        ItemStack threadStack = inventory.getItem(THREAD_SLOT);
        ItemStack patternStack = inventory.getItem(PATTERN_SLOT);
        return fabric.test(fabricStack)
                && FabricAttributeMap.isDyed(fabricStack)
                && thread.test(threadStack)
                && matchesPatternItem(patternStack)
                && PatternData.fromStack(patternStack)
                .map(data -> level.getRecipeManager().byKey(data.recipe()).filter(holder -> holder.value() == this).isPresent())
                .orElse(false);
    }

    @Override
    public ItemStack assemble(RecipeWrapper inventory, net.minecraft.core.HolderLookup.Provider registryAccess) {
        if (inventory.size() <= PATTERN_SLOT) {
            return ItemStack.EMPTY;
        }
        FabricAttribute attribute = FabricAttributeMap.get(inventory.getItem(FABRIC_SLOT)).orElse(null);
        PatternData pattern = PatternData.fromStack(inventory.getItem(PATTERN_SLOT)).orElse(null);
        GarmentDefinition definition = GarmentDefinitionManager.get(resultId).orElse(null);
        Item resultItem = BuiltInRegistries.ITEM.get(resultId);
        if (attribute == null
                || attribute.color().isEmpty()
                || pattern == null
                || !pattern.recipe().equals(recipeId)
                || definition == null
                || !definition.designs().contains(pattern.design())
                || !(resultItem instanceof GarmentItem)) {
            return ItemStack.EMPTY;
        }

        GarmentData garmentData = new GarmentData(
                attribute.material(),
                pattern.design(),
                attribute.color().orElseThrow(),
                pattern.secondaryColor(),
                pattern.accentColor()
        );
        ItemStack result = new ItemStack(resultItem);
        garmentData.write(result);
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(net.minecraft.core.HolderLookup.Provider registryAccess) {
        Item item = BuiltInRegistries.ITEM.get(resultId);
        return item instanceof GarmentItem ? new ItemStack(item) : ItemStack.EMPTY;
    }


    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.SEWING.getSerializer();
    }

    @Override
    public RecipeType<?> getType() {
        return recipeType();
    }

    @SuppressWarnings("unchecked")
    public static RecipeType<SewingRecipe> recipeType() {
        return (RecipeType<SewingRecipe>) (RecipeType<?>) ModRecipeTypes.SEWING.getType();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public int getFabricCount() {
        return fabric.count();
    }

    public List<TagKey<Item>> fabricTags() {
        return fabric.tags();
    }

    public List<TagKey<Item>> threadTags() {
        return thread.tags();
    }

    public int getThreadCount() {
        return thread.count();
    }

    public ResourceLocation getResultId() {
        return resultId;
    }

    public boolean matchesFabric(ItemStack stack) {
        return FabricAttributeMap.isDyed(stack) && fabric.matchesItem(stack);
    }

    public boolean matchesThread(ItemStack stack) {
        return thread.matchesItem(stack);
    }

    public boolean matchesPatternItem(ItemStack stack) {
        Item patternItem = BuiltInRegistries.ITEM.get(patternItemId);
        return patternItem != null && stack.is(patternItem);
    }

    public record ItemRequirement(List<TagKey<Item>> tags, int count) {
        public ItemRequirement {
            tags = List.copyOf(tags);
            if (tags.isEmpty()) {
                throw new IllegalArgumentException("Sewing recipe item tags must not be empty");
            }
            if (count <= 0) {
                throw new IllegalArgumentException("Sewing recipe item count must be positive");
            }
        }

        public boolean test(ItemStack stack) {
            return stack.getCount() >= count && matchesItem(stack);
        }

        public boolean matchesItem(ItemStack stack) {
            return !stack.isEmpty() && tags.stream().anyMatch(stack::is);
        }
    }

    public static final class Serializer implements RecipeSerializer<SewingRecipe> {
        private static final com.mojang.serialization.Codec<ResourceLocation> ITEM_ID = ResourceLocation.CODEC.validate(id ->
                BuiltInRegistries.ITEM.containsKey(id) ? com.mojang.serialization.DataResult.success(id)
                        : com.mojang.serialization.DataResult.error(() -> "Unknown sewing recipe item: " + id));
        private static final com.mojang.serialization.Codec<ResourceLocation> GARMENT_ID = ITEM_ID.validate(id ->
                BuiltInRegistries.ITEM.get(id) instanceof GarmentItem ? com.mojang.serialization.DataResult.success(id)
                        : com.mojang.serialization.DataResult.error(() -> "Sewing recipe result is not a GarmentItem: " + id));
        private static final com.mojang.serialization.Codec<ItemRequirement> REQUIREMENT_CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                        TagKey.codec(Registries.ITEM).listOf().validate(tags -> tags.isEmpty()
                                ? com.mojang.serialization.DataResult.error(() -> "Sewing recipe item tags must not be empty")
                                : com.mojang.serialization.DataResult.success(tags)).fieldOf("tag").forGetter(ItemRequirement::tags),
                        com.mojang.serialization.Codec.intRange(1, Integer.MAX_VALUE).fieldOf("count").forGetter(ItemRequirement::count)
                ).apply(instance, ItemRequirement::new));
        private static final com.mojang.serialization.MapCodec<SewingRecipe> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                        REQUIREMENT_CODEC.fieldOf("fabric").forGetter(recipe -> recipe.fabric),
                        REQUIREMENT_CODEC.fieldOf("thread").forGetter(recipe -> recipe.thread),
                        ITEM_ID.fieldOf("pattern").forGetter(recipe -> recipe.patternItemId),
                        GARMENT_ID.fieldOf("id").codec().fieldOf("result").forGetter(recipe -> recipe.resultId)
                ).apply(instance, SewingRecipe::new));
        private static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, SewingRecipe> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.of((buffer, recipe) -> {
                    writeRequirement(buffer, recipe.fabric);
                    writeRequirement(buffer, recipe.thread);
                    buffer.writeResourceLocation(recipe.patternItemId);
                    buffer.writeResourceLocation(recipe.resultId);
                }, buffer -> new SewingRecipe(readRequirement(buffer), readRequirement(buffer),
                        buffer.readResourceLocation(), buffer.readResourceLocation()));

        @Override public com.mojang.serialization.MapCodec<SewingRecipe> codec() { return CODEC; }
        @Override public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, SewingRecipe> streamCodec() { return STREAM_CODEC; }

        private static ItemRequirement readRequirement(FriendlyByteBuf buffer) {
            int tagCount = buffer.readVarInt();
            if (tagCount <= 0 || tagCount > MAX_TAG_COUNT) {
                throw new IllegalArgumentException("Invalid sewing recipe tag count: " + tagCount);
            }
            List<TagKey<Item>> tags = new ArrayList<>(tagCount);
            for (int index = 0; index < tagCount; index++) {
                tags.add(TagKey.create(Registries.ITEM, buffer.readResourceLocation()));
            }
            return new ItemRequirement(tags, buffer.readVarInt());
        }

        private static void writeRequirement(FriendlyByteBuf buffer, ItemRequirement requirement) {
            buffer.writeVarInt(requirement.tags().size());
            requirement.tags().forEach(tag -> buffer.writeResourceLocation(tag.location()));
            buffer.writeVarInt(requirement.count());
        }
    }
}
