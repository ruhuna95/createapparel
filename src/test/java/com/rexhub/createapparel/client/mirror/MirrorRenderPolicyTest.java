package com.rexhub.createapparel.client.mirror;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MirrorRenderPolicyTest {
    @Test void tenDifferentPlanesCannotStartMoreThanFourCapturesPerFrame() {
        var budget = new MirrorRenderPolicy.CaptureBudget(4, 50_000_000);
        for (int i = 0; i < 10; i++) if (budget.canStart(0, 1_000_000)) budget.start();
        assertEquals(4, budget.started());
        assertThrows(IllegalStateException.class, budget::start);
    }

    @Test void largeSynchronizedGroupMakesBoundedProgressOverMultipleFrames() {
        int remaining = 100;
        int frames = 0;
        while (remaining > 0) {
            var budget = new MirrorRenderPolicy.CaptureBudget(4, 4_000_000);
            long elapsed = 0;
            while (remaining > 0 && budget.canStart(elapsed, 1_000_000)) {
                budget.start();
                remaining--;
                elapsed += 1_000_000;
            }
            assertTrue(budget.started() <= 4);
            frames++;
        }
        assertEquals(25, frames);
    }

    @Test void onlyOneExpensiveCaptureCanIgnoreTheTimeBudget() {
        var budget = new MirrorRenderPolicy.CaptureBudget(4, 4_000_000);
        assertTrue(budget.canStart(0, 8_000_000));
        budget.start();
        assertFalse(budget.canStart(8_000_000, 1_000_000));
        assertFalse(budget.canStart(1_000_000, 4_000_000));
        assertEquals(1, budget.started());
    }

    @Test void farRecursiveMirrorsUseViewerDistanceNotTheVirtualReflectedCameraDistance() {
        assertTrue(MirrorRenderPolicy.withinRenderDistance(1, 35, 25, 32));
        assertTrue(MirrorRenderPolicy.withinRenderDistance(4, 100, 25, 32));
        assertFalse(MirrorRenderPolicy.withinRenderDistance(0, 35, 25, 32));
        assertFalse(MirrorRenderPolicy.withinRenderDistance(1, 25, 35, 32));
    }

    @Test void recursiveImagesMustMatchTheParentCaptureFrameEvenForFarMirrors() {
        assertTrue(MirrorRenderPolicy.canDrawCachedReflection(0, 1, 100));
        for (int depth = 1; depth <= 5; depth++) {
            assertTrue(MirrorRenderPolicy.canDrawCachedReflection(depth, 100, 100));
            assertFalse(MirrorRenderPolicy.canDrawCachedReflection(depth, 99, 100));
            assertFalse(MirrorRenderPolicy.canDrawCachedReflection(depth, 1, 100));
        }
    }

    @Test void branchingRecursionHasALinearGlobalPassLimit() {
        for (int depth = 0; depth <= 4; depth++) {
            assertEquals(4 * (depth + 1), MirrorRenderPolicy.reflectionPassLimit(4, depth));
            assertEquals(8 * (depth + 1), MirrorRenderPolicy.reflectionPassLimit(8, depth));
        }
    }

    @Test void oneRecursionLevelAllowsTwoWorldCapturesThenStops() {
        assertTrue(MirrorRenderPolicy.canRenderReflection(0, 1));
        assertTrue(MirrorRenderPolicy.canRenderReflection(1, 1));
        assertFalse(MirrorRenderPolicy.canRenderReflection(2, 1));
    }

    @Test void recursionCanBeDisabledOrExtendedWithoutChangingTheRenderer() {
        for (int limit = 0; limit <= 4; limit++) {
            for (int depth = 0; depth <= limit; depth++) {
                assertTrue(MirrorRenderPolicy.canRenderReflection(depth, limit));
            }
            assertFalse(MirrorRenderPolicy.canRenderReflection(limit + 1, limit));
        }
    }

    @Test void recursiveTargetsShrinkByLevelButKeepAMinimumResolution() {
        assertEquals(2048, MirrorRenderPolicy.recursiveTextureWidth(2048, 0, 2048));
        assertEquals(1024, MirrorRenderPolicy.recursiveTextureWidth(2048, 1, 2048));
        assertEquals(512, MirrorRenderPolicy.recursiveTextureWidth(2048, 2, 1024));
        assertEquals(256, MirrorRenderPolicy.recursiveTextureWidth(256, 1, 2048));
        assertEquals(128, MirrorRenderPolicy.recursiveTextureWidth(2048, 4, 128));
    }

    @Test void fourKWindowsScaleReflectionButKeepThe2048PixelLimit() {
        assertEquals(2048, MirrorRenderPolicy.textureWidth(3840, 0.5, 1, 1, true, 0));
        assertEquals(1024, MirrorRenderPolicy.textureWidth(3840, 0.5, 0.25, 1, true, 0));
        assertEquals(2048, MirrorRenderPolicy.textureWidth(3840, 1, 1, 1, true, 0));
    }
    @Test void closeMirrorsReach1024EvenInSmallWindowsAndMediumQuality() {
        assertEquals(1024, MirrorRenderPolicy.textureWidth(854, 0.5, 0.7, 1, true, 0));
        assertEquals(512, MirrorRenderPolicy.textureWidth(854, 0.5, 0.1, 2, true, 0));
        assertEquals(256, MirrorRenderPolicy.textureWidth(854, 0.5, 0.01, 20, true, 0));
    }
    @Test void resolutionHysteresisAvoidsResizingAtTierBoundaries() {
        assertEquals(1024, MirrorRenderPolicy.textureWidth(1920, 0.5, 0.26, 5, true, 1024));
        assertEquals(256, MirrorRenderPolicy.textureWidth(1920, 0.5, 0.01, 20, true, 1024));
        assertTrue(MirrorRenderPolicy.textureWidth(7680, 1, 1, 1, false, 0) <= 2048);
    }
    @Test void refreshRateIsTimeBasedAndMovingMirrorsRespectTheBudgetedMaximum() {
        assertEquals(1_000_000_000L / 30, MirrorRenderPolicy.updateInterval(0.5, 1, false, 30, true));
        assertEquals(1_000_000_000L / 30, MirrorRenderPolicy.updateInterval(0.01, 30, true, 30, true));
        assertTrue(MirrorRenderPolicy.updateInterval(0.01, 30, false, 30, true) > 100_000_000L);
        assertEquals(1_000_000_000L / 60, MirrorRenderPolicy.updateInterval(0.01, 30, false, 60, false));
    }

    @Test void tinySecondaryMirrorsDoNotTakeTheNearMirrorRefreshRate() {
        assertFalse(MirrorRenderPolicy.prominentNearby(0.01, 1));
        assertTrue(MirrorRenderPolicy.prominentNearby(0.5, 1));
        assertFalse(MirrorRenderPolicy.prominentNearby(0.5, 30));
        assertEquals(MirrorRenderPolicy.updateInterval(0.01, 30, false, 30, true),
                MirrorRenderPolicy.updateInterval(0.01, 1, false, 30, true));
    }

    @Test void frameAlignedDeadlinesMaintainSixtyUpdatesInsteadOfAddingPassDuration() {
        long interval = 1_000_000_000L / 60;
        long frame = 1_000_000_000L / 120;
        long deadline = 0;
        int updates = 0;
        for (int i = 0; i < 120; i++) {
            long now = i * frame;
            if (!MirrorRenderPolicy.updateDue(now, deadline, interval, frame)) continue;
            deadline = MirrorRenderPolicy.nextUpdate(now, deadline, interval);
            updates++;
        }
        assertEquals(60, updates);
        assertEquals(interval * 60, deadline);
    }

    @Test void missedDeadlinesDropBacklogAndDoNotBurstCatchUpPasses() {
        assertEquals(220, MirrorRenderPolicy.nextUpdate(200, 100, 20));
        assertEquals(140, MirrorRenderPolicy.nextUpdate(125, 120, 20));
        assertFalse(MirrorRenderPolicy.updateDue(124, 140, 20, 10));
        assertTrue(MirrorRenderPolicy.updateDue(136, 140, 20, 10));
    }

    @Test void extraPassesUseEstimatedCostBeforeExceedingTheBudget() {
        assertTrue(MirrorRenderPolicy.fitsBudget(0, 4_000_000, 8_000_000, 0));
        assertTrue(MirrorRenderPolicy.fitsBudget(1_000_000, 4_000_000, 2_000_000, 1));
        assertFalse(MirrorRenderPolicy.fitsBudget(3_000_000, 4_000_000, 2_000_000, 1));
    }

    @Test void scissorMatchesMirrorBoundsWithMarginAndClampsToTheTarget() {
        assertArrayEquals(new int[]{0, 0, 1000, 500}, MirrorRenderPolicy.scissor(new double[]{-1, -1, 1, 1}, 1000, 500));
        assertArrayEquals(new int[]{235, 117, 530, 266}, MirrorRenderPolicy.scissor(new double[]{-0.5, -0.5, 0.5, 0.5}, 1000, 500));
    }

    @Test void staleBackgroundMirrorsGetARefreshRegardlessOfMainFrameRate() {
        assertTrue(MirrorRenderPolicy.reserveBackgroundTurn(1_200_000_000, 200_000_000));
        assertTrue(MirrorRenderPolicy.reserveBackgroundTurn(Long.MAX_VALUE, 200_000_000));
        assertFalse(MirrorRenderPolicy.reserveBackgroundTurn(500_000_000, 200_000_000));
        assertFalse(MirrorRenderPolicy.reserveBackgroundTurn(1_200_000_000, 50_000_000));
    }
}
