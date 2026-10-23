import { useEffect, useState } from "react";
import { api } from "../api";
import { formatDateTime } from "../format";

/**
 * Gestión de cuentas: buscar, cambiar rol y activar/desactivar.
 *
 * Los cambios se guardan por fila para no enviar ediciones a medias.
 */
export default function Users() {
  const [users, setUsers] = useState([]);
  const [drafts, setDrafts] = useState({});
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [savingId, setSavingId] = useState(null);

  useEffect(() => {
    let active = true;
    const timer = setTimeout(async () => {
      setLoading(true);
      setError("");
      try {
        const data = await api(
          `/api/admin/users?q=${encodeURIComponent(query)}&limit=100`
        );
        if (!active) return;
        setUsers(data);
        setDrafts(
          Object.fromEntries(
            data.map((user) => [
              user.id,
              { role: user.role, active: user.active },
            ])
          )
        );
      } catch (problem) {
        if (active) setError(problem.message);
      } finally {
        if (active) setLoading(false);
      }
    }, 300);

    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [query]);

  function updateDraft(id, patch) {
    setDrafts((previous) => ({
      ...previous,
      [id]: { ...previous[id], ...patch },
    }));
  }

  async function save(user) {
    const draft = drafts[user.id];
    setSavingId(user.id);
    setError("");
    setNotice("");
    try {
      const updated = await api(`/api/admin/users/${user.id}`, {
        method: "PATCH",
        body: JSON.stringify(draft),
      });
      setUsers((previous) =>
        previous.map((item) => (item.id === updated.id ? updated : item))
      );
      setDrafts((previous) => ({
        ...previous,
        [updated.id]: { role: updated.role, active: updated.active },
      }));
      setNotice(`Usuario ${updated.username} actualizado.`);
    } catch (problem) {
      setError(problem.message);
    } finally {
      setSavingId(null);
    }
  }

  return (
    <article className="panel">
      <div className="panel-head">
        <h3>Usuarios ({users.length})</h3>
        <div className="toolbar">
          <input
            className="search"
            placeholder="Buscar por usuario o correo…"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>
      </div>

      {error && <div className="alert error">{error}</div>}
      {notice && <div className="alert ok">{notice}</div>}

      {loading ? (
        <div className="loading">Cargando usuarios…</div>
      ) : users.length === 0 ? (
        <div className="empty">No hay usuarios que coincidan.</div>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Usuario</th>
              <th>Correo</th>
              <th>Rol</th>
              <th>Estado</th>
              <th>Alta</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {users.map((user) => {
              const draft = drafts[user.id] || {
                role: user.role,
                active: user.active,
              };
              const dirty =
                draft.role !== user.role || draft.active !== user.active;
              return (
                <tr key={user.id}>
                  <td>
                    <strong>{user.username}</strong>
                  </td>
                  <td className="muted">{user.email || "—"}</td>
                  <td>
                    <select
                      className="role-select"
                      value={draft.role}
                      onChange={(event) =>
                        updateDraft(user.id, { role: event.target.value })
                      }
                    >
                      <option value="user">user</option>
                      <option value="admin">admin</option>
                    </select>
                  </td>
                  <td>
                    <button
                      type="button"
                      className={`tag ${draft.active ? "on" : "off"}`}
                      style={{ border: "none", cursor: "pointer" }}
                      onClick={() =>
                        updateDraft(user.id, { active: !draft.active })
                      }
                    >
                      {draft.active ? "activo" : "inactivo"}
                    </button>
                  </td>
                  <td className="muted">{formatDateTime(user.createdAt)}</td>
                  <td>
                    <button
                      className="btn"
                      disabled={!dirty || savingId === user.id}
                      onClick={() => save(user)}
                    >
                      {savingId === user.id ? "Guardando…" : "Guardar"}
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </article>
  );
}
