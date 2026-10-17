package com.rexhub.createapparel.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.citizen.*;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenAge;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricColor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.*;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitizenConversionGameTests {
    private CitizenConversionGameTests() { }

    private static Villager villager(GameTestHelper helper, boolean baby) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        if (villager == null) throw new IllegalStateException("Villager factory failed");
        villager.setAge(baby ? -24000 : 0);
        villager.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        villager.setYRot(123);
        villager.setXRot(12);
        villager.setNoAi(true);
        villager.setNoGravity(true);
        villager.setCustomName(Component.literal("Original Villager"));
        villager.setCustomNameVisible(true);
        helper.getLevel().addFreshEntity(villager);
        return villager;
    }

    private static List<Citizen> nearby(GameTestHelper helper, Villager original) {
        return helper.getLevel().getEntitiesOfClass(Citizen.class, original.getBoundingBox().inflate(0.1));
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void phoneConvertsBothAgesAndHandsExactlyOnceWithoutConsumption(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        for (boolean baby : new boolean[]{false, true}) for (InteractionHand hand : InteractionHand.values()) {
            var original = villager(helper, baby);
            for (var existing : nearby(helper, original)) existing.discard();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            ItemStack phone = new ItemStack(ModItems.SMART_PHONE.get());
            player.setItemInHand(hand, phone);
            CompoundTag before = phone.save(new CompoundTag());
            var otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            var unrelated = new PlayerInteractEvent.EntityInteract(player, otherHand, original);
            SmartPhoneEvents.interact(unrelated);
            helper.assertTrue(!unrelated.isCanceled() && !original.isRemoved() && nearby(helper, original).isEmpty(),
                    "Phone in the other hand does not intercept this interaction");
            var event = new PlayerInteractEvent.EntityInteract(player, hand, original);
            SmartPhoneEvents.interact(event);
            helper.assertTrue(event.isCanceled() && event.getCancellationResult() == InteractionResult.CONSUME,
                    "Phone in the interaction hand converts the villager");
            SmartPhoneEvents.interact(new PlayerInteractEvent.EntityInteract(player, hand, original));
            helper.assertTrue(original.isRemoved() && nearby(helper, original).size() == 1, "Exactly one Citizen, no Villager");
            var citizen = nearby(helper, original).get(0);
            helper.assertTrue(citizen.isBaby() == baby && citizen.citizenState().age() == (baby ? CitizenAge.CHILD : CitizenAge.ADULT), "Age group preserved");
            helper.assertTrue(citizen.position().equals(original.position()) && citizen.getYRot() == 123 && citizen.getXRot() == 12,
                    "Position and rotation preserved");
            helper.assertTrue(Objects.equals(citizen.getCustomName(), original.getCustomName()) && citizen.isCustomNameVisible()
                    && citizen.isNoAi() && citizen.isNoGravity(), "Common name and stationary flags preserved");
            helper.assertTrue(CitizenCoverage.isCovered(citizen.citizenState().loadout()), "Clothes cover upper and lower body");
            helper.assertTrue(phone.save(new CompoundTag()).equals(before) && !phone.isDamageableItem() && phone.getMaxStackSize() == 1,
                    "Phone is unchanged and has no durability");
            CompoundTag saved = citizen.saveWithoutId(new CompoundTag());
            Citizen restored = ModEntities.CITIZEN.get().create(helper.getLevel());
            if (restored == null) throw new IllegalStateException("Citizen factory failed");
            restored.load(saved);
            helper.assertTrue(restored.citizenState().serializeNBT().equals(citizen.citizenState().serializeNBT()), "NBT round trip preserves full state");
            helper.assertTrue(!saved.contains("VillagerData") && !saved.contains("Offers") && !saved.contains("Gossips"), "Villager-specific data excluded");
            citizen.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void rejectedSpawnAndBrokenFallbackKeepOriginalVillager(GameTestHelper helper) {
        var original = villager(helper, false);
        CompoundTag before = original.saveWithoutId(new CompoundTag());
        var snapshot = CitizenPresetManager.snapshot();
        var result = CitizenConversion.convert(original, snapshot, citizen -> {
            helper.getLevel().addFreshEntity(citizen);
            return false;
        });
        helper.assertTrue(result == CitizenConversion.Result.FAILED && original.isAlive() && !original.isRemoved()
                && nearby(helper, original).isEmpty(), "Rejected spawn cleans up any Citizen and preserves Villager");
        var broken = new CitizenPresetManager.Snapshot(Map.of(), Map.of(), Map.of(), Map.of());
        result = CitizenConversion.convert(original, broken, citizen -> { throw new AssertionError("Must not spawn naked Citizen"); });
        helper.assertTrue(result == CitizenConversion.Result.FAILED && before.equals(original.saveWithoutId(new CompoundTag())),
                "Broken fallback leaves original entirely unchanged");
        helper.assertTrue(nearby(helper, original).isEmpty(), "No partial Citizen remains");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void reloadCatalogValidatesAllGarmentsAndFallsBackSafely(GameTestHelper helper) {
        var live = CitizenPresetManager.snapshot();
        helper.assertTrue(live.garments().size() >= 1 && live.bodies().get(CitizenAge.ADULT).size() >= 1
                && live.bodies().get(CitizenAge.CHILD).size() >= 1, "Three exact roots are loaded after garment definitions and recipe tags");
        var defaultId = ResourceLocation.parse("createapparel:default");
        var preset = live.garments().get(defaultId);
        helper.assertTrue(preset != null && preset.equals(CitizenGarmentPreset.builtIn()), "Shipped fallback uses old Phase17 clothes");
        for (String invalid : List.of(
                "{\"garments\":{\"feet\":{\"item\":\"createapparel:sneakers\",\"material\":\"synthetic\",\"primary_color\":\"white\"}}}",
                "{\"garments\":{\"top\":{\"item\":\"createapparel:missing\",\"material\":\"cotton\",\"primary_color\":\"white\"}}}",
                "{\"garments\":{\"top\":{\"item\":\"createapparel:t_shirt\",\"material\":\"cotton\",\"primary_color\":\"white\",\"styles\":{\"tuck\":\"wrong\"}}}}")) {
            var next = CitizenPresetManager.validate(Map.of("citizen/garment_presets", Map.of(defaultId, JsonParser.parseString(invalid))),
                    live.definitions(), live.materials());
            helper.assertTrue(next.garments().isEmpty(), "Unused invalid garment also excluded");
            var original = villager(helper, true);
            helper.assertTrue(CitizenConversion.convert(original, next, helper.getLevel()::addFreshEntity) == CitizenConversion.Result.SUCCESS,
                    "All-invalid catalog still converts using compiled fallback");
            var citizen = nearby(helper, original).get(0);
            helper.assertTrue(citizen.isBaby() && CitizenCoverage.isCovered(citizen.citizenState().loadout()), "Safe child fallback covers both halves");
            citizen.discard();
        }
        var invalidMaterial = new HashMap<>(live.materials());
        invalidMaterial.put(ResourceLocation.parse("createapparel:t_shirt"), Set.of());
        var restricted = new CitizenPresetManager.Snapshot(Map.of(), Map.of(), live.definitions(), invalidMaterial);
        try {
            restricted.createLoadout(preset);
            throw new AssertionError("Unsupported material accepted");
        } catch (IllegalArgumentException expected) { }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void allDyeNamesSurvivePresetConversionSaveAndEntityWire(GameTestHelper helper) {
        var live = CitizenPresetManager.snapshot();
        var bodyId = ResourceLocation.parse("createapparel:color_check");
        var outfitId = ResourceLocation.parse("createapparel:color_check_outfit");
        for (FabricColor color : FabricColor.values()) {
            JsonObject json = JsonParser.parseString("""
                    {"arm_profile":"slim", "chest_profile":"bust", "skin_tone":"natural",
                     "hair_style":"createapparel:bob", "hair_color":"brown", "eye_color":"blue",
                     "garment_presets":["createapparel:color_check_outfit"]}
                    """).getAsJsonObject();
            json.addProperty("hair_color", color.getSerializedName());
            json.addProperty("eye_color", color.getSerializedName());
            var body = CitizenBodyPreset.fromJson(json);
            var snapshot = new CitizenPresetManager.Snapshot(Map.of(CitizenAge.ADULT, Map.of(bodyId, body)),
                    Map.of(outfitId, CitizenGarmentPreset.builtIn()), live.definitions(), live.materials());
            var original = villager(helper, false);
            helper.assertTrue(CitizenConversion.convert(original, snapshot, helper.getLevel()::addFreshEntity) == CitizenConversion.Result.SUCCESS, "Named dye conversion succeeds");
            Citizen citizen = nearby(helper, original).get(0);
            var appearance = citizen.citizenState().appearance();
            helper.assertTrue(appearance.hairColorRgb() == color.tintRgb() && appearance.eyeColorRgb() == color.tintRgb(), "Shared RGB " + color);
            var dirty = citizen.getEntityData().packDirty();
            helper.assertTrue(dirty != null && dirty.stream().anyMatch(entry -> entry.value() instanceof CompoundTag tag
                    && tag.equals(citizen.citizenState().serializeNBT())), "Existing SynchedEntityData contains the complete converted state");
            var buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                buffer.writeNbt(citizen.citizenState().serializeNBT());
                CompoundTag wire = buffer.readNbt();
                helper.assertTrue(wire != null && wire.getInt("HairColor") == color.tintRgb() && wire.getInt("EyeColor") == color.tintRgb(), "Entity CompoundTag wire preserves RGB");
            } finally { buffer.release(); }
            citizen.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void dressCoverageUsesGameRuleTagAndTraderInteractionsStayUnchanged(GameTestHelper helper) {
        var live = CitizenPresetManager.snapshot();
        helper.assertTrue(live.lowerCoveringTops().size() == 26, "All 26 current dresses classified independently of rendering");
        for (ResourceLocation id : live.lowerCoveringTops()) {
            var material = live.materials().get(id).iterator().next();
            var outfit = new CitizenGarmentPreset(Map.of(GarmentSlot.TOP, new CitizenGarmentPreset.Entry(id,
                    new com.rexhub.createapparel.garment.GarmentData(material, "plain", FabricColor.WHITE,
                            Optional.empty(), Optional.empty()), Map.of())));
            var loadout = live.createLoadout(outfit);
            helper.assertTrue(CitizenCoverage.isCovered(loadout), "Tagged dress covers both halves: " + id);
            var unclassified = new CitizenPresetManager.Snapshot(Map.of(), Map.of(), live.definitions(), live.materials());
            try {
                unclassified.createLoadout(outfit);
                throw new AssertionError("Geometry was incorrectly used as lower coverage");
            } catch (IllegalArgumentException expected) { }
        }
        var player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
        var trader = EntityType.WANDERING_TRADER.create(helper.getLevel());
        if (trader == null) throw new IllegalStateException("Trader factory failed");
        var event = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, trader);
        SmartPhoneEvents.interact(event);
        helper.assertTrue(!event.isCanceled() && !trader.isRemoved(), "Wandering trader is not intercepted");
        var original = villager(helper, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        event = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, original);
        SmartPhoneEvents.interact(event);
        helper.assertTrue(!event.isCanceled() && !original.isRemoved(), "Normal villager interaction remains unchanged");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen", timeoutTicks = 200)
    public static void everyShippedPresetLoadsAndResolvedVariationsKeepValidClothes(GameTestHelper helper) {
        var live = CitizenPresetManager.snapshot();
        var resources = helper.getLevel().getServer().getResourceManager();
        for (String root : List.of("citizen/garment_presets", "citizen/body_presets/adult", "citizen/body_presets/child")) {
            Set<ResourceLocation> expected = new HashSet<>();
            resources.listResources(root, id -> id.getNamespace().equals(CreateApparel.MOD_ID) && id.getPath().endsWith(".json"))
                    .keySet().forEach(file -> expected.add(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID,
                            file.getPath().substring(root.length() + 1, file.getPath().length() - 5))));
            Set<ResourceLocation> loaded = root.equals("citizen/garment_presets") ? live.garments().keySet()
                    : live.bodies().get(root.endsWith("adult") ? CitizenAge.ADULT : CitizenAge.CHILD).keySet();
            helper.assertTrue(loaded.equals(expected), "Every resource is valid and loaded: " + root);
        }
        var random = RandomSource.create(11471);
        for (var pair : live.garments().entrySet()) {
            for (int i = 0; i < 32; i++) {
                var selected = pair.getValue().resolve(random);
                var loadout = live.createLoadout(selected);
                helper.assertTrue(CitizenCoverage.isCovered(loadout), "Resolved outfit covers both halves: " + pair.getKey());
                var restored = new com.rexhub.createapparel.apparel.ApparelLoadout();
                restored.deserializeNBT(loadout.serializeNBT());
                helper.assertTrue(loadout.serializeNBT().equals(restored.serializeNBT()), "Selected colors, design and styles persist: " + pair.getKey());
                for (var entry : selected.garments().entrySet()) {
                    if (entry.getValue().appearance() instanceof com.rexhub.createapparel.garment.GarmentData expected) {
                        var stack = loadout.getStack(entry.getKey());
                        var actual = com.rexhub.createapparel.garment.GarmentData.fromStackStrict(stack, loadout.definition(stack).orElseThrow());
                        helper.assertTrue(expected.equals(actual), "Stack contains selected data, not a palette: " + pair.getKey());
                    }
                }
            }
        }
        for (CitizenAge age : CitizenAge.values()) {
            for (var pair : live.bodies().get(age).entrySet()) {
                var body = pair.getValue();
                helper.assertTrue(live.garments().keySet().containsAll(body.garmentPresets()), "No unresolved outfit references: " + pair.getKey());
                var singleton = new CitizenPresetManager.Snapshot(Map.of(age, Map.of(pair.getKey(), body)),
                        live.garments(), live.definitions(), live.materials(), live.lowerCoveringTops());
                var original = villager(helper, age == CitizenAge.CHILD);
                helper.assertTrue(CitizenConversion.convert(original, singleton, helper.getLevel()::addFreshEntity) == CitizenConversion.Result.SUCCESS,
                        "Body can convert with its allowed outfits: " + pair.getKey());
                var citizen = nearby(helper, original).get(0);
                var appearance = citizen.citizenState().appearance();
                if (pair.getKey().equals(ResourceLocation.parse("createapparel:default"))) {
                    helper.assertTrue(appearance.equals(CitizenPresetSelector.fallback().appearance()), "Default-only catalog uses compiled emergency appearance");
                } else {
                    helper.assertTrue(body.skinTones().contains(appearance.skinTone())
                            && body.hairColors().stream().anyMatch(color -> color.tintRgb() == appearance.hairColorRgb())
                            && body.eyeColors().stream().anyMatch(color -> color.tintRgb() == appearance.eyeColorRgb()), "Body choices remain within preset");
                }
                helper.assertTrue(citizen.citizenState().age() == age, "Age stays outside appearance options");
                citizen.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void defaultPresetsStayLoadedButNeverEnterNormalConversionDraw(GameTestHelper helper) {
        var live = CitizenPresetManager.snapshot();
        var defaultId = ResourceLocation.parse("createapparel:default");
        var bodyId = ResourceLocation.parse("createapparel:normal_check");
        var outfitId = ResourceLocation.parse("createapparel:male_casual_01");
        helper.assertTrue(live.garments().containsKey(defaultId), "Default garment remains loaded and validated");
        var normal = CitizenBodyPreset.fromJson(JsonParser.parseString("""
                {"arm_profile":"slim", "chest_profile":"flat", "skin_tone":"natural",
                 "hair_style":"createapparel:bob", "hair_color":"navy", "eye_color":"blue",
                 "garment_presets":["createapparel:default","createapparel:male_casual_01"]}
                """));
        for (var age : CitizenAge.values()) {
            helper.assertTrue(live.bodies().get(age).containsKey(defaultId), "Default body remains loaded for " + age);
            var catalog = new CitizenPresetManager.Snapshot(Map.of(age, Map.of(
                    defaultId, live.bodies().get(age).get(defaultId), bodyId, normal)),
                    live.garments(), live.definitions(), live.materials(), live.lowerCoveringTops());
            for (int i = 0; i < 24; i++) {
                var original = villager(helper, age == CitizenAge.CHILD);
                original.getRandom().setSeed(7901 + i);
                helper.assertTrue(CitizenConversion.convert(original, catalog, helper.getLevel()::addFreshEntity) == CitizenConversion.Result.SUCCESS,
                        "Normal preset converts despite reserved defaults");
                var citizen = nearby(helper, original).get(0);
                helper.assertTrue(citizen.citizenState().appearance().equals(normal.appearance()), "Reserved default body is never drawn");
                helper.assertTrue(citizen.citizenState().age() == age && CitizenCoverage.isCovered(citizen.citizenState().loadout()),
                        "Normal outfit is covered and age is retained");
                helper.assertTrue(CitizenPresetSelector.select(catalog, age, RandomSource.create(7901 + i)).garment()
                        .garments().get(GarmentSlot.TOP).item().equals(live.garments().get(outfitId).garments().get(GarmentSlot.TOP).item()),
                        "Normal outfit reference is selected instead of default");
                citizen.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void adultSchoolOutfitsConvertWithoutChildSchoolReferences(GameTestHelper helper) {
        var live = CitizenPresetManager.snapshot();
        Set<ResourceLocation> schoolIds = new HashSet<>();
        for (String name : List.of("male_school_01", "female_school_01", "female_school_02", "female_school_03")) {
            var id = ResourceLocation.parse("createapparel:" + name);
            schoolIds.add(id);
            helper.assertTrue(live.garments().containsKey(id), "School outfit is loaded: " + id);
            var bodyId = ResourceLocation.parse("createapparel:" + (name.startsWith("male_") ? "male_mash" : "female_bob"));
            var body = live.bodies().get(CitizenAge.ADULT).get(bodyId);
            helper.assertTrue(body != null && body.garmentPresets().contains(id), "Adult body allows school outfit: " + id);
            var selectedBody = new CitizenBodyPreset(body.armProfile(), body.chestProfile(), body.hairStyle(),
                    body.skinTones(), body.hairColors(), body.eyeColors(), List.of(id));
            var singleton = new CitizenPresetManager.Snapshot(Map.of(CitizenAge.ADULT, Map.of(bodyId, selectedBody)),
                    live.garments(), live.definitions(), live.materials(), live.lowerCoveringTops());
            var original = villager(helper, false);
            helper.assertTrue(CitizenConversion.convert(original, singleton, helper.getLevel()::addFreshEntity) == CitizenConversion.Result.SUCCESS,
                    "Adult converts with school outfit: " + id);
            var citizen = nearby(helper, original).get(0);
            helper.assertTrue(citizen.citizenState().age() == CitizenAge.ADULT, "School clothes retain adult proportions");
            var outfit = live.garments().get(id);
            for (var entry : outfit.garments().entrySet()) {
                var stack = citizen.citizenState().loadout().getStack(entry.getKey());
                if (entry.getValue().optional() && stack.isEmpty()) continue;
                helper.assertTrue(!stack.isEmpty() && entry.getValue().item().equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())),
                        "Converted school clothes use allowed items: " + id);
                if (entry.getValue().fabric().isPresent()) {
                    var options = entry.getValue().fabric().get();
                    var data = com.rexhub.createapparel.garment.GarmentData.fromStackStrict(stack, live.definitions().get(entry.getValue().item()));
                    helper.assertTrue(options.material() == data.material() && options.designs().contains(data.design())
                            && options.primaryColors().contains(data.primaryColor())
                            && options.secondaryColors().map(values -> data.secondaryColor().map(values::contains).orElse(false)).orElse(data.secondaryColor().isEmpty())
                            && options.accentColors().map(values -> data.accentColor().map(values::contains).orElse(false)).orElse(data.accentColor().isEmpty()),
                            "School color and design choices stay within user-edited preset: " + id);
                }
            }
            citizen.discard();
        }
        for (var body : live.bodies().get(CitizenAge.CHILD).values()) {
            helper.assertTrue(Collections.disjoint(body.garmentPresets(), schoolIds), "Child bodies do not draw school uniforms");
        }
        for (String gender : List.of("male", "female")) {
            helper.assertTrue(!live.garments().containsKey(ResourceLocation.parse("createapparel:child_" + gender + "_school_01")),
                    "Renamed child school resources are not packaged");
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void optionalEquipmentAndStylesPersistAndKeepCoverage(GameTestHelper helper) {
        var live = CitizenPresetManager.snapshot();
        var json = JsonParser.parseString("""
                {"garments":{
                "top":{"item":"createapparel:shirt_longsleeves","material":"cotton","primary_color":"white",
                "styles":{"sleeves":["normal","rolled"]}},
                "bottom":{"item":"createapparel:slacks","material":"wool","primary_color":"navy"},
                "back":{"item":"createapparel:backpack","material":"synthetic","primary_color":"black","optional":true},
                "neck":{"item":"createapparel:necklace","metal_color":"silver","gem":"diamond","optional":true}}}
                """);
        var preset = CitizenGarmentPreset.fromJson(json);
        live.createLoadout(preset);
        var random = RandomSource.create(4112);
        Set<Integer> masks = new HashSet<>();
        Set<String> sleeves = new HashSet<>();
        for (int i = 0; i < 128; i++) {
            var selected = preset.resolve(random);
            var loadout = live.createLoadout(selected);
            helper.assertTrue(CitizenCoverage.isCovered(loadout), "All optional draws retain upper/lower coverage");
            var restored = new com.rexhub.createapparel.apparel.ApparelLoadout();
            restored.deserializeNBT(loadout.serializeNBT());
            helper.assertTrue(restored.serializeNBT().equals(loadout.serializeNBT()), "Optional absence and selected styles persist");
            helper.assertTrue(selected == selected.resolve(random), "Resolved optional equipment cannot reroll");
            masks.add((loadout.getStack(GarmentSlot.BACK).isEmpty() ? 0 : 1) | (loadout.getStack(GarmentSlot.NECK).isEmpty() ? 0 : 2));
            sleeves.add(loadout.styleState(GarmentSlot.TOP).styles().get("sleeves"));
        }
        helper.assertTrue(masks.equals(Set.of(0, 1, 2, 3)) && sleeves.equals(Set.of("normal", "rolled")), "Every optional and style branch is reachable");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void optionalCoverageAndInvalidStyleCandidatesCannotEnterCatalog(GameTestHelper helper) {
        var live = CitizenPresetManager.snapshot();
        var id = ResourceLocation.parse("createapparel:test_optional");
        for (String invalid : List.of("top", "bottom", "style", "outfit_style", "optional_item")) {
            var json = JsonParser.parseString("""
                    {"garments":{
                    "top":{"item":"createapparel:shirt_longsleeves","material":"cotton","primary_color":"white"},
                    "bottom":{"item":"createapparel:slacks","material":"cotton","primary_color":"navy"}}}
                    """).getAsJsonObject();
            var entries = json.getAsJsonObject("garments");
            if (invalid.equals("style")) entries.getAsJsonObject("top").add("styles", JsonParser.parseString("{\"sleeves\":[\"normal\",\"missing\"]}"));
            else if (invalid.equals("outfit_style")) {
                entries.getAsJsonObject("top").add("styles", JsonParser.parseString("{\"sleeves\":[\"normal\",\"rolled\"]}"));
                entries.add("outer", JsonParser.parseString("{\"item\":\"createapparel:blazer_jacket\",\"material\":\"wool\",\"primary_color\":\"navy\",\"optional\":true}"));
            }
            else if (invalid.equals("optional_item")) entries.add("back", JsonParser.parseString("{\"item\":\"createapparel:missing\",\"material\":\"cotton\",\"primary_color\":\"black\",\"optional\":true}"));
            else entries.getAsJsonObject(invalid).addProperty("optional", true);
            var rejected = CitizenPresetManager.validate(Map.of("citizen/garment_presets", Map.of(id, json)), live.definitions(), live.materials());
            helper.assertTrue(rejected.garments().isEmpty(), "Invalid optional or non-first style choice is rejected: " + invalid);
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void invalidNonFirstDesignAndColorCandidateCannotEnterCatalog(GameTestHelper helper) {
        var live = CitizenPresetManager.snapshot();
        var id = ResourceLocation.parse("createapparel:test_options");
        for (String options : List.of("\"design\":[\"plain\",\"missing\"],\"primary_color\":[\"white\"]",
                "\"design\":[\"plain\"],\"primary_color\":[\"white\",\"missing\"]")) {
            var json = JsonParser.parseString("{\"garments\":{\"top\":{\"item\":\"createapparel:t_shirt\",\"material\":\"cotton\"," + options
                    + "},\"bottom\":{\"item\":\"createapparel:denim_pants\",\"material\":\"cotton\",\"primary_color\":[\"navy\"]}}}");
            var snapshot = CitizenPresetManager.validate(Map.of("citizen/garment_presets", Map.of(id, json)), live.definitions(), live.materials());
            helper.assertTrue(snapshot.garments().isEmpty(), "Every candidate is validated before random draw");
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen_reload", timeoutTicks = 400)
    public static void realDatapackReloadPublishesNewSnapshotAndPreservesConvertedState(GameTestHelper helper) {
        var original = villager(helper, true);
        helper.assertTrue(CitizenConversion.convert(original) == CitizenConversion.Result.SUCCESS, "Convert before reload");
        var citizen = nearby(helper, original).get(0);
        CompoundTag before = citizen.citizenState().serializeNBT();
        var previous = CitizenPresetManager.snapshot();
        var server = helper.getLevel().getServer();
        var reload = server.reloadResources(server.getPackRepository().getSelectedIds());
        helper.succeedWhen(() -> {
            helper.assertTrue(reload.isDone(), "Reload finished");
            reload.join();
            var next = CitizenPresetManager.snapshot();
            helper.assertTrue(next != previous && next.garments().equals(previous.garments())
                    && next.materials().equals(previous.materials()) && next.lowerCoveringTops().equals(previous.lowerCoveringTops()),
                    "Atomic new snapshot uses newly staged recipe tags");
            helper.assertTrue(before.equals(citizen.citizenState().serializeNBT()), "Reload does not reroll existing Citizens");
        });
    }
}
