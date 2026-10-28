/**
 * Cliente del backend Django (servicio de usuario).
 *
 * Django hace todo el análisis (Pl@ntNet + Gemini con skills): la web solo
 * sube fotos, pide la identificación y conversa.
 */
const API = "/agronomia-api/api";

async function request(url, options = {}) {
  const response = await fetch(url, options);
  const contentType = response.headers.get("content-type") || "";
  const data = contentType.includes("application/json")
    ? await response.json().catch(() => null)
    : null;

  if (!response.ok || (data && data.success === false)) {
    const detail =
      (data && (data.error || data.message)) || `Error ${response.status}`;
    throw new Error(detail);
  }
  return data;
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

export function sendChat(message, context, sessionId) {
  return request(`${API}/chat`, {
    method: "POST",
    body: form({ message, context, session_id: sessionId }),
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
