export function createHitClaimTracker({
    tickWindow = 5,
    retentionTicks = 180,
    maxTrackedClaims = 256
} = {}) {
    const sentHitClaims = new Map();

    function prune(currentTick) {
        const minTickToKeep = Math.max(0, currentTick - retentionTicks);
        for (const [trackedShotId, trackedTick] of sentHitClaims.entries()) {
            if (trackedTick < minTickToKeep) {
                sentHitClaims.delete(trackedShotId);
            }
        }

        while (sentHitClaims.size > maxTrackedClaims) {
            const oldestKey = sentHitClaims.keys().next().value;
            if (oldestKey == null) {
                break;
            }
            sentHitClaims.delete(oldestKey);
        }
    }

    return {
        clear() {
            sentHitClaims.clear();
        },

        register(shotId, snapshotTick) {
            const previousTick = sentHitClaims.get(shotId);
            if (previousTick != null && Math.abs(previousTick - snapshotTick) <= tickWindow) {
                return false;
            }

            sentHitClaims.set(shotId, snapshotTick);
            prune(snapshotTick);
            return true;
        }
    };
}
