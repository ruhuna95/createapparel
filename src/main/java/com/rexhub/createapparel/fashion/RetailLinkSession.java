package com.rexhub.createapparel.fashion;

import com.rexhub.createapparel.init.ModItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

public final class RetailLinkSession {
    private static final Map<Player, Session> SESSIONS = new WeakHashMap<>();
    private record Session(PurchasedRegisterRef register, InteractionHand hand, int slot, ItemStack phone) { }

    private RetailLinkSession() { }

    public static void start(Player player, InteractionHand hand, PurchasedRegisterRef register) {
        if (player.level().isClientSide) return;
        SESSIONS.put(player, new Session(register, hand, player.getInventory().selected, player.getItemInHand(hand)));
        player.getPersistentData().put("ApparelRegisterLink", register.save());
        if (player instanceof net.minecraft.server.level.ServerPlayer server)
            com.rexhub.createapparel.network.ModNetwork.sendRetailLinkSession(server,
                    new com.rexhub.createapparel.network.RetailLinkSessionPacket(register, hand, player.getInventory().selected));
    }

    @Nullable public static PurchasedRegisterRef selected(Player player) {
        if (player.level().isClientSide) return null;
        var session = SESSIONS.get(player);
        if (session == null || player.getInventory().selected != session.slot()
                || !player.getItemInHand(session.hand()).is(ModItems.SMART_PHONE.get())
                || !ItemStack.isSameItemSameComponents(player.getItemInHand(session.hand()), session.phone())) {
            clear(player);
            return null;
        }
        return session.register();
    }

    public static void carriedSlotChanged(Player player, int slot) {
        if (slot >= 0 && slot < 9 && slot != player.getInventory().selected) clear(player);
    }

    public static void clear(Player player) {
        if (player.level().isClientSide) return;
        var previous = SESSIONS.remove(player);
        player.getPersistentData().remove("ApparelRegisterLink");
        if (previous != null && player instanceof net.minecraft.server.level.ServerPlayer server)
            com.rexhub.createapparel.network.ModNetwork.sendRetailLinkSession(server,
                    new com.rexhub.createapparel.network.RetailLinkSessionPacket(null, InteractionHand.MAIN_HAND, 0));
    }
}
