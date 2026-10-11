package com.rexhub.createapparel.citizen;

import net.minecraft.server.Bootstrap;

final class CitizenColorTestBootstrap {
    private CitizenColorTestBootstrap() { }

    static void initializeTags() throws ReflectiveOperationException {
        // Plain JUnit needs tag keys without Forge's launch-time registry transformations.
        var initialized = Bootstrap.class.getDeclaredField("isBootstrapped");
        initialized.setAccessible(true);
        initialized.setBoolean(null, true);
        net.minecraft.SharedConstants.tryDetectVersion();
        Class.forName("net.minecraft.core.registries.BuiltInRegistries");
    }
}
