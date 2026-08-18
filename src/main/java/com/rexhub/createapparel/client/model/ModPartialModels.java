package com.rexhub.createapparel.client.model;

import com.rexhub.createapparel.CreateApparel;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public final class ModPartialModels {
    public static final PartialModel SPINNING_MACHINE_ROTATING = block("spinning_machine/rotating");
    public static final PartialModel SPINNING_MACHINE_BOBBIN_THREAD = block("spinning_machine/bobbin_thread");
    public static final PartialModel LOOM_SLIDE = block("loom/slide");
    public static final PartialModel LOOM_SLIDE_WORKING = block("loom/slide_working");

    private ModPartialModels() {
    }

    public static void bootstrap() {
    }

    private static PartialModel block(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "block/" + path));
    }
}
