/**
 * Persistencia local del navegador: sesión y jardín.
 */
const SESSION_KEY = "agronomia.user.session";
const GARDEN_KEY = "agronomia.user.garden";

export function loadSession() {
  try {
    const session = JSON.parse(localStorage.getItem(SESSION_KEY) || "null");
    return session?.token ? session : null;
  } catch {
    return null;
  }
}

export function saveSession(session) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function clearSession() {
  localStorage.removeItem(SESSION_KEY);
}

export function loadGarden() {
  try {
    const items = JSON.parse(localStorage.getItem(GARDEN_KEY) || "[]");
    return Array.isArray(items) ? items : [];
  } catch {
    return [];
  }
}

export function saveGarden(items) {
  // Se guardan como máximo 30 fichas para no llenar el almacenamiento.
  localStorage.setItem(GARDEN_KEY, JSON.stringify(items.slice(0, 30)));
}

/**
 * Reduce la foto a una miniatura JPEG para poder guardarla en el jardín sin
 * ocupar el espacio de la imagen original.
 */
export function makeThumbnail(file, maxSize = 360) {
  return new Promise((resolve) => {
    const image = new Image();
    const url = URL.createObjectURL(file);
    image.onload = () => {
      const scale = Math.min(1, maxSize / Math.max(image.width, image.height));
      const canvas = document.createElement("canvas");
      canvas.width = Math.max(1, Math.round(image.width * scale));
      canvas.height = Math.max(1, Math.round(image.height * scale));
      canvas.getContext("2d").drawImage(image, 0, 0, canvas.width, canvas.height);
      URL.revokeObjectURL(url);
      resolve(canvas.toDataURL("image/jpeg", 0.72));
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      resolve(null);
    };
    image.src = url;
  });
}
