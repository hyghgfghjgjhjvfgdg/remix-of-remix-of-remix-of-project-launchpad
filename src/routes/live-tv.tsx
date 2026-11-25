import { useRef, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowLeft, RotateCw, Tv } from "lucide-react";
import { Button } from "@/components/ui/button";
import { MobileNav } from "@/components/youku/MobileNav";

export const Route = createFileRoute("/live-tv")({
  head: () => ({
    meta: [
      { title: "MovieMax TV — Watch Live Inside MOVIE MAX" },
      { name: "description", content: "Watch MovieMax TV live inside MOVIE MAX, with your app navigation always within reach." },
      { property: "og:title", content: "MovieMax TV — Watch Live Inside MOVIE MAX" },
      { property: "og:description", content: "Watch MovieMax TV live inside MOVIE MAX." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: LiveTvPage,
});

function LiveTvPage() {
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const frame = useRef<HTMLIFrameElement>(null);

  return (
    <div className="flex h-dvh flex-col overflow-hidden bg-background text-foreground pt-[env(safe-area-inset-top)] pb-[calc(88px+env(safe-area-inset-bottom))] lg:pb-0">
      <header className="flex h-12 shrink-0 items-center gap-3 border-b border-border px-3">
        <Link to="/" aria-label="Back to home" className="grid size-9 shrink-0 place-items-center rounded-md text-muted-foreground hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
          <ArrowLeft className="size-5" />
        </Link>
        <h1 className="flex min-w-0 flex-1 items-center gap-2 text-base font-bold"><Tv className="size-5 shrink-0 text-sidebar-primary" /> MovieMax TV</h1>
        <Button variant="ghost" size="icon" aria-label="Reload MovieMax TV" title="Reload MovieMax TV" onClick={() => { setLoading(true); setRevision((value) => value + 1); }}>
          <RotateCw className="size-4" />
        </Button>
      </header>
      <main className="relative min-h-0 flex-1">
        {loading && <div role="status" aria-label="Loading MovieMax TV" className="pointer-events-none absolute inset-x-0 top-0 z-10 h-1 animate-pulse bg-sidebar-primary" />}
        <iframe
          key={revision}
          ref={frame}
          title="MovieMax TV"
          src="https://livetv.luofilm.site/"
          allow="autoplay; fullscreen; picture-in-picture; encrypted-media"
          allowFullScreen
          onLoad={() => setLoading(false)}
          className="block size-full border-0 bg-background"
        />
      </main>
      <MobileNav />
    </div>
  );
}