package com.rexhub.createapparel.multiplayer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class MultiplayerProbe {
    static final Path ROOT = Path.of(System.getProperty("apparel.multiplayer.root", "run/multiplayer-tests/disabled"));
    static final String SESSION = Boolean.getBoolean("apparel.multiplayer.resume") ? "resume" : "initial";

    private MultiplayerProbe() { }

    static Path file(String name) { return ROOT.resolve(SESSION + "-" + name); }

    static void write(CompoundTag plan) throws IOException {
        Files.createDirectories(ROOT);
        var temporary = file("plan.tmp");
        NbtIo.writeCompressed(plan, temporary.toFile());
        Files.move(temporary, file("plan.nbt"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    static void mark(String name, String value) throws IOException {
        Files.writeString(file(name + ".txt"), value);
    }

    static boolean marked(String name) { return Files.isRegularFile(file(name + ".txt")); }

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
