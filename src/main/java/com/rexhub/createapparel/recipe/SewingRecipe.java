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
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class SewingRecipe implements Recipe<RecipeWrapper> {
    public static final int PROCESSING_DURATION = 2000;
    public static final int FABRIC_SLOT = 0;
    public static final int THREAD_SLOT = 1;
    public static final int PATTERN_SLOT = 2;
    private static final int MAX_TAG_COUNT = 256;

    private final ResourceLocation id;
    private final ItemRequirement fabric;
    private final ItemRequirement thread;
    private final ResourceLocation patternItemId;
    private final ResourceLocation resultId;

    public SewingRecipe(
            ResourceLocation id,
            ItemRequirement fabric,
            ItemRequirement thread,
            ResourceLocation patternItemId,
            ResourceLocation resultId
    ) {
        this.id = id;
        this.fabric = fabric;
        this.thread = thread;
        this.patternItemId = patternItemId;
        this.resultId = resultId;
    }

    @Override
    public boolean matches(RecipeWrapper inventory, Level level) {
        if (inventory.getContainerSize() <= PATTERN_SLOT) {
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
                .map(data -> data.recipe().equals(id))
                .orElse(false);
    }

    @Override
    public ItemStack assemble(RecipeWrapper inventory, RegistryAccess registryAccess) {
        if (inventory.getContainerSize() <= PATTERN_SLOT) {
            return ItemStack.EMPTY;
        }
        FabricAttribute attribute = FabricAttributeMap.get(inventory.getItem(FABRIC_SLOT)).orElse(null);
        PatternData pattern = PatternData.fromStack(inventory.getItem(PATTERN_SLOT)).orElse(null);
        GarmentDefinition definition = GarmentDefinitionManager.get(resultId).orElse(null);
        Item resultItem = ForgeRegistries.ITEMS.getValue(resultId);
        if (attribute == null
                || attribute.color().isEmpty()
                || pattern == null
                || !pattern.recipe().equals(id)
                || definition == null
                || !definition.designs().contains(pattern.design())
                || !(resultItem instanceof GarmentItem)) {
            return ItemStack.EMPTY;
        }

        LinkedHashMap<String, String> styles = new LinkedHashMap<>();
        definition.styles().forEach((key, values) -> styles.put(key, values.get(0)));
        GarmentData garmentData = new GarmentData(
                attribute.material(),
                pattern.design(),
                attribute.color().orElseThrow(),
                pattern.secondaryColor(),
                pattern.accentColor(),
                styles
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
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        Item item = ForgeRegistries.ITEMS.getValue(resultId);
        return item instanceof GarmentItem ? new ItemStack(item) : ItemStack.EMPTY;
    }

    @Override
    public ResourceLocation getId() {
        return id;
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
        Item patternItem = ForgeRegistries.ITEMS.getValue(patternItemId);
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
        @Override
        public SewingRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            ItemRequirement fabric = readRequirement(GsonHelper.getAsJsonObject(json, "fabric"), "fabric");
            ItemRequirement thread = readRequirement(GsonHelper.getAsJsonObject(json, "thread"), "thread");
            ResourceLocation pattern = readItemId(GsonHelper.getAsString(json, "pattern"), "pattern");
            JsonObject resultJson = GsonHelper.getAsJsonObject(json, "result");
            ResourceLocation result = readItemId(GsonHelper.getAsString(resultJson, "id"), "result.id");
            if (!(ForgeRegistries.ITEMS.getValue(result) instanceof GarmentItem)) {
                throw new IllegalArgumentException("Sewing recipe result is not a GarmentItem: " + result);
            }
            return new SewingRecipe(recipeId, fabric, thread, pattern, result);
        }

        @Override
        public SewingRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            ItemRequirement fabric = readRequirement(buffer);
            ItemRequirement thread = readRequirement(buffer);
            ResourceLocation pattern = buffer.readResourceLocation();
            ResourceLocation result = buffer.readResourceLocation();
            return new SewingRecipe(recipeId, fabric, thread, pattern, result);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, SewingRecipe recipe) {
            writeRequirement(buffer, recipe.fabric);
            writeRequirement(buffer, recipe.thread);
            buffer.writeResourceLocation(recipe.patternItemId);
            buffer.writeResourceLocation(recipe.resultId);
        }

        private static ItemRequirement readRequirement(JsonObject json, String field) {
            JsonArray tagsJson = GsonHelper.getAsJsonArray(json, "tag");
            List<TagKey<Item>> tags = new ArrayList<>();
            for (JsonElement element : tagsJson) {
                String value = GsonHelper.convertToString(element, field + ".tag");
                ResourceLocation id = ResourceLocation.tryParse(value);
                if (id == null) {
                    throw new IllegalArgumentException("Invalid sewing recipe tag: " + value);
                }
                tags.add(TagKey.create(Registries.ITEM, id));
            }
            return new ItemRequirement(tags, GsonHelper.getAsInt(json, "count"));
        }

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

        private static ResourceLocation readItemId(String value, String field) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
                throw new IllegalArgumentException("Unknown item for " + field + ": " + value);
            }
            return id;
        }
    }
}
