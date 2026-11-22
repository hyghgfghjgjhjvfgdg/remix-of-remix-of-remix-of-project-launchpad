import { describe, expect, it } from "vitest";
import { cleanSiteUrl, isPublicSiteHost } from "@/lib/active-site";

describe("active website for the phone app", () => {
  it("records real public domains", () => {
    expect(cleanSiteUrl("https://luofilm.site/admin?x=1")).toBe("https://luofilm.site");
    expect(isPublicSiteHost("moviemax.lovable.app")).toBe(true);
  });
  it("never records previews, local or insecure addresses", () => {
    expect(isPublicSiteHost("id-preview--55b92f5b.lovable.app")).toBe(false);
    expect(isPublicSiteHost("abc.lovableproject.com")).toBe(false);
    expect(isPublicSiteHost("localhost")).toBe(false);
    expect(cleanSiteUrl("http://luofilm.site")).toBeNull();
  });
});
