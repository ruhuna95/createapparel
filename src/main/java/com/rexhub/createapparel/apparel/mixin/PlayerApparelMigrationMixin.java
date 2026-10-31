package com.rexhub.createapparel.apparel.mixin;

import com.rexhub.createapparel.apparel.ApparelCapabilities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class PlayerApparelMigrationMixin {
    @Inject(method = "load", at = @At("RETURN"))
    private void createapparel$importLegacyApparel(CompoundTag tag, CallbackInfo callback) {
        if (!((Object) this instanceof Player player)) return;
        String id = "createapparel:apparel_inventory";
        if (tag.getCompound("neoforge:attachments").contains(id)) return;
        var capabilities = tag.getCompound("ForgeCaps");
        if (capabilities.contains(id, Tag.TAG_COMPOUND)) {
            player.getData(ApparelCapabilities.PLAYER_APPAREL)
                    .deserializeNBT(player.registryAccess(), capabilities.getCompound(id));
        }
    }
}
