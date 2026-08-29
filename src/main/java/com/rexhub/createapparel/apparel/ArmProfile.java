package com.rexhub.createapparel.apparel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public enum ArmProfile implements StringRepresentable {
    CLASSIC("classic"),
    SLIM("slim");

    private final String serializedName;

    ArmProfile(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public static ArmProfile fromSerializedName(String name) {
        for (ArmProfile profile : values()) {
            if (profile.serializedName.equals(name)) {
                return profile;
            }
        }
        throw new IllegalArgumentException("Unknown arm profile: " + name);
    }

    public static ArmProfile defaultFor(Player player) {
        GameProfile gameProfile = player.getGameProfile();
        if (gameProfile != null) {
            for (Property property : gameProfile.getProperties().get("textures")) {
                try {
                    String decoded = new String(
                            Base64.getDecoder().decode(property.getValue()),
                            StandardCharsets.UTF_8
                    );
                    JsonObject root = JsonParser.parseString(decoded).getAsJsonObject();
                    JsonObject textures = root.getAsJsonObject("textures");
                    if (textures == null || !textures.has("SKIN")) {
                        continue;
                    }
                    JsonObject skin = textures.getAsJsonObject("SKIN");
                    JsonObject metadata = skin.getAsJsonObject("metadata");
                    if (metadata != null
                            && metadata.has("model")
                            && "slim".equals(metadata.get("model").getAsString())) {
                        return SLIM;
                    }
                    return CLASSIC;
                } catch (RuntimeException exception) {
                    // Fall through to the deterministic default skin profile.
                }
            }
        }
        return Math.floorMod(player.getUUID().hashCode(), 18) < 9 ? SLIM : CLASSIC;
    }
}
