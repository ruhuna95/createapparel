package com.rexhub.createapparel.entity;

import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.IForgeShearable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class Silkworm extends PathfinderMob implements IForgeShearable {
    public static final int COCOONING_DURATION_TICKS = 20 * 60;
    private static final int COCOONING_PARTICLE_INTERVAL_TICKS = 20;
    private static final int COCOONING_PARTICLE_COUNT = 2;
    private static final int LEAF_SEARCH_HORIZONTAL_RADIUS = 4;
    private static final int LEAF_SEARCH_VERTICAL_RADIUS = 3;
    private static final float PLAYER_LOOK_DISTANCE = 6.0F;
    private static final EntityDataAccessor<Byte> DATA_STATE =
            SynchedEntityData.defineId(Silkworm.class, EntityDataSerializers.BYTE);
    private static final String STATE_TAG = "SilkwormState";
    private static final String COCOONING_TICKS_TAG = "CocooningTicks";

    private int cocooningTicks;

    public Silkworm(EntityType<? extends Silkworm> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.12D)
                .add(Attributes.FOLLOW_RANGE, 10.0D);
    }

    public static boolean checkSpawnRules(
            EntityType<Silkworm> entityType,
            LevelAccessor level,
            MobSpawnType spawnType,
            BlockPos pos,
            RandomSource random
    ) {
        return Mob.checkMobSpawnRules(entityType, level, spawnType, pos, random)
                && level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON)
                && level.getRawBrightness(pos, 0) > 8
                && hasNearbyLeaves(level, pos);
    }

    private static boolean hasNearbyLeaves(LevelAccessor level, BlockPos origin) {
        BlockPos min = origin.offset(
                -LEAF_SEARCH_HORIZONTAL_RADIUS,
                -LEAF_SEARCH_VERTICAL_RADIUS,
                -LEAF_SEARCH_HORIZONTAL_RADIUS
        );
        BlockPos max = origin.offset(
                LEAF_SEARCH_HORIZONTAL_RADIUS,
                LEAF_SEARCH_VERTICAL_RADIUS,
                LEAF_SEARCH_HORIZONTAL_RADIUS
        );
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos).is(BlockTags.LEAVES)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TemptGoal(this, 0.8D, Ingredient.of(ModTags.Items.SILKWORM_FOOD), false));
        goalSelector.addGoal(2, new RandomStrollGoal(this, 0.6D, 120));
        goalSelector.addGoal(3, new SilkwormLookAtPlayerGoal(this, PLAYER_LOOK_DISTANCE));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_STATE, SilkwormState.LARVA.id());
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && getSilkwormState() == SilkwormState.COCOONING) {
            if (cocooningTicks > 0) {
                --cocooningTicks;
                if (cocooningTicks % COCOONING_PARTICLE_INTERVAL_TICKS == 0) {
                    spawnCocooningParticles();
                }
            }
            if (cocooningTicks <= 0) {
                setSilkwormState(SilkwormState.COCOONED);
            }
        }
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || getSilkwormState() == SilkwormState.COCOONED;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (getSilkwormState() == SilkwormState.LARVA && stack.is(ModTags.Items.SILKWORM_FOOD)) {
            if (!level().isClientSide) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                setPersistenceRequired();
                cocooningTicks = COCOONING_DURATION_TICKS;
                setSilkwormState(SilkwormState.COCOONING);
                spawnFeedingHearts();
                playSound(SoundEvents.GENERIC_EAT, 0.5F, 1.1F);
                gameEvent(GameEvent.EAT, player);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    private void spawnFeedingHearts() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.HEART,
                    getX(),
                    getY() + getBbHeight() + 0.2D,
                    getZ(),
                    5,
                    getBbWidth() * 0.5D,
                    0.2D,
                    getBbWidth() * 0.5D,
                    0.0D
            );
        }
    }

    private void spawnCocooningParticles() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WHITE_CONCRETE.defaultBlockState()),
                    getX(),
                    getY() + 0.1D,
                    getZ(),
                    COCOONING_PARTICLE_COUNT,
                    getBbWidth() * 0.25D,
                    0.05D,
                    getBbWidth() * 0.25D,
                    0.01D
            );
        }
    }

    private static boolean isHoldingSilkwormFood(Player player) {
        return player.getMainHandItem().is(ModTags.Items.SILKWORM_FOOD)
                || player.getOffhandItem().is(ModTags.Items.SILKWORM_FOOD);
    }

    public SilkwormState getSilkwormState() {
        return SilkwormState.byId(entityData.get(DATA_STATE));
    }

    private void setSilkwormState(SilkwormState state) {
        entityData.set(DATA_STATE, state.id());
        setNoAi(state == SilkwormState.COCOONED);
        boolean movementEnabled = state == SilkwormState.LARVA;
        goalSelector.setControlFlag(Goal.Flag.MOVE, movementEnabled);
        goalSelector.setControlFlag(Goal.Flag.JUMP, movementEnabled);
        if (state != SilkwormState.LARVA) {
            getNavigation().stop();
        } else {
            cocooningTicks = 0;
        }
    }

    @Override
    public MobType getMobType() {
        return MobType.ARTHROPOD;
    }

    @Override
    public boolean isShearable(ItemStack item, Level level, BlockPos pos) {
        return isAlive() && getSilkwormState() == SilkwormState.COCOONED;
    }

    @Override
    public List<ItemStack> onSheared(
            @Nullable Player player,
            ItemStack item,
            Level level,
            BlockPos pos,
            int fortune
    ) {
        level.playSound(null, this, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
        gameEvent(GameEvent.SHEAR, player);
        setSilkwormState(SilkwormState.LARVA);
        return List.of(new ItemStack(ModItems.COCOON.get()));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte(STATE_TAG, getSilkwormState().id());
        tag.putInt(COCOONING_TICKS_TAG, cocooningTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        cocooningTicks = Math.max(0, tag.getInt(COCOONING_TICKS_TAG));
        setSilkwormState(SilkwormState.byId(tag.getByte(STATE_TAG)));
    }

    private static final class SilkwormLookAtPlayerGoal extends LookAtPlayerGoal {
        private final Silkworm silkworm;
        private final TargetingConditions cocooningLookAtContext;

        private SilkwormLookAtPlayerGoal(Silkworm silkworm, float lookDistance) {
            super(silkworm, Player.class, lookDistance);
            this.silkworm = silkworm;
            this.cocooningLookAtContext = TargetingConditions.forNonCombat()
                    .range(lookDistance)
                    .selector(entity -> EntitySelector.notRiding(silkworm).test(entity)
                            && entity instanceof Player player
                            && isHoldingSilkwormFood(player));
        }

        @Override
        public boolean canUse() {
            if (silkworm.getSilkwormState() == SilkwormState.LARVA) {
                return super.canUse();
            }
            if (silkworm.getSilkwormState() != SilkwormState.COCOONING
                    || silkworm.getRandom().nextFloat() >= probability) {
                return false;
            }
            lookAt = silkworm.level().getNearestPlayer(
                    cocooningLookAtContext,
                    silkworm,
                    silkworm.getX(),
                    silkworm.getEyeY(),
                    silkworm.getZ()
            );
            return lookAt != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (silkworm.getSilkwormState() == SilkwormState.LARVA) {
                return super.canContinueToUse();
            }
            return silkworm.getSilkwormState() == SilkwormState.COCOONING
                    && lookAt instanceof Player player
                    && isHoldingSilkwormFood(player)
                    && super.canContinueToUse();
        }
    }
}
