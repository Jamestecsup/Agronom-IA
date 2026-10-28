import { useEffect, useState } from "react";
import { clearSession, loadSession } from "./api";
import Login from "./pages/Login";
import Shell from "./components/Shell";

/**
 * Raíz del panel: muestra el login o el panel según haya sesión guardada.
 */
export default function App() {
  const [session, setSession] = useState(() => loadSession());

  useEffect(() => {
    // El cliente del API avisa cuando el token caduca (401).
    const onUnauthorized = () => setSession(null);
    window.addEventListener("agronomia:unauthorized", onUnauthorized);
    return () =>
      window.removeEventListener("agronomia:unauthorized", onUnauthorized);
  }, []);

  function handleLogout() {
    clearSession();
    setSession(null);
  }

  if (!session) {
    return <Login onLogin={setSession} />;
  }
  return <Shell session={session} onLogout={handleLogout} />;
}
