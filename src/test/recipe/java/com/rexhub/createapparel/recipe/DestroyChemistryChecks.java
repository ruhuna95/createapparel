package com.rexhub.createapparel.recipe;

import petrolpark.mc.destroy.chemistry.legacy.LegacyElement;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import com.rexhub.createapparel.compat.destroy.DestroyChemistry;
import com.rexhub.createapparel.init.ModFluids;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllFluids;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;

final class DestroyChemistryChecks {
    private DestroyChemistryChecks() { }

    static void definitions(GameTestHelper helper) {
        var silver = LegacyElement.fromSymbol("Ag");
        helper.assertTrue(silver.name().equals("SILVER") && Math.abs(silver.getMass() - 107.8682f) < 0.001f,
                "Real Ag is appended without substituting a different element");
        var ion = DestroyChemistry.SILVER_ION;
        var complex = DestroyChemistry.DIAMMINESILVER_I;
        helper.assertTrue(ion.getCharge() == 1 && ion.getAtoms().size() == 1 && ion.getMolecularFormula().get(silver) == 1,
                "Silver ion has one Ag atom and charge +1");
        helper.assertTrue(complex.getCharge() == 1 && complex.getMolecularFormula().get(silver) == 1
                        && complex.getMolecularFormula().get(LegacyElement.NITROGEN) == 2
                        && complex.getMolecularFormula().get(LegacyElement.HYDROGEN) == 6,
                "Diamminesilver is AgN2H6 with charge +1");
        helper.assertTrue(LegacySpecies.getMolecule("createapparel:diamminesilver_i") == complex,
                "Registered species resolves through Destroy's namespace lookup");
        var dissolution = DestroyChemistry.SILVER_DISSOLUTION;
        helper.assertTrue(dissolution.getMolesPerItem() == 4.5f && dissolution.getItemReactants().size() == 1,
                "One ingot supplies exactly 4.5 mol");
        helper.assertTrue(dissolution.getReactantMolarRatio(DestroyMolecules.PROTON) == 2
                        && dissolution.getReactantMolarRatio(DestroyMolecules.NITRATE) == 1
                        && dissolution.getProductMolarRatio(ion) == 1
                        && dissolution.getProductMolarRatio(DestroyMolecules.NITROGEN_DIOXIDE) == 1
                        && dissolution.getProductMolarRatio(DestroyMolecules.WATER) == 1,
                "Nitric dissolution conserves the approved stoichiometry");
        var complexation = DestroyChemistry.SILVER_AMMONIA_COMPLEXATION;
        helper.assertTrue(complexation.getReactantMolarRatio(ion) == 1
                        && complexation.getReactantMolarRatio(DestroyMolecules.AMMONIA) == 2
                        && complexation.getProductMolarRatio(complex) == 1,
                "Complexation consumes two ammonia molecules per Ag");
        for (var reaction : List.of(dissolution, complexation)) {
            helper.assertTrue(reaction.getActivationEnergy() == 25f && reaction.getPreexponentialFactor() == 1e4f
                            && reaction.getEnthalpyChange() == 0f,
                    "Approved Destroy kinetic defaults are retained");
            boolean selected = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(
                    "createapparel:mixing/mirror_coating_fluid_from_destroy")).isPresent();
            helper.assertTrue(reaction.includeInJei() == selected, "Chemical JEI route follows the selected coating route");
        }
        helper.succeed();
    }

    static void reactions(GameTestHelper helper) {
        var silverItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("thesilverage:silver_ingot"));
        helper.assertTrue(silverItem != null && silverItem != Items.AIR, "Actual test silver ingot is registered");
        var ingot = new ItemStack(silverItem);
        var solution = acid(9f, 9f);
        solution.dissolveItems(new LegacyMixture.ReactionContext(List.of(ingot), 0, false), 1d);
        helper.assertTrue(ingot.isEmpty(), "Dissolution consumes exactly one real silver ingot");
        near(helper, solution.getConcentrationOf(DestroyChemistry.SILVER_ION), 4.5f, "One ingot yields 4.5 mol Ag+");
        near(helper, solution.getConcentrationOf(DestroyMolecules.PROTON), 0f, "Dissolution consumes 9 mol H+");
        near(helper, solution.getConcentrationOf(DestroyMolecules.NITRATE), 4.5f, "Dissolution consumes 4.5 mol nitrate");
        near(helper, solution.getConcentrationOf(DestroyMolecules.NITROGEN_DIOXIDE), 4.5f, "Nitrogen dioxide coproduct");
        var insufficient = new ItemStack(silverItem);
        acid(8.9f, 9f).dissolveItems(new LegacyMixture.ReactionContext(List.of(insufficient), 0, false), 1d);
        helper.assertTrue(insufficient.getCount() == 1, "Insufficient acid does not partially consume an ingot");
        var noNitrate = new ItemStack(silverItem);
        acid(9f, 0f).dissolveItems(new LegacyMixture.ReactionContext(List.of(noNitrate), 0, false), 1d);
        helper.assertTrue(noNitrate.getCount() == 1, "Acid without nitrate does not dissolve silver");
        var complexation = new LegacyMixture().addMolecule(DestroyMolecules.WATER, 50f)
                .addMolecule(DestroyChemistry.SILVER_ION, 1f).addMolecule(DestroyMolecules.AMMONIA, 2f);
        var context = new LegacyMixture.ReactionContext(List.of(), 0, false);
        for (int i = 0; i < 12000 && complexation.getConcentrationOf(DestroyChemistry.DIAMMINESILVER_I) < 0.91f; i++) {
            complexation.reactForTick(context, 1);
        }
        float product = complexation.getConcentrationOf(DestroyChemistry.DIAMMINESILVER_I);
        helper.assertTrue(product > 0.9f, "Destroy's actual reaction engine forms coating-strength silver complex: " + product);
        near(helper, complexation.getConcentrationOf(DestroyChemistry.SILVER_ION) + product, 1f, "Silver is conserved");
        var restored = LegacyMixture.readNBT(complexation.writeNBT());
        near(helper, restored.getConcentrationOf(DestroyChemistry.DIAMMINESILVER_I), product, "Mixture NBT round trip");
        helper.succeed();
    }

    static void mixing(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(
                "createapparel:mixing/mirror_coating_fluid_from_destroy"));
        if (recipe.isEmpty()) {
            helper.assertTrue(com.rexhub.createapparel.TestRegistries.items(net.minecraft.tags.ItemTags.create(
                            ResourceLocation.parse("c:ingots/silver"))).isEmpty(),
                    "Destroy finishing recipe is absent only when silver is unavailable");
            helper.succeed();
            return;
        }
        var mixing = (MixingRecipe) recipe.orElseThrow().value();
        var ingredient = mixing.getFluidIngredients().get(0);
        helper.assertTrue(ingredient.test(complex(1f, 900)), "Target concentration is accepted");
        helper.assertTrue(ingredient.test(complex(0.9f, 900)) && ingredient.test(complex(1.1f, 900)),
                "Both approved concentration boundaries are accepted");
        helper.assertTrue(!ingredient.test(complex(0.899f, 900)) && !ingredient.test(complex(1.101f, 900)),
                "Concentrations outside the approved tolerance are rejected");
        helper.assertTrue(!ingredient.test(MixtureFluid.of(900, new LegacyMixture().addMolecule(DestroyChemistry.SILVER_ION, 1f))),
                "Uncomplexed silver solution is not accepted");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                helper.getLevel().registryAccess(), net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        try {
            net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient.STREAM_CODEC.encode(buffer, ingredient);
            var restored = net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient.STREAM_CODEC.decode(buffer);
            helper.assertTrue(restored.amount() == 900 && restored.test(complex(1f, 900))
                            && !restored.test(complex(0.899f, 900)) && !restored.test(complex(1.101f, 900)),
                    "Network ingredient round trip preserves counterion handling and strict concentration bounds");
        } finally {
            buffer.release();
        }
        var pos = helper.absolutePos(new BlockPos(1, 3, 1));
        helper.getLevel().removeBlock(pos, false);
        helper.getLevel().removeBlock(pos.below(), false);
        helper.getLevel().setBlockAndUpdate(pos, AllBlocks.BASIN.getDefaultState());
        var basin = (BasinBlockEntity) helper.getLevel().getBlockEntity(pos);
        basin.tick();
        var input = basin.inputTank.getCapability();
        helper.assertTrue(input.fill(complex(1f, 900), IFluidHandler.FluidAction.EXECUTE) == 900, "Basin accepts 900 mB mixture");
        helper.assertTrue(input.fill(new FluidStack((net.minecraft.world.level.material.Fluid) AllFluids.HONEY.getSource(), 100), IFluidHandler.FluidAction.EXECUTE) == 100,
                "Basin accepts 100 mB honey in a separate input tank");
        helper.assertTrue(BasinRecipe.match(basin, mixing), "Real basin matches finishing recipe: " + input.getFluidInTank(0)
                + " / " + input.getFluidInTank(1));
        helper.assertTrue(BasinRecipe.apply(basin, mixing), "Real basin executes finishing recipe");
        for (int tank = 0; tank < input.getTanks(); tank++) {
            helper.assertTrue(input.getFluidInTank(tank).isEmpty(), "Exactly 900 mB mixture and 100 mB honey are consumed");
        }
        var output = basin.getTanks().getSecond().getPrimaryHandler().getFluid();
        helper.assertTrue(output.getFluid() == ModFluids.MIRROR_COATING_FLUID.get() && output.getAmount() == 1000,
                "Exactly 1000 mB shared coating fluid is produced");
        helper.succeed();
    }

    private static LegacyMixture acid(float proton, float nitrate) {
        return new LegacyMixture().addMolecule(DestroyMolecules.WATER, 50f)
                .addMolecule(DestroyMolecules.PROTON, proton).addMolecule(DestroyMolecules.NITRATE, nitrate);
    }

    private static FluidStack complex(float concentration, int amount) {
        return MixtureFluid.of(amount, new LegacyMixture().addMolecule(DestroyMolecules.WATER, 50f)
                .addMolecule(DestroyChemistry.DIAMMINESILVER_I, concentration).addMolecule(DestroyMolecules.NITRATE, concentration));
    }

    private static void near(GameTestHelper helper, float actual, float expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 0.001f, message + ": " + actual + " != " + expected);
    }
}
