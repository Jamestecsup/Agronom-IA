import { useEffect, useState } from "react";
import { api } from "../api";
import { formatDay } from "../format";

/**
 * Resumen general: totales, especies más identificadas y actividad reciente.
 */
export default function Dashboard() {
  const [stats, setStats] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    api("/api/admin/stats")
      .then((data) => {
        if (active) setStats(data);
      })
      .catch((problem) => {
        if (active) setError(problem.message);
      });
    return () => {
      active = false;
    };
  }, []);

  if (error) {
    return <div className="alert error">{error}</div>;
  }
  if (!stats) {
    return <div className="loading">Cargando resumen…</div>;
  }

  const maxDaily = Math.max(1, ...stats.daily.map((day) => day.count));

  return (
    <>
      <section className="cards">
        <article className="card stat">
          <div className="label">Usuarios</div>
          <div className="value">{stats.users}</div>
        </article>
        <article className="card stat">
          <div className="label">Identificaciones</div>
          <div className="value">{stats.plants}</div>
        </article>
        <article className="card stat">
          <div className="label">Fotos subidas</div>
          <div className="value">{stats.images}</div>
        </article>
        <article className="card stat">
          <div className="label">Mensajes de chat</div>
          <div className="value">{stats.chats}</div>
        </article>
      </section>

      <section className="two-cols">
        <article className="panel">
          <div className="panel-head">
            <h3>Actividad de los últimos 14 días</h3>
            <span className="muted">identificaciones por día</span>
          </div>
          {stats.daily.length === 0 ? (
            <div className="empty">Todavía no hay identificaciones registradas.</div>
          ) : (
            <div className="chart">
              {stats.daily.map((day) => (
                <div className="bar-wrap" key={day.date}>
                  <div
                    className="bar"
                    style={{ height: `${(day.count / maxDaily) * 100}%` }}
                    title={`${day.date}: ${day.count}`}
                  />
                  <span className="bar-label">{formatDay(day.date)}</span>
                </div>
              ))}
            </div>
          )}
        </article>

        <article className="panel">
          <div className="panel-head">
            <h3>Especies más identificadas</h3>
          </div>
          {stats.topSpecies.length === 0 ? (
            <div className="empty">Aún no hay especies registradas.</div>
          ) : (
            <div className="rank">
              {stats.topSpecies.map((species, index) => (
                <div className="rank-row" key={species.scientificName}>
                  <div className="pos">{index + 1}</div>
                  <div className="names">
                    <strong>{species.commonName || species.scientificName}</strong>
                    <span>{species.scientificName}</span>
                  </div>
                  <div className="count">{species.count}</div>
                </div>
              ))}
            </div>
          )}
        </article>
      </section>
    </>
  );
}
