import { useEffect } from "react";
import { toast } from "sonner";
import { pendingCardTxs, syncCardPayment } from "@/lib/payments";
import { useSubscription } from "@/hooks/useSubscription";

/**
 * Keeps confirming card / Google Pay / Apple Pay payments in the background,
 * on every page, even after the Whop window was closed or the buyer came back
 * from Google or Apple. Activates the membership as soon as Whop says "paid".
 */
export function CardPaymentWatcher() {
  const { refreshSubscription } = useSubscription();
  useEffect(() => {
    let busy = false;
    const check = async () => {
      if (busy) return;
      const ids = pendingCardTxs();
      if (!ids.length) return;
      busy = true;
      try {
        for (const id of ids) {
          const r = await syncCardPayment(id).catch(() => null);
          if (r?.status === "completed") {
            refreshSubscription();
            toast.success("Card payment confirmed — your membership is active.");
          }
        }
      } finally {
        busy = false;
      }
    };
    void check();
    const timer = window.setInterval(() => void check(), 5000);
    const onBack = () => {
      if (document.visibilityState === "visible") void check();
    };
    document.addEventListener("visibilitychange", onBack);
    window.addEventListener("focus", onBack);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", onBack);
      window.removeEventListener("focus", onBack);
    };
  }, [refreshSubscription]);
  return null;
}
