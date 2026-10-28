package com.rexhub.createapparel.photo;

import com.rexhub.createapparel.apparel.ApparelCapabilities;
import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.entity.*;
import com.rexhub.createapparel.config.ModConfigs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import java.util.UUID;

public final class PhotographyService {
    private PhotographyService() { }
    public static int quote(Citizen citizen) {
        long day = citizen.level().getDayTime() / 24000;
        long seed = ((net.minecraft.server.level.ServerLevel) citizen.level()).getSeed();
        long value = citizen.getUUID().getMostSignificantBits() ^ citizen.getUUID().getLeastSignificantBits() ^ seed ^ day * 0x9E3779B97F4A7C15L;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL; value ^= value >>> 31;
        return ModConfigs.COMMON.fashion.photographyBaseCost.get() + (int) Math.floorMod(value, (long) ModConfigs.COMMON.fashion.photographyExtra.get() + 1);
    }
    public static ApparelLoadout loadout(LivingEntity entity) {
        return entity instanceof Citizen citizen ? citizen.citizenState().loadout()
                : entity instanceof net.minecraft.world.entity.player.Player player ? ApparelCapabilities.get(player).map(value -> (ApparelLoadout) value).orElseGet(ApparelLoadout::new) : new ApparelLoadout();
    }
    public static AdvertisingPhotoData snapshot(LivingEntity entity) {
        CitizenAppearance appearance;
        if (entity instanceof Citizen citizen) appearance = citizen.citizenState().appearance();
        else {
            var apparel = entity instanceof net.minecraft.world.entity.player.Player player ? ApparelCapabilities.get(player).orElse(null) : null;
            appearance = apparel == null ? CitizenAppearance.DEFAULT : new CitizenAppearance(apparel.skinTone(), apparel.armProfile(),
                    apparel.chestProfile(), CitizenAppearance.DEFAULT_HAIR, CitizenAppearance.DEFAULT_HAIR_COLOR, CitizenAppearance.DEFAULT_EYE_COLOR);
        }
        return new AdvertisingPhotoData(new UUID(0, 0), new UUID(0, 0), entity instanceof net.minecraft.world.entity.player.Player ? entity.getUUID() : null,
                appearance, entity instanceof Citizen citizen ? citizen.citizenState().age() : CitizenAge.ADULT,
                AdvertisingPhotoData.garments(loadout(entity)), PosePreset.STANDING, PhotoLayout.DEFAULT);
    }
    public static void open(ServerPlayer player, LivingEntity target, InteractionHand hand) {
        if (!validTarget(player, target) || !player.getItemInHand(hand).is(PhotoRegistries.CAMERA.get())) return;
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
        if (target instanceof ServerPlayer subject && subject != player) {
            long now = player.level().getGameTime();
            if (now < player.getPersistentData().getLong("ApparelPhotographyRequestAfter")) return;
            player.getPersistentData().putLong("ApparelPhotographyRequestAfter", now + 100);
            var preview = snapshot(subject);
            NetworkHooks.openScreen(subject, new SimpleMenuProvider((id, inventory, viewer) ->
                    PhotographyMenu.consent(id, inventory, preview, player.getId(), slot), Component.translatable("screen.createapparel.photo.consent")),
                    buffer -> PhotographyMenu.writeOpening(buffer, PhotographyMenu.Mode.CONSENT, preview, player.getId(), slot, 0));
            return;
        }
        openApproved(player, target, slot);
    }
    public static void openApproved(ServerPlayer player, LivingEntity target, int slot) {
        if (!validTarget(player, target) || !player.getInventory().getItem(slot).is(PhotoRegistries.CAMERA.get())) return;
        if (target instanceof Citizen citizen && !citizen.interaction().canBegin(player)) return;
        var preview = snapshot(target); int cost = target instanceof Citizen citizen ? quote(citizen) : 0;
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, viewer) ->
                PhotographyMenu.capture(id, inventory, preview, target.getId(), slot, cost), Component.translatable("screen.createapparel.photo.capture")),
                buffer -> PhotographyMenu.writeOpening(buffer, PhotographyMenu.Mode.CAPTURE, preview, target.getId(), slot, cost));
    }
    public static boolean validTarget(ServerPlayer player, LivingEntity target) {
        return target.isAlive() && target.level() == player.level() && player.distanceToSqr(target) <= 64
                && player.getServer() != null && !player.isSpectator() && (target instanceof Citizen || target instanceof ServerPlayer);
    }
    public static boolean issue(ServerPlayer player, PhotographyMenu menu) {
        return !menu.takePhoto(player, true).isEmpty();
    }
    public static boolean canPhotograph(ServerPlayer player, PhotographyMenu menu) {
        if (player.containerMenu != menu || menu.mode() != PhotographyMenu.Mode.CAPTURE || !menu.stillValid(player)
                || !(player.level().getEntity(menu.targetId()) instanceof LivingEntity target)
                || !validTarget(player, target) || menu.quoteDay() != player.level().getDayTime() / 24000) return false;
        if (target instanceof Citizen citizen && quote(citizen) != menu.cost()) return false;
        if (!menu.hasPayment()) return false;
        var outfit = menu.loadout();
        for (var slot : com.rexhub.createapparel.garment.GarmentSlot.values()) {
            var garment = outfit.getStack(slot); if (garment.isEmpty()) continue;
            boolean owned = java.util.Arrays.stream(com.rexhub.createapparel.garment.GarmentSlot.values())
                    .anyMatch(wornSlot -> sameAppearance(loadout(target).getStack(wornSlot), garment));
            if (!owned) owned = player.getInventory().items.stream().anyMatch(stack -> sameAppearance(stack, garment));
            if (!owned) return false;
        }
        var preview = menu.preview(); var current = snapshot(target);
        if (!preview.appearance().equals(current.appearance()) || preview.age() != current.age()) return false;
        return true;
    }
    private static boolean sameAppearance(ItemStack owned, ItemStack preview) {
        if (owned.isEmpty() || preview.isEmpty() || owned.getItem() != preview.getItem()) return false;
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(owned.getItem());
        var definition = id == null ? null : com.rexhub.createapparel.garment.GarmentDefinitionManager.get(id).orElse(null);
        if (definition == null) return false;
        try {
            return com.rexhub.createapparel.garment.GarmentAppearance.fromStack(owned, definition)
                    .equals(com.rexhub.createapparel.garment.GarmentAppearance.fromStack(preview, definition));
        } catch (IllegalArgumentException exception) { return false; }
    }
}
