/**
 * Formatos de fecha y número en español para el panel.
 */
export function formatDateTime(value) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("es-PE", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** Convierte "2026-10-23" en "23/10". */
export function formatDay(value) {
  if (!value) return "";
  const parts = value.split("-");
  return parts.length === 3 ? `${parts[2]}/${parts[1]}` : value;
}

export function percent(value) {
  return `${Math.round((value || 0) * 100)}%`;
}
