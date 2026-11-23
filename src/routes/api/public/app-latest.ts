import { createFileRoute } from "@tanstack/react-router";
import { fetchHome, fetchDetails } from "@/lib/moviebox";
import { watchSlug } from "@/lib/watch-links";
import { listCollection } from "@/lib/firestore-rest";
import { toTitle } from "@/lib/luo";
import { cleanNaraboxTitle, type NaraboxItem } from "@/lib/narabox";

type Latest = { id: string; title: string; description: string; poster: string | null; path: string; source: string; year?: string | null; type?: string };
let cached: { at: number; items: Latest[] } | null = null;
export const Route = createFileRoute("/api/public/app-latest")({
  server: { handlers: { GET: async () => {
    if (cached && Date.now() - cached.at < 600000) return Response.json({ items: cached.items });
    const results = await Promise.allSettled([
      (async (): Promise<Latest[]> => {
        const home = await fetchHome();
        const latest = home.rows.filter(r => /latest|new|recent/i.test(r.title) && !/coming|upcoming/i.test(r.title));
        // Current web home has no latest rail: detect newly appearing titles across its actual sections.
        const rows = latest.length ? latest : home.rows.filter(r => !/adult|hot short|coming|upcoming/i.test(r.title));
        const unique = [...new Map(rows.flatMap(r => r.items).map(i => [i.id, i])).values()].slice(0, 80);
        return Promise.all(unique.map(async i => {
          const detail = unique.indexOf(i) < 3 ? await fetchDetails(i.id).catch(() => null) : null;
          return { id: `mb-${i.id}`, title: i.title, description: detail?.description ?? [i.genre, i.year].filter(Boolean).join(" · "), poster: i.poster, path: `/watch/${watchSlug(i)}`, source: "MovieBox", year: i.year, type: i.type };
        }));
      })(),
      (async (): Promise<Latest[]> => {
        const groups = await Promise.all(["movies", "tv-shows"].map(async kind => {
          const res = await fetch(`https://portal.naraboxtv.com/api/v1/${kind}?page=1&per_page=12&sort=latest&order=desc`, { signal: AbortSignal.timeout(12000) });
          if (!res.ok) throw new Error("Luganda feed unavailable");
          const body = await res.json() as { data: NaraboxItem[] };
          return body.data.map(i => ({ id: `nb-${kind}-${i.id}`, title: cleanNaraboxTitle(i.title), description: i.description ?? i.genre.join(" · "), poster: i.thumbnail, path: `/luganda/narabox/${kind}/${i.id}`, source: "Luganda", type: kind === "movies" ? "movie" : "series" }));
        })); return groups.flat();
      })(),
      (async (): Promise<Latest[]> => (await listCollection("media")).map(toTitle).filter(i => i.published && i.language === "luganda").sort((a,b) => Date.parse(b.created_at)-Date.parse(a.created_at)).slice(0,12).map(i => ({ id: `upload-${i.id}`, title: i.title, description: i.description ?? i.genre ?? "", poster: i.poster_url, path: `/luganda/${i.id}`, source: "Luganda" })))(),
    ]);
    const items = results.flatMap(r => r.status === "fulfilled" ? r.value : []);
    if (items.length) cached = { at: Date.now(), items };
    return Response.json({ items: items.length ? items : cached?.items ?? [] }, { status: items.length || cached ? 200 : 503, headers: { "cache-control": "public, max-age=600" } });
  } } },
});
