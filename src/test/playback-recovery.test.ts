import { describe, expect, it } from "vitest";
import { hasRecovered, recoveryStep, resumePosition } from "@/lib/playback-recovery";

describe("player recovery after a failed load or seek", () => {
  it("reloads the same file at the same spot for the first three failures", () => {
    expect(recoveryStep(1)).toBe("reload");
    expect(recoveryStep(2)).toBe("reload");
    expect(recoveryStep(3)).toBe("reload");
  });
  it("tries another quality on the fourth failure", () => {
    expect(recoveryStep(4)).toBe("switch");
  });
  it("keeps reloading the new quality, then shows the error after eight failures", () => {
    expect(recoveryStep(5)).toBe("reload");
    expect(recoveryStep(7)).toBe("reload");
    expect(recoveryStep(8)).toBe("give-up");
  });
  it("resumes where the viewer seeked to, never past the end", () => {
    expect(resumePosition(3000, 6000)).toBe(3000);
    expect(resumePosition(6000, 6000)).toBe(5999);
    expect(resumePosition(1200, Number.NaN)).toBe(1200);
    expect(resumePosition(-5, 6000)).toBe(0);
  });
  it("only counts playback as healthy after it moves past the restored spot", () => {
    expect(hasRecovered(3001, 3000)).toBe(false);
    expect(hasRecovered(3003, 3000)).toBe(true);
  });
});
