import { createFileRoute } from "@tanstack/react-router";
import { fetchMostTrending } from "@/lib/moviebox";
import { watchSlug } from "@/lib/watch-links";

/** Newest catalog titles for the Android app's "new movie" phone notifications. Public, read-only. */
export const Route = createFileRoute("/api/public/app-latest")({
  server: {
    handlers: {
      GET: async () => {
        try {
          const items = (await fetchMostTrending()).slice(0, 20).map((item) => ({
            id: item.id,
            title: item.title,
            year: item.year,
            type: item.type,
            poster: item.poster,
            path: `/watch/${watchSlug(item)}`,
          }));
          return Response.json({ items }, { headers: { "cache-control": "public, max-age=600" } });
        } catch {
          return Response.json({ items: [] }, { status: 503 });
        }
      },
    },
  },
});
