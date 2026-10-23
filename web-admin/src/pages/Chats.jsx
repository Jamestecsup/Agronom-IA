import { useEffect, useMemo, useState } from "react";
import { api } from "../api";
import { formatDateTime } from "../format";

/**
 * Últimos mensajes del chat botánico, con filtro por autor.
 */
export default function Chats() {
  const [messages, setMessages] = useState([]);
  const [filter, setFilter] = useState("all");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    api("/api/admin/chats?limit=100")
      .then((data) => {
        if (active) setMessages(data);
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

  const visible = useMemo(
    () =>
      filter === "all"
        ? messages
        : messages.filter((message) => message.role === filter),
    [messages, filter]
  );

  return (
    <article className="panel">
      <div className="panel-head">
        <h3>Chat ({messages.length})</h3>
        <div className="toolbar">
          <select
            className="role-select"
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
          >
            <option value="all">Todos</option>
            <option value="user">Usuarios</option>
            <option value="assistant">Asistente</option>
          </select>
        </div>
      </div>

      {error && <div className="alert error">{error}</div>}

      {loading ? (
        <div className="loading">Cargando mensajes…</div>
      ) : visible.length === 0 ? (
        <div className="empty">No hay mensajes para este filtro.</div>
      ) : (
        <div className="chat-list">
          {visible.map((message) => (
            <div
              key={message.id}
              className={`chat-item ${message.role === "user" ? "user" : "assistant"}`}
            >
              <div className="meta">
                <span>
                  {message.role === "user" ? "Usuario" : "Asistente"} · sesión{" "}
                  {message.sessionId.slice(0, 8)}
                </span>
                <span>{formatDateTime(message.createdAt)}</span>
              </div>
              <p>{message.content}</p>
            </div>
          ))}
        </div>
      )}
    </article>
  );
}
