package com.rexhub.createapparel.client.compat.valkyrienskies;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public final class MirrorShipOwner {
    @Nullable private static Method lookup;
    @Nullable private static Method shipId;
    private static boolean initialized;
    private static boolean unavailable;
    private record Owner(Level level, long shipId) { }
    private record SeparateOwner(Level level, BlockPos pos) { }

    private MirrorShipOwner() { }

    public static Object owner(Level level, BlockPos pos) {
        if (!initialized) initialize();
        if (lookup == null || shipId == null) return unavailable ? new SeparateOwner(level, pos) : level;
        try {
            Object ship = lookup.invoke(null, level, pos);
            return ship == null ? level : new Owner(level, (long) shipId.invoke(ship));
        } catch (ReflectiveOperationException | LinkageError exception) {
            lookup = null;
            unavailable = true;
            LogUtils.getLogger().warn("VS mirror owner lookup unavailable; keeping mirror surfaces separate", exception);
            return new SeparateOwner(level, pos);
        }
    }

    private static void initialize() {
        initialized = true;
        if (!ModList.get().isLoaded("valkyrienskies")) return;
        try {
            lookup = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt").getMethod("getShipManagingPos", Level.class, BlockPos.class);
            shipId = Class.forName("org.valkyrienskies.core.api.ships.Ship").getMethod("getId");
        } catch (ReflectiveOperationException | LinkageError exception) {
            lookup = null;
            unavailable = true;
            LogUtils.getLogger().warn("VS mirror owner lookup unavailable", exception);
        }
    }
}
