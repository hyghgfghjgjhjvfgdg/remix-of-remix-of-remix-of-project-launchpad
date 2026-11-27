const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("mm", {
  feed: () => ipcRenderer.invoke("feed"),
  watch: (p) => ipcRenderer.invoke("watch", p),
  open: (p) => ipcRenderer.invoke("open", p),
  win: (a) => ipcRenderer.send("win", a),
});
