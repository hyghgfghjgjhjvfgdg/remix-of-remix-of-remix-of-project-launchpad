import { useEffect, useState } from "react";
import { Gift } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { WhatsAppIcon } from "@/components/icons/WhatsAppIcon";
import { isNativeApp } from "@/lib/native-app";
import { referralLink } from "@/lib/referrals";

const CHANNEL_URL = "https://whatsapp.com/channel/0029VbEBSz6J3jv5PPsldQ3u";

/** Android app only: small landscape WhatsApp + referral cards inside the home feed (no pop-ups). */
export function InAppPromos() {
  const { user } = useAuth();
  const [show, setShow] = useState(false);
  useEffect(() => setShow(isNativeApp()), []);
  if (!show) return null;

  const share = async () => {
    if (!user) {
      window.dispatchEvent(new Event("luofilm:open-auth"));
      toast.info("Sign in first so your friends are counted for you.");
      return;
    }
    const link = referralLink(user.id);
    const text = `🎬 Watch & download movies, series, Luo and Luganda translated movies on MOVIE MAX. Join with my link: ${link}`;
    if (navigator.share) {
      try {
        await navigator.share({ title: "MOVIE MAX", text, url: link });
        return;
      } catch {
        /* cancelled */
      }
    }
    window.open(`https://wa.me/?text=${encodeURIComponent(text)}`, "_blank", "noopener");
  };

  const joinWhatsApp = () => {
    try {
      localStorage.setItem("luofilm:whatsapp-channel-joined", "1");
    } catch {
      /* ignore */
    }
    window.open(CHANNEL_URL, "_blank", "noopener");
  };

  return (
    <div className="-ml-0.5 mb-4 mr-3 flex snap-x snap-mandatory gap-2.5 overflow-x-auto pb-1 [scrollbar-width:none] sm:mr-4">
      <button
        type="button"
        onClick={joinWhatsApp}
        className="bg-promo-whatsapp relative flex h-[76px] w-[88%] max-w-sm shrink-0 snap-start items-center gap-3 overflow-hidden rounded-xl px-3 text-left text-whatsapp-foreground shadow-lg"
      >
        <span className="promo-arcs pointer-events-none absolute inset-0" />
        <span className="relative grid size-12 shrink-0 place-items-center rounded-full bg-whatsapp-foreground shadow-md">
          <span className="grid size-10 place-items-center rounded-full bg-whatsapp">
            <WhatsAppIcon className="size-6 text-whatsapp-foreground" />
          </span>
        </span>
        <span className="relative min-w-0 flex-1 leading-tight">
          <span className="block text-[11px] font-light opacity-90">Join Our</span>
          <span className="block truncate text-[15px] font-extrabold">WhatsApp Channel</span>
        </span>
        <span className="relative shrink-0 rounded-full bg-whatsapp-foreground px-3 py-1.5 text-[10px] font-extrabold uppercase tracking-wide text-whatsapp">
          Join now
        </span>
      </button>
      <button
        type="button"
        onClick={() => void share()}
        className="bg-promo-referral relative flex h-[76px] w-[88%] max-w-sm shrink-0 snap-start items-center gap-3 overflow-hidden rounded-xl border border-vip/40 px-3 text-left text-vip shadow-lg"
      >
        <span className="promo-arcs pointer-events-none absolute inset-0 opacity-60" />
        <span className="relative grid size-12 shrink-0 place-items-center rounded-full badge-vip shadow-md">
          <Gift className="size-6 text-vip-foreground" />
        </span>
        <span className="relative min-w-0 flex-1 leading-tight">
          <span className="block text-[11px] font-light text-foreground/85">Share with 10 friends</span>
          <span className="block truncate text-[15px] font-extrabold">Get Free Access</span>
        </span>
        <span className="badge-vip relative shrink-0 rounded-full px-3 py-1.5 text-[10px] font-extrabold uppercase tracking-wide text-vip-foreground">
          Share
        </span>
      </button>
    </div>
  );
}
