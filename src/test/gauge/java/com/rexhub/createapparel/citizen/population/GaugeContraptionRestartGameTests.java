package com.rexhub.createapparel.citizen.population;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity;
import com.rexhub.createapparel.init.ModBlocks;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GaugeContraptionRestartGameTests {
    private GaugeContraptionRestartGameTests() { }
    private static java.io.File expectedFile(ServerLevel level) {
        return level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("gauge-moving-expected.dat").toFile();
    }
    private static CitizenGaugeBlockEntity panel(ServerLevel level, BlockPos pos, boolean district) {
        level.setBlock(pos.south(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.CITIZEN_GAUGE.get().defaultBlockState(), 3);
        var entity = (CitizenGaugeBlockEntity) level.getBlockEntity(pos); entity.initialize(district ? pos.south(4) : null); return entity;
    }
    private static void force(ServerLevel level, BlockPos pos, boolean value) {
        var chunk = new ChunkPos(pos); level.setChunkForced(chunk.x, chunk.z, value);
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_moving_restart", timeoutTicks = 600)
    public static void nativeContraptionAndMissingOwnerRecoverAcrossSeparateServerProcesses(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); var manager = CitizenPopulationManager.get(level); var data = manager.data();
        if (Boolean.getBoolean("apparel.gaugeTest.movingResume")) {
            var expected = NbtIo.readCompressed(expectedFile(level)); var id = expected.getUUID("Gauge"); var district = expected.getUUID("District");
            force(level, BlockPos.of(expected.getLong("OwnerPos")), true); force(level, BlockPos.of(expected.getLong("PeerPos")), true); force(level, BlockPos.of(expected.getLong("LostPos")), true);
            helper.succeedWhen(() -> {
                helper.assertTrue(level.getEntity(expected.getUUID("Owner")) instanceof OrientedContraptionEntity, "Native Contraption entity must restore from the actual entity-region save");
                helper.assertTrue(data.gauge(expected.getUUID("LostGauge")) == null, "A missing entity in a loaded entity-ticking chunk is eventually reconciled after restart");
                var owner = (OrientedContraptionEntity) level.getEntity(expected.getUUID("Owner"));
                helper.assertTrue(data.ownedByContraption(id, owner.getUUID()) && data.gauge(id).districtId().equals(district)
                        && data.linked(id).contains(expected.getUUID("Peer")) && district.equals(data.citizen(expected.getUUID("Resident")).districtId()), "Moving Gauge, District, links and resident survive the process boundary");
                helper.assertTrue(data.citizen(expected.getUUID("LostResident")) != null && data.citizen(expected.getUUID("LostResident")).districtId() == null, "Missing carrier never deletes its Citizen");
                owner.setPos(owner.position().add(16, 0, 0)); owner.disassemble();
                helper.assertTrue(data.gauge(id) != null && !data.moving(id) && manager.loaded(data.gauge(id))
                        && data.gauge(id).districtId().equals(district) && data.linked(id).contains(expected.getUUID("Peer"))
                        && district.equals(data.citizen(expected.getUUID("Resident")).districtId()), "Restored native owner reassembles without reallocating Gauge or District IDs");
                force(level, BlockPos.of(expected.getLong("OwnerPos")), false); force(level, BlockPos.of(expected.getLong("PeerPos")), false); force(level, BlockPos.of(expected.getLong("LostPos")), false);
                LogUtils.getLogger().info("CITIZEN_CONTRAPTION_RESTART_PASSED gauge={} district={}", id, district);
            }); return;
        }
        var pos = new BlockPos(256, -58, 256); var peerPos = pos.east(8); var lostPos = pos.east(32);
        force(level, pos, true); force(level, peerPos, true); force(level, lostPos, true);
        panel(level, pos, true); var peer = panel(level, peerPos, false); var lost = panel(level, lostPos, true);
        var gauge = data.at(pos); var lostGauge = data.at(lost.getBlockPos()); var resident = UUID.randomUUID(); var lostResident = UUID.randomUUID();
        data.register(resident, new CitizenPopulationState(gauge.districtId(), false, false, 0)); data.register(lostResident, new CitizenPopulationState(lostGauge.districtId(), false, false, 0));
        manager.link(gauge.id(), peer.gaugeId()); var support = pos.south(); var contraption = new BearingContraption(false, Direction.UP);
        helper.assertTrue(contraption.assemble(level, support.below()), "Native assembly captures the persistent Gauge");
        contraption.removeBlocksFromWorld(level, BlockPos.ZERO); contraption.getStorage().initialize();
        var owner = OrientedContraptionEntity.create(level, contraption, Direction.NORTH); owner.setPos(support.getX(), support.getY(), support.getZ());
        helper.assertTrue(level.addFreshEntity(owner) && data.ownedByContraption(gauge.id(), owner.getUUID()), "Native entity binds before world save");
        level.setBlock(lostPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_ALL);
        var absent = UUID.randomUUID(); var lostOwnerPos = new BlockPos(65536, -58, 65536);
        data.bindMoving(lostGauge.id(), absent); data.movingLocation(absent, new ChunkPos(lostOwnerPos).toLong()); force(level, lostPos, false);
        var expected = new CompoundTag(); expected.putUUID("Gauge", gauge.id()); expected.putUUID("District", gauge.districtId()); expected.putUUID("Peer", peer.gaugeId());
        expected.putUUID("Owner", owner.getUUID()); expected.putUUID("Resident", resident); expected.putUUID("LostGauge", lostGauge.id()); expected.putUUID("LostResident", lostResident);
        expected.putLong("OwnerPos", owner.blockPosition().asLong()); expected.putLong("PeerPos", peerPos.asLong()); expected.putLong("LostPos", lostOwnerPos.asLong());
        NbtIo.writeCompressed(expected, expectedFile(level)); level.getServer().saveEverything(true, true, true);
        LogUtils.getLogger().info("CITIZEN_CONTRAPTION_RESTART_SAVED gauge={} owner={}", gauge.id(), owner.getUUID()); helper.succeed();
    }
}
