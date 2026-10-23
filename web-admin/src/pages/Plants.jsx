import { useEffect, useState } from "react";
import { api } from "../api";
import { formatDateTime, percent } from "../format";

/**
 * Identificaciones recientes (las escribe el backend Django en MySQL).
 */
export default function Plants() {
  const [plants, setPlants] = useState([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    api("/api/admin/plants?limit=100")
      .then((data) => {
        if (active) setPlants(data);
      })
      .catch((problem) => {
        if (active) setError(problem.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <article className="panel">
      <div className="panel-head">
        <h3>Identificaciones ({plants.length})</h3>
        <span className="muted">ordenadas por fecha</span>
      </div>

      {error && <div className="alert error">{error}</div>}

      {loading ? (
        <div className="loading">Cargando identificaciones…</div>
      ) : plants.length === 0 ? (
        <div className="empty">
          Aún no hay identificaciones. Haz una prueba desde el móvil o la web.
        </div>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Especie</th>
              <th>Nombre común</th>
              <th>Familia</th>
              <th>Confianza</th>
              <th>Fecha</th>
            </tr>
          </thead>
          <tbody>
            {plants.map((plant) => (
              <tr key={plant.id}>
                <td>
                  <em>{plant.scientificName}</em>
                </td>
                <td>{plant.commonName || "—"}</td>
                <td className="muted">{plant.family || "—"}</td>
                <td>
                  <div className="rank-row">
                    <div
                      className="bar"
                      style={{
                        width: `${Math.max(4, plant.confidence * 100)}px`,
                        height: "8px",
                        minHeight: "8px",
                        maxWidth: "90px",
                        borderRadius: "4px",
                      }}
                    />
                    <span>{percent(plant.confidence)}</span>
                  </div>
                </td>
                <td className="muted">{formatDateTime(plant.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </article>
  );
}
