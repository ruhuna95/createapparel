package com.rexhub.createapparel.compat.jei;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.GarmentCreativePresets;
import com.rexhub.createapparel.init.ModRegistries;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.client.event.ScreenEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.rexhub.createapparel.compat.jei.JeiClientTestLauncher.check;

@JeiPlugin
public final class JeiClientChecks implements IModPlugin {
    private static IJeiRuntime runtime;
    private static int generation;
    private static int stage;
    private static int delay;
    private static int capture;
    private static int reloadGeneration;
    private static List<Capture> captures = List.of();
    private static volatile boolean reloaded;
    private static boolean captureReady;
    private static final List<RecipeType<?>> TYPES = List.of(ApparelJeiRecipeTypes.SPINNING,
            ApparelJeiRecipeTypes.WEAVING, ApparelJeiRecipeTypes.SEWING, ApparelJeiRecipeTypes.SILKWORM_REARING);

    @Override public ResourceLocation getPluginUid() { return id("client_test"); }
    @Override public void onRuntimeAvailable(IJeiRuntime available) { runtime = available; generation++; }
    @Override public void onRuntimeUnavailable() { runtime = null; }

    static void tick() throws Exception {
        if (runtime == null || Minecraft.getInstance().getOverlay() != null || ++delay < 30) return;
        delay = 0;
        var mc = Minecraft.getInstance();
        if (stage == 0) {
            audit();
            captures = List.of(new Capture("spinning-en", ApparelJeiRecipeTypes.SPINNING, "en_us", 2),
                    new Capture("weaving-en", ApparelJeiRecipeTypes.WEAVING, "en_us", 2),
                    new Capture("sewing-en", ApparelJeiRecipeTypes.SEWING, "en_us", 2),
                    new Capture("rearing-en", ApparelJeiRecipeTypes.SILKWORM_REARING, "en_us", 2),
                    new Capture("silkworm-en", ApparelJeiRecipeTypes.SILKWORM_REARING, "en_us", 2, true),
                    new Capture("spinning-ja", ApparelJeiRecipeTypes.SPINNING, "ja_jp", 2),
                    new Capture("weaving-ja", ApparelJeiRecipeTypes.WEAVING, "ja_jp", 2),
                    new Capture("sewing-ja", ApparelJeiRecipeTypes.SEWING, "ja_jp", 2),
                    new Capture("rearing-ja", ApparelJeiRecipeTypes.SILKWORM_REARING, "ja_jp", 2),
                    new Capture("silkworm-ja", ApparelJeiRecipeTypes.SILKWORM_REARING, "ja_jp", 2, true),
                    new Capture("sewing-ja-scale1", ApparelJeiRecipeTypes.SEWING, "ja_jp", 1),
                    new Capture("sewing-ja-scale3", ApparelJeiRecipeTypes.SEWING, "ja_jp", 3));
            stage = 1;
            showCapture();
        } else if (stage == 2) {
            check(mc.screen == (Object) runtime.getRecipesGui(), "JEI screen opened");
            captureReady = true;
        } else if (stage == 6) {
            if (++capture < captures.size()) { stage = 1; showCapture(); }
            else { stage = 3; reloadPack(); }
        } else if (stage == 4 && generation > reloadGeneration && reloaded) {
            auditReload();
            stage = 5;
            JeiClientTestLauncher.finish("JEI");
        }
    }

    static void render(ScreenEvent.Render.Post event) {
        var mc = Minecraft.getInstance();
        if (!captureReady || mc.getOverlay() != null || event.getScreen() != mc.screen) return;
        Screenshot.grab(JeiClientTestLauncher.root().resolve("build/phase23").toFile(),
                captures.get(capture).name + ".png", mc.getMainRenderTarget(), message -> { });
        captureReady = false;
        stage = 6;
    }

    private static void showCapture() {
        var mc = Minecraft.getInstance();
        var next = captures.get(capture);
        mc.options.guiScale().set(next.scale);
        mc.resizeDisplay();
        if (mc.getLanguageManager().getSelected().equals(next.language)) {
            openCapture(next);
            return;
        }
        mc.getLanguageManager().setSelected(next.language);
        mc.options.languageCode = next.language;
        mc.reloadResourcePacks().thenRun(() -> mc.execute(() -> {
            openCapture(next);
        }));
    }

    private static void openCapture(Capture capture) {
        if (capture.type == ApparelJeiRecipeTypes.SILKWORM_REARING) {
            var recipe = runtime.getRecipeManager().createRecipeLookup(ApparelJeiRecipeTypes.SILKWORM_REARING).get()
                    .filter(display -> display.tray() != capture.silkworm).findFirst().orElseThrow();
            runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(ApparelJeiRecipeTypes.SILKWORM_REARING),
                    List.of(recipe), List.of());
        } else {
            runtime.getRecipesGui().showTypes(List.of(capture.type));
        }
        stage = 2;
    }

    private static void audit() throws Exception {
        var manager = runtime.getRecipeManager();
        var mc = Minecraft.getInstance();
        var rows = new ArrayList<String>();
        for (var type : TYPES) {
            var category = manager.getRecipeCategory(type);
            check(!category.getTitle().getString().startsWith("jei."), "Translated title");
            long count = manager.createRecipeLookup(type).get().count();
            long expected = mc.level.getRecipeManager().getRecipes().stream()
                    .filter(recipe -> recipe.getType().toString().equals(type.getUid().toString())).count();
            if (type == ApparelJeiRecipeTypes.SILKWORM_REARING) expected++;
            check(count > 0 && count == expected, "Recipe count " + type + ": " + count + "/" + expected);
            check(manager.createRecipeCatalystLookup(type).get().count() == 1, "One machine catalyst");
            rows.add(type.getUid() + "\t" + count);
            LogUtils.getLogger().info("PHASE23_CATEGORY {} {}", type.getUid(), count);
        }
        List<SewingRecipeDisplay> sewing = manager.createRecipeLookup(ApparelJeiRecipeTypes.SEWING).get().toList();
        var factory = new SewingRecipeDisplay.Factory(ClientGarmentAssets::getDefinition);
        for (var display : sewing) {
            var recipe = display.recipe();
            var rebuilt = factory.create(recipe).orElseThrow();
            check(rebuilt.fabrics().size() == display.fabrics().size(), "Stable cached candidate count");
            var creativeEntry = ModRegistries.ITEMS.getEntries().stream()
                    .filter(entry -> entry.getId().equals(recipe.getResultId())).findFirst().orElseThrow();
            check(ItemStack.matches(GarmentCreativePresets.create(creativeEntry), display.result()), "Creative garment preview");
            var patternBefore = display.pattern().save(new net.minecraft.nbt.CompoundTag());
            for (var stack : display.fabrics()) lookup(ApparelJeiRecipeTypes.SEWING, RecipeIngredientRole.INPUT, stack, recipe.getId());
            for (var stack : display.threads()) lookup(ApparelJeiRecipeTypes.SEWING, RecipeIngredientRole.INPUT, stack, recipe.getId());
            lookup(ApparelJeiRecipeTypes.SEWING, RecipeIngredientRole.CATALYST, display.pattern(), recipe.getId());
            lookup(ApparelJeiRecipeTypes.SEWING, RecipeIngredientRole.CATALYST, new ItemStack(ModItems.SEWING_PATTERN.get()), recipe.getId());
            var output = display.result().copy();
            var definition = ClientGarmentAssets.getDefinition(recipe.getResultId()).orElseThrow();
            new GarmentData(FabricMaterial.SYNTHETIC, definition.designs().iterator().next(), FabricColor.NAVY,
                    Optional.of(FabricColor.RED), Optional.of(FabricColor.YELLOW)).write(output);
            lookup(ApparelJeiRecipeTypes.SEWING, RecipeIngredientRole.OUTPUT, output, recipe.getId());
            check(display.pattern().save(new net.minecraft.nbt.CompoundTag()).equals(patternBefore), "Pattern NBT unchanged");
            var layout = manager.createRecipeLayoutDrawable(manager.getRecipeCategory(ApparelJeiRecipeTypes.SEWING),
                    display, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
            check(layout.getRecipeSlotsView().getSlotViews().size() == 4, "Sewing has exactly four slots");
        }
        for (var type : List.of(ApparelJeiRecipeTypes.SPINNING, ApparelJeiRecipeTypes.WEAVING)) {
            for (var display : manager.createRecipeLookup(type).get().toList()) {
                for (var stack : display.inputs()) lookup(type, RecipeIngredientRole.INPUT, stack, display.recipe().getId());
                lookup(type, RecipeIngredientRole.OUTPUT, display.result(), display.recipe().getId());
            }
        }
        for (var display : manager.createRecipeLookup(ApparelJeiRecipeTypes.SILKWORM_REARING).get().toList()) {
            for (var stack : display.inputs()) lookup(ApparelJeiRecipeTypes.SILKWORM_REARING, RecipeIngredientRole.INPUT, stack, display.id());
            lookup(ApparelJeiRecipeTypes.SILKWORM_REARING, RecipeIngredientRole.OUTPUT, display.result(), display.id());
            var layout = manager.createRecipeLayoutDrawable(manager.getRecipeCategory(ApparelJeiRecipeTypes.SILKWORM_REARING),
                    display, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
            check(layout.getRecipeSlotsView().getSlotViews().size() == (display.tray() ? 2 : 3), "Rearing slots and non-consumed shears");
            if (!display.tray()) lookup(ApparelJeiRecipeTypes.SILKWORM_REARING, RecipeIngredientRole.CATALYST,
                    new ItemStack(Items.SHEARS), display.id());
        }
        var mixing = manager.getRecipeType(ResourceLocation.parse("create:mixing"), BasinRecipe.class).orElseThrow();
        var clientRecipes = Minecraft.getInstance().level.getRecipeManager();
        for (String path : List.of("mixing/synthetic_fiber", "mixing/mirror_coating_fluid", "mixing/mirror_coating_fluid_from_destroy")) {
            check(manager.createRecipeLookup(mixing).get().anyMatch(recipe -> recipe.getId().equals(id(path)))
                            == clientRecipes.byKey(id(path)).isPresent(), "Optional mixing follows synchronized recipes: " + path);
        }
        clientRecipes.byKey(id("mixing/mirror_coating_fluid_from_destroy")).ifPresent(recipe -> {
            var layout = manager.createRecipeLayoutDrawable(manager.getRecipeCategory(mixing), (BasinRecipe) recipe,
                    runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
            check(layout.getRecipeSlotsView().getSlotViews().size() >= 3, "Native Destroy coating mixing layout");
        });
        if (net.minecraftforge.fml.ModList.get().isLoaded("destroy")) DestroyJeiChecks.audit(runtime);
        var fiberFocus = runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT,
                VanillaTypes.ITEM_STACK, new ItemStack(ModItems.SYNTHETIC_FIBER.get()));
        check(manager.createRecipeLookup(mixing).limitFocus(List.of(fiberFocus)).get()
                        .anyMatch(recipe -> recipe.getId().equals(id("mixing/synthetic_fiber")))
                        == clientRecipes.byKey(id("mixing/synthetic_fiber")).isPresent(), "Fiber recipe lookup is exclusive");
        check(manager.createRecipeLookup(RecipeTypes.CRAFTING).limitFocus(List.of(fiberFocus)).get()
                        .anyMatch(recipe -> recipe.getId().equals(id("synthetic_fiber_from_nylon")))
                        == clientRecipes.byKey(id("synthetic_fiber_from_nylon")).isPresent(), "Nylon recipe lookup is exclusive");
        for (String recipe : List.of("mixing/dyeing/blue_cotton_fabric", "mixing/dyeing/navy_cotton_fabric", "mixing/decolorizing/cotton_fabric")) {
            var actual = manager.createRecipeLookup(mixing).get().filter(value -> value.getId().equals(id(recipe)))
                    .findFirst().orElseThrow();
            check(actual.getFluidIngredients().size() == 1 && actual.getFluidIngredients().get(0).getRequiredAmount() == 250,
                    "Native mixing water quantity: " + recipe);
            var layout = manager.createRecipeLayoutDrawable(manager.getRecipeCategory(mixing), actual,
                    runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
            check(layout.getRecipeSlotsView().getSlotViews().size() >= 3, "Native mixing slots: " + recipe);
        }
        long dyeing = manager.createRecipeLookup(mixing).get().filter(recipe -> recipe.getId().getNamespace().equals("createapparel")
                && recipe.getId().getPath().startsWith("mixing/dyeing/")).count();
        check(dyeing == 231, "All 7 x 33 dyeing recipes");
        for (var color : FabricColor.values()) if (!color.isVanilla()) {
            String path = "apparel_dyes/" + color.getSerializedName() + "_dye";
            check(manager.createRecipeLookup(RecipeTypes.CRAFTING).get().anyMatch(recipe -> recipe.getId().equals(id(path))), "Native crafting: " + path);
        }
        check(manager.getRecipeType(id("fabric_dyeing")).isEmpty(), "No duplicate dyeing category");
        Files.write(JeiClientTestLauncher.root().resolve("build/phase23/category-counts.tsv"), rows);
        LogUtils.getLogger().info("PHASE23_LOOKUPS_AND_NATIVE_RECIPES_PASSED");
    }

    private static <T> void lookup(RecipeType<T> type, RecipeIngredientRole role, ItemStack stack, ResourceLocation id) {
        var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(role, VanillaTypes.ITEM_STACK, stack);
        check(runtime.getRecipeManager().createRecipeLookup(type).limitFocus(List.of(focus)).get().anyMatch(recipe -> {
            if (recipe instanceof SewingRecipeDisplay sewing) return sewing.recipe().getId().equals(id);
            if (recipe instanceof SilkwormRearingDisplay rearing) return rearing.id().equals(id);
            return ((ProcessingRecipeDisplay) recipe).recipe().getId().equals(id);
        }), "JEI reverse lookup: " + id + " / " + stack);
    }

    private static void reloadPack() throws Exception {
        var mc = Minecraft.getInstance();
        mc.setScreen(null);
        reloadGeneration = generation;
        stage = 4;
        var server = mc.getSingleplayerServer();
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("phase23_test");
        write(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":15,\"description\":\"Phase23 test\"}}");
        write(pack.resolve("data/createapparel/recipes/phase23_test.json"),
                "{\"type\":\"createapparel:spinning\",\"ingredients\":[{\"item\":\"minecraft:stick\"}],\"results\":[{\"item\":\"createapparel:cotton_thread\",\"count\":3}],\"processingTime\":37}");
        write(pack.resolve("data/createapparel/tags/items/fabrics/cotton.json"),
                "{\"replace\":true,\"values\":[\"createapparel:white_cotton_fabric\"]}");
        write(pack.resolve("data/createapparel/recipes/spinning/cotton_thread.json"),
                "{\"conditions\":[{\"type\":\"forge:false\"}],\"type\":\"createapparel:spinning\"}");
        server.execute(() -> {
            server.getPackRepository().reload();
            var packs = new ArrayList<>(server.getPackRepository().getSelectedIds());
            packs.add("file/phase23_test");
            server.reloadResources(packs).thenRun(() -> reloaded = true);
        });
    }

    private static void auditReload() {
        var manager = runtime.getRecipeManager();
        if (net.minecraftforge.fml.ModList.get().isLoaded("destroy")) DestroyJeiChecks.audit(runtime);
        var spinning = manager.createRecipeLookup(ApparelJeiRecipeTypes.SPINNING).get().toList();
        check(spinning.stream().noneMatch(display -> display.recipe().getId().equals(id("spinning/cotton_thread"))), "Removed recipe disappears");
        var added = spinning.stream().filter(display -> display.recipe().getId().equals(id("phase23_test"))).findFirst().orElseThrow();
        check(added.duration() == 37 && added.result().getCount() == 3, "New recipe has updated data");
        lookup(ApparelJeiRecipeTypes.SPINNING, RecipeIngredientRole.INPUT, new ItemStack(Items.STICK), id("phase23_test"));
        var tshirt = manager.createRecipeLookup(ApparelJeiRecipeTypes.SEWING).get()
                .filter(display -> display.recipe().getId().equals(id("t_shirt"))).findFirst().orElseThrow();
        check(tshirt.fabrics().stream().anyMatch(stack -> stack.is(ModItems.dyedFabric(FabricMaterial.COTTON, FabricColor.WHITE).get())), "Retained tag member");
        check(tshirt.fabrics().stream().noneMatch(stack -> stack.is(ModItems.dyedFabric(FabricMaterial.COTTON, FabricColor.RED).get())), "Removed tag member");
        LogUtils.getLogger().info("PHASE23_DATAPACK_RELOAD_PASSED generation={}", generation);
    }

    private static void write(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("createapparel", path); }
    private record Capture(String name, RecipeType<?> type, String language, int scale, boolean silkworm) {
        private Capture(String name, RecipeType<?> type, String language, int scale) {
            this(name, type, language, scale, false);
        }
    }
}
