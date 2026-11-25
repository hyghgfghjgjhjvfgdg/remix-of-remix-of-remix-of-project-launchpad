# MovieBox homepage feed
- Append MovieBox's web suggestions pager below the existing home sections, using browser-first requests with the existing server fallback. Why: scrolling must retain exact upstream titles and continuation pages.
- Phone website shows FloatNav inside AppTopBar; the native WebView header is logo + search bar + tabs with no FloatNav (subscribe lives in the sections grid). Why: the app must match the MovieBox reference screen exactly.
- Android app shows no WhatsApp/App/referral pop-ups; WhatsApp + referral appear as small landscape cards in the home feed (InAppPromos) and referral keeps only its bottom Share button. Why: owner wants in-app ads, not floats.
