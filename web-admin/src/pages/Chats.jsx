import { useEffect, useMemo, useState } from "react";
import { api } from "../api";
import { formatDateTime } from "../format";
import Icon from "../components/Icon";

/**
 * Últimos mensajes del chat botánico, con filtro por autor.
 */
export default function Chats() {
  const [messages, setMessages] = useState([]);
  const [filter, setFilter] = useState("all");
  const [query, setQuery] = useState("");
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

  const conversations = useMemo(() => {
    const grouped = new Map();
    messages.forEach((message) => {
      const username = message.username || "Sin usuario";
      const sessionId = message.sessionId || "sesion-sin-id";
      const key = `${username}::${sessionId}`;
      if (!grouped.has(key)) grouped.set(key, { key, username, sessionId, messages: [] });
      grouped.get(key).messages.push(message);
    });

    const search = query.trim().toLocaleLowerCase("es");
    return [...grouped.values()]
      .map((conversation) => ({
        ...conversation,
        messages: conversation.messages
          .filter((message) => filter === "all" || message.role === filter)
          .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt)),
      }))
      .filter((conversation) => {
        const content = conversation.messages.map((message) => message.content).join(" ");
        return conversation.messages.length > 0 &&
          (!search || `${conversation.username} ${conversation.sessionId} ${content}`.toLocaleLowerCase("es").includes(search));
      })
      .sort((a, b) => {
        const lastA = new Date(a.messages.at(-1)?.createdAt || 0);
        const lastB = new Date(b.messages.at(-1)?.createdAt || 0);
        return lastB - lastA;
      });
  }, [messages, filter, query]);

  const userGroups = useMemo(() => {
    const grouped = new Map();
    conversations.forEach((conversation) => {
      if (!grouped.has(conversation.username)) grouped.set(conversation.username, []);
      grouped.get(conversation.username).push(conversation);
    });
    return [...grouped.entries()];
  }, [conversations]);

  return (
    <article className="panel">
      <div className="panel-head chat-panel-head">
        <div>
          <h3>Conversaciones</h3>
          <p className="panel-subtitle">Mensajes agrupados por usuario y sesión</p>
        </div>
        <div className="toolbar chat-toolbar">
          <label className="search-box">
            <Icon name="search" size={18} />
            <input
              className="search"
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Buscar usuario o mensaje"
              aria-label="Buscar por usuario, sesión o contenido"
            />
          </label>
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
      ) : conversations.length === 0 ? (
        <div className="empty">No encontramos conversaciones con esa búsqueda.</div>
      ) : (
        <div className="conversation-users">
          {userGroups.map(([username, userConversations], index) => (
            <details className="user-conversation-group" key={username} open={index === 0 || Boolean(query)}>
              <summary>
                <span className="thread-avatar">{username.slice(0, 1).toLocaleUpperCase("es")}</span>
                <span className="thread-user-name">{username}</span>
                <span className="thread-count">{userConversations.length} {userConversations.length === 1 ? "conversación" : "conversaciones"}</span>
                <span className="thread-chevron" aria-hidden="true" />
              </summary>
              <div className="user-conversation-list">
                {userConversations.map((conversation) => (
                  <details className="conversation-card" key={conversation.key} open={Boolean(query)}>
                    <summary className="conversation-summary">
                      <div className="conversation-summary-main">
                        <span className="session-mark"><Icon name="chat" size={16} /></span>
                        <span>
                          <strong>Sesión {conversation.sessionId.slice(0, 8)}</strong>
                          <small>{conversation.messages.length} mensajes · {formatDateTime(conversation.messages.at(-1)?.createdAt)}</small>
                        </span>
                      </div>
                      <span className="thread-chevron" aria-hidden="true" />
                    </summary>
                    <div className="chat-list">
                      {conversation.messages.map((message) => (
                        <div key={message.id} className={`chat-item ${message.role === "user" ? "user" : "assistant"}`}>
                          <div className="meta">
                            <span>{message.role === "user" ? "Usuario" : "Asistente"}</span>
                            <span>{formatDateTime(message.createdAt)}</span>
                          </div>
                          <p>{message.content}</p>
                        </div>
                      ))}
                    </div>
                  </details>
                ))}
              </div>
            </details>
          ))}
        </div>
      )}
    </article>
  );
}
