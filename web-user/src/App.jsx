import { lazy, Suspense, useEffect, useRef, useState } from "react";
import Header from "./components/Header";
import { clearSession, loadSession, saveSession } from "./storage";
import { clearCache, getPreferences } from "./api";
import { navigate, useRoute } from "./route";
import { applyAppearance, readAppearance, saveAppearance } from "./appearance";

const Login = lazy(() => import("./pages/Login"));
const Chat = lazy(() => import("./pages/Chat"));
const Scanner = lazy(() => import("./pages/Scanner"));
const Plant = lazy(() => import("./pages/Plant"));
const Garden = lazy(() => import("./pages/Garden"));
const Settings = lazy(() => import("./pages/Settings"));

export default function App() {
  const [session, setSession] = useState(loadSession);
  const [appearance, setAppearance] = useState(readAppearance);
  const [expired, setExpired] = useState(false);
  const path = useRoute();
  const main = useRef(null);
  const destination = useRef(path === "/login" ? "/" : path);

  useEffect(() => {
    const onExpired = () => {
      destination.current = window.location.pathname + window.location.search;
      clearSession();
      clearCache();
      setSession(null);
      setExpired(true);
      navigate("/login", true);
    };
    window.addEventListener("agronomia:expired", onExpired);
    return () => window.removeEventListener("agronomia:expired", onExpired);
  }, []);

  useEffect(() => {
    if (!session) {
      if (path !== "/login") navigate("/login", true);
      return;
    }
    if (path === "/login") navigate(destination.current, true);
    window.scrollTo(0, 0);
    main.current?.focus({ preventScroll: true });
  }, [path, session]);

  useEffect(() => {
    if (!session) return;
    let active = true;
    getPreferences().then(({ preferences }) => {
      if (active && Object.keys(preferences).length) {
        setAppearance(saveAppearance(preferences));
      }
    }).catch(() => {});
    return () => { active = false; };
  }, [session]);

  useEffect(() => { applyAppearance(appearance); }, [appearance]);

  function login(user) {
    clearCache();
    saveSession(user);
    setSession(user);
    setExpired(false);
  }

  function logout() {
    destination.current = "/";
    clearCache();
    clearSession();
    setSession(null);
    navigate("/login", true);
  }

  const [pathname, search = ""] = path.split("?");
  const plantId = /^\/plants\/([a-f0-9-]{36})$/i.exec(pathname)?.[1];
  const wait = <p className="page-status" role="status">Cargando…</p>;
  if (!session) return <Suspense fallback={wait}>
    {expired && <p className="session-notice" role="status">Tu sesión terminó. Identifícate para continuar.</p>}
    <Login onLogin={login} />
  </Suspense>;

  return <div className={pathname === "/chat" ? "app app-chat" : "app"}>
    <a className="skip-link" href="#main-content">Ir al contenido</a>
    <Header username={session.username} path={pathname} onLogout={logout} />
    <main id="main-content" ref={main} tabIndex={-1} className={pathname === "/chat" ? "page page-chat" : "page"}>
      <Suspense fallback={wait}>
        {pathname === "/" || pathname === "/scanner" || pathname === "/login"
          ? <Scanner onResult={(plant) => navigate(plant.id ? "/plants/" + plant.id : "/garden")} />
          : pathname === "/garden" ? <Garden layout={appearance.garden} />
          : pathname === "/chat" ? <Chat initialPlant={new URLSearchParams(search).get("plant") || ""} />
          : pathname === "/settings" ? <Settings username={session.username} appearance={appearance} onAppearance={setAppearance} />
          : plantId ? <Plant key={plantId} id={plantId} />
          : <div className="card inset"><h2>Página no encontrada</h2><a href="/">Volver al escáner</a></div>}
      </Suspense>
    </main>
  </div>;
}
