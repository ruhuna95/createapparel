package com.rexhub.createapparel;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class TestRegistries {
    private TestRegistries() { }

    public static RegistryAccess lookup() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY) : server.registryAccess();
    }

    public static java.util.List<net.minecraft.world.item.Item> items(net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        return BuiltInRegistries.ITEM.getTag(tag).map(values -> values.stream().map(net.minecraft.core.Holder::value).toList())
                .orElseGet(java.util.List::of);
    }
}
