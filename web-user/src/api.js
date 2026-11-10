/**
 * Cliente del backend Django (servicio de usuario).
 *
 * Django hace todo el análisis (Pl@ntNet + Gemini con skills): la web solo
 * sube fotos, pide la identificación y conversa.
 */
const API = "/agronomia-api/api";
const USER_SESSION_KEY = "agronomia.user.session";

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
  try {
    response = await fetch(url, {
      ...options,
      headers: authenticatedHeaders(options.headers),
    });
  } catch {
    throw new Error("No pudimos conectar con el servicio. Revisa tu conexión e inténtalo de nuevo.");
  }
  const contentType = response.headers.get("content-type") || "";
  const data = contentType.includes("application/json")
    ? await response.json().catch(() => null)
    : null;

  if (!response.ok || (data && data.success === false)) {
    const detail =
      (data && (data.error || data.message)) || "";
    throw new Error(userFacingError(response.status, detail));
  }
  return data;
}

function userFacingError(status, detail = "") {
  const technical = /Pl@ntNet|Gemini|HTTP\s*\d{3}|statusCode|Unsupported file type|Bad Request|API_KEY|traceback|exception|SQL|database|JSON|\{\s*"/i.test(detail);
  const safeDetail = detail && detail.length < 240 && !technical;

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
  if (status === 429) return "El servicio está ocupado. Espera un momento y vuelve a intentarlo.";
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

export function uploadImages(files) {
  const body = new FormData();
  files.forEach((file) => body.append("photos[]", file));
  return request(`${API}/images.php`, { method: "POST", body });
}

export function identify(batchId, organ) {
  return request(`${API}/identify`, {
    method: "POST",
    body: form({ batch_id: batchId, organ }),
  });
}

export function confirmCandidate(batchId, scientificName) {
  return request(`${API}/confirm`, {
    method: "POST",
    body: form({ batch_id: batchId, scientific_name: scientificName }),
  });
}

export function listIdentifiedPlants() {
  return request(`${API}/plants`);
}

export function getChatHistory(plantId) {
  return request(`${API}/chat/history?plant_id=${encodeURIComponent(plantId)}`);
}

export function sendChat(message, plantId) {
  return request(`${API}/chat`, {
    method: "POST",
    body: form({ message, plant_id: plantId }),
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
