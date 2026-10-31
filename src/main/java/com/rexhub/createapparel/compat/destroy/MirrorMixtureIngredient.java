package com.rexhub.createapparel.compat.destroy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rexhub.createapparel.CreateApparel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.crafting.FluidIngredientType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import petrolpark.mc.destroy.core.recipe.ingredient.fluid.IonFluidIngredient;

import java.util.function.Supplier;

public final class MirrorMixtureIngredient extends IonFluidIngredient {
    public static final MapCodec<MirrorMixtureIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("molecule").forGetter(ingredient -> ingredient.ionId),
            Codec.FLOAT.fieldOf("min_concentration").forGetter(ingredient -> ingredient.minConcentration),
            Codec.FLOAT.fieldOf("max_concentration").forGetter(ingredient -> ingredient.maxConcentration)
    ).apply(instance, MirrorMixtureIngredient::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, MirrorMixtureIngredient> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ingredient -> ingredient.ionId,
            ByteBufCodecs.FLOAT, ingredient -> ingredient.minConcentration,
            ByteBufCodecs.FLOAT, ingredient -> ingredient.maxConcentration,
            MirrorMixtureIngredient::new);
    private static final DeferredRegister<FluidIngredientType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_INGREDIENT_TYPES, CreateApparel.MOD_ID);
    public static final Supplier<FluidIngredientType<MirrorMixtureIngredient>> TYPE =
            TYPES.register("mirror_mixture", () -> new FluidIngredientType<>(CODEC, STREAM_CODEC));

    public MirrorMixtureIngredient(String molecule, float minimum, float maximum) {
        super(molecule, minimum, maximum);
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    @Override
    public FluidIngredientType<?> getType() {
        return TYPE.get();
    }
}
