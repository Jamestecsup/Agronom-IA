import Link from "./Link";

const routes = [["/", "Escáner"], ["/garden", "Mi jardín"], ["/chat", "Chat"], ["/settings", "Ajustes"]];

export default function Header({ username, path, onLogout }) {
  const activePath = path.startsWith("/plants/") ? "/garden" : path === "/scanner" ? "/" : path;
  return <header className="header">
    <div className="header-inner">
      <Link className="brand" to="/" aria-label="Agronom-IA, escáner">
        <span className="brand-mark" aria-hidden="true">A</span>
        <span className="brand-title">Agronom-IA</span>
      </Link>
      <nav aria-label="Navegación principal" className="nav">
        {routes.map(([route, label]) => <Link key={route} to={route}
          className={activePath === route ? "active" : ""} aria-current={activePath === route ? "page" : undefined}>{label}</Link>)}
      </nav>
      <div className="user"><span>{username}</span>
        <button type="button" className="btn ghost small" onClick={onLogout}>Salir</button>
      </div>
    </div>
  </header>;
}
