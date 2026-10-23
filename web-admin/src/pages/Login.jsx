import { useState } from "react";
import { login, saveSession } from "../api";

/**
 * Pantalla de acceso. Solo las cuentas con rol admin pueden entrar; el
 * servidor lo comprueba y responde 403 en caso contrario.
 */
export default function Login({ onLogin }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const data = await login(username.trim(), password);
      const session = {
        username: data.username,
        role: data.role,
        expiresAt: data.expiresAt,
      };
      saveSession(data.token, session);
      onLogin(session);
    } catch (problem) {
      setError(problem.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="brand">
          <div className="brand-mark">🌿</div>
          <div>
            <h1>Agronom-IA</h1>
            <span>ADMINISTRACIÓN</span>
          </div>
        </div>

        <p className="hint">
          Panel para revisar usuarios, identificaciones y actividad del asistente
          botánico.
        </p>

        {error && <div className="alert error">{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="username">Usuario</label>
            <input
              id="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              autoComplete="username"
              required
            />
          </div>
          <div className="field">
            <label htmlFor="password">Contraseña</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              required
            />
          </div>
          <button className="btn full" type="submit" disabled={busy}>
            {busy ? "Entrando…" : "Entrar"}
          </button>
        </form>

        <div className="demo-note">
          Demo local: <strong>admin</strong> / <strong>Admin12345</strong>
          <br />
          Se crea con <code>py manage.py seed_demo</code>.
        </div>
      </div>
    </div>
  );
}
