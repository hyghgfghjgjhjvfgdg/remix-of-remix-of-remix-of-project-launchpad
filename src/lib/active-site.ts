import { getSetting, saveSetting } from "./app-settings";

/**
 * The live website address the MOVIE MAX phone app opens. Stored in Firestore
 * `app_settings/active_site` (world-readable), so the app can follow a domain change.
 */
export const ACTIVE_SITE_KEY = "active_site";

/** Only real public domains count — never previews or local addresses. */
export function isPublicSiteHost(hostname: string) {
  const h = hostname.toLowerCase();
  if (!h.includes(".") || h === "localhost" || /^\d+\.\d+\.\d+\.\d+$/.test(h)) return false;
  if (h.endsWith(".lovableproject.com") || h.endsWith(".lovableproject-dev.com")) return false;
  if (/^(id-preview|preview)(-[a-z0-9]+)?--/.test(h)) return false;
  return true;
}

export function cleanSiteUrl(value: string): string | null {
  try {
    const u = new URL(value.trim());
    if (u.protocol !== "https:" || !isPublicSiteHost(u.hostname)) return null;
    return u.origin;
  } catch {
    return null;
  }
}

/** Called when an admin opens the dashboard: records the domain they are on. */
export async function syncActiveSite(): Promise<string | null> {
  if (typeof window === "undefined") return null;
  const here = cleanSiteUrl(window.location.origin);
  if (!here) return null;
  const saved = await getSetting<string>(ACTIVE_SITE_KEY, "").catch(() => "");
  if (saved === here) return here;
  await saveSetting(ACTIVE_SITE_KEY, here);
  return here;
}
