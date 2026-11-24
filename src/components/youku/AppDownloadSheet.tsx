import { useEffect, useMemo, useState } from "react";
import { ChevronDown, Check, Crown, Download, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { toast } from "sonner";
import { catalogMediaUrl } from "@/lib/download";
import { getDetailPath } from "@/lib/moviebox";
import { sourcesAny } from "@/lib/catalog-client";
import { nativeDownload } from "@/lib/native-app";
import { useSubscription } from "@/hooks/useSubscription";

type Source = { id: string; resolution: number; bytes?: number; size: string | null };
type Season = { season: number; episodes: number };

type Props = {
  onClose: () => void;
  sources: Source[];
  baseName: string;
  catalogId: string;
  season: number;
  episode: number;
  seasons?: Season[] | undefined;
  /** Base title without the SxEy suffix, used for episode file names. */
  titleName?: string | undefined;
};

const pad = (n: number) => String(n).padStart(2, "0");

/** In-app download sheet: quality chips, season picker, episode checklist, Select All + Download. */
export function AppDownloadSheet({ onClose, sources, baseName, catalogId, season, episode, seasons, titleName }: Props) {
  const { subscribed, requireSubscription } = useSubscription();
  const qualities = useMemo(
    () => [...new Set(sources.map((s) => s.resolution).filter((r) => r > 0))].sort((a, b) => a - b),
    [sources],
  );
  const [quality, setQuality] = useState<number>(() => {
    const list = [...new Set(sources.map((s) => s.resolution))].sort((a, b) => a - b);
    return list.find((r) => r >= 480) ?? list[list.length - 1] ?? 0;
  });
  const series = !!seasons?.length && season > 0;
  const [pickSeason, setPickSeason] = useState(season);
  const count = seasons?.find((s) => s.season === pickSeason)?.episodes ?? 0;
  const [picked, setPicked] = useState<Set<string>>(() => new Set(series ? [`${season}:${episode}`] : []));
  const [busy, setBusy] = useState(false);

  const keys = Array.from({ length: count }, (_, i) => `${pickSeason}:${i + 1}`);
  const allPicked = keys.length > 0 && keys.every((k) => picked.has(k));
  const toggle = (key: string) =>
    setPicked((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  const toggleAll = () =>
    setPicked((prev) => {
      const next = new Set(prev);
      keys.forEach((k) => (allPicked ? next.delete(k) : next.add(k)));
      return next;
    });

  const closest = (list: Source[]) =>
    [...list].sort((a, b) => Math.abs(a.resolution - quality) - Math.abs(b.resolution - quality))[0];

  const start = async () => {
    if (!requireSubscription()) return onClose();
    const detailPath = getDetailPath(catalogId);
    if (!series) {
      const src = closest(sources);
      if (!src) return toast.error("No downloadable file available");
      const name = `${baseName}.${src.resolution || "auto"}p`;
      nativeDownload(catalogMediaUrl(catalogId, 0, 0, src.id, src.resolution, name, detailPath), `${name}.mp4`);
      toast.success("Added to Downloads");
      return onClose();
    }
    const list = [...picked].map((k) => k.split(":").map(Number) as [number, number]).sort((a, b) => a[0] - b[0] || a[1] - b[1]);
    if (!list.length) return toast.error("Pick at least one episode");
    setBusy(true);
    let added = 0;
    for (const [s, e] of list) {
      const epSources = s === season && e === episode ? sources : await sourcesAny(catalogId, s, e).catch(() => []);
      const src = closest(epSources as Source[]);
      if (!src) continue;
      const name = `${titleName ?? baseName} S${pad(s)}E${pad(e)}.${src.resolution || "auto"}p`;
      nativeDownload(catalogMediaUrl(catalogId, s, e, src.id, src.resolution, name, detailPath), `${name}.mp4`);
      added += 1;
    }
    setBusy(false);
    if (added) toast.success(`${added} added to Downloads`);
    else toast.error("Those episodes are not available yet");
    onClose();
  };

  // Phones: keep the player visible at the top and open the panel right under it (MovieBox layout).
  const [top, setTop] = useState<number | null>(null);
  useEffect(() => {
    if (window.innerWidth >= 1024) return;
    let cleanup: (() => void) | undefined;
    let tries = 0;
    const dock = (player: HTMLElement) => {
      const place = () => setTop(Math.max(0, Math.round(player.getBoundingClientRect().bottom)));
      const y = player.getBoundingClientRect().top + window.scrollY;
      window.scrollTo({ top: Math.max(0, y), behavior: "instant" as ScrollBehavior });
      place();
      const raf = requestAnimationFrame(place);
      const ro = new ResizeObserver(place);
      ro.observe(player);
      window.addEventListener("resize", place);
      const prev = document.body.style.overflow;
      document.body.style.overflow = "hidden";
      cleanup = () => {
        cancelAnimationFrame(raf);
        ro.disconnect();
        window.removeEventListener("resize", place);
        document.body.style.overflow = prev;
      };
    };
    // The player may still be loading when the panel opens from a notification.
    const timer = window.setInterval(() => {
      const player = document.querySelector<HTMLElement>("[data-watch-player]");
      tries += 1;
      if (player) { window.clearInterval(timer); dock(player); }
      else if (tries > 40) window.clearInterval(timer);
    }, 250);
    return () => { window.clearInterval(timer); cleanup?.(); };
  }, []);
  const current = closest(sources);
  const docked = top !== null;

  return (
    <div
      className={docked ? "fixed inset-x-0 bottom-0 z-[70] flex" : "fixed inset-0 z-[70] flex items-end bg-background/70 lg:items-center lg:justify-center"}
      style={docked ? { top } : undefined}
    >
      {!docked && <Button variant="ghost" aria-label="Close" className="absolute inset-0 cursor-default" onClick={onClose} />}
      <div className={docked ? "relative z-10 flex h-full w-full flex-col overflow-hidden bg-card" : "relative z-10 flex max-h-[78vh] w-full flex-col overflow-hidden rounded-t-2xl bg-card lg:max-w-lg lg:rounded-2xl"}>
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <h3 className="text-base font-bold text-foreground">Download</h3>
          <Button variant="ghost" onClick={onClose} aria-label="Close download" className="h-auto p-1 text-muted-foreground">
            <X className="size-5" />
          </Button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto px-3 pb-3">
          <div className="mt-3 rounded-xl bg-muted/50 p-3">
            <p className="text-sm font-bold text-foreground">
              Resource {!subscribed && <span className="ml-1 text-[11px] font-medium text-vip">Membership required</span>}
            </p>
            {series && (
              <label className="relative mt-2 inline-flex items-center">
                <select
                  value={pickSeason}
                  onChange={(e) => setPickSeason(Number(e.target.value))}
                  aria-label="Season"
                  className="appearance-none rounded-md border border-border bg-transparent py-1.5 pl-3 pr-8 text-sm text-foreground"
                >
                  {(seasons ?? []).map((s) => (
                    <option key={s.season} value={s.season} className="bg-card">
                      Season {pad(s.season)}
                    </option>
                  ))}
                </select>
                <ChevronDown className="pointer-events-none absolute right-2 size-4 text-muted-foreground" />
              </label>
            )}
            <div className="mt-3 grid grid-cols-4 gap-2">
              {(qualities.length ? qualities : [0]).map((r) => (
                <Button variant="ghost"
                  key={r}
                  type="button"
                  onClick={() => setQuality(r)}
                  className={`flex h-9 items-center justify-center gap-1 rounded-md text-[13px] font-semibold ${
                    quality === r ? "bg-app-accent/20 text-app-accent" : "bg-muted text-foreground/80"
                  }`}
                >
                  {r >= 720 && <Crown className="size-3" />}
                  {r ? `${r}P` : "AUTO"}
                </Button>
              ))}
            </div>
          </div>

          {!series && (
            <div className="mt-1 flex items-center gap-3 py-3">
              <span className="grid size-5 shrink-0 place-items-center rounded-full border border-app-accent bg-app-accent text-app-accent-foreground">
                <Check className="size-3.5" strokeWidth={3} />
              </span>
              <span className="min-w-0">
                <span className="block truncate text-[15px] text-foreground">{titleName ?? baseName}</span>
                {current?.size && <span className="block text-xs text-muted-foreground">{current.size}</span>}
              </span>
            </div>
          )}
          {series && (
            <ul className="mt-1">
              {keys.map((key, i) => {
                const on = picked.has(key);
                return (
                  <li key={key}>
                    <Button variant="ghost" type="button" onClick={() => toggle(key)} className="flex h-auto w-full items-center justify-start gap-3 px-0 py-3 text-left hover:bg-transparent">
                      <span className={`grid size-5 shrink-0 place-items-center rounded-full border ${on ? "border-app-accent bg-app-accent text-app-accent-foreground" : "border-muted-foreground"}`}>
                        {on && <Check className="size-3.5" strokeWidth={3} />}
                      </span>
                      <span className="min-w-0">
                        <span className="block text-[15px] text-foreground">S{pad(pickSeason)} E{pad(i + 1)}</span>
                        {pickSeason === season && i + 1 === episode && current?.size && (
                          <span className="block text-xs text-muted-foreground">{current.size}</span>
                        )}
                      </span>
                    </Button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        <div className="flex items-center gap-3 border-t border-border bg-background px-4 py-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))]">
          {series && (
            <Button variant="ghost" type="button" onClick={toggleAll} className="flex h-auto shrink-0 items-center gap-2 px-0 text-sm text-foreground hover:bg-transparent">
              <span className={`grid size-5 place-items-center rounded-full border ${allPicked ? "border-app-accent bg-app-accent text-app-accent-foreground" : "border-muted-foreground"}`}>
                {allPicked && <Check className="size-3.5" strokeWidth={3} />}
              </span>
              Select All
            </Button>
          )}
          <Button variant="ghost"
            type="button"
            disabled={busy}
            onClick={() => void start()}
            className="app-gradient flex h-11 flex-1 items-center justify-center gap-2 rounded-xl text-base font-bold text-app-accent-foreground disabled:opacity-60"
          >
            <Download className="size-5" />
            {busy ? "Adding…" : "Download"}
          </Button>
        </div>
      </div>
    </div>
  );
}
