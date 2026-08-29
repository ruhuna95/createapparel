package com.rexhub.createapparel.client.renderer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.util.List;

public final class PlayerApparelLayers {
    private PlayerApparelLayers() {
    }

    public static void add(PlayerRenderer renderer) {
        List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> layers = layers(renderer);
        int firstArmorLayer = -1;
        for (int index = 0; index < layers.size(); index++) {
            RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer = layers.get(index);
            if (!(layer instanceof HumanoidArmorLayer<?, ?, ?>)) {
                continue;
            }
            if (firstArmorLayer < 0) {
                firstArmorLayer = index;
            }
            layers.set(index, new ArmorVisibilityLayer(renderer, layer));
        }
        if (firstArmorLayer < 0) {
            throw new IllegalStateException("Player renderer has no HumanoidArmorLayer");
        }
        layers.add(firstArmorLayer, new PlayerApparelLayer(renderer));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> layers(
            PlayerRenderer renderer
    ) {
        List<?> layers = ObfuscationReflectionHelper.getPrivateValue(
                LivingEntityRenderer.class,
                renderer,
                "f_115291_"
        );
        if (layers == null) {
            throw new IllegalStateException("Unable to access player render layers");
        }
        return (List) layers;
    }
}
