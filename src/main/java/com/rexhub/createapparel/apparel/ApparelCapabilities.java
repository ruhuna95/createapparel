package com.rexhub.createapparel.apparel;

import com.rexhub.createapparel.CreateApparel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Optional;

public final class ApparelCapabilities {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CreateApparel.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ApparelInventory>> PLAYER_APPAREL =
            ATTACHMENTS.register("apparel_inventory", () -> AttachmentType.serializable(
                    holder -> ApparelInventoryProvider.create((Player) holder)).copyOnDeath().build());

    private ApparelCapabilities() { }

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
    }

    public static Optional<ApparelInventory> get(Player player) {
        return Optional.of(player.getData(PLAYER_APPAREL));
    }
}
