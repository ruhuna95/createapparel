package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.citizen.CitizenCoverage;
import com.rexhub.createapparel.citizen.CitizenBodyPreset;
import com.rexhub.createapparel.citizen.CitizenPresetManager;
import com.rexhub.createapparel.citizen.CitizenPresetSelector;
import com.rexhub.createapparel.citizen.CitizenSpawnInitialization;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenAge;
import com.rexhub.createapparel.entity.CitizenAppearance;
import com.rexhub.createapparel.entity.CitizenState;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitizenSpawnEggGameTests {
    private CitizenSpawnEggGameTests() { }

    private static boolean matchesBody(CitizenBodyPreset body, CitizenAppearance appearance) {
        return body.armProfile() == appearance.armProfile() && body.chestProfile() == appearance.chestProfile()
                && body.hairStyle().equals(appearance.hairStyleId()) && body.skinTones().contains(appearance.skinTone())
                && body.hairColors().stream().anyMatch(color -> color.tintRgb() == appearance.hairColorRgb())
                && body.eyeColors().stream().anyMatch(color -> color.tintRgb() == appearance.eyeColorRgb());
    }

    @GameTest(template = "apparel_state", batch = "citizen", timeoutTicks = 200)
    public static void genderPoolsStayBalancedAndRemovedBaldPresetDoesNotSpawn(GameTestHelper helper) {
        var live = CitizenPresetManager.snapshot();
        helper.assertTrue(!live.bodies().get(CitizenAge.ADULT).containsKey(net.minecraft.resources.ResourceLocation.parse("createapparel:male_bald")),
                "Deleted male bald preset is not in the loaded catalog");
        for (var age : CitizenAge.values()) {
            var males = live.bodies().get(age).entrySet().stream().filter(entry -> entry.getKey().getPath().startsWith("male_"))
                    .map(java.util.Map.Entry::getValue).toList();
            var females = live.bodies().get(age).entrySet().stream().filter(entry -> entry.getKey().getPath().startsWith("female_"))
                    .map(java.util.Map.Entry::getValue).toList();
            helper.assertTrue(!males.isEmpty() && !females.isEmpty() && males.size() != females.size(), "Shipped age group has unequal male/female preset counts");
            var random = RandomSource.create(1855050 + age.ordinal());
            int maleCount = 0;
            for (int i = 0; i < 2000; i++) {
                var appearance = CitizenPresetSelector.select(live, age, random).appearance();
                boolean male = males.stream().anyMatch(body -> matchesBody(body, appearance));
                boolean female = females.stream().anyMatch(body -> matchesBody(body, appearance));
                helper.assertTrue(male != female, "Spawn appearance belongs to exactly one named gender pool");
                maleCount += male ? 1 : 0;
            }
            helper.assertTrue(maleCount > 900 && maleCount < 1100, "Shipped gender draws are balanced: " + age + " / " + maleCount);
            for (int i = 0; i < 24; i++) {
                var citizen = ModEntities.CITIZEN.get().create(helper.getLevel());
                helper.assertTrue(citizen != null, "Citizen factory");
                CitizenSpawnInitialization.initialize(citizen, age, random);
                helper.assertTrue(citizen.citizenState().age() == age, "Balanced spawn retains its age");
                checkSaved(helper, citizen);
            }
        }
        helper.succeed();
    }

    private static void checkSaved(GameTestHelper helper, Citizen citizen) {
        helper.assertTrue(CitizenCoverage.isCovered(citizen.citizenState().loadout()), "Spawned Citizen is clothed");
        helper.assertTrue(citizen.getScale() * citizen.getAgeScale() == citizen.citizenState().age().scale(), "Spawned age updates body scale");
        var restored = new CitizenState();
        restored.deserializeNBT(com.rexhub.createapparel.TestRegistries.lookup(), citizen.saveWithoutId(new CompoundTag()));
        helper.assertTrue(restored.serializeNBT(com.rexhub.createapparel.TestRegistries.lookup()).equals(citizen.citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup())), "Age, appearance and clothes persist");
        var appearance = citizen.citizenState().appearance();
        helper.assertTrue(CitizenPresetManager.snapshot().bodies().get(citizen.citizenState().age()).values().stream()
                .anyMatch(body -> body.armProfile() == appearance.armProfile() && body.chestProfile() == appearance.chestProfile()
                        && body.hairStyle().equals(appearance.hairStyleId()) && body.skinTones().contains(appearance.skinTone())
                        && body.hairColors().stream().anyMatch(color -> color.tintRgb() == appearance.hairColorRgb())
                        && body.eyeColors().stream().anyMatch(color -> color.tintRgb() == appearance.eyeColorRgb())),
                "Spawned appearance belongs to the correct age catalog");
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void babyRollMatchesVanillaZombieAndBothAgeCatalogsSpawn(GameTestHelper helper) {
        var actual = RandomSource.create(31819);
        var vanilla = RandomSource.create(31819);
        int childCount = 0;
        for (int i = 0; i < 2000; i++) {
            boolean baby = Zombie.getSpawnAsBabyOdds(vanilla);
            CitizenAge age = CitizenSpawnInitialization.selectAge(actual);
            helper.assertTrue((age == CitizenAge.CHILD) == baby, "Baby roll matches Forge vanilla Zombie exactly");
            childCount += baby ? 1 : 0;
        }
        helper.assertTrue(childCount > 60 && childCount < 140, "Default child chance is approximately five percent");
        for (CitizenAge age : CitizenAge.values()) {
            var citizen = ModEntities.CITIZEN.get().create(helper.getLevel());
            helper.assertTrue(citizen != null, "Citizen factory");
            CitizenSpawnInitialization.initialize(citizen, age, RandomSource.create(516));
            helper.assertTrue(citizen.citizenState().age() == age, "Requested spawn age is retained");
            checkSaved(helper, citizen);
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen", timeoutTicks = 200)
    public static void eggAndDispenserPathsSpawnAgeAppropriateCitizens(GameTestHelper helper) {
        var egg = (SpawnEggItem) ModItems.CITIZEN_SPAWN_EGG.get();
        helper.assertTrue(egg.getType(new ItemStack(egg)) == ModEntities.CITIZEN.get(), "NeoForge egg supplier resolves correct entity");
        helper.assertTrue(ModItems.creativeTabItems().contains(ModItems.CITIZEN_SPAWN_EGG), "Egg is in Apparel creative tab");
        Set<CitizenAge> ages = new HashSet<>();
        for (var reason : List.of(MobSpawnType.SPAWN_EGG, MobSpawnType.DISPENSER)) {
            for (int i = 0; i < 96; i++) {
                var citizen = ModEntities.CITIZEN.get().spawn(helper.getLevel(), new ItemStack(egg), null,
                        helper.absolutePos(new BlockPos(1, 2, 1)), reason, false, false);
                helper.assertTrue(citizen != null, "Actual EntityType spawn succeeds: " + reason);
                checkSaved(helper, citizen);
                ages.add(citizen.citizenState().age());
                citizen.discard();
            }
        }
        helper.assertTrue(!ages.isEmpty(), "Egg initialization produces valid age groups");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void eggOnCitizenCreatesChildWithoutChangingParent(GameTestHelper helper) {
        var parent = ModEntities.CITIZEN.get().create(helper.getLevel());
        helper.assertTrue(parent != null, "Parent factory");
        var before = parent.citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup());
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        var stack = new ItemStack(ModItems.CITIZEN_SPAWN_EGG.get(), 2);
        var child = ((SpawnEggItem) stack.getItem()).spawnOffspringFromSpawnEgg(player, parent, ModEntities.CITIZEN.get(),
                helper.getLevel(), helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)), stack).orElseThrow();
        helper.assertTrue(child instanceof Citizen && child.isBaby(), "Vanilla offspring interaction creates a Citizen child");
        checkSaved(helper, (Citizen) child);
        helper.assertTrue(parent.citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup()).equals(before), "Parent is not changed");
        helper.assertTrue(stack.getCount() == 1, "Survival egg is consumed once");
        child.discard();
        helper.succeed();
    }
}
