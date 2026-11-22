/** Preserve the browser's byte request; never turn an ordinary GET into a partial file. */
export function movieRequestRange(range: string | null, probe = false) {
  return probe ? "bytes=0-0" : range;
}

/** Only the exact resource may be resumed; equal resolution does not mean equal bytes. */
export function exactMovieSource<T extends { id: string }>(sources: T[], resourceId: string) {
  return sources.find((source) => source.id === resourceId);
}

export function validMovieContentRange(value: string | null) {
  const match = /^bytes (\d+)-(\d+)\/(\d+)$/.exec(value ?? "");
  if (!match) return false;
  const start = Number(match[1]);
  const end = Number(match[2]);
  const total = Number(match[3]);
  return [start, end, total].every(Number.isSafeInteger) && start <= end && end < total;
}
/** Only MovieBox's own signed file hosts may be passed in as a playback hint. */
const MEDIA_HOST = /(^|\.)(hakunaymatata\.com|aoneroom\.com)$/;

/**
 * Signed file URL the player already holds. Using it lets every seek go straight
 * to the file instead of asking MovieBox for the source list again.
 */
export function movieSourceHint(value: string | null | undefined): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || !MEDIA_HOST.test(url.hostname)) return null;
    if (!/\.mp4$/i.test(url.pathname)) return null;
    return url.toString();
  } catch {
    return null;
  }
}

/** Upstream answers that mean the signed link expired or was refused: resolve a fresh one. */
export function needsFreshMovieUrl(status: number | null | undefined) {
  return status == null || [401, 403, 404, 410, 429].includes(status);
}
