package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.citizen.population.DiagnosticTiming;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class GarmentDiagnostics {
    private static final DiagnosticTiming TIMING = new DiagnosticTiming();
    private static long[] framesBuffer = new long[0];
    private static final Set<Integer> VISIBLE = new HashSet<>();
    private static final Set<Integer> CLOTHED = new HashSet<>(), UNCLOTHED = new HashSet<>();
    private static final Set<Object> TYPES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<DualEnvelopeDeformer.Mesh> GEOMETRIES = Collections.newSetFromMap(new IdentityHashMap<>());
    public static void geometry(DualEnvelopeDeformer.Mesh mesh) {
        if (rendering() && GEOMETRIES.add(mesh)) { count("observedGeometryMeshes", 1); count("observedTopologyBytes", mesh.topologyBytes()); }
    }
    private static long previousFrame, frames, totalFrameNanos, visibleTotal, visibleMax;
    private static Object previousTexture;
    private static boolean citizenPass;
    private static int renderTypes;
    private static int currentLod;
    private static final String[] LOD_CALLS = {"lod0Citizens", "lod1Citizens", "lod2Citizens", "lod3Citizens"};
    private static final String[] LOD_VERTICES = {"lod0Vertices", "lod1Vertices", "lod2Vertices", "lod3Vertices"};
    private static final String[] LOD_TIMES = {"lod0Garment", "lod1Garment", "lod2Garment", "lod3Garment"};
    public static DiagnosticTiming.Scope measureLod(int lod) { return measure(LOD_TIMES[lod]); }
    @SubscribeEvent public static void entityLeft(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof com.rexhub.createapparel.entity.Citizen citizen
                && Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(citizen) instanceof com.rexhub.createapparel.client.renderer.CitizenRenderer renderer)
            renderer.forget(citizen);
    }
    public static boolean debugDisableCitizenLod = Boolean.getBoolean("apparel.diagnostics.debugDisableCitizenLod");
    public static boolean debugDisableCitizenOcclusion = Boolean.getBoolean("apparel.diagnostics.debugDisableCitizenOcclusion");
    public static boolean debugEnableCitizenOcclusion = Boolean.getBoolean("apparel.diagnostics.debugEnableCitizenOcclusion");
    public static int debugForceCitizenLod = Math.max(-1, Math.min(3, Integer.getInteger("apparel.diagnostics.debugForceCitizenLod", -1)));
    public static boolean debugUseLegacyTextureComposition = Boolean.parseBoolean(System.getProperty("apparel.diagnostics.debugUseLegacyTextureComposition", "true"));
    public static boolean debugUseLegacyRigidRendering = Boolean.getBoolean("apparel.diagnostics.debugUseLegacyRigidRendering");
    private static boolean recordingFrames, detailed, collectingFrame;
    private static com.sun.management.ThreadMXBean allocationBean;
    private static long allocationStart = -1, allocationBytes, allocationFrames;
    public static boolean debugUseLegacyEnvelopeRendering = Boolean.getBoolean("apparel.diagnostics.debugUseLegacyEnvelopeRendering");
    public static boolean debugDisableCitizenGarmentRendering = Boolean.getBoolean("apparel.diagnostics.debugDisableCitizenGarmentRendering");
    public static boolean debugDisableCitizenBodyRendering = Boolean.getBoolean("apparel.diagnostics.debugDisableCitizenBodyRendering");
    public static boolean debugDisableCitizenCosmeticRendering = Boolean.getBoolean("apparel.diagnostics.debugDisableCitizenCosmeticRendering");
    private GarmentDiagnostics() { }
    public static boolean enabled() { return TIMING.enabled(); }
    public static DiagnosticTiming timing() { return TIMING; }
    public static long visibleUniqueMax() { return visibleMax; }
    private static boolean rendering() { return collectingFrame && enabled(); }
    public static DiagnosticTiming.Scope measure(String section) { return rendering() ? TIMING.measure(section) : DiagnosticTiming.NONE; }
    public static void count(String key, long value) {
        TIMING.count(key, value);
        if (citizenPass && key.equals("submittedVertices")) TIMING.count(LOD_VERTICES[currentLod], value);
    }
    public static void lod(int level, boolean changed) {
        if (!rendering()) return;
        currentLod = level;
        count(LOD_CALLS[level], 1);
        if (changed) count("citizenLodChanges", 1);
    }
    public static void clothing(int id, boolean clothed) {
        if (!rendering()) return;
        count(clothed ? "clothedCitizenCalls" : "unclothedCitizenCalls", 1);
        if (!com.rexhub.createapparel.client.mirror.MirrorRenderManager.INSTANCE.isRenderingReflection()) (clothed ? CLOTHED : UNCLOTHED).add(id);
    }
    public static void garmentPart() { if (rendering()) { count("allHumanoidGarmentParts", 1); if (citizenPass) count("citizenGarmentParts", 1); } }
    public static DiagnosticTiming.Scope citizen(int id) {
        if (!rendering()) return DiagnosticTiming.NONE;
        if (com.rexhub.createapparel.client.mirror.MirrorRenderManager.INSTANCE.isRenderingReflection()) count("reflectionCitizenCalls", 1);
        else VISIBLE.add(id);
        count("citizenRenderCalls", 1);
        boolean previous = citizenPass; int previousLod = currentLod; citizenPass = true; var scope = measure("citizenRenderer");
        return () -> { try { scope.close(); } finally { citizenPass = previous; currentLod = previousLod; } };
    }
    public static void part(Object type, Object texture) {
        if (!rendering()) return;
        count("allGarmentAndSupportParts", 1); if (citizenPass) count("citizenGarmentAndSupportParts", 1);
        TYPES.add(type); if (!Objects.equals(texture, previousTexture)) count("textureBindingRequestsChanged", 1); previousTexture = texture;
    }
    public static void start() {
        allocationBean = null;
        GEOMETRIES.clear();
        allocationBytes = allocationFrames = 0;
        allocationStart = -1;
        if (Boolean.getBoolean("apparel.diagnostics.renderAllocation")
                && java.lang.management.ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported() && bean.isThreadAllocatedMemoryEnabled()) allocationBean = bean;
        framesBuffer = new long[8192];
        TIMING.start(); frames = totalFrameNanos = visibleTotal = visibleMax = previousFrame = 0; VISIBLE.clear(); CLOTHED.clear(); UNCLOTHED.clear(); TYPES.clear(); previousTexture = null;
        recordingFrames = detailed = true;
        collectingFrame = false;
    }
    public static void startFramesOnly() { start(); TIMING.stop(); detailed = false; allocationBean = null; }
    public static void stop() { renderTypes = TYPES.size(); TIMING.stop(); recordingFrames = collectingFrame = false; previousFrame = 0; allocationStart = -1; previousTexture = null; TYPES.clear(); GEOMETRIES.clear(); VISIBLE.clear(); CLOTHED.clear(); UNCLOTHED.clear(); }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if (!recordingFrames) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.screen != null || mc.isPaused()) { collectingFrame = false; previousFrame = 0; allocationStart = -1; VISIBLE.clear(); CLOTHED.clear(); UNCLOTHED.clear(); return; }
        if (event.phase == TickEvent.Phase.START) {
            if (allocationBean != null) allocationStart = allocationBean.getThreadAllocatedBytes(Thread.currentThread().getId());
            collectingFrame = detailed;
            long now = System.nanoTime();
            if (previousFrame != 0) { long elapsed = now - previousFrame; framesBuffer[(int) (frames++ % framesBuffer.length)] = elapsed; totalFrameNanos += elapsed; }
            previousFrame = now; VISIBLE.clear(); CLOTHED.clear(); UNCLOTHED.clear(); previousTexture = null;
        } else {
            if (allocationBean != null && allocationStart >= 0) {
                long end = allocationBean.getThreadAllocatedBytes(Thread.currentThread().getId());
                if (end >= allocationStart) { allocationBytes += end - allocationStart; allocationFrames++; }
            }
            visibleTotal += VISIBLE.size(); visibleMax = Math.max(visibleMax, VISIBLE.size());
            count("clothedVisibleSum", CLOTHED.size()); count("unclothedVisibleSum", UNCLOTHED.size()); TIMING.interval();
            collectingFrame = false;
        }
    }
    public static String report() {
        var sorted = Arrays.copyOf(framesBuffer, (int) Math.min(frames, framesBuffer.length)); Arrays.sort(sorted);
        long slowestTotal = 0; int slowest = Math.max(1, (int) Math.ceil(sorted.length * .01));
        for (int i = Math.max(0, sorted.length - slowest); i < sorted.length; i++) slowestTotal += sorted[i];
        return String.format(Locale.ROOT, "CPU submission only, not GPU time; fps=%.3f fps1PercentLow=%.3f meanFrameMs=%.3f frameP95Ms=%.3f frameP99Ms=%.3f sampledIntervals=%d visibleUniqueMean=%.3f visibleUniqueMax=%d renderTypes=%d garmentEntries=%d citizenTextureEntries=%d (frame quantiles/1%%Low: last8192 intervals; render passes include previews/mirrors)%n%s",
                totalFrameNanos == 0 ? 0 : frames * 1e9 / totalFrameNanos, slowestTotal == 0 ? 0 : slowest * 1e9 / slowestTotal,
                frames == 0 ? 0 : totalFrameNanos / 1e6 / frames, DiagnosticTiming.quantile(sorted, .95) / 1e6, DiagnosticTiming.quantile(sorted, .99) / 1e6,
                frames, TIMING.intervals() == 0 ? Double.NaN : (double) visibleTotal / TIMING.intervals(), visibleMax, enabled() ? TYPES.size() : renderTypes,
                RuntimeGarmentTextureCache.size(), CitizenTextureCache.size(), TIMING.report("renderFrame"))
                + String.format(Locale.ROOT, "%nlegacyEnvelope=%s renderThreadAllocatedBytesPerFrame=%.3f allocationFrames=%d (whole render thread/frame Java heap only, includes profiler; not native/GPU)%nclientSwitches garment=%s body=%s cosmetic=%s detailTiming=%s garmentMsPerRenderedCitizen=%.6f citizenRendererCpuPercentOfFrameIntervals=%.3f garmentCpuPercentOfFrameIntervals=%.3f",
                debugUseLegacyEnvelopeRendering, allocationFrames == 0 ? Double.NaN : (double) allocationBytes / allocationFrames, allocationFrames,
                debugDisableCitizenGarmentRendering, debugDisableCitizenBodyRendering, debugDisableCitizenCosmeticRendering, detailed,
                TIMING.result("citizenGarment").calls() == 0 ? Double.NaN : TIMING.result("citizenGarment").inclusive() / 1e6 / TIMING.result("citizenGarment").calls(),
                totalFrameNanos == 0 || !detailed ? Double.NaN : 100.0 * TIMING.result("citizenRenderer").inclusive() / totalFrameNanos,
                totalFrameNanos == 0 || !detailed ? Double.NaN : 100.0 * TIMING.result("citizenGarment").inclusive() / totalFrameNanos)
                + String.format(Locale.ROOT, "%nLOD disabled=%s forced=%d occlusionDisabled=%s legacyRigid=%s legacyTexture=%s experimentalOcclusion=%s",
                debugDisableCitizenLod, debugForceCitizenLod, debugDisableCitizenOcclusion, debugUseLegacyRigidRendering, debugUseLegacyTextureComposition, debugEnableCitizenOcclusion)
                + System.lineSeparator() + ClientGarmentAssets.INSTANCE.envelopeCacheReport();
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("apparelprofile").then(Commands.literal("lod")
                .then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-1, 3))
                        .executes(context -> { debugForceCitizenLod = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level"); return 1; }))));
        event.getDispatcher().register(Commands.literal("apparelprofile")
                .then(Commands.literal("start").executes(context -> { start(); return 1; }))
                .then(Commands.literal("frames").executes(context -> { startFramesOnly(); return 1; }))
                .then(Commands.literal("stop").executes(context -> { stop(); logReport(); return 1; }))
                .then(Commands.literal("report").executes(context -> { logReport(); return 1; }))
                .then(Commands.literal("switch").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> { for (String name : List.of("debugDisableCitizenGarmentRendering", "debugDisableCitizenBodyRendering", "debugDisableCitizenCosmeticRendering", "debugUseLegacyEnvelopeRendering", "debugDisableCitizenLod", "debugDisableCitizenOcclusion", "debugEnableCitizenOcclusion", "debugUseLegacyTextureComposition", "debugUseLegacyRigidRendering")) builder.suggest(name); return builder.buildFuture(); })
                        .then(Commands.argument("disabled", BoolArgumentType.bool()).executes(context -> {
                            boolean disabled = BoolArgumentType.getBool(context, "disabled");
                            switch (StringArgumentType.getString(context, "name")) {
                                case "debugDisableCitizenGarmentRendering" -> debugDisableCitizenGarmentRendering = disabled;
                                case "debugDisableCitizenBodyRendering" -> debugDisableCitizenBodyRendering = disabled;
                                case "debugDisableCitizenCosmeticRendering" -> debugDisableCitizenCosmeticRendering = disabled;
                                case "debugUseLegacyEnvelopeRendering" -> debugUseLegacyEnvelopeRendering = disabled;
                                case "debugDisableCitizenLod" -> debugDisableCitizenLod = disabled;
                                case "debugDisableCitizenOcclusion" -> debugDisableCitizenOcclusion = disabled;
                                case "debugEnableCitizenOcclusion" -> debugEnableCitizenOcclusion = disabled;
                                case "debugUseLegacyTextureComposition" -> debugUseLegacyTextureComposition = disabled;
                                case "debugUseLegacyRigidRendering" -> debugUseLegacyRigidRendering = disabled;
                                default -> { return 0; }
                            }
                            com.mojang.logging.LogUtils.getLogger().warn("DIAGNOSTIC ONLY: Citizen render switch changed; equipment retained"); return 1;
                        })))));
    }
    private static void logReport() {
        com.mojang.logging.LogUtils.getLogger().info("APPAREL_CLIENT_PROFILE {}", report());
        var player = Minecraft.getInstance().player; if (player != null) player.displayClientMessage(Component.literal("Diagnostic report written to logs/latest.log (CPU, not GPU timing)"), false);
    }
}
