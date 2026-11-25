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
export function AppTopBar({ floatNav = false }: { floatNav?: boolean } = {}) {
  const isActive = useActive();
  const [grid, setGrid] = useState(false);
  const navigate = useNavigate();
  const searchStr = useRouterState({ select: (s) => s.location.searchStr });
  const [term, setTerm] = useState("");
  useEffect(() => {
    setTerm(new URLSearchParams(searchStr).get("q") ?? "");
  }, [searchStr]);
  const { openSubscribe } = useSubscription();
  useEffect(() => {
    const action = new URLSearchParams(window.location.search).get("appAction");
    if (action === "subscribe") window.dispatchEvent(new Event("luofilm:open-subscribe"));
  }, []);

  return (
    <header className="fixed inset-x-0 top-0 z-50 bg-background pt-[env(safe-area-inset-top)]">
      {floatNav ? (
        <FloatNav />
      ) : (
        <div className="flex h-14 items-center gap-2 px-3">
          <Link to="/" aria-label="Movie max home" className="shrink-0">
            <img src={logoSrc} alt="Movie max" className="h-9 w-auto max-w-[96px] object-contain" />
          </Link>
          <form
            role="search"
            onSubmit={(event) => {
              event.preventDefault();
              const q = term.trim();
              if (q) void navigate({ to: "/search", search: { q } });
            }}
            className="flex h-10 min-w-0 flex-1 items-center gap-2 rounded-md bg-muted px-3 text-[15px] text-muted-foreground"
          >
            <Search className="size-5 shrink-0" aria-hidden="true" />
            <input
              type="search"
              enterKeyHint="search"
              aria-label="Search movies and series"
              placeholder="Search movies / series"
              value={term}
              onChange={(event) => setTerm(event.target.value)}
              className="min-w-0 w-full bg-transparent text-base text-foreground outline-none placeholder:text-muted-foreground"
            />
          </form>
          <Link to="/profile" aria-label="Profile" title="Profile" className="grid size-10 shrink-0 place-items-center rounded-full bg-muted text-foreground transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
            <User className="size-5" />
          </Link>
        </div>
      )}

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
        <Button variant="ghost" type="button" onClick={() => setGrid((v) => !v)} aria-label={grid ? "Close sections" : "All sections"} className="grid h-10 w-11 shrink-0 place-items-center bg-gradient-to-l from-background from-60% text-foreground/80">
          {grid ? <X className="size-5" /> : <LayoutGrid className="size-5" />}
        </Button>
      </div>

      {grid && (
        <div className="grid grid-cols-4 gap-2 border-t border-border bg-background px-3 pb-4 pt-3 shadow-2xl">
          {!floatNav && (
            <Button variant="ghost"
              type="button"
              onClick={() => {
                setGrid(false);
                openSubscribe();
              }}
              className="flex h-10 items-center justify-center gap-1 truncate rounded-lg bg-app-accent/20 px-1 text-[13px] font-semibold text-sidebar-primary"
            >
              <Crown className="size-4" /> Subscribe
            </Button>
          )}
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
        <Link to="/downloads" className={cell(pathname.startsWith("/downloads"))}>
          <Body icon={<Download className="size-6" />} label="Downloads" />
        </Link>
        {user ? (
          <Link to="/profile" className={cell(pathname.startsWith("/profile"))}>
            <Body icon={<User className="size-6" />} label="Me" />
          </Link>
        ) : (
          <Button variant="ghost" type="button" onClick={() => setAuthOpen(true)} className={`${cell(false)} h-auto rounded-none px-0 py-0 hover:bg-transparent [&_svg]:size-6`}>
            <Body icon={<User className="size-6" />} label="Me" />
          </Button>
        )}
      </nav>
      <AuthModal open={authOpen} onOpenChange={setAuthOpen} />
    </div>
  );
}
