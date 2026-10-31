package com.rexhub.createapparel.fashion;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.citizen.population.CitizenIntent;
import com.rexhub.createapparel.citizen.population.CitizenPopulationManager;
import com.rexhub.createapparel.citizen.route.CitizenActivityScheduler;
import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.Mannequin;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import javax.annotation.Nullable;
import java.util.*;

public final class CitizenFashionController {
    public record Source(Object id, Vec3 position, ApparelLoadout loadout,
                         java.util.function.Function<GarmentSlot, PurchasedRegisterRef> register, double points,
                         boolean seeThroughGlass) { }
    private static final Map<ServerLevel, Budget> BUDGETS = new IdentityHashMap<>();
    private static final class Budget { long tick = -1; int events; final LinkedHashSet<CitizenFashionController> waiting = new LinkedHashSet<>(); }
    private final Citizen citizen;
    private long nextObservation, nextShopping;
    private final LinkedHashMap<Object, Long> observations = new LinkedHashMap<>();
    public CitizenFashionController(Citizen citizen) { this.citizen = citizen; }
    public static void unload(ServerLevel level) { BUDGETS.remove(level); FashionWorldIndex.unload(level); }
    public void removed() {
        if (citizen.level() instanceof ServerLevel level) { var budget = BUDGETS.get(level); if (budget != null) budget.waiting.remove(this); }
    }
    public void tick() {
        if (!(citizen.level() instanceof ServerLevel level) || !citizen.isAlive()) return;
        var fashion = citizen.citizenState().fashion();
        var intent = citizen.intents().active();
        if (fashion.forceFashion()) {
            removed();
            if (intent != null && intent.source() == CitizenIntent.Source.FASHION) citizen.intents().cancel();
            fashion.clearDesire(); return;
        }
        long now = level.getGameTime();
        if (fashion.desiredRegister() != null && (intent == null || intent.source() != CitizenIntent.Source.FASHION)) finish(false);
        if (citizen.isNoAi() || citizen.isPassenger() || citizen.isLeashed() || now < fashion.cooldownUntil()
                || intent != null || CitizenPopulationManager.get(level).reserved(citizen.getUUID())
                || CitizenActivityScheduler.get(level).state(citizen) == CitizenActivityScheduler.State.THROTTLED) { removed(); return; }
        if (nextObservation == 0) {
            nextObservation = now + 20 + Math.floorMod(citizen.getUUID().hashCode(), ModConfigs.COMMON.fashion.observationTicks.get());
            nextShopping = now + ModConfigs.COMMON.fashion.shoppingTicks.get(); return;
        }
        if (now < nextObservation && now < nextShopping) return;
        var budget = BUDGETS.computeIfAbsent(level, ignored -> new Budget());
        if (budget.tick != now) { budget.tick = now; budget.events = 0; }
        budget.waiting.add(this);
        if (budget.events >= ModConfigs.COMMON.fashion.eventBudget.get() || budget.waiting.iterator().next() != this) return;
        budget.waiting.remove(this);
        budget.events++;
        if (now >= nextObservation) {
            nextObservation = now + ModConfigs.COMMON.fashion.observationTicks.get() + citizen.getRandom().nextInt(100);
            observe(level, now);
        } else {
            nextShopping = now + ModConfigs.COMMON.fashion.shoppingTicks.get() + citizen.getRandom().nextInt(200);
            if (citizen.getRandom().nextDouble() < ModConfigs.COMMON.fashion.shoppingChance.get()) shop(level);
        }
    }
    public void arrived() {
        var fashion = citizen.citizenState().fashion();
        var reference = fashion.desiredRegister();
        var slot = fashion.desiredSlot();
        if (!(citizen.level() instanceof ServerLevel level) || reference == null || slot == null || fashion.forceFashion()) { finish(false); return; }
        var register = reference.resolve(level);
        if (register == null || !register.isOpen() || citizen.position().distanceToSqr(Vec3.atCenterOf(register.getBlockPos())) > 16) { finish(false); return; }
        var candidate = RetailPurchase.select(citizen, register, slot, true);
        if (candidate == null && citizen.getRandom().nextDouble() < ModConfigs.COMMON.fashion.impulseChance.get())
            candidate = RetailPurchase.select(citizen, register, slot, false);
        finish(candidate != null && RetailPurchase.purchase(citizen, register, candidate));
    }
    private void finish(boolean success) {
        var fashion = citizen.citizenState().fashion();
        fashion.clearDesire();
        fashion.cooldown(citizen.level().getGameTime() + (success ? ModConfigs.COMMON.fashion.purchaseCooldown.get() : ModConfigs.COMMON.fashion.failureCooldown.get()));
    }
    private boolean desire(net.minecraft.world.item.ItemStack stack, GarmentSlot slot, @Nullable PurchasedRegisterRef reference) {
        if (!(citizen.level() instanceof ServerLevel level) || reference == null) return false;
        var register = reference.resolve(level);
        if (register == null || !register.isOpen()) return false;
        var targetDistrict = CitizenPopulationManager.get(level).physicalDistrict(Vec3.atCenterOf(reference.lastKnownPos()));
        var intent = new CitizenIntent(CitizenIntent.Source.FASHION, CitizenIntent.DestinationType.POSITION,
                Vec3.atBottomCenterOf(reference.lastKnownPos().relative(register.getBlockState().getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING))),
                targetDistrict == null ? null : targetDistrict.districtId(), level.getGameTime(),
                level.getGameTime() + ModConfigs.COMMON.populationIntentTimeout.get(), 0);
        if (!citizen.intents().request(intent)) return false;
        citizen.citizenState().fashion().desire(stack, slot, reference); return true;
    }
    private void shop(ServerLevel level) {
        var registers = FashionWorldIndex.get(level).registers(citizen.position(), 32).stream().filter(CashRegisterBlockEntity::isOpen).toList();
        if (registers.isEmpty()) return;
        var register = registers.get(citizen.getRandom().nextInt(registers.size()));
        var candidate = RetailPurchase.select(citizen, register, null, false);
        if (candidate != null) desire(candidate.stack(), candidate.slot(), register.reference());
    }
    private void observe(ServerLevel level, long now) {
        List<Source> sources = new ArrayList<>();
        var config = ModConfigs.COMMON.fashion;
        var district = citizen.citizenState().population().districtId();
        var area = citizen.getBoundingBox().inflate(16);
        for (var other : level.getEntitiesOfClass(Citizen.class, area, entity -> entity != citizen && entity.isAlive())) {
            boolean same = district != null && district.equals(other.citizenState().population().districtId());
            sources.add(new Source(other.getUUID(), other.getEyePosition(), other.citizenState().loadout(),
                    other.citizenState().fashion()::purchasedRegister, same ? config.sameCitizenPoints.get() : config.otherCitizenPoints.get(), false));
            if (sources.size() >= 32) break;
        }
        for (var mannequin : level.getEntitiesOfClass(Mannequin.class, area, entity -> entity.mannequinState().linkedRegister() != null)) {
            sources.add(new Source(mannequin.getUUID(), mannequin.getEyePosition(), mannequin.mannequinState().loadout(),
                    slot -> mannequin.mannequinState().linkedRegister(), sameDistrict(level, mannequin.position())
                    ? config.sameMannequinPoints.get() : config.otherMannequinPoints.get(), true));
            if (sources.size() >= 64) break;
        }
        sources.addAll(com.rexhub.createapparel.advertisement.AdvertisementFashionSource.sources(citizen));
        if (sources.isEmpty()) return;
        Source source = sources.get(citizen.getRandom().nextInt(sources.size()));
        if (now < observations.getOrDefault(source.id(), 0L) || !visible(level, source)) return;
        observations.put(source.id(), now + 600);
        while (observations.size() > 32) observations.remove(observations.keySet().iterator().next());
        GarmentSlot selected = null; int successes = 0;
        var demand = FashionEvaluator.demand(citizen);
        for (var slot : GarmentSlot.values()) {
            var stack = source.loadout().getStack(slot);
            if (!FashionEvaluator.eligible(citizen, stack, slot)) continue;
            double points = source.points() * FashionEvaluator.attractiveness(stack, demand);
            if (citizen.getRandom().nextDouble() < FashionEvaluator.probability(points, config.pointScale.get())
                    && citizen.getRandom().nextInt(++successes) == 0) selected = slot;
        }
        if (selected != null) desire(source.loadout().getStack(selected), selected, source.register().apply(selected));
    }
    public boolean sameDistrict(ServerLevel level, Vec3 position) {
        var population = CitizenPopulationManager.get(level);
        var district = population.data().district(citizen.citizenState().population().districtId());
        return district != null && district.contains(position);
    }
    private boolean visible(ServerLevel level, Source source) {
        var target = source.position();
        var direction = target.subtract(citizen.getEyePosition());
        if (direction.lengthSqr() > 32 * 32 || citizen.getLookAngle().dot(direction.normalize()) < 0) return false;
        var context = new ClipContext(citizen.getEyePosition(), target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, citizen);
        return BlockGetter.traverseBlocks(context.getFrom(), context.getTo(), context, (ray, pos) -> {
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
            var state = level.getBlockState(pos);
            if (source.seeThroughGlass() && (state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES))) return null;
            var shape = ray.getBlockShape(state, level, pos);
            return level.clipWithInteractionOverride(ray.getFrom(), ray.getTo(), pos, shape, state) == null ? null : false;
        }, ray -> true);
    }
}
