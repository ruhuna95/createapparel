package com.rexhub.createapparel.network;

import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.recipe.SewingRecipe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public record UpdateSewingPatternPacket(
        InteractionHand hand,
        ResourceLocation recipeId,
        String design,
        Optional<FabricColor> secondaryColor,
        Optional<FabricColor> accentColor
) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<UpdateSewingPatternPacket> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.rexhub.createapparel.CreateApparel.MOD_ID, "update_sewing_pattern_packet"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, UpdateSewingPatternPacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), UpdateSewingPatternPacket::decode);

    @Override
    public Type<UpdateSewingPatternPacket> type() {
        return TYPE;
    }

    private static final int MAX_DESIGN_LENGTH = 64;

    public UpdateSewingPatternPacket {
        secondaryColor = secondaryColor == null ? Optional.empty() : secondaryColor;
        accentColor = accentColor == null ? Optional.empty() : accentColor;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(hand);
        buffer.writeResourceLocation(recipeId);
        buffer.writeUtf(design, MAX_DESIGN_LENGTH);
        writeColor(buffer, secondaryColor);
        writeColor(buffer, accentColor);
    }

    public static UpdateSewingPatternPacket decode(FriendlyByteBuf buffer) {
        return new UpdateSewingPatternPacket(
                buffer.readEnum(InteractionHand.class),
                buffer.readResourceLocation(),
                buffer.readUtf(MAX_DESIGN_LENGTH),
                readColor(buffer),
                readColor(buffer)
        );
    }

    public void handle(ServerPlayer player) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ModItems.SEWING_PATTERN.get())) {
            return;
        }

        SewingRecipe recipe = player.level().getRecipeManager()
                .getAllRecipesFor(SewingRecipe.recipeType())
                .stream()
                .filter(candidate -> candidate.id().equals(recipeId))
                .map(net.minecraft.world.item.crafting.RecipeHolder::value)
                .findFirst()
                .orElse(null);
        if (recipe == null || !recipe.matchesPatternItem(stack)) {
            return;
        }

        GarmentDefinition definition = GarmentDefinitionManager.get(recipe.getResultId()).orElse(null);
        if (definition == null || !definition.designs().contains(design)) {
            return;
        }

        new PatternData(recipeId, design, secondaryColor, accentColor).write(stack);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static void writeColor(FriendlyByteBuf buffer, Optional<FabricColor> color) {
        buffer.writeBoolean(color.isPresent());
        color.ifPresent(buffer::writeEnum);
    }

    private static Optional<FabricColor> readColor(FriendlyByteBuf buffer) {
        return buffer.readBoolean()
                ? Optional.of(buffer.readEnum(FabricColor.class))
                : Optional.empty();
    }
}
