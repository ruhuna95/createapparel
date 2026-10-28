package com.rexhub.createapparel.advertisement;

import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.fashion.CitizenFashionController;
import com.rexhub.createapparel.fashion.FashionWorldIndex;
import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class AdvertisementFashionSource {
    private AdvertisementFashionSource() { }
    public static List<CitizenFashionController.Source> sources(Citizen citizen) {
        List<CitizenFashionController.Source> result = new ArrayList<>();
        if (!(citizen.level() instanceof ServerLevel level)) return result;
        for (var pos : FashionWorldIndex.get(level).advertisements(citizen.position(), 24)) {
            if (!(level.getBlockEntity(pos) instanceof AdvertisementPanelBlockEntity panel) || !panel.isController()) continue;
            var photo = panel.currentSnapshot().displayPhoto();
            var facing = panel.getBlockState().getValue(AdvertisementPanelBlock.FACING);
            var position = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.1));
            if (citizen.position().subtract(position).dot(Vec3.atLowerCornerOf(facing.getNormal())) <= 0) continue;
            var outfit = photo.loadout(GarmentDefinitionManager::get);
            for (var slot : GarmentSlot.values()) {
                boolean visible = com.rexhub.createapparel.photo.PhotoVisibility.visible(slot, photo.layout(), panel.width(), panel.height(), outfit);
                if (!visible) outfit.setStackInSlot(slot.ordinal(), net.minecraft.world.item.ItemStack.EMPTY);
            }
            var config = ModConfigs.COMMON.fashion;
            double points = citizen.fashionController().sameDistrict(level, position) ? config.sameAdvertisementPoints.get() : config.otherAdvertisementPoints.get();
            result.add(new CitizenFashionController.Source(panel.groupId(), position, outfit, slot -> panel.linkedRegister(), points, true));
        }
        return result;
    }
}
