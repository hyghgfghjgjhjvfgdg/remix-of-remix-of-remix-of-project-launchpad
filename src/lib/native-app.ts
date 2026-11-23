/**
 * Bridge between the website and the Movie max Android app.
 * The app's WebView adds "MovieMaxApp/<version>" to its user agent and exposes
 * `window.MovieMaxApp` (download, openDownloads). Everything here is browser-only:
 * call it from effects or event handlers.
 */
type Bridge = { download: (url: string, name: string) => void; openDownloads: () => void };

const APP_PACKAGE = "com.moviemax.app";
const SESSION_HASH = "mmx_s=";

function bridge(): Bridge | null {
  if (typeof window === "undefined") return null;
  return (window as unknown as { MovieMaxApp?: Bridge }).MovieMaxApp ?? null;
}

export function isNativeApp() {
  if (typeof navigator === "undefined") return false;
  return /MovieMaxApp\//.test(navigator.userAgent) || !!bridge();
}

export function isAndroidBrowser() {
  if (typeof navigator === "undefined") return false;
  return /Android/i.test(navigator.userAgent) && !isNativeApp();
}

/** Hand a same-origin download URL to the app's download manager. Returns false outside the app. */
export function nativeDownload(href: string, filename: string) {
  const b = bridge();
  if (!b) return false;
  b.download(new URL(href, window.location.href).toString(), filename);
  return true;
}

export function openNativeDownloads() {
  bridge()?.openDownloads();
}

function toBase64Url(text: string) {
  const bytes = new TextEncoder().encode(text);
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromBase64Url(value: string) {
  const bin = atob(value.replace(/-/g, "+").replace(/_/g, "/"));
  return new TextDecoder().decode(Uint8Array.from(bin, (c) => c.charCodeAt(0)));
}

/**
 * Android intent link: opens the installed app on the same page (signed in as the
 * same account when `sessionJson` is given), or downloads the app when it is missing.
 */
export function buildOpenInAppUrl(path: string, apkUrl: string, sessionJson?: string | null) {
  const params = new URLSearchParams({ path });
  if (sessionJson) params.set("s", toBase64Url(sessionJson));
  return `intent://open?${params.toString()}#Intent;scheme=moviemax;package=${APP_PACKAGE};S.browser_fallback_url=${encodeURIComponent(apkUrl)};end`;
}

/** Validated session handed over by the browser (Firebase user JSON). */
export function parseHandoffSession(encoded: string): { key: string; json: string } | null {
  try {
    const json = fromBase64Url(encoded);
    const user = JSON.parse(json) as { uid?: unknown; apiKey?: unknown; appName?: unknown; stsTokenManager?: { refreshToken?: unknown } };
    if (typeof user.uid !== "string" || typeof user.apiKey !== "string" || typeof user.stsTokenManager?.refreshToken !== "string") return null;
    const appName = typeof user.appName === "string" ? user.appName : "[DEFAULT]";
    return { key: `firebase:authUser:${user.apiKey}:${appName}`, json };
  } catch {
    return null;
  }
}

/**
 * Inside the app, a page opened from the browser carries `#mmx_s=<session>`.
 * Save it where the sign-in library restores sessions from, drop it from the
 * address and reload once so the user is signed in.
 */
export function consumeSessionHandoff() {
  if (typeof window === "undefined") return;
  const hash = window.location.hash;
  const at = hash.indexOf(SESSION_HASH);
  if (at < 0) return;
  const encoded = hash.slice(at + SESSION_HASH.length).split("&")[0] ?? "";
  const clean = window.location.pathname + window.location.search;
  const session = parseHandoffSession(encoded);
  window.history.replaceState(null, "", clean);
  if (!session) return;
  try {
    if (localStorage.getItem(session.key)) return; // already signed in here
    localStorage.setItem(session.key, session.json);
    window.location.replace(clean);
  } catch {
    /* storage unavailable */
  }
}
