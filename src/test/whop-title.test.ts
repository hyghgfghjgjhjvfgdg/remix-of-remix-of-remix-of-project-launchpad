import { describe, expect, it } from "vitest";
import { whopTitle } from "@/lib/whop.server";

describe("whopTitle", () => {
  it("keeps developer plan names within Whop's 30-character limit", () => {
    expect(whopTitle("Developer API plan (monthly)")).toBe("MOVIE MAX Developer API");
  });
  it("never exceeds 30 characters for long names", () => {
    expect(whopTitle("SVIP Member Quarterly Super Long Name Here").length).toBeLessThanOrEqual(30);
  });
});

import { whopPaymentMatches } from "@/lib/whop.server";

describe("matching a Whop payment to its transaction", () => {
  it("matches by tx id in metadata", () => {
    expect(whopPaymentMatches({ id: "p1", metadata: { tx_id: "tx123" } }, "tx123")).toBe(true);
  });
  it("matches Google/Apple Pay payments that only reference the checkout session", () => {
    const p = { id: "p2", metadata: null, checkout_configuration: { id: "ch_ABC123XYZ" } } as never;
    expect(whopPaymentMatches(p, "tx999", "ch_ABC123XYZ")).toBe(true);
  });
  it("does not match another buyer's payment", () => {
    expect(whopPaymentMatches({ id: "p3", metadata: { tx_id: "other" } }, "tx123", "ch_ABC123XYZ")).toBe(false);
  });
});
