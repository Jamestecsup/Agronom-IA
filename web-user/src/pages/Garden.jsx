/**
 * Mi jardín: fichas guardadas en el navegador (últimas 30).
 */
export default function Garden({ items, onOpen, onScan, onClear }) {
  if (items.length === 0) {
    return (
      <div className="card center" style={{ padding: 48 }}>
        <div style={{ fontSize: 48 }}>🪴</div>
        <h2>Tu jardín está vacío</h2>
        <p className="hint">
          Cada planta que identifiques se guarda aquí, en este navegador, para
          volver a leer su ficha cuando quieras.
        </p>
        <button className="btn" onClick={onScan}>
          Escanear mi primera planta
        </button>
      </div>
    );
  }

  return (
    <>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 12,
          marginBottom: 18,
          flexWrap: "wrap",
        }}
      >
        <div>
          <h2 style={{ margin: 0 }}>Mi jardín</h2>
          <p className="hint" style={{ margin: "4px 0 0" }}>
            {items.length} planta{items.length === 1 ? "" : "s"} guardada
            {items.length === 1 ? "" : "s"} en este navegador.
          </p>
        </div>
        <button className="btn ghost small" onClick={onClear}>
          Vaciar jardín
        </button>
      </div>

      <div className="garden-grid">
        {items.map((item) => (
          <button
            className="garden-card"
            key={item.id}
            onClick={() => onOpen(item)}
          >
            <div className="thumb">
              {item.photo ? (
                <img src={item.photo} alt={item.plant.common_name} />
              ) : (
                "🌿"
              )}
            </div>
            <div className="info">
              <strong>
                {item.plant.common_name || item.plant.scientific_name}
              </strong>
              <em>{item.plant.scientific_name}</em>
              <span>
                {new Date(item.createdAt).toLocaleDateString("es-PE", {
                  day: "2-digit",
                  month: "short",
                  year: "numeric",
                })}
              </span>
            </div>
          </button>
        ))}
      </div>
    </>
  );
}
