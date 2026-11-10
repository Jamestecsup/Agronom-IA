/**
 * Foto de referencia de una especie en Wikimedia Commons.
 *
 * Es la misma fuente que usa la app Android. No necesita clave y no bloquea:
 * si no hay foto (o falla la red), devuelve "" y la tarjeta muestra un
 * marcador. Las respuestas se cachean por nombre científico.
 */
const cache = new Map();

export function fetchCandidateImage(scientificName) {
  const name = (scientificName || "").trim();
  if (!name) return Promise.resolve("");
  const key = name.toLowerCase();
  if (!cache.has(key)) {
    cache.set(key, lookup(name));
  }
  return cache.get(key);
}

async function lookup(name) {
  try {
    const params = new URLSearchParams({
      action: "query",
      format: "json",
      origin: "*",
      generator: "search",
      gsrsearch: name,
      gsrlimit: "5",
      gsrnamespace: "6",
      prop: "imageinfo",
      iiprop: "url",
      iiurlwidth: "480",
    });
    const response = await fetch(
      `https://commons.wikimedia.org/w/api.php?${params.toString()}`
    );
    if (!response.ok) return "";
    const data = await response.json();
    const pages = data?.query?.pages;
    if (!pages) return "";
    for (const page of Object.values(pages)) {
      const info = page?.imageinfo?.[0];
      const url = info?.thumburl || info?.url;
      if (url) return url;
    }
  } catch {
    // Sin foto de referencia: la tarjeta sigue funcionando con el marcador.
  }
  return "";
}
