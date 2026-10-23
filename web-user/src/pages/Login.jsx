import { useState } from "react";
import { login, register } from "../api";

/**
 * Acceso a la web de usuario. El registro crea una cuenta con rol `user`
 * y luego inicia sesión automáticamente.
 */
export default function Login({ onLogin }) {
  const [mode, setMode] = useState("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (mode === "register") {
        await register(username.trim(), password, email.trim());
      }
      const data = await login(username.trim(), password);
      onLogin({ username: data.username, role: data.role });
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
            <span>ASISTENTE BOTÁNICO</span>
          </div>
        </div>

        <p className="lead">
          Sube una foto de tu planta y recibe una ficha clara: luz, riego,
          suelo, clima, floración, usos y cuidados. Después puedes preguntarle
          al asistente.
        </p>

        <div className="tabs">
          <button
            type="button"
            className={mode === "login" ? "active" : ""}
            onClick={() => {
              setMode("login");
              setError("");
            }}
          >
            Entrar
          </button>
          <button
            type="button"
            className={mode === "register" ? "active" : ""}
            onClick={() => {
              setMode("register");
              setError("");
            }}
          >
            Crear cuenta
          </button>
        </div>

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

          {mode === "register" && (
            <div className="field">
              <label htmlFor="email">Correo (opcional)</label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                autoComplete="email"
              />
            </div>
          )}

          <div className="field">
            <label htmlFor="password">Contraseña</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete={mode === "login" ? "current-password" : "new-password"}
              required
            />
          </div>

          <button className="btn full" type="submit" disabled={busy}>
            {busy
              ? "Espera…"
              : mode === "login"
              ? "Entrar"
              : "Crear cuenta y entrar"}
          </button>
        </form>

        <div className="demo-note">
          Demo local: <strong>agricultor</strong> / <strong>Campo12345</strong>
          <br />
          Se crea con <code>py manage.py seed_demo</code>.
        </div>
      </div>
    </div>
  );
}
