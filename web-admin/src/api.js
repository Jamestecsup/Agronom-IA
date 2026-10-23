/**
 * Cliente del API de administración (Spring Boot).
 *
 * Guarda el token en localStorage y avisa a la app cuando el servidor responde
 * 401 para volver a la pantalla de login.
 */
const TOKEN_KEY = "agronomia.admin.token";
const SESSION_KEY = "agronomia.admin.session";

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || "";
}

export function saveSession(token, session) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function loadSession() {
  const raw = localStorage.getItem(SESSION_KEY);
  if (!raw) return null;
  try {
    const session = JSON.parse(raw);
    if (session.expiresAt && session.expiresAt * 1000 < Date.now()) {
      clearSession();
      return null;
    }
    return session;
  } catch {
    clearSession();
    return null;
  }
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(SESSION_KEY);
}

/**
 * @param {string} path ruta relativa, por ejemplo "/api/admin/stats"
 * @param {RequestInit} options
 */
export async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (options.body) {
    headers["Content-Type"] = "application/json";
  }
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(path, { ...options, headers });
  const contentType = response.headers.get("content-type") || "";
  const data = contentType.includes("application/json")
    ? await response.json()
    : null;

  if (response.status === 401) {
    clearSession();
    window.dispatchEvent(new Event("agronomia:unauthorized"));
    throw new Error("Sesión caducada. Vuelve a entrar.");
  }
  if (!response.ok) {
    throw new Error((data && data.error) || `Error ${response.status}`);
  }
  return data;
}

export function login(username, password) {
  return api("/api/admin/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}
