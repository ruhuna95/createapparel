package com.rexhub.createapparel.data.client;

import com.google.common.hash.HashCode;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class AdvertisementIconProvider implements DataProvider {
    private final Path output;
    public AdvertisementIconProvider(PackOutput output) { this.output = output.getOutputFolder().resolve("assets/createapparel/textures/item"); }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        try {
            for (String name : java.util.List.of("billboard_panel", "signage_panel")) {
                var path = output.resolve(name + ".png");
                if (!Files.isRegularFile(path)) throw new java.io.IOException("Render advertisement icons with runFashionTestClient -PgenerateAdvertisementIcons=true before runData");
                var data = Files.readAllBytes(path);
                cache.writeIfNeeded(path, data, HashCode.fromBytes(java.security.MessageDigest.getInstance("SHA-1").digest(data)));
            }
            return CompletableFuture.completedFuture(null);
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException exception) { return CompletableFuture.failedFuture(exception); }
    }
    @Override public String getName() { return "Create: Apparel rendered advertisement item icons"; }
}
