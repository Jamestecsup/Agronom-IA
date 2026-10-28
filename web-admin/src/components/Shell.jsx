import { useState } from "react";
import Dashboard from "../pages/Dashboard";
import Users from "../pages/Users";
import Plants from "../pages/Plants";
import Chats from "../pages/Chats";

const VIEWS = [
  { id: "dashboard", label: "Resumen", icon: "📊" },
  { id: "users", label: "Usuarios", icon: "👥" },
  { id: "plants", label: "Identificaciones", icon: "🌿" },
  { id: "chats", label: "Chat", icon: "💬" },
];

/**
 * Estructura del panel: barra lateral de navegación y cabecera con la sesión.
 */
export default function Shell({ session, onLogout }) {
  const [view, setView] = useState("dashboard");
  const current = VIEWS.find((item) => item.id === view) || VIEWS[0];

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">🌿</div>
          <div>
            <h1>Agronom-IA</h1>
            <span>ADMINISTRACIÓN</span>
          </div>
        </div>

        <nav className="nav">
          {VIEWS.map((item) => (
            <button
              key={item.id}
              className={item.id === view ? "active" : ""}
              onClick={() => setView(item.id)}
            >
              <span aria-hidden="true">{item.icon}</span>
              {item.label}
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          Servicio admin · Spring Boot
          <br />
          MySQL compartida con Django
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <h2>{current.label}</h2>
          <div className="who">
            <div className="avatar">{session.username.slice(0, 1)}</div>
            <span>
              {session.username} · administrador
            </span>
            <button className="btn ghost" onClick={onLogout}>
              Salir
            </button>
          </div>
        </header>

        <main className="content">
          {view === "dashboard" && <Dashboard />}
          {view === "users" && <Users />}
          {view === "plants" && <Plants />}
          {view === "chats" && <Chats />}
        </main>
      </div>
    </div>
  );
}
