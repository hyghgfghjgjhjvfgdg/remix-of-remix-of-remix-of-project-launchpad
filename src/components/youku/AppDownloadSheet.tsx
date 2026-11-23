import { useMemo, useState } from "react";
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

  return (
    <div className="fixed inset-0 z-[70] flex items-end bg-background/70">
      <Button aria-label="Close" className="absolute inset-0 cursor-default" onClick={onClose} />
      <div className="relative z-10 flex max-h-[78vh] w-full flex-col overflow-hidden rounded-t-2xl bg-card">
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <h3 className="text-base font-bold text-foreground">Download</h3>
          <Button onClick={onClose} aria-label="Close download" className="p-1 text-muted-foreground">
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
                <Button
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

          {series && (
            <ul className="mt-1">
              {keys.map((key, i) => {
                const on = picked.has(key);
                return (
                  <li key={key}>
                    <Button type="button" onClick={() => toggle(key)} className="flex w-full items-center gap-3 py-3 text-left">
                      <span className={`grid size-5 shrink-0 place-items-center rounded-full border ${on ? "border-app-accent bg-app-accent text-app-accent-foreground" : "border-muted-foreground"}`}>
                        {on && <Check className="size-3.5" strokeWidth={3} />}
                      </span>
                      <span className="text-[15px] text-foreground">S{pad(pickSeason)} E{pad(i + 1)}</span>
                    </Button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        <div className="flex items-center gap-3 border-t border-border bg-background px-4 py-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))]">
          {series && (
            <Button type="button" onClick={toggleAll} className="flex shrink-0 items-center gap-2 text-sm text-foreground">
              <span className={`grid size-5 place-items-center rounded-full border ${allPicked ? "border-app-accent bg-app-accent text-app-accent-foreground" : "border-muted-foreground"}`}>
                {allPicked && <Check className="size-3.5" strokeWidth={3} />}
              </span>
              Select All
            </Button>
          )}
          <Button
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
