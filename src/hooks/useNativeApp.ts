import { useEffect, useState } from "react";
import { isNativeApp } from "@/lib/native-app";

/** True only after hydration inside the Movie max Android app. */
export function useNativeApp() {
  const [native, setNative] = useState(false);
  useEffect(() => {
    const on = isNativeApp();
    setNative(on);
    document.documentElement.classList.toggle("native-app", on);
  }, []);
  return native;
}
