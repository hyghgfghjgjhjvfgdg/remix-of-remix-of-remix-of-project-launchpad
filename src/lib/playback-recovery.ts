/**
 * What the player does after the video stops loading (network drop, failed
 * seek, relay hiccup). Failures count from the last point where playback
 * really moved forward, so a seek that keeps failing cannot loop forever.
 */
export const MAX_PLAYBACK_FAILURES = 8;
/** Seconds of real progress after a recovery before the failure count resets. */
export const RECOVERY_PROGRESS_SECONDS = 3;
/** A spinner this long while the viewer wants to watch counts as a failure. */
export const STALL_TIMEOUT_MS = 20_000;

export type RecoveryStep = "reload" | "switch" | "give-up";

export function recoveryStep(failures: number): RecoveryStep {
  if (failures >= MAX_PLAYBACK_FAILURES) return "give-up";
  // Three quick reloads of the same file at the same spot, then try another quality.
  if (failures === 4) return "switch";
  return "reload";
}

/** Where to resume after a reload: the viewer's spot, kept off the very end. */
export function resumePosition(target: number, duration: number) {
  if (!Number.isFinite(target) || target <= 0) return 0;
  if (Number.isFinite(duration) && duration > 0) return Math.min(target, Math.max(0, duration - 1));
  return target;
}

/** Playback is healthy again once it has moved past the restored spot. */
export function hasRecovered(currentTime: number, restoredAt: number) {
  return currentTime >= restoredAt + RECOVERY_PROGRESS_SECONDS;
}
