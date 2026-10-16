package com.rexhub.createapparel.data.client;

import com.google.common.hash.Hashing;
import net.minecraft.SharedConstants;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public final class PonderStructureProvider implements DataProvider {
    private final PackOutput output;

    public PonderStructureProvider(PackOutput output) { this.output = output; }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return CompletableFuture.runAsync(() -> {
            var root = new CompoundTag();
            root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            root.put("size", integers(7, 6, 7));
            var palette = new ListTag();
            for (String block : new String[]{"minecraft:air", "minecraft:white_concrete", "minecraft:snow_block"}) {
                var entry = new CompoundTag();
                entry.putString("Name", block);
                palette.add(entry);
            }
            root.put("palette", palette);
            var blocks = new ListTag();
            for (int x = 0; x < 7; x++) {
                for (int y = 0; y < 6; y++) {
                    for (int z = 0; z < 7; z++) {
                        var block = new CompoundTag();
                        block.put("pos", integers(x, y, z));
                        boolean floor = y == 0 && x >= 1 && x <= 5 && z >= 1 && z <= 5;
                        block.putInt("state", floor ? 1 + (x + z) % 2 : 0);
                        blocks.add(block);
                    }
                }
            }
            root.put("blocks", blocks);
            root.put("entities", new ListTag());
            try (var bytes = new ByteArrayOutputStream()) {
                NbtIo.writeCompressed(root, bytes);
                byte[] data = bytes.toByteArray();
                cache.writeIfNeeded(output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
                        .resolve("createapparel/ponder/production.nbt"), data, Hashing.sha256().hashBytes(data));
            } catch (IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        });
    }

    private static ListTag integers(int... values) {
        var list = new ListTag();
        for (int value : values) list.add(IntTag.valueOf(value));
        return list;
    }

    @Override
    public String getName() { return "Apparel Ponder structures"; }
}
