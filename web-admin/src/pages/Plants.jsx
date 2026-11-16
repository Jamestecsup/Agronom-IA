import { useEffect, useMemo, useState } from "react";
import { api } from "../api";
import { formatDateTime, percent } from "../format";
import Icon from "../components/Icon";

/**
 * Identificaciones recientes (las escribe el backend Django en MySQL).
 */
export default function Plants() {
  const [plants, setPlants] = useState([]);
  const [query, setQuery] = useState("");
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

  const visiblePlants = useMemo(() => {
    const search = query.trim().toLocaleLowerCase("es");
    if (!search) return plants;
    return plants.filter((plant) =>
      [plant.username, plant.scientificName, plant.commonName, plant.family]
        .filter(Boolean)
        .some((value) => value.toLocaleLowerCase("es").includes(search))
    );
  }, [plants, query]);

  return (
    <article className="panel">
      <div className="panel-head identification-head">
        <div>
          <h3>Identificaciones <span className="result-count">{visiblePlants.length}</span></h3>
          <p className="panel-subtitle">Busca por usuario, nombre de planta o familia</p>
        </div>
        <label className="search-box identification-search">
          <Icon name="search" size={18} />
          <input
            className="search"
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Buscar identificación"
            aria-label="Buscar por usuario, especie, nombre común o familia"
          />
        </label>
      </div>

      {error && <div className="alert error">{error}</div>}

      {loading ? (
        <div className="loading">Cargando identificaciones…</div>
      ) : plants.length === 0 ? (
        <div className="empty">
          Aún no hay identificaciones. Haz una prueba desde el móvil o la web.
        </div>
      ) : visiblePlants.length === 0 ? (
        <div className="empty">No encontramos identificaciones con esa búsqueda.</div>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Usuario</th>
              <th>Especie</th>
              <th>Nombre común</th>
              <th>Familia</th>
              <th>Confianza</th>
              <th>Fecha</th>
            </tr>
          </thead>
          <tbody>
            {visiblePlants.map((plant) => (
              <tr key={plant.id}>
                <td>{plant.username || "Sin usuario"}</td>
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
