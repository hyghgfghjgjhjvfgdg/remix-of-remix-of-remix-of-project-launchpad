import { describe, expect, it } from "vitest";
import { exactMovieSource, movieRequestRange, validMovieContentRange } from "@/lib/movie-relay-rules";

describe("Full MovieBox playback and downloads", () => {
  it("preserves full GET and open-ended playback requests", () => {
    expect(movieRequestRange(null)).toBeNull();
    expect(movieRequestRange("bytes=0-")).toBe("bytes=0-");
    expect(movieRequestRange("bytes=10485760-")).toBe("bytes=10485760-");
    expect(movieRequestRange("bytes=100-199")).toBe("bytes=100-199");
    expect(movieRequestRange(null, true)).toBe("bytes=0-0");
  });
  it("never resumes a different resource of the same quality", () => {
    const sources = [{ id: "episode-one", resolution: 480 }, { id: "different-file", resolution: 480 }];
    expect(exactMovieSource(sources, "episode-one")).toBe(sources[0]);
    expect(exactMovieSource(sources, "missing-file")).toBeUndefined();
  });
  it("requires valid complete-file bounds for partial media", () => {
    expect(validMovieContentRange("bytes 0-1023/286868942")).toBe(true);
    expect(validMovieContentRange("bytes 2048-1023/286868942")).toBe(false);
    expect(validMovieContentRange("bytes 0-1024/1024")).toBe(false);
    expect(validMovieContentRange(null)).toBe(false);
  });
});
import { movieSourceHint, needsFreshMovieUrl } from "@/lib/movie-relay-rules";

describe("signed file link passed by the player", () => {
  it("accepts MovieBox's own signed MP4 links", () => {
    const url = "https://bcdnxw.hakunaymatata.com/bt/abc.mp4?sign=x&t=1";
    expect(movieSourceHint(url)).toBe(url);
  });
  it("rejects other hosts, plain http and non-video paths", () => {
    expect(movieSourceHint("https://evil.example.com/a.mp4")).toBeNull();
    expect(movieSourceHint("http://bcdnxw.hakunaymatata.com/a.mp4")).toBeNull();
    expect(movieSourceHint("https://bcdnxw.hakunaymatata.com/a.html")).toBeNull();
    expect(movieSourceHint("https://hakunaymatata.com.evil.io/a.mp4")).toBeNull();
    expect(movieSourceHint(null)).toBeNull();
  });
  it("fetches a fresh link when the file server refuses or the request fails", () => {
    for (const status of [401, 403, 404, 410, 429]) expect(needsFreshMovieUrl(status)).toBe(true);
    expect(needsFreshMovieUrl(null)).toBe(true);
    expect(needsFreshMovieUrl(206)).toBe(false);
    expect(needsFreshMovieUrl(416)).toBe(false);
  });
});
