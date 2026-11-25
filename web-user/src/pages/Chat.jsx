import { useEffect, useRef, useState } from "react";
import Markdown from "../components/Markdown";
import PlantPhoto from "../components/PlantPhoto";
import { deleteChat, getCapabilities, getChatHistory, listIdentifiedPlants, sendChat } from "../api";
import Cover from "../components/Cover";
import ConfirmDialog from "../components/ConfirmDialog";
import { preparePhoto } from "../image";
import { navigate } from "../route";

export default function Chat({ initialPlant = "" }) {
  const [plants, setPlants] = useState([]);
  const [selectedId, setSelectedId] = useState(initialPlant);
  const [photo, setPhoto] = useState(null);
  const [vision, setVision] = useState(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState("");
  const [loadingPlants, setLoadingPlants] = useState(true);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const bodyRef = useRef(null);
  const submitting = useRef(false);
  const activePlantId = useRef(selectedId);
  activePlantId.current = selectedId;
  const selectedPlant = plants.find((plant) => plant.id === selectedId);

  useEffect(() => {
    let active = true;
    listIdentifiedPlants()
      .then((data) => {
        if (!active) return;
        const items = data.plants || [];
        setPlants(items);
        setSelectedId((current) =>
          items.some((plant) => plant.id === current) ? current : items[0]?.id || "",
        );
      })
      .catch((problem) => active && setError(problem.message))
      .finally(() => active && setLoadingPlants(false));
    getCapabilities().then((data) => {
      if (active) setVision(data.features.chat_photo);
    }).catch(() => { if (active) setVision({ available: false, reason: "No se pudo verificar la compatibilidad con fotos." }); });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (initialPlant && plants.some((plant) => plant.id === initialPlant)) setSelectedId(initialPlant);
  }, [initialPlant, plants]);

  useEffect(() => {
    if (!selectedId) {
      setMessages([]);
      setLoadingHistory(false);
      return undefined;
    }
    let active = true;
    setMessages([]);
    setError("");
    setBusy(false);
    setLoadingHistory(true);
    getChatHistory(selectedId)
      .then((data) => active && setMessages(data.messages || []))
      .catch((problem) => active && setError(problem.message))
      .finally(() => active && setLoadingHistory(false));
    return () => {
      active = false;
    };
  }, [selectedId]);

  useEffect(() => {
    if (bodyRef.current) bodyRef.current.scrollTop = bodyRef.current.scrollHeight;
  }, [messages, busy]);

  async function submit(event) {
    event.preventDefault();
    const text = draft.trim();
    if ((!text && !photo) || !selectedPlant || submitting.current || busy || deleting || loadingHistory) return;
    submitting.current = true;
    const plantId = selectedPlant.id;
    setDraft("");
    setError("");
    setBusy(true);
    try {
      const image = photo && vision?.available ? await preparePhoto(photo) : null;
      const data = await sendChat(text, plantId, image);
      if (activePlantId.current === plantId) {
        setPhoto(null);
        setMessages((previous) => [
          ...previous,
          { id: `local-${Date.now()}`, role: "user", content: text || "Foto de progreso" },
          { id: `reply-${Date.now()}`, role: "assistant", content: data.reply },
        ]);
      }
    } catch (problem) {
      if (activePlantId.current === plantId) { setError(problem.message); setDraft(text); }
    } finally {
      submitting.current = false;
      if (activePlantId.current === plantId) setBusy(false);
    }
  }

  async function removeConversation() {
    setDeleting(true);
    setError("");
    try {
      await deleteChat(selectedId);
      setMessages([]);
      setConfirmDelete(false);
    } catch (problem) {
      setConfirmDelete(false);
      setError(problem.message);
    } finally {
      setDeleting(false);
    }
  }

  return (
    <section className="chat-page">
      <aside className="chat-plant-list">
        <div className="chat-page-heading">
          <div>
            <h2>Tus plantas</h2>
            <p>Elige una para continuar su conversación.</p>
          </div>
        </div>
        {loadingPlants ? (
          <p className="muted">Cargando tus identificaciones…</p>
        ) : plants.length === 0 ? (
          <p className="chat-empty-list">Todavía no tienes plantas identificadas.</p>
        ) : (
          <div className="chat-plant-options">
            {plants.map((plant) => (
              <button
                key={plant.id}
                className={`chat-plant-option ${selectedId === plant.id ? "active" : ""}`}
                disabled={busy || deleting}
                aria-pressed={selectedId === plant.id}
                onClick={() => {
                  setSelectedId(plant.id); setPhoto(null);
                  navigate("/chat?plant=" + encodeURIComponent(plant.id));
                }}
              >
                <span className="chat-plant-copy">
                  <strong>{plant.common_name || plant.scientific_name}</strong>
                  <em>{plant.scientific_name}</em>
                  <small>{new Date(plant.created_at).toLocaleDateString("es-PE")}</small>
                </span>
              </button>
            ))}
          </div>
        )}
      </aside>

      <div className="chat-conversation">
        <header className="chat-conversation-head">
          {selectedPlant && <Cover id={selectedPlant.id} alt={selectedPlant.common_name || selectedPlant.scientific_name} />}
          <div>
            <span className="chat-head-eyebrow">CONVERSACIÓN DE CUIDADO</span>
            <strong>{selectedPlant?.common_name || selectedPlant?.scientific_name || "Chat botánico"}</strong>
            <span>
              {selectedPlant
                ? `${selectedPlant.scientific_name} · ${Math.round(selectedPlant.confidence * 100)}% de similitud`
                : "Tus conversaciones se guardan en tu cuenta"}
            </span>
          </div>
          {selectedPlant && <details className="chat-menu"><summary>Opciones del chat</summary>
            <button className="btn ghost" disabled={busy || loadingHistory || deleting} onClick={() => setConfirmDelete(true)}>
              Eliminar conversación
            </button>
          </details>}
        </header>

        <div className="chat-conversation-body" ref={bodyRef}>
          {!selectedPlant && !loadingPlants && (
            <div className="chat-welcome">
              <PlantPhoto className="chat-welcome-photo" alt="Ilustración botánica" />
              <h3>Tu asistente botánico</h3>
              <p>Identifica una planta para iniciar una conversación que podrás retomar cuando quieras.</p>
            </div>
          )}
          {selectedPlant && loadingHistory && <p className="muted">Recuperando conversación…</p>}
          {selectedPlant && !loadingHistory && messages.length === 0 && (
            <div className="chat-welcome">
              <PlantPhoto
                className="chat-welcome-photo"
                alt={`Foto de ${selectedPlant.common_name || selectedPlant.scientific_name}`}
              />
              <h3>Conversemos sobre {selectedPlant.common_name || selectedPlant.scientific_name}</h3>
              <p>Pregúntame sobre sus cuidados, riego, luz, suelo o posibles plagas.</p>
            </div>
          )}
          {messages.map((message) => (
            <div key={message.id} className={`chat-message ${message.role}`}>
              {message.role === "assistant" ? <Markdown text={message.content} /> : message.content}
            </div>
          ))}
          {busy && <div className="chat-message assistant muted">Escribiendo…</div>}
          {error && <div className="alert error" role="alert">{error}</div>}
        </div>

        <form className="chat-page-form" onSubmit={submit}>
          <input
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder={selectedPlant ? "Escribe tu pregunta…" : "Primero identifica una planta"}
            disabled={!selectedPlant || busy || loadingHistory}
            aria-label="Mensaje para el asistente botánico"
            maxLength={4000}
          />
          <label className="chat-photo-control">Adjuntar foto
            <input type="file" accept="image/jpeg,image/png,image/webp" disabled={!selectedPlant || busy || !vision?.available}
              aria-describedby="chat-photo-reason" onChange={(event) => {
                const file = event.target.files[0];
                setPhoto(file && file.size <= 8 * 1024 * 1024 ? file : null);
                if (file && file.size > 8 * 1024 * 1024) setError("La foto supera 8 MB.");
                event.target.value = "";
              }} />
            <small id="chat-photo-reason">{vision?.reason || photo?.name || ""}</small>
          </label>
          <button className="btn" type="submit" disabled={!selectedPlant || busy || loadingHistory || (!draft.trim() && !photo)}>
            Enviar
          </button>
        </form>
      </div>
      {confirmDelete && <ConfirmDialog title="Eliminar conversación" text="Se eliminarán los mensajes y las fotos de progreso de esta planta."
        confirmLabel="Eliminar" busy={deleting} onConfirm={removeConversation} onClose={() => setConfirmDelete(false)} />}
    </section>
  );
}
