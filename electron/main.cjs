// Movie max desktop: opens the active MovieMax website (from Firebase) in its own window.
const { app, BrowserWindow, shell } = require("electron");
const fs = require("node:fs");
const path = require("node:path");

const DEFAULT_SITE = "https://luofilm.site";
const ACTIVE_SITE_DOC = "https://firestore.googleapis.com/v1/projects/luo-film-2026/databases/(default)/documents/app_settings/active_site?key=AIzaSyBTSFk7GSITIHQ9WxSdi1BoVvcr4FXuZe0";
const store = () => path.join(app.getPath("userData"), "site.json");

function savedSite() {
  try { return JSON.parse(fs.readFileSync(store(), "utf8")).site || DEFAULT_SITE; } catch { return DEFAULT_SITE; }
}

async function activeSite() {
  try {
    const ctrl = new AbortController();
    setTimeout(() => ctrl.abort(), 4000);
    const doc = await (await fetch(ACTIVE_SITE_DOC, { signal: ctrl.signal })).json();
    const u = new URL(doc.fields.value.stringValue);
    if (u.protocol === "https:") {
      const site = u.origin;
      fs.writeFileSync(store(), JSON.stringify({ site }));
      return site;
    }
  } catch {}
  return savedSite();
}

async function createWindow() {
  const win = new BrowserWindow({
    width: 1280, height: 800, backgroundColor: "#0b0d14", title: "Movie max", autoHideMenuBar: true,
    webPreferences: { contextIsolation: true, nodeIntegration: false },
  });
  const site = await activeSite();
  const origin = new URL(site).origin;
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith(origin) || /accounts\.google\.com|whop\.com|paypal\.com/.test(url)) return { action: "allow" };
    if (/^https?:/.test(url)) shell.openExternal(url);
    return { action: "deny" };
  });
  win.webContents.on("did-fail-load", (_e, code, _d, url, main) => {
    if (main && code !== -3) win.loadURL(url, { extraHeaders: "pragma: no-cache\n" }).catch(() => {});
  });
  win.loadURL(site);
}

if (!app.requestSingleInstanceLock()) app.exit(0);
app.on("second-instance", () => { const [w] = BrowserWindow.getAllWindows(); if (w) { if (w.isMinimized()) w.restore(); w.focus(); } });
app.on("window-all-closed", () => app.quit());
app.whenReady().then(createWindow);
