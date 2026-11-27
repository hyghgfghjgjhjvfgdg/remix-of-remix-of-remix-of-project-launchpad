const $ = (s) => document.querySelector(s);
const esc = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const api = window.mm || { feed: async () => ({ items: [] }), watch() {}, open() {}, win() {} };
let items = [], view = "home", q = "";

const skeleton = () => `<div class="hero"><div class="big sk"></div><div class="feat sk"></div></div>
<div class="tiles">${"<div class='tile sk'></div>".repeat(5)}</div><h3 class="sk" style="width:140px;height:16px;border-radius:6px"></h3>
<div class="grid">${Array.from({ length: 12 }, () => `<div><div class="p sk" style="aspect-ratio:2/3;border-radius:10px"></div><div class="skt sk"></div></div>`).join("")}</div>`;
const card = (i, n) => `<div class="card" data-i="${n}"><div class="p">${i.poster ? `<img loading="lazy" src="${esc(i.poster)}" alt="">` : ""}</div><b>${esc(i.title)}</b><small>${esc([i.source, i.year].filter(Boolean).join(" · "))}</small></div>`;
const grid = (list) => list.length ? `<div class="grid">${list.map((i) => card(i, items.indexOf(i))).join("")}</div>` : `<div class="empty">Nothing here yet.</div>`;

function render() {
  const v = $("#view");
  if (view === "tv") { v.innerHTML = `<iframe class="tv" src="https://livetv.luofilm.site/" allow="autoplay; fullscreen"></iframe>`; return; }
  if (!items.length) { v.innerHTML = skeleton(); return; }
  let list = items;
  if (q) list = items.filter((i) => i.title.toLowerCase().includes(q));
  else if (view === "luganda") list = items.filter((i) => i.source === "Luganda");
  else if (view === "movie") list = items.filter((i) => i.source !== "Luganda" && i.type !== "series" && i.type !== "tv");
  else if (view === "series") list = items.filter((i) => i.type === "series" || i.type === "tv");
  if (view !== "home" || q) { v.innerHTML = `<h3>${q ? `Results for “${esc(q)}”` : $("#nav .on").textContent}</h3>${grid(list)}`; return; }
  const [a, b] = [items[0], items.find((i) => i.source === "Luganda") || items[1]];
  v.innerHTML = `<div class="hero"><div class="big" data-i="0"><small>NOW TRENDING</small><h2>${esc(a.title)}</h2><p>${esc(a.description)}</p>${a.poster ? `<img src="${esc(a.poster)}">` : ""}</div>
  <div class="feat" data-i="${items.indexOf(b)}"><span class="tag">New</span>${b.poster ? `<img src="${esc(b.poster)}">` : ""}<div><b>${esc(b.title)}</b><small>${esc(b.description)}</small></div></div></div>
  <div class="tiles"><button class="tile" data-go="movie"><i>▶</i>Movies</button><button class="tile" data-go="series"><i>▤</i>Series</button><button class="tile" data-go="luganda"><i>★</i>Luganda</button><button class="tile" data-go="tv"><i>◉</i>Live TV</button><button class="tile" data-open="/plans"><i>♛</i>Subscribe</button></div>
  <h3>Latest on Movie max</h3>${grid(items.filter((i) => i.source !== "Luganda").slice(0, 24))}<h3>Latest Luganda</h3>${grid(items.filter((i) => i.source === "Luganda").slice(0, 18))}`;
}

function detail(i) {
  const m = document.createElement("div"); m.className = "modal";
  m.innerHTML = `<div class="sheet">${i.poster ? `<img src="${esc(i.poster)}">` : "<span></span>"}<div><h2>${esc(i.title)}</h2><span class="chips">${esc([i.source, i.year, i.type].filter(Boolean).join(" · "))}</span><p>${esc(i.description)}</p><div class="btns"><button class="pri" data-a="watch">▶ Watch now</button><button class="sec" data-a="close">Close</button></div></div></div>`;
  m.onclick = (e) => { const a = e.target.dataset.a; if (a === "watch") api.watch(i.path); if (a || e.target === m) m.remove(); };
  document.body.append(m);
}

function go(v) { view = v; q = ""; $("#q").value = ""; document.querySelectorAll("#nav button").forEach((b) => b.classList.toggle("on", b.dataset.v === v)); render(); }
$("#nav").onclick = (e) => { const b = e.target.closest("button"); if (b) go(b.dataset.v); };
$("#view").onclick = (e) => { const t = e.target.closest("[data-i],[data-go],[data-open]"); if (!t) return;
  if (t.dataset.go) go(t.dataset.go); else if (t.dataset.open) api.open(t.dataset.open); else if (items[t.dataset.i]) detail(items[t.dataset.i]); };
$("#q").oninput = (e) => { q = e.target.value.trim().toLowerCase(); render(); };
$("#me").onclick = () => api.open("/profile");
document.querySelector(".wc").onclick = (e) => { const w = e.target.closest("button")?.dataset.w; if (w) api.win(w); };

render();
api.feed().then((d) => { items = d.items || []; $("#status").textContent = d.offline ? (items.length ? "Offline · showing saved titles" : "No internet") : ""; render(); });
