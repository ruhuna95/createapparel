package com.rexhub.createapparel.client.model;

import com.rexhub.createapparel.CreateApparel;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public final class ModPartialModels {
    public static final PartialModel SPINNING_MACHINE_ROTATING = block("spinning_machine/rotating");
    public static final PartialModel SPINNING_MACHINE_BOBBIN_THREAD = block("spinning_machine/bobbin_thread");
    public static final PartialModel LOOM_SLIDE = block("loom/slide");
    public static final PartialModel LOOM_SLIDE_WITH_THREAD = block("loom/slide_working");
    public static final PartialModel SEWING_MACHINE_ROTATING = block("sewing_machine/rotating");
    public static final PartialModel SEWING_MACHINE_PISTON = block("sewing_machine/piston");
    public static final PartialModel SEWING_MACHINE_THREAD = block("sewing_machine/thread");

    private ModPartialModels() {
    }

    private static PartialModel block(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "block/" + path));
    }
}
