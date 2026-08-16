package com.rexhub.createapparel.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.commons.lang3.tuple.Pair;

public final class ModConfigs {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        Pair<Common, ForgeConfigSpec> commonPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = commonPair.getLeft();
        COMMON_SPEC = commonPair.getRight();

        Pair<Client, ForgeConfigSpec> clientPair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();
    }

    private ModConfigs() {
    }

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "createapparel-common.toml");
        context.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "createapparel-client.toml");
    }

    public static final class Common {
        private Common(ForgeConfigSpec.Builder builder) {
        }
    }

    public static final class Client {
        private Client(ForgeConfigSpec.Builder builder) {
        }
    }
}
