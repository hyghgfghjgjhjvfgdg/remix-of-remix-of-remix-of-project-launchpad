import { useState, useEffect, type ReactNode } from "react";
import { Link, useNavigate, useRouterState } from "@tanstack/react-router";
import { Crown, Download, Home, LayoutGrid, Music2, Search, Tv, User, X } from "lucide-react";
import logoSrc from "@/assets/moviemax-logo.png";
import { AuthModal } from "@/components/auth/AuthModal";
import { useAuth } from "@/hooks/useAuth";
import { useSubscription } from "@/hooks/useSubscription";
import { CATEGORIES } from "@/lib/categories";
import { FloatNav } from "./FloatNav";
import { Button } from "@/components/ui/button";
import { openNativeDownloads } from "@/lib/native-app";

type PageTo = "/" | "/channels" | "/luo" | "/luganda" | "/teso" | "/music" | "/search" | "/developer";
type Item = { label: string; page: PageTo; slug?: never } | { label: string; slug: string; page?: never };

/** Tabs under the search bar, MovieBox style; the grid button shows every section. */
const TABS: Item[] = [
  { label: "Trending", page: "/" },
  { label: "Movie", slug: "movies" },
  { label: "TV/Series", slug: "tv" },
  { label: "Luo", page: "/luo" },
  { label: "Luganda", page: "/luganda" },
  { label: "Teso", page: "/teso" },
  { label: "Kids", slug: "animation" },
  { label: "Music", page: "/music" },
];

const GRID: Item[] = [
  ...TABS,
  { label: "Live TV", page: "/channels" },
  { label: "Search", page: "/search" },
  { label: "Dashboard", page: "/developer" },
  ...CATEGORIES.filter((c) => c.slug !== "home" && !c.adult && !TABS.some((t) => t.slug === c.slug)).map((c) => ({ label: c.short, slug: c.slug })),
];

function useActive() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  return (item: Item) =>
    item.slug
      ? pathname === `/category/${item.slug}`
      : item.page === "/"
        ? pathname === "/" || pathname.startsWith("/watch")
        : pathname.startsWith(item.page ?? "#");
}

function ItemLink({ item, className, onClick }: { item: Item; className: string; onClick?: () => void }) {
  return item.slug ? (
    <Link to="/category/$slug" params={{ slug: item.slug }} className={className} onClick={onClick}>
      {item.label}
    </Link>
  ) : (
    <Link to={item.page ?? "/"} className={className} onClick={onClick}>
      {item.label}
    </Link>
  );
}

/** App header: logo + search, then LIVE + category tabs that scroll, with a grid of every section. */
export function AppTopBar() {
  const isActive = useActive();
  const [grid, setGrid] = useState(false);
  useEffect(() => {
    const action = new URLSearchParams(window.location.search).get("appAction");
    if (action === "subscribe") window.dispatchEvent(new Event("luofilm:open-subscribe"));
  }, []);

  return (
    <header className="fixed inset-x-0 top-0 z-50 bg-background pt-[env(safe-area-inset-top)]">
      <FloatNav />

      <div className="flex h-10 items-center">
        <nav className="flex min-w-0 flex-1 items-center gap-5 overflow-x-auto whitespace-nowrap px-3 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          <Link to="/channels" className="flex shrink-0 items-center gap-1 text-[15px] font-bold text-foreground/80">
            <Tv className="size-4" /> LIVE
          </Link>
          {TABS.map((item) => (
            <ItemLink
              key={item.label}
              item={item}
              className={`shrink-0 transition-all ${isActive(item) ? "text-[17px] font-bold text-foreground" : "text-[15px] text-foreground/65"}`}
            />
          ))}
        </nav>
        <Button type="button" onClick={() => setGrid((v) => !v)} aria-label={grid ? "Close sections" : "All sections"} className="grid h-10 w-11 shrink-0 place-items-center bg-gradient-to-l from-background from-60% text-foreground/80">
          {grid ? <X className="size-5" /> : <LayoutGrid className="size-5" />}
        </Button>
      </div>

      {grid && (
        <div className="grid grid-cols-4 gap-2 border-t border-border bg-background px-3 pb-4 pt-3 shadow-2xl">
          {GRID.map((item) => (
            <ItemLink
              key={item.label}
              item={item}
              onClick={() => setGrid(false)}
              className={`flex h-10 items-center justify-center truncate rounded-lg px-1 text-[13px] font-semibold ${isActive(item) ? "bg-app-accent/20 text-sidebar-primary" : "bg-muted text-foreground/80"}`}
            />
          ))}
        </div>
      )}
    </header>
  );
}

/** App bottom bar: Home · Music (HOT) · raised LIVE · Downloads · Me. */
export function AppBottomNav() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const { user } = useAuth();
  const [authOpen, setAuthOpen] = useState(false);

  const cell = (active: boolean) =>
    `relative flex min-w-0 flex-1 flex-col items-center justify-center gap-1 text-[11px] ${active ? "font-semibold text-sidebar-primary" : "text-muted-foreground"}`;
  const Body = ({ icon, label }: { icon: ReactNode; label: string }) => (
    <>
      {icon}
      <span>{label}</span>
    </>
  );
  const home = pathname === "/" || pathname.startsWith("/category") || pathname.startsWith("/watch");

  return (
    <div className="fixed inset-x-0 bottom-0 z-40 lg:hidden">
      <nav className="relative flex h-16 items-stretch rounded-t-xl border-t border-sidebar-border bg-card pb-[env(safe-area-inset-bottom)]">
        <Link to="/" className={cell(home)}>
          <Body icon={<Home className="size-6" fill={home ? "currentColor" : "none"} />} label="Home" />
        </Link>
        <Link to="/music" className={cell(pathname.startsWith("/music"))}>
          <span className="absolute right-[18%] top-1 rounded-sm bg-app-hot px-1 text-[9px] font-bold leading-[14px] text-destructive-foreground">HOT</span>
          <Body icon={<Music2 className="size-6" />} label="Music" />
        </Link>
        <div className="flex min-w-0 flex-1 justify-center">
          <Link
            to="/channels"
            aria-label="Live TV"
            className={`-mt-6 grid size-[72px] shrink-0 place-items-center rounded-full border-4 border-background bg-card text-sidebar-primary ${pathname.startsWith("/channel") ? "ring-2 ring-sidebar-primary" : ""}`}
          >
            <span className="flex flex-col items-center text-[11px] font-bold leading-none">
              <Tv className="mb-1 size-7" />
              LIVE
            </span>
          </Link>
        </div>
        <Button variant="ghost" type="button" onClick={openNativeDownloads} className={cell(false)}>
          <Body icon={<Download className="size-6" />} label="Downloads" />
        </Button>
        {user ? (
          <Link to="/profile" className={cell(pathname.startsWith("/profile"))}>
            <Body icon={<User className="size-6" />} label="Me" />
          </Link>
        ) : (
          <Button variant="ghost" type="button" onClick={() => setAuthOpen(true)} className={cell(false)}>
            <Body icon={<User className="size-6" />} label="Me" />
          </Button>
        )}
      </nav>
      <AuthModal open={authOpen} onOpenChange={setAuthOpen} />
    </div>
  );
}
