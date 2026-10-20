package com.rexhub.createapparel.client.citizen;

import com.rexhub.createapparel.menu.CitizenMenu;
import com.rexhub.createapparel.network.CitizenBehaviorInfoPacket;
import net.minecraft.client.Minecraft;

public final class ClientCitizenBehaviorInfo {
    private ClientCitizenBehaviorInfo() { }

    public static void accept(CitizenBehaviorInfoPacket packet) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof CitizenMenu menu && menu.containerId == packet.containerId()
                && menu.citizen() != null && menu.citizen().getId() == packet.entityId()) {
            menu.acceptFollowDisplay(packet.targetUuid(), packet.display());
            menu.acceptDestination(packet.destination());
            menu.acceptDistrict(packet.districtId(), packet.districtName());
        }
    }
}
