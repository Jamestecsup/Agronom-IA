import { useEffect, useState } from "react";
import { api } from "../api";
import { formatDay } from "../format";
import Icon from "../components/Icon";

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
  const metrics = [
    { label: "Usuarios", value: stats.users, icon: "users", note: "cuentas registradas", tone: "sage" },
    { label: "Identificaciones", value: stats.plants, icon: "plant", note: "plantas reconocidas", tone: "leaf" },
    { label: "Fotos subidas", value: stats.images, icon: "photos", note: "imágenes analizadas", tone: "amber" },
    { label: "Mensajes de chat", value: stats.chats, icon: "messages", note: "interacciones botánicas", tone: "blue" },
  ];

  return (
    <>
      <section className="dashboard-welcome">
        <div className="welcome-copy">
          <span className="eyebrow"><span className="status-dot" /> PANEL DE CONTROL</span>
          <h1>Actividad de Agronom-IA</h1>
          <p>Un vistazo claro a la comunidad, las plantas identificadas y el uso de la aplicación.</p>
        </div>
        <div className="welcome-art" aria-hidden="true">
          <span className="art-orbit orbit-one" />
          <span className="art-orbit orbit-two" />
          <div className="art-leaf"><Icon name="plant" size={52} strokeWidth={1.35} /></div>
          <div className="art-spark"><Icon name="sparkle" size={25} /></div>
        </div>
      </section>

      <section className="cards">
        {metrics.map((metric) => (
          <article className={`card stat stat-${metric.tone}`} key={metric.label}>
            <div className="stat-topline">
              <span className="stat-icon"><Icon name={metric.icon} size={21} /></span>
              <span className="stat-label">{metric.label}</span>
            </div>
            <div className="value">{metric.value.toLocaleString("es-PE")}</div>
            <div className="stat-note">{metric.note}</div>
          </article>
        ))}
      </section>

      <section className="two-cols">
        <article className="panel">
          <div className="panel-head">
            <h3>Actividad de los últimos 14 días</h3>
            <span className="panel-kicker"><Icon name="chart" size={15} /> últimos 14 días</span>
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
                  <div className="species-icon"><Icon name="plant" size={17} /></div>
                  <div className="names">
                    <strong>{species.commonName || species.scientificName}</strong>
                    <span>{species.scientificName}</span>
                  </div>
                  <div className="species-count"><span>{species.count}</span><small>veces</small></div>
                </div>
              ))}
            </div>
          )}
        </article>
      </section>
    </>
  );
}
