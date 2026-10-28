package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.route.CitizenActivityScheduler;
import com.rexhub.createapparel.entity.Citizen;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import javax.annotation.Nullable;

public final class CitizenInteractionController {
    private final Citizen citizen;
    @Nullable private Player viewer;
    @Nullable private AbstractContainerMenu menu;
    private long startedAt;
    private float resumeYaw, resumePitch;

    public CitizenInteractionController(Citizen citizen) { this.citizen = citizen; }
    public boolean active() { return menu != null; }
    public boolean isViewer(Player player, AbstractContainerMenu menu) { return viewer == player && this.menu == menu; }

    public boolean canBegin(Player player) {
        if (citizen.level().isClientSide) return false;
        pruneInvalidViewer();
        return eligible(player) && (viewer == null || viewer == player);
    }

    public boolean begin(Player player, AbstractContainerMenu menu) {
        if (!canBegin(player)) return false;
        if (!active()) {
            citizen.fashionController().removed();
            startedAt = citizen.level().getGameTime();
            resumeYaw = citizen.getYRot();
            resumePitch = citizen.getXRot();
        }
        viewer = player;
        this.menu = menu;
        face(player);
        return true;
    }

    public void end(AbstractContainerMenu menu) {
        if (this.menu != menu) return;
        this.menu = null;
        viewer = null;
        resume();
    }

    public void resumeYaw(float yaw) { if (active()) resumeYaw = yaw; }

    public void tick() {
        if (!active()) return;
        pruneInvalidViewer();
        var player = viewer;
        if (player == null) return;
        if (citizen.level() instanceof ServerLevel level) {
            CitizenActivityScheduler.get(level).forceActive(citizen, CitizenActivityScheduler.Reason.INTERACTION, 40);
        }
        face(player);
    }

    private boolean eligible(Player player) {
        return citizen.isAlive() && !citizen.isRemoved() && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && player.level() == citizen.level() && player.distanceToSqr(citizen) <= 64
                && (!(player instanceof ServerPlayer server) || !server.hasDisconnected());
    }

    private void pruneInvalidViewer() {
        var currentMenu = menu;
        var currentViewer = viewer;
        if (currentMenu != null && (currentViewer == null || !eligible(currentViewer)
                || currentViewer.containerMenu != currentMenu || !currentMenu.stillValid(currentViewer))) end(currentMenu);
    }

    private void face(Player player) {
        citizen.setZza(0);
        citizen.setXxa(0);
        citizen.setJumping(false);
        citizen.setDeltaMovement(0, citizen.getDeltaMovement().y, 0);
        double x = player.getX() - citizen.getX();
        double z = player.getZ() - citizen.getZ();
        double horizontal = Math.sqrt(x * x + z * z);
        if (horizontal > 0.001) {
            float yaw = Mth.wrapDegrees((float) (Mth.atan2(z, x) * 180 / Math.PI) - 90);
            citizen.setYRot(yaw);
            citizen.yBodyRot = citizen.yHeadRot = yaw;
            citizen.setYHeadRot(yaw);
            citizen.setXRot(Mth.clamp((float) (-Mth.atan2(player.getEyeY() - citizen.getEyeY(), horizontal) * 180 / Math.PI), -45, 45));
        }
    }

    private void resume() {
        long elapsed = Math.max(0, citizen.level().getGameTime() - startedAt);
        citizen.intents().resumeAfterInteraction(elapsed);
        citizen.routeMovement().resumeAfterInteraction(elapsed);
        if (citizen.getNavigation() instanceof CitizenNavigation navigation) navigation.resumeAfterInteraction();
        citizen.setYRot(resumeYaw);
        citizen.yBodyRot = citizen.yHeadRot = resumeYaw;
        citizen.setYHeadRot(resumeYaw);
        citizen.setXRot(resumePitch);
    }
}
