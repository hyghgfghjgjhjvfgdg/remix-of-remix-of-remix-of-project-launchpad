import { describe, expect, it } from "vitest";
import { buildOpenInAppUrl, parseHandoffSession } from "@/lib/native-app";

const user = { uid: "u1", apiKey: "AIzaKey", appName: "[DEFAULT]", stsTokenManager: { refreshToken: "r1" } };

describe("open in app link", () => {
  it("opens the same page in the app and falls back to downloading the app", () => {
    const url = buildOpenInAppUrl("/watch/the-beast-2026", "https://site.test/downloads/MovieMax.apk");
    expect(url.startsWith("intent://open?path=%2Fwatch%2Fthe-beast-2026")).toBe(true);
    expect(url).toContain("package=com.moviemax.app");
    expect(url).toContain("S.browser_fallback_url=https%3A%2F%2Fsite.test%2Fdownloads%2FMovieMax.apk");
  });

  it("carries the signed-in session so the app opens signed in", () => {
    const url = buildOpenInAppUrl("/", "https://s.test/a.apk", JSON.stringify(user));
    const s = new URLSearchParams(url.slice("intent://open?".length, url.indexOf("#"))).get("s")!;
    expect(parseHandoffSession(s)).toEqual({ key: "firebase:authUser:AIzaKey:[DEFAULT]", json: JSON.stringify(user) });
  });

  it("rejects a session without a refresh token", () => {
    const bad = btoa(JSON.stringify({ uid: "u1", apiKey: "k" }));
    expect(parseHandoffSession(bad)).toBeNull();
  });
});
