import { useEffect, useState } from "react";
import { OPTIONS, DEFAULT_APPEARANCE, saveAppearance } from "../appearance";
import { deleteAllChats, deleteChat, listIdentifiedPlants, listPresets, listProviders,
  probeProvider, removeProvider, savePreferences, saveProvider, selectProvider } from "../api";
import ConfirmDialog from "../components/ConfirmDialog";

const labels = {
  mode: "Tema", accent: "Acento", font: "Tipografía", scale: "Tamaño de texto",
  density: "Densidad", garden: "Diseño del jardín", motion: "Movimiento",
};

export default function Settings({ username, appearance, onAppearance }) {
  const [data, setData] = useState({ providers: [], active_id: null });
  const [presets, setPresets] = useState([]);
  const [plants, setPlants] = useState([]);
  const [edit, setEdit] = useState(null);
  const [preset, setPreset] = useState("");
  const [confirm, setConfirm] = useState(null);
  const [plant, setPlant] = useState("");
  const [busy, setBusy] = useState(false);
  const [ready, setReady] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    Promise.all([listProviders(), listPresets(), listIdentifiedPlants()]).then(([profiles, choices, garden]) => {
      if (!active) return;
      setData(profiles);
      setPresets(choices.presets);
      setPlants(garden.plants);
      setReady(true);
    }).catch((reason) => { if (active) setError(reason.message); });
    return () => { active = false; };
  }, []);

  function appearanceChange(values) { onAppearance(saveAppearance(values)); }

  async function perform(action, message) {
    if (busy) return;
    setBusy(true);
    setNotice("");
    setError("");
    try {
      await action();
      setConfirm(null);
      setNotice(message);
    } catch (problem) {
      setConfirm(null);
      setError(problem.message);
    } finally { setBusy(false); }
  }

  async function providerAction(action) {
    await action();
    setData(await listProviders());
  }

  function submitProvider(event) {
    event.preventDefault();
    if (busy) return;
    const form = event.currentTarget;
    const value = Object.fromEntries(new FormData(form));
    form.elements.api_key.value = "";
    if (!value.api_key) delete value.api_key;
    for (const key of ["context_window", "max_output_tokens", "max_concurrency"]) {
      if (value[key]) value[key] = Number(value[key]);
      else delete value[key];
    }
    perform(async () => {
      await providerAction(() => {
        const sent = saveProvider(value, edit?.id);
        delete value.api_key;
        return sent;
      });
      setEdit(null);
      setPreset("");
      form.reset();
    }, "Proveedor guardado. Prueba la conexión antes de activarlo.");
  }

  const defaults = edit || presets[preset] || {};
  return <section aria-labelledby="settings-heading">
    <div className="section-heading"><div><h1 id="settings-heading">Ajustes</h1><p>Personaliza tu espacio y controla tus datos.</p></div></div>
    {error && <p className="alert error" role="alert">{error}</p>}
    {notice && <p className="alert ok" role="status">{notice}</p>}
    <div className="settings-grid">
      <section className="card inset" aria-labelledby="appearance-heading">
        <h2 id="appearance-heading">Apariencia</h2>
        <form onSubmit={(event) => { event.preventDefault(); perform(() => savePreferences(appearance), "Apariencia sincronizada."); }}>
          <div className="field-grid">
            {Object.entries(OPTIONS).map(([key, options]) => <label key={key}>{labels[key]}
              <select value={appearance[key]} onChange={(event) => appearanceChange({ ...appearance, [key]: event.target.value })}>
                {Object.entries(options).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select></label>)}
          </div>
          <div className="theme-preview"><strong>Vista previa</strong><p>Revisa el contraste y la lectura antes de guardar.</p></div>
          <div className="actions">
            <button className="btn" disabled={busy}>Guardar apariencia</button>
            <button className="btn ghost" type="button" onClick={() => appearanceChange(DEFAULT_APPEARANCE)}>Restablecer</button>
          </div>
        </form>
      </section>
      <section className="card inset" aria-labelledby="provider-heading">
        <h2 id="provider-heading">Modelos de IA</h2>
        {ready && <button className="btn ghost" disabled={busy || data.active_id == null}
          onClick={() => perform(() => providerAction(() => selectProvider(null, false)), "Modelo del sistema activo.")}>
          {data.active_id == null ? "Modelo del sistema activo" : "Usar modelo del sistema"}
        </button>}
        {!ready && !error && <p role="status">Cargando proveedores…</p>}
        <ul className="provider-list">{data.providers.map((provider) => <li key={provider.id}>
          <strong>{provider.name}{data.active_id === provider.id ? " · Activo" : ""}</strong>
          <span>{provider.model} · clave terminada en {provider.key_suffix}</span>
          <span>{provider.status === "activo" ? `Prueba: ${provider.latency_ms} ms · visión ${provider.supports_vision ? "sí" : "no"} · JSON ${provider.supports_json_mode ? "sí" : "no"}`
            : provider.status === "fallido" ? "La conexión falló" : "Pendiente de prueba"}</span>
          <div className="actions">
            <button className="btn ghost" disabled={busy} onClick={() => perform(() => providerAction(() => probeProvider(provider.id)), "Prueba terminada.")}>Probar conexión</button>
            <button className="btn ghost" disabled={busy || provider.status !== "activo" || data.active_id === provider.id}
              onClick={() => setConfirm({ kind: "activate", provider })}>Activar</button>
            <button className="btn ghost" disabled={busy} onClick={() => setEdit(provider)}>Editar</button>
            <button className="btn ghost" disabled={busy} onClick={() => setConfirm({ kind: "remove", provider })}>Eliminar</button>
          </div>
        </li>)}</ul>
        <h3>{edit ? "Editar proveedor" : "Añadir proveedor"}</h3>
        {!edit && <label>Rellenar con un modelo conocido
          <select onChange={(event) => setPreset(event.target.value)} value={preset}>
            <option value="">Configuración propia</option>
            {presets.map((item, i) => <option key={item.model} value={i}>{item.name}</option>)}
          </select>
        </label>}
        <form key={edit?.id || "new-" + preset} onSubmit={submitProvider}>
          <div className="field-grid">
            <label>Nombre<input name="name" required maxLength={80} defaultValue={defaults.name} /></label>
            <label>URL base<input name="base_url" required type="url" maxLength={500} defaultValue={defaults.base_url} /></label>
            <label>Identificador del modelo<input name="model" required maxLength={160} defaultValue={defaults.model} /></label>
            <label>{edit ? "Nueva clave (opcional)" : "Clave del proveedor"}<input name="api_key" type="password"
              required={!edit} minLength={8} maxLength={1024} autoComplete="new-password" /></label>
          </div>
          <details><summary>Límites de contexto y concurrencia</summary><div className="field-grid">
            <label>Ventana de contexto<input type="number" name="context_window" min="1024" max="262144" defaultValue={defaults.context_window} /></label>
            <label>Tokens de salida<input type="number" name="max_output_tokens" min="128" max="8192" defaultValue={defaults.max_output_tokens} /></label>
            <label>Llamadas simultáneas<input type="number" name="max_concurrency" min="1" max="4" defaultValue={defaults.max_concurrency} /></label>
          </div></details>
          <div className="actions">
            <button className="btn" disabled={busy}>{edit ? "Guardar cambios" : "Añadir proveedor"}</button>
            {edit && <button type="button" className="btn ghost" onClick={() => setEdit(null)}>Cancelar edición</button>}
          </div>
        </form>
      </section>
      <section className="card inset" aria-labelledby="privacy-heading">
        <h2 id="privacy-heading">Datos y privacidad</h2>
        <p>Elimina mensajes y fotos de progreso de tus conversaciones.</p>
        <label>Planta<select value={plant} onChange={(event) => setPlant(event.target.value)}><option value="">Elige una planta</option>
          {plants.map((item) => <option key={item.id} value={item.id}>{item.common_name || item.scientific_name}</option>)}
        </select></label>
        <div className="actions">
          <button className="btn ghost" disabled={!plant || busy} onClick={() => setConfirm({ kind: "plant" })}>Eliminar chat de la planta</button>
          <button className="btn ghost" disabled={busy} onClick={() => setConfirm({ kind: "all" })}>Eliminar todos los chats</button>
        </div>
      </section>
      <section className="card inset" aria-labelledby="account-heading">
        <h2 id="account-heading">Cuenta</h2><p>Sesión de <strong>{username}</strong>.</p>
      </section>
    </div>
    {confirm && <ConfirmDialog busy={busy}
      title={confirm.kind === "activate" ? "Activar proveedor" : "Confirmar eliminación"}
      text={confirm.kind === "activate"
        ? `Aceptas que ${confirm.provider.name} (${confirm.provider.base_url}) reciba los datos necesarios para procesar fichas, conversaciones, fotos y horarios según el uso.`
        : confirm.kind === "remove" ? "Se eliminarán el perfil y su credencial." : "Se eliminarán los mensajes y las fotos de progreso de forma permanente."}
      confirmLabel={confirm.kind === "activate" ? "Acepto y activo" : "Eliminar"}
      onClose={() => setConfirm(null)} onConfirm={() => perform(async () => {
        if (confirm.kind === "activate") await providerAction(() => selectProvider(confirm.provider.id, true));
        else if (confirm.kind === "remove") await providerAction(() => removeProvider(confirm.provider.id));
        else if (confirm.kind === "plant") await deleteChat(plant);
        else await deleteAllChats();
      }, confirm.kind === "activate" ? "Proveedor activo." : "Eliminación completada.")} />}
  </section>;
}
