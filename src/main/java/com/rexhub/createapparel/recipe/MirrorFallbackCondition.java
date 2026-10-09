package com.rexhub.createapparel.recipe;

import com.google.gson.JsonObject;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.config.ModConfigs;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

public final class MirrorFallbackCondition implements ICondition {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "mirror_coating_fallback");
    public static final MirrorFallbackCondition INSTANCE = new MirrorFallbackCondition();
    public static final IConditionSerializer<MirrorFallbackCondition> SERIALIZER = new IConditionSerializer<>() {
        @Override public void write(JsonObject json, MirrorFallbackCondition value) { }
        @Override public MirrorFallbackCondition read(JsonObject json) { return INSTANCE; }
        @Override public ResourceLocation getID() { return ID; }
    };

    private MirrorFallbackCondition() { }
    @Override public ResourceLocation getID() { return ID; }
    @Override public boolean test(IContext context) { return ModConfigs.COMMON.enableMirrorCoatingFallback.get(); }
}
