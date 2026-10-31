package com.rexhub.createapparel.block.entity;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

final class MachineOutputTransport {
    private static final Direction[] OUTPUT_DIRECTIONS = Direction.values();

    private MachineOutputTransport() {
    }

    static void eject(KineticBlockEntity source, ItemStack result) {
        Level level = source.getLevel();
        if (level == null || result.isEmpty()) {
            return;
        }

        BlockPos sourcePos = source.getBlockPos();
        ItemStack remainder = exportThroughFunnels(source, result);
        if (remainder.isEmpty()) {
            return;
        }

        for (Direction direction : OUTPUT_DIRECTIONS) {
            remainder = insertIntoReceivingFunnel(level, sourcePos, remainder, direction);
            if (remainder.isEmpty()) {
                return;
            }
        }
        for (Direction direction : OUTPUT_DIRECTIONS) {
            remainder = insertIntoDirectOutput(level, sourcePos.relative(direction), remainder, direction);
            if (remainder.isEmpty()) {
                return;
            }
        }
        for (Direction direction : OUTPUT_DIRECTIONS) {
            remainder = insertIntoItemHandler(level, sourcePos, remainder, direction);
            if (remainder.isEmpty()) {
                return;
            }
        }

        spawnResult(level, remainder, Vec3.atBottomCenterOf(sourcePos.above()), Vec3.ZERO);
    }

    static ItemStack exportThroughFunnels(KineticBlockEntity source, ItemStack result) {
        Level level = source.getLevel();
        if (level == null || result.isEmpty()) {
            return result;
        }

        ItemStack remainder = ejectThroughAttachedFunnel(level, source.getBlockPos(), result.copy());
        if (remainder.isEmpty()) {
            return remainder;
        }

        DirectBeltInputBehaviour ownBeltInput = source.getBehaviour(DirectBeltInputBehaviour.TYPE);
        if (ownBeltInput != null) {
            ItemStack beltFunnelRemainder = ownBeltInput.tryExportingToBeltFunnel(remainder, null, false);
            if (beltFunnelRemainder != null) {
                remainder = beltFunnelRemainder;
            }
        }
        return remainder;
    }

    private static ItemStack ejectThroughAttachedFunnel(Level level, BlockPos sourcePos, ItemStack stack) {
        for (Direction direction : OUTPUT_DIRECTIONS) {
            BlockPos funnelPos = sourcePos.relative(direction);
            if (!level.hasChunk(funnelPos.getX() >> 4, funnelPos.getZ() >> 4)) {
                continue;
            }
            BlockState funnelState = level.getBlockState(funnelPos);
            if (funnelState.getOptionalValue(BlockStateProperties.POWERED).orElse(false)
                    || FunnelBlock.getFunnelFacing(funnelState) != direction) {
                continue;
            }

            boolean beltFunnel = isExtractingBeltFunnel(level, funnelPos, funnelState, direction);
            if (!beltFunnel && (!(funnelState.getBlock() instanceof FunnelBlock)
                    || !funnelState.getValue(FunnelBlock.EXTRACTING))) {
                continue;
            }

            FilteringBehaviour filter = BlockEntityBehaviour.get(level, funnelPos, FilteringBehaviour.TYPE);
            if (filter != null && !filter.test(stack)) {
                continue;
            }

            if (beltFunnel) {
                ItemStack remainder = insertIntoDirectOutput(level, funnelPos.below(), stack, direction);
                int transferred = stack.getCount() - remainder.getCount();
                if (transferred > 0) {
                    notifyFunnel(level, funnelPos, stack.copyWithCount(transferred));
                    stack = remainder;
                    if (stack.isEmpty()) {
                        return stack;
                    }
                }
                continue;
            }

            double outputOffset = direction.getAxis().isVertical()
                    ? direction == Direction.UP ? 0.15D : 0.5D
                    : 0.25D;
            Vec3 outputPosition = Vec3.atCenterOf(funnelPos)
                    .add(Vec3.atLowerCornerOf(direction.getNormal()).scale(outputOffset));
            if (direction.getAxis().isHorizontal()) {
                outputPosition = outputPosition.subtract(0.0D, 0.45D, 0.0D);
            }
            Vec3 outputMotion = direction == Direction.UP ? new Vec3(0.0D, 0.25D, 0.0D) : Vec3.ZERO;
            spawnResult(level, stack, outputPosition, outputMotion);

            notifyFunnel(level, funnelPos, stack);
            return ItemStack.EMPTY;
        }
        return stack;
    }

    private static boolean isExtractingBeltFunnel(Level level, BlockPos pos, BlockState state, Direction direction) {
        if (!(state.getBlock() instanceof BeltFunnelBlock)) {
            return false;
        }
        return switch (state.getValue(BeltFunnelBlock.SHAPE)) {
            case PUSHING -> true;
            case PULLING -> false;
            case RETRACTED, EXTENDED -> {
                var belt = BeltHelper.getSegmentBE(level, pos.below());
                yield belt != null && belt.getMovementFacing() == direction;
            }
        };
    }

    private static void notifyFunnel(Level level, BlockPos funnelPos, ItemStack transferred) {
        BlockEntity blockEntity = level.getBlockEntity(funnelPos);
        if (blockEntity instanceof FunnelBlockEntity funnelBlockEntity) {
            funnelBlockEntity.onTransfer(transferred);
            funnelBlockEntity.flap(false);
        }
    }

    private static ItemStack insertIntoReceivingFunnel(
            Level level,
            BlockPos sourcePos,
            ItemStack stack,
            Direction direction
    ) {
        BlockPos funnelPos = sourcePos.relative(direction);
        BlockState funnelState = level.getBlockState(funnelPos);
        if (!(funnelState.getBlock() instanceof FunnelBlock)
                || funnelState.getValue(FunnelBlock.EXTRACTING)
                || funnelState.getOptionalValue(BlockStateProperties.POWERED).orElse(false)
                || FunnelBlock.getFunnelFacing(funnelState) != direction.getOpposite()) {
            return stack;
        }
        return FunnelBlock.tryInsert(level, funnelPos, stack.copy(), false);
    }

    private static ItemStack insertIntoDirectOutput(
            Level level,
            BlockPos targetPos,
            ItemStack stack,
            Direction direction
    ) {
        DirectBeltInputBehaviour output = BlockEntityBehaviour.get(
                level,
                targetPos,
                DirectBeltInputBehaviour.TYPE
        );
        if (output == null || !output.canInsertFromSide(direction) || output.isOccupied(direction)) {
            return stack;
        }
        return output.handleInsertion(stack.copy(), direction, false);
    }

    private static ItemStack insertIntoItemHandler(
            Level level,
            BlockPos sourcePos,
            ItemStack stack,
            Direction direction
    ) {
        BlockEntity blockEntity = level.getBlockEntity(sourcePos.relative(direction));
        if (blockEntity == null) {
            return stack;
        }
        IItemHandler handler = blockEntity.getCapability(
                        ForgeCapabilities.ITEM_HANDLER,
                        direction.getOpposite()
                )
                .orElse(null);
        if (handler == null) {
            return stack;
        }
        return ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
    }

    private static void spawnResult(Level level, ItemStack stack, Vec3 position, Vec3 motion) {
        ItemEntity itemEntity = new ItemEntity(level, position.x, position.y, position.z, stack);
        itemEntity.setDefaultPickUpDelay();
        itemEntity.setDeltaMovement(motion);
        level.addFreshEntity(itemEntity);
    }
}
