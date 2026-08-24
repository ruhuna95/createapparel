package com.rexhub.createapparel.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class ModConfigs {
    private static final ForgeConfigSpec COMMON_SPEC;
    private static final ForgeConfigSpec CLIENT_SPEC;

    static {
        COMMON_SPEC = new ForgeConfigSpec.Builder().configure(Common::new).getRight();
        CLIENT_SPEC = new ForgeConfigSpec.Builder().configure(Client::new).getRight();
    }

    private ModConfigs() {
    }

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "createapparel-common.toml");
        context.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "createapparel-client.toml");
    }

    private static final class Common {
        private Common(ForgeConfigSpec.Builder builder) {
        }
    }

    private static final class Client {
        private Client(ForgeConfigSpec.Builder builder) {
        }
    }
}
