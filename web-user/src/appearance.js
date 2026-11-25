export const APPEARANCE_KEY = "agronomia.appearance";
export const OPTIONS = {
  mode: { system: "Sistema", light: "Claro", dark: "Oscuro" },
  accent: { green: "Verde", blue: "Azul", plum: "Ciruela" },
  font: { sans: "Sans", serif: "Serif", accessible: "Lectura clara" },
  scale: { normal: "Normal", large: "Grande", extra: "Muy grande" },
  density: { comfortable: "Cómoda", compact: "Compacta" },
  garden: { cards: "Tarjetas", list: "Lista" },
  motion: { system: "Sistema", off: "Sin animaciones" },
};
export const DEFAULT_APPEARANCE = Object.freeze({
  mode: "system", accent: "green", font: "sans", scale: "normal",
  density: "comfortable", garden: "cards", motion: "system",
});
export function normalizeAppearance(value) {
  const normalized = { ...DEFAULT_APPEARANCE };
  for (const key of Object.keys(OPTIONS)) {
    if (Object.hasOwn(OPTIONS[key], value?.[key])) normalized[key] = value[key];
  }
  return normalized;
}
export function readAppearance() {
  try { return normalizeAppearance(JSON.parse(localStorage.getItem(APPEARANCE_KEY))); }
  catch { return normalizeAppearance(null); }
}
export function applyAppearance(value) {
  const style = normalizeAppearance(value);
  for (const [key, setting] of Object.entries(style)) document.documentElement.dataset[key] = setting;
}
export function saveAppearance(value) {
  const style = normalizeAppearance(value);
  try { localStorage.setItem(APPEARANCE_KEY, JSON.stringify(style)); } catch {}
  applyAppearance(style);
  return style;
}
