import { useState } from "react";
import Header from "./components/Header";
import ChatPanel from "./components/ChatPanel";
import Login from "./pages/Login";
import Scanner from "./pages/Scanner";
import Result from "./pages/Result";
import Garden from "./pages/Garden";
import {
  clearSession,
  loadGarden,
  loadSession,
  makeThumbnail,
  saveGarden,
  saveSession,
} from "./storage";

/**
 * Web de usuario: login, escáner, ficha, jardín y chat flotante.
 */
export default function App() {
  const [session, setSession] = useState(() => loadSession());
  const [view, setView] = useState("scanner");
  const [garden, setGarden] = useState(() => loadGarden());
  const [result, setResult] = useState(null);
  const [chatOpen, setChatOpen] = useState(false);

  function handleLogin(user) {
    saveSession(user);
    setSession(user);
  }

  function handleLogout() {
    clearSession();
    setSession(null);
    setResult(null);
    setView("scanner");
    setChatOpen(false);
  }

  async function handleResult(plant, file) {
    // Se guarda una miniatura para el jardín, no la foto completa.
    const photo = file ? await makeThumbnail(file) : null;
    const entry = {
      id: crypto.randomUUID(),
      plant,
      photo,
      createdAt: new Date().toISOString(),
    };

    setResult(entry);
    setGarden((previous) => {
      const next = [entry, ...previous].slice(0, 30);
      saveGarden(next);
      return next;
    });
    setView("result");
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function openFromGarden(entry) {
    setResult(entry);
    setView("result");
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  if (!session) {
    return <Login onLogin={handleLogin} />;
  }

  return (
    <div className="app">
      <Header
        username={session.username}
        view={view}
        onNavigate={(next) => {
          setView(next);
          window.scrollTo({ top: 0 });
        }}
        onLogout={handleLogout}
      />

      <main className="page">
        {view === "scanner" && <Scanner onResult={handleResult} />}

        {view === "garden" && (
          <Garden
            items={garden}
            onOpen={openFromGarden}
            onScan={() => setView("scanner")}
            onClear={() => {
              saveGarden([]);
              setGarden([]);
            }}
          />
        )}

        {view === "result" &&
          (result ? (
            <Result entry={result} onBack={() => setView("scanner")} />
          ) : (
            <p className="empty">
              No hay ninguna ficha abierta. Vuelve al escáner para identificar
              una planta.
            </p>
          ))}
      </main>

      <button
        className="chat-fab"
        onClick={() => setChatOpen((open) => !open)}
        aria-expanded={chatOpen}
      >
        {chatOpen ? "Cerrar chat" : "💬 Preguntar"}
      </button>

      {chatOpen && (
        <ChatPanel plant={result?.plant} onClose={() => setChatOpen(false)} />
      )}
    </div>
  );
}
