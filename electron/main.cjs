// Movie max desktop: standalone app UI (renderer/) with its own frameless window.
// Data comes from the active MovieMax domain (Firebase), cached for offline start.
const { app, BrowserWindow, ipcMain, shell } = require("electron");
const fs = require("node:fs");
const path = require("node:path");

const DEFAULT_SITE = "https://luofilm.site";
const ACTIVE_SITE_DOC = "https://firestore.googleapis.com/v1/projects/luo-film-2026/databases/(default)/documents/app_settings/active_site?key=AIzaSyBTSFk7GSITIHQ9WxSdi1BoVvcr4FXuZe0";
const file = (n) => path.join(app.getPath("userData"), n);
const readJson = (n, d) => { try { return JSON.parse(fs.readFileSync(file(n), "utf8")); } catch { return d; } };
const timeout = (ms) => { const c = new AbortController(); setTimeout(() => c.abort(), ms); return c.signal; };

let site = null;
async function activeSite() {
  if (site) return site;
  try {
    const doc = await (await fetch(ACTIVE_SITE_DOC, { signal: timeout(4000) })).json();
    const u = new URL(doc.fields.value.stringValue);
    if (u.protocol === "https:") { site = u.origin; fs.writeFileSync(file("site.json"), JSON.stringify({ site })); return site; }
  } catch {}
  site = readJson("site.json", {}).site || DEFAULT_SITE;
  return site;
}

ipcMain.handle("feed", async () => {
  const s = await activeSite();
  try {
    const res = await fetch(`${s}/api/public/app-latest`, { signal: timeout(20000) });
    const body = await res.json();
    if (Array.isArray(body.items) && body.items.length) {
      const data = { at: Date.now(), site: s, items: body.items };
      fs.writeFileSync(file("feed.json"), JSON.stringify(data));
      return { ...data, offline: false };
    }
  } catch {}
  const cached = readJson("feed.json", null);
  return cached ? { ...cached, offline: true } : { items: [], site: s, offline: true };
});

let player = null;
ipcMain.handle("watch", async (_e, p) => {
  const s = await activeSite();
  if (typeof p !== "string" || !p.startsWith("/")) return;
  if (!player || player.isDestroyed()) {
    player = new BrowserWindow({ width: 1200, height: 720, backgroundColor: "#07080c", title: "Movie max Player", autoHideMenuBar: true, icon: path.join(__dirname, "icon.ico") });
    player.webContents.setWindowOpenHandler(({ url }) => { if (/^https?:/.test(url)) shell.openExternal(url); return { action: "deny" }; });
  }
  player.loadURL(s + p); player.show(); player.focus();
});
ipcMain.handle("open", async (_e, p) => { const s = await activeSite(); shell.openExternal(/^https?:/.test(p) ? p : s + p); });
ipcMain.on("win", (e, a) => {
  const w = BrowserWindow.fromWebContents(e.sender); if (!w) return;
  if (a === "min") w.minimize(); else if (a === "max") w.isMaximized() ? w.unmaximize() : w.maximize(); else w.close();
});

function createWindow() {
  const win = new BrowserWindow({
    width: 1240, height: 780, minWidth: 960, minHeight: 600, frame: false, backgroundColor: "#0b0c10", title: "Movie max",
    icon: path.join(__dirname, "icon.ico"),
    webPreferences: { preload: path.join(__dirname, "preload.cjs"), contextIsolation: true, nodeIntegration: false, webviewTag: false },
  });
  win.webContents.setWindowOpenHandler(({ url }) => { if (/^https?:/.test(url)) shell.openExternal(url); return { action: "deny" }; });
  win.loadFile(path.join(__dirname, "renderer", "index.html"));
}

if (!app.requestSingleInstanceLock()) app.exit(0);
app.on("second-instance", () => { const [w] = BrowserWindow.getAllWindows(); if (w?.isMinimized()) w.restore(); w?.focus(); });
app.on("window-all-closed", () => app.quit());
app.whenReady().then(createWindow);
