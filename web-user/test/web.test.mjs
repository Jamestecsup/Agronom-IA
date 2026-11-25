import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { createServer } from "vite";
import { normalizeAppearance, saveAppearance, readAppearance } from "../src/appearance.js";
import { clearCache, listIdentifiedPlants, saveProvider, deleteChat, deleteAllChats } from "../src/api.js";

function memoryStorage() {
  const data = new Map();
  return { getItem: (key) => data.get(key) ?? null, setItem: (key,value) => data.set(key,value), data };
}

test("persists appearance and applies the CSP-compatible pre-paint script", async () => {
  globalThis.localStorage = memoryStorage();
  globalThis.document = { documentElement: { dataset: {} } };
  saveAppearance({ mode: "dark", accent: "blue", garden: "list", unknown: "bad", scale: "inherit" });
  assert.deepEqual(readAppearance(), normalizeAppearance({ mode: "dark", accent: "blue", garden: "list" }));
  const boot = await readFile(new URL("../public/appearance-init.js", import.meta.url), "utf8");
  const document = { documentElement: { dataset: {} } };
  vm.runInNewContext(boot, { document, localStorage });
  assert.deepEqual(document.documentElement.dataset, globalThis.document.documentElement.dataset);
});

test("server garden is cached and refreshes on mutation; provider keys are sent only in the request", async () => {
  globalThis.localStorage = memoryStorage();
  localStorage.setItem("agronomia.user.session", JSON.stringify({ token: "fixture" }));
  const previous = globalThis.fetch;
  const requests = [];
  globalThis.fetch = async (url, opts) => {
    requests.push([url, opts]);
    const data = url.endsWith("/plants") ? { plants: Array.from({length: 45}, (_, i) => ({ id: i })) } : { id: "saved" };
    return new Response(JSON.stringify(data), { headers: { "content-type": "application/json" } });
  };
  clearCache();
  try {
    assert.equal((await listIdentifiedPlants()).plants.length, 45);
    await listIdentifiedPlants();
    assert.equal(requests.length, 1);
    await saveProvider({ name: "fixture", api_key: "synthetic-credential" });
    assert.deepEqual(JSON.parse(requests[1][1].body), { name: "fixture", api_key: "synthetic-credential" });
    assert.equal([...localStorage.data.values()].some(value => value.includes("synthetic-credential")), false);
    await listIdentifiedPlants();
    await deleteChat("demo");
    await deleteAllChats();
    assert.equal(requests[3][0], "/agronomia-api/api/chat?plant_id=demo");
    assert.equal(requests[3][1].method, "DELETE");
    assert.equal(requests[4][0], "/agronomia-api/api/chats?confirm=true");
  } finally {
    globalThis.fetch = previous;
    clearCache();
  }
});

test("an authenticated 401 emits expiration and retains the response status", async () => {
  globalThis.localStorage = memoryStorage();
  globalThis.window = new EventTarget();
  localStorage.setItem("agronomia.user.session", JSON.stringify({ token: "fixture" }));
  const old = globalThis.fetch;
  globalThis.fetch = async () => new Response(null, {status: 401});
  let expired = 0;
  window.addEventListener("agronomia:expired", () => expired++);
  clearCache();
  try {
    await assert.rejects(listIdentifiedPlants(), {status: 401});
    assert.equal(expired, 1);
  } finally { globalThis.fetch = old; clearCache(); }
});

test("Markdown escapes hostile HTML and never creates links from model content", async () => {
  const vite = await createServer({ server: { middlewareMode: true, hmr: false } });
  try {
    const { default: Markdown } = await vite.ssrLoadModule("/src/components/Markdown.jsx");
    const markup = renderToStaticMarkup(createElement(Markdown, {
      text: "<script>alert(1)</script>\n**<img src=x onerror=alert(2)>** [Abrir](javascript:alert(3))"
    }));
    assert.match(markup, /&lt;script&gt;/);
    assert.match(markup, /&lt;img/);
    assert.doesNotMatch(markup, /<(script|img|a)\b/);
  } finally { await vite.close(); }
});
