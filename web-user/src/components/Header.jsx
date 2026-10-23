/**
 * Cabecera con la marca, la navegación principal y la sesión del agricultor.
 */
export default function Header({ username, view, onNavigate, onLogout }) {
  return (
    <header className="header">
      <div className="brand">
        <div className="brand-mark">🌿</div>
        <div>
          <h1>Agronom-IA</h1>
          <span>ASISTENTE BOTÁNICO</span>
        </div>
      </div>

      <nav className="nav">
        <button
          className={view === "scanner" ? "active" : ""}
          onClick={() => onNavigate("scanner")}
        >
          Escáner
        </button>
        <button
          className={view === "garden" ? "active" : ""}
          onClick={() => onNavigate("garden")}
        >
          Mi jardín
        </button>
      </nav>

      <div className="spacer" />

      <div className="user">
        <div className="avatar">{username.slice(0, 1)}</div>
        <span>{username}</span>
        <button className="btn ghost small" onClick={onLogout}>
          Salir
        </button>
      </div>
    </header>
  );
}
