package com.rexhub.createapparel.item;

import com.rexhub.createapparel.entity.MannequinVariant;
import com.rexhub.createapparel.init.ModEntities;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public final class MannequinItem extends Item {
    private final MannequinVariant variant;

    public MannequinItem(MannequinVariant variant) {
        super(new Properties().stacksTo(16));
        this.variant = variant;
    }

    public MannequinVariant variant() { return variant; }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() == Direction.DOWN) return InteractionResult.FAIL;
        var placement = new BlockPlaceContext(context);
        var pos = placement.getClickedPos();
        var player = context.getPlayer();
        if (player != null && !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())) {
            return InteractionResult.FAIL;
        }
        var level = context.getLevel();
        var type = ModEntities.mannequins().get(variant).get();
        Vec3 location = Vec3.atBottomCenterOf(pos);
        var bounds = type.getDimensions().makeBoundingBox(location);
        if (!level.noCollision(null, bounds) || !level.getEntities(null, bounds).isEmpty()) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel) {
            var mannequin = type.create(serverLevel);
            if (mannequin == null) return InteractionResult.FAIL;
            float yaw = Mth.floor((Mth.wrapDegrees(context.getRotation() - 180) + 22.5F) / 45) * 45;
            mannequin.moveTo(location.x, location.y, location.z, yaw, 0);
            if (context.getItemInHand().hasCustomHoverName()) {
                mannequin.setCustomName(context.getItemInHand().getHoverName());
            }
            if (!serverLevel.addFreshEntity(mannequin)) return InteractionResult.FAIL;
            level.playSound(null, location.x, location.y, location.z,
                    SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 0.75F, 0.8F);
            mannequin.gameEvent(GameEvent.ENTITY_PLACE, player);
            if (player == null || !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
