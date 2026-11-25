/**
 * Cliente de la API de usuario de Spring Boot.
 */
const API = "/agronomia-api/api";
const USER_SESSION_KEY = "agronomia.user.session";
const cache = new Map();
export const clearCache = () => cache.clear();

function cached(path) {
  const entry = cache.get(path);
  if (entry && Date.now() < entry.expires) return entry.promise;
  const promise = request(API + path).catch((error) => {
    if (cache.get(path)?.promise === promise) cache.delete(path);
    throw error;
  });
  cache.set(path, { expires: Date.now() + 30_000, promise });
  return promise;
}

function scopedChange(path, method, data) {
  return request(API + path, {
    method, ...(data === undefined ? {} : { headers: { "Content-Type": "application/json" }, body: JSON.stringify(data) }),
  }).then((result) => { clearCache(); return result; });
}

export const getPreferences = () => cached("/preferences");
export const savePreferences = (preferences) => scopedChange("/preferences", "PUT", { version: 1, preferences });
export const listProviders = () => cached("/ai/providers");
export const listPresets = () => cached("/ai/presets");
export const getCapabilities = () => cached("/ai/capabilities");
export const saveProvider = (data, id) => scopedChange(id ? "/ai/providers/" + encodeURIComponent(id) : "/ai/providers", id ? "PATCH" : "POST", data);
export const probeProvider = (id) => scopedChange("/ai/providers/" + encodeURIComponent(id) + "/test", "POST");
export const selectProvider = (id, consent) => scopedChange("/ai/selection", "PUT", { provider_id: id, consent });
export const removeProvider = (id) => scopedChange("/ai/providers/" + encodeURIComponent(id), "DELETE");
export const deleteChat = (id) => scopedChange("/chat?plant_id=" + encodeURIComponent(id), "DELETE");
export const deleteAllChats = () => scopedChange("/chats?confirm=true", "DELETE");
export const getPlant = (id) => cached("/plants/" + encodeURIComponent(id));

function authenticatedHeaders(headers = {}) {
  let token = "";
  try {
    token = JSON.parse(localStorage.getItem(USER_SESSION_KEY) || "null")?.token || "";
  } catch {
    token = "";
  }
  return token ? { ...headers, Authorization: `Bearer ${token}` } : headers;
}

async function request(url, options = {}) {
  let response;
  const headers = authenticatedHeaders(options.headers);
  try {
    response = await fetch(url, {
      ...options,
      headers,
    });
  } catch (error) {
    if (error.name === "AbortError") throw error;
    throw new Error("No pudimos conectar con el servicio. Revisa tu conexión e inténtalo de nuevo.");
  }
  const contentType = response.headers.get("content-type") || "";
  const data = contentType.includes("application/json")
    ? await response.json().catch(() => null)
    : null;

  if (!response.ok || (data && data.success === false)) {
    if (response.status === 401 && headers.Authorization && !/\/(login|register)$/.test(url)
        && headers.Authorization === authenticatedHeaders().Authorization) {
      window.dispatchEvent(new Event("agronomia:expired"));
    }
    const detail =
      (data && (data.error || data.message)) || "";
    throw Object.assign(new Error(userFacingError(response.status, detail)), { status: response.status });
  }
  return data;
}

function userFacingError(status, detail = "") {
  const technical = /Pl@ntNet|Gemini|HTTP\s*\d{3}|statusCode|Unsupported file type|Bad Request|API_KEY|traceback|exception|SQL|database|JSON|\{\s*"/i.test(detail);
  const safeDetail = detail && detail.length < 240 && !technical;

  if (status === 502) return "El proveedor no pudo completar la consulta. Puedes volver a intentarlo.";
  if (status >= 500) {
    if (safeDetail && /^(No pudimos|No se pudo|El servicio)/i.test(detail)) return detail;
    return "No pudimos completar la solicitud. Inténtalo de nuevo.";
  }
  if (status === 413) return "La foto es demasiado pesada. Elige una imagen más ligera.";
  if (status === 422) {
    if (safeDetail && /^(Máximo|Solo se añadieron|No se pudo leer|Formato no admitido|La imagen|La foto|El archivo)/i.test(detail)) return detail;
    return "No pudimos usar esa foto. Comprueba que sea JPG o PNG y que no pese demasiado.";
  }
  if (status === 401) return safeDetail ? detail : "Tu sesión terminó. Inicia sesión nuevamente.";
  if (status === 403) return safeDetail ? detail : "No tienes permiso para realizar esta acción.";
  if (status === 404) return "No encontramos la información solicitada. Vuelve a intentarlo.";
  if (status === 409) return safeDetail ? detail : "Ese usuario ya existe. Prueba con otro nombre.";
  if (status === 429) return safeDetail ? detail : "El servicio está ocupado. Espera un momento y vuelve a intentarlo.";
  if (status === 400) {
    if (safeDetail && !/photos\[\]|batch_id|session_id|plant_id|username|password/i.test(detail)) return detail;
    return "No pudimos procesar la solicitud. Revisa la información e inténtalo de nuevo.";
  }
  return safeDetail ? detail : "Ocurrió un problema. Inténtalo de nuevo.";
}

function form(fields) {
  const body = new FormData();
  Object.entries(fields).forEach(([key, value]) => {
    if (value !== undefined && value !== null) body.append(key, value);
  });
  return body;
}

export function uploadImages(files, signal) {
  const body = new FormData();
  files.forEach((file) => body.append("photos[]", file));
  return request(`${API}/images.php`, { method: "POST", body, signal });
}

export function identify(batchId, organ, signal) {
  return request(`${API}/identify`, {
    method: "POST",
    body: form({ batch_id: batchId, organ }),
    signal,
  }).then((result) => { if (result?.plant) cache.delete("/plants"); return result; });
}

export function confirmCandidate(batchId, scientificName, signal) {
  return request(`${API}/confirm`, {
    method: "POST",
    body: form({ batch_id: batchId, scientific_name: scientificName }),
    signal,
  }).then((result) => { if (result?.plant) cache.delete("/plants"); return result; });
}

export function listIdentifiedPlants() {
  return cached("/plants");
}

export async function getPlantCoverUrl(plantId) {
  try {
    const response = await fetch(
      `${API}/plants/${encodeURIComponent(plantId)}/cover`,
      { headers: authenticatedHeaders() },
    );
    if (!response.ok) return null;
    return URL.createObjectURL(await response.blob());
  } catch {
    return null;
  }
}

export function getChatHistory(plantId) {
  return request(`${API}/chat/history?plant_id=${encodeURIComponent(plantId)}`);
}

export function sendChat(message, plantId, image) {
  return request(`${API}/chat`, {
    method: "POST",
    body: form({ message, plant_id: plantId, image }),
  });
}

export function login(username, password) {
  return request(`${API}/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
}

export function register(username, password, email) {
  return request(`${API}/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password, email, role: "user" }),
  });
}
