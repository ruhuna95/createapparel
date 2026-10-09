package com.rexhub.createapparel.client.mirror;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.culling.Frustum;
import org.jetbrains.annotations.Nullable;

public interface MirrorLevelRendererAccess {
    void createapparel$beginReflection();
    void createapparel$endReflection();

    record Snapshot(ObjectArrayList<Object> chunks, @Nullable Frustum frustum, int renderedEntities, int culledEntities) { }
}
