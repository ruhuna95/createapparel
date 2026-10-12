package com.rexhub.createapparel.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenAge;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.citizen.CitizenHairStyles;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitizenCommandGameTests {
    private CitizenCommandGameTests() { }

    private static CommandSourceStack source(GameTestHelper helper) {
        return helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel())
                .withPosition(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                .withPermission(2).withSuppressedOutput();
    }

    private static List<Citizen> nearby(CommandSourceStack source) {
        return source.getLevel().getEntitiesOfClass(Citizen.class, new AABB(source.getPosition(), source.getPosition()).inflate(0.1));
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void commandSpawnsSpecifiedChildAndAgeSurvivesSaveAndWire(GameTestHelper helper) throws CommandSyntaxException {
        var source = source(helper);
        var dispatcher = source.getServer().getCommands().getDispatcher();
        int result = dispatcher.execute("createapparel citizen slim flat child dark createapparel:twintails #123ABC cyan default", source);
        helper.assertTrue(result == 1 && nearby(source).size() == 1, "Command creates one Citizen");
        var child = nearby(source).get(0);
        helper.assertTrue(child.position().equals(source.getPosition()), "Spawn at command execution position");
        helper.assertTrue(child.isBaby() && child.getScale() == 0.5F, "Child category and render scale");
        helper.assertTrue(Math.abs(child.getBbHeight() - 0.9375F) < 0.00001 && Math.abs(child.getBbWidth() - 0.3F) < 0.00001,
                "Child dimensions scale with body");
        helper.assertTrue(child.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.125, "Child movement speed scales with body");
        var appearance = child.citizenState().appearance();
        helper.assertTrue(appearance.armProfile() == ArmProfile.SLIM && appearance.chestProfile() == ChestProfile.FLAT,
                "Explicit non-default body pairing remains selectable");
        helper.assertTrue(appearance.eyeColorRgb() == com.rexhub.createapparel.material.FabricColor.CYAN.tintRgb() && appearance.hairColorRgb() == 0x123ABC,
                "Exact dye eye and independent RGB hair");
        CompoundTag saved = child.saveWithoutId(new CompoundTag());
        var restored = ModEntities.CITIZEN.get().create(helper.getLevel());
        if (restored == null) throw new IllegalStateException("Citizen factory failed");
        restored.load(saved);
        helper.assertTrue(restored.isBaby() && restored.getBbHeight() == child.getBbHeight()
                && restored.getEyeHeight() == child.getEyeHeight(), "Restored child dimensions and eye height");
        helper.assertTrue(restored.citizenState().serializeNBT().equals(child.citizenState().serializeNBT()), "Actual clothes and appearance persist");
        child.getEntityData().packDirty();
        child.citizenState().setAge(CitizenAge.CHILD);
        helper.assertTrue(child.getEntityData().packDirty() == null, "Same age does not resend");
        child.citizenState().setAge(CitizenAge.ADULT);
        var dirty = child.getEntityData().packDirty();
        helper.assertTrue(dirty != null && dirty.stream().anyMatch(value -> value.value() instanceof CompoundTag tag
                && tag.getString("Age").equals("adult")), "Age update is synchronized");
        helper.assertTrue(!child.isBaby() && child.getBbHeight() == 1.875F && child.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.25,
                "Adult dimensions and speed restored after age change");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void commandAcceptsAllTrailingOmissionsAndWildcards(GameTestHelper helper) throws CommandSyntaxException {
        var source = source(helper);
        var dispatcher = source.getServer().getCommands().getDispatcher();
        var seen = new HashSet<UUID>();
        for (String suffix : new String[]{"", "*", "slim", "classic *", "* bust", "* * * * * * * *",
                "classic flat adult", "classic flat adult fair", "classic flat adult fair bob",
                "classic flat adult fair bob brown", "classic flat adult fair bob brown blue",
                "classic flat adult fair bob brown blue default"}) {
            helper.assertTrue(dispatcher.execute("createapparel citizen" + (suffix.isEmpty() ? "" : " " + suffix), source) == 1,
                    "Optional argument prefix works: " + suffix);
            var fresh = nearby(source).stream().filter(entity -> !seen.contains(entity.getUUID())).toList();
            helper.assertTrue(fresh.size() == 1, "Exactly one spawn per command");
            var citizen = fresh.get(0);
            var appearance = citizen.citizenState().appearance();
            helper.assertTrue(appearance.chestProfile() == (appearance.armProfile() == ArmProfile.CLASSIC ? ChestProfile.FLAT : ChestProfile.BUST),
                    "Randomized profiles preserve pairing");
            seen.add(citizen.getUUID());
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void allTintHairstylesIncludingBaldSpawnAndPersist(GameTestHelper helper) throws CommandSyntaxException {
        var source = source(helper);
        var dispatcher = source.getServer().getCommands().getDispatcher();
        var seen = new HashSet<UUID>();
        for (String hair : CitizenHairStyles.BUILT_IN) {
            dispatcher.execute("createapparel citizen classic flat adult natural " + hair + " brown blue default", source);
            var entity = nearby(source).stream().filter(candidate -> !seen.contains(candidate.getUUID())).findFirst().orElseThrow();
            helper.assertTrue(entity.citizenState().appearance().hairStyleId().getPath().equals(hair), "Hair ID " + hair);
            var saved = entity.saveWithoutId(new CompoundTag());
            var restored = ModEntities.CITIZEN.get().create(helper.getLevel());
            if (restored == null) throw new IllegalStateException("Citizen factory failed");
            restored.load(saved);
            helper.assertTrue(restored.citizenState().appearance().equals(entity.citizenState().appearance()), "Persist hair " + hair);
            seen.add(entity.getUUID());
        }
        helper.assertTrue(seen.size() == 10, "Nine hairstyles and bald");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void invalidCommandAndInsufficientPermissionNeverSpawn(GameTestHelper helper) {
        var source = source(helper);
        var dispatcher = source.getServer().getCommands().getDispatcher();
        for (String suffix : new String[]{"invalid", "* * * * * * invalid", "* * * * * * * missing", "* * * * * * * * extra"}) {
            try {
                dispatcher.execute("createapparel citizen " + suffix, source);
                helper.fail("Invalid command was accepted: " + suffix);
            } catch (CommandSyntaxException expected) { }
        }
        try {
            dispatcher.execute("createapparel citizen", source.withPermission(0));
            helper.fail("Non-operator may not spawn Citizens");
        } catch (CommandSyntaxException expected) { }
        helper.assertTrue(nearby(source).isEmpty(), "Failures do not leave spawned entities");
        helper.succeed();
    }
}
