package dev.hyperionfps.culling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.hyperionfps.core.ThoriumFeatureToggles;

/**
 * Tests for the storage-room and item-frame culler.
 *
 * <p>The engine is a pure policy layer with no game state of its own, so the whole
 * decision table can be exercised here without a running Minecraft: the decision
 * seams take plain booleans, and the view angles are passed in explicitly.</p>
 */
class StorageCullingEngineTest {

    @BeforeEach
    void setUp() {
        ThoriumFeatureToggles.setStorageItemFramesEnabled(true);
        StorageCullingEngine.resetMetrics();
    }

    // ------------------------------------------------------------------
    // Feature wiring
    // ------------------------------------------------------------------

    @Test
    void installMarksTheFeatureAsWired() {
        ThoriumFeatureToggles.resetWiredForTests();
        assertFalse(ThoriumFeatureToggles.Feature.STORAGE_ITEM_FRAMES.isWired());

        StorageCullingEngine.install();

        assertTrue(ThoriumFeatureToggles.Feature.STORAGE_ITEM_FRAMES.isWired());
    }

    @Test
    void engineFollowsTheFeatureSwitch() {
        ThoriumFeatureToggles.setStorageItemFramesEnabled(false);
        assertFalse(StorageCullingEngine.isEnabled());

        ThoriumFeatureToggles.setStorageItemFramesEnabled(true);
        assertTrue(StorageCullingEngine.isEnabled());
    }

    // ------------------------------------------------------------------
    // Distance policy
    // ------------------------------------------------------------------

    @Test
    void distanceComparisonUsesTheSquaredLimit() {
        double limit = 32.0;

        // Exactly at the limit is still inside it.
        assertFalse(StorageCullingEngine.isBeyondDistance(limit * limit, limit));
        // A hair beyond it is outside.
        assertTrue(StorageCullingEngine.isBeyondDistance(limit * limit + 0.01, limit));
        // Comfortably inside it.
        assertFalse(StorageCullingEngine.isBeyondDistance(100.0, limit));
    }

    @Test
    void containerDistanceIsTighterThanTheGenericBlockEntityDistance() {
        // The generic block-entity cull distance is 48 blocks; the storage culler
        // must be strictly tighter or it would add nothing.
        assertTrue(StorageCullingEngine.CONTAINER_CULL_DISTANCE < 48.0);
        assertTrue(StorageCullingEngine.CONTAINER_CULL_DISTANCE_EXTREME
                < StorageCullingEngine.CONTAINER_CULL_DISTANCE);
    }

    @Test
    void itemFrameDistanceIsTighterThanTheGenericEntityDistance() {
        // The generic entity cull distance is 64 blocks.
        assertTrue(StorageCullingEngine.ITEM_FRAME_CULL_DISTANCE < 64.0);
        assertTrue(StorageCullingEngine.ITEM_FRAME_CULL_DISTANCE_EXTREME
                < StorageCullingEngine.ITEM_FRAME_CULL_DISTANCE);
    }

    @Test
    void extremeModePullsEveryLimitIn() {
        assertTrue(StorageCullingEngine.CONTAINER_CULL_DISTANCE_EXTREME
                < StorageCullingEngine.CONTAINER_CULL_DISTANCE);
        assertTrue(StorageCullingEngine.ITEM_FRAME_CULL_DISTANCE_EXTREME
                < StorageCullingEngine.ITEM_FRAME_CULL_DISTANCE);
        assertTrue(StorageCullingEngine.FOCAL_CONE_MIN_DISTANCE_EXTREME
                < StorageCullingEngine.FOCAL_CONE_MIN_DISTANCE);
    }

    // ------------------------------------------------------------------
    // Focal cone
    // ------------------------------------------------------------------

    @Test
    void targetStraightAheadIsInsideTheFocalCone() {
        // Minecraft's yaw convention: yaw 0 looks down +Z (south).
        assertFalse(StorageCullingEngine.isOutsideFocalCone(0.0, 0.0, 20.0, 0.0, 0.0,
                StorageCullingEngine.FOCAL_CONE_COSINE));
    }

    @Test
    void targetDirectlyBehindIsOutsideTheFocalCone() {
        assertTrue(StorageCullingEngine.isOutsideFocalCone(0.0, 0.0, -20.0, 0.0, 0.0,
                StorageCullingEngine.FOCAL_CONE_COSINE));
    }

    @Test
    void targetToTheSideIsOutsideTheFocalCone() {
        // Looking down +Z, a target straight to the side (+X) is 90 degrees off.
        assertTrue(StorageCullingEngine.isOutsideFocalCone(20.0, 0.0, 0.0, 0.0, 0.0,
                StorageCullingEngine.FOCAL_CONE_COSINE));
    }

    @Test
    void targetAboveThePlayerIsOutsideTheFocalCone() {
        // Looking horizontally, a target straight up is 90 degrees off.
        assertTrue(StorageCullingEngine.isOutsideFocalCone(0.0, 20.0, 0.0, 0.0, 0.0,
                StorageCullingEngine.FOCAL_CONE_COSINE));
    }

    @Test
    void targetAtTheCameraIsNeverOutsideTheFocalCone() {
        assertFalse(StorageCullingEngine.isOutsideFocalCone(0.0, 0.0, 0.0, 0.0, 0.0,
                StorageCullingEngine.FOCAL_CONE_COSINE));
    }

    @Test
    void focalConeFollowsThePlayersRotation() {
        // The same offset is inside the cone in one direction and outside it in
        // another: the cone has to follow where the player is looking.
        double offset = 20.0;

        assertFalse(StorageCullingEngine.isOutsideFocalCone(0.0, 0.0, offset, 0.0, 0.0,
                StorageCullingEngine.FOCAL_CONE_COSINE));
        assertTrue(StorageCullingEngine.isOutsideFocalCone(0.0, 0.0, offset, 180.0, 0.0,
                StorageCullingEngine.FOCAL_CONE_COSINE));
    }

    @Test
    void focalConeHalfAngleIsApplied() {
        // 30 degrees off the view axis is inside a 45 degree cone; 60 degrees off
        // is outside it.
        double inside = Math.toRadians(30.0);
        assertFalse(StorageCullingEngine.isOutsideFocalCone(
                Math.sin(inside) * 20.0, 0.0, Math.cos(inside) * 20.0, 0.0, 0.0,
                StorageCullingEngine.FOCAL_CONE_COSINE));

        double outside = Math.toRadians(60.0);
        assertTrue(StorageCullingEngine.isOutsideFocalCone(
                Math.sin(outside) * 20.0, 0.0, Math.cos(outside) * 20.0, 0.0, 0.0,
                StorageCullingEngine.FOCAL_CONE_COSINE));
    }

    // ------------------------------------------------------------------
    // The container decision table
    // ------------------------------------------------------------------

    @Test
    void theOpenContainerIsNeverCulled() {
        // No matter how the other rules come out, the container the player is
        // looking into stays on the screen.
        assertFalse(StorageCullingEngine.decideContainerCull(true, false, false, false));
        assertFalse(StorageCullingEngine.decideContainerCull(true, true, true, true));
    }

    @Test
    void anEnclosedContainerIsCulled() {
        // Sealed in on all six sides means it cannot be seen from anywhere.
        assertTrue(StorageCullingEngine.decideContainerCull(false, true, false, false));
    }

    @Test
    void aDistantPeripheralContainerIsCulled() {
        // Far away <em>and</em> off to the side: it is a couple of pixels on the
        // screen, so the storage culler drops it.
        assertTrue(StorageCullingEngine.decideContainerCull(false, false, true, true));
    }

    @Test
    void aDistantContainerInViewIsKept() {
        // Far away but straight ahead: the player is looking at it, so it stays.
        assertFalse(StorageCullingEngine.decideContainerCull(false, false, true, false));
    }

    @Test
    void aNearPeripheralContainerIsKept() {
        // Close by: the player may well walk into it.
        assertFalse(StorageCullingEngine.decideContainerCull(false, false, false, true));
    }

    @Test
    void aNearCentralContainerIsKept() {
        assertFalse(StorageCullingEngine.decideContainerCull(false, false, false, false));
    }

    // ------------------------------------------------------------------
    // The item-frame decision table
    // ------------------------------------------------------------------

    @Test
    void aDistantItemFrameIsCulled() {
        assertTrue(StorageCullingEngine.decideItemFrameCull(true, false));
    }

    @Test
    void aPeripheralItemFrameIsCulled() {
        assertTrue(StorageCullingEngine.decideItemFrameCull(false, true));
    }

    @Test
    void aNearCentralItemFrameIsKept() {
        assertFalse(StorageCullingEngine.decideItemFrameCull(false, false));
    }

    // ------------------------------------------------------------------
    // The enclosure rule
    // ------------------------------------------------------------------

    @Test
    void anOpaqueFullBlockHidesTheContainer() {
        // Stone: opaque, fills its cell, carries no block entity.
        assertTrue(StorageCullingEngine.isBlockingNeighbour(true, true, false));
    }

    @Test
    void aSeeThroughBlockDoesNotHideTheContainer() {
        // Glass: not opaque, so whatever is behind it stays visible.
        assertFalse(StorageCullingEngine.isBlockingNeighbour(false, true, false));
    }

    @Test
    void aPartialBlockDoesNotHideTheContainer() {
        // A slab or a stair fills only half its cell - the container behind it
        // is still visible over the top.
        assertFalse(StorageCullingEngine.isBlockingNeighbour(true, false, false));
    }

    @Test
    void aNeighbourWithABlockEntityDoesNotHideTheContainer() {
        // The other half of a double chest leaves a visible gap, and it must
        // never disappear while its partner is on the screen.
        assertFalse(StorageCullingEngine.isBlockingNeighbour(true, true, true));
    }

    // ------------------------------------------------------------------
    // Classification
    // ------------------------------------------------------------------

    @Test
    void nullEntitiesAreNotClassified() {
        assertFalse(StorageCullingEngine.isStorageContainer(null));
        assertFalse(StorageCullingEngine.isItemFrame(null));
    }

    @Test
    void aNullBlockEntityIsNeverTheOpenContainer() {
        assertFalse(StorageCullingEngine.isOpenContainer(null));
    }

    // ------------------------------------------------------------------
    // Metrics
    // ------------------------------------------------------------------

    @Test
    void metricsStartAtZeroAndReset() {
        assertEquals(0L, StorageCullingEngine.getContainersCulled());
        assertEquals(0L, StorageCullingEngine.getEnclosedContainersCulled());
        assertEquals(0L, StorageCullingEngine.getItemFramesCulled());
        assertEquals(0L, StorageCullingEngine.getOpenContainersExempt());

        StorageCullingEngine.resetMetrics();

        assertEquals(0L, StorageCullingEngine.getContainersCulled());
        assertEquals(0L, StorageCullingEngine.getItemFramesCulled());
    }

    @Test
    void activityDescriptionMentionsBothCounters() {
        String description = StorageCullingEngine.describeActivity();
        assertTrue(description.contains("containers"), description);
        assertTrue(description.contains("item frames"), description);
    }
}
