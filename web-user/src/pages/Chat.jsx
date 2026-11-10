import { useEffect, useRef, useState } from "react";
import Markdown from "../components/Markdown";
import { getChatHistory, listIdentifiedPlants, sendChat } from "../api";

export default function Chat() {
  const [plants, setPlants] = useState([]);
  const [selectedId, setSelectedId] = useState("");
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState("");
  const [loadingPlants, setLoadingPlants] = useState(true);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const bodyRef = useRef(null);
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
    return () => {
      active = false;
    };
  }, []);

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
    if (!text || !selectedPlant || busy || loadingHistory) return;
    const plantId = selectedPlant.id;
    setDraft("");
    setError("");
    setMessages((previous) => [
      ...previous,
      { id: `local-${Date.now()}`, role: "user", content: text },
    ]);
    setBusy(true);
    try {
      const data = await sendChat(text, plantId);
      if (activePlantId.current === plantId) {
        setMessages((previous) => [
          ...previous,
          { id: `reply-${Date.now()}`, role: "assistant", content: data.reply },
        ]);
      }
    } catch (problem) {
      if (activePlantId.current === plantId) setError(problem.message);
    } finally {
      if (activePlantId.current === plantId) setBusy(false);
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
                onClick={() => setSelectedId(plant.id)}
              >
                <span className="chat-plant-icon">🌱</span>
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
          <div className="brand-mark">🌿</div>
          <div>
            <strong>{selectedPlant?.common_name || selectedPlant?.scientific_name || "Chat botánico"}</strong>
            <span>
              {selectedPlant
                ? `${selectedPlant.scientific_name} · ${Math.round(selectedPlant.confidence * 100)}% de similitud`
                : "Tus conversaciones se guardan en tu cuenta"}
            </span>
          </div>
        </header>

        <div className="chat-conversation-body" ref={bodyRef}>
          {!selectedPlant && !loadingPlants && (
            <div className="chat-welcome">
              <span>🌿</span>
              <h3>Tu asistente botánico</h3>
              <p>Identifica una planta para iniciar una conversación que podrás retomar cuando quieras.</p>
            </div>
          )}
          {selectedPlant && loadingHistory && <p className="muted">Recuperando conversación…</p>}
          {selectedPlant && !loadingHistory && messages.length === 0 && (
            <div className="chat-welcome">
              <span>🌿</span>
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
          {error && <div className="alert error">{error}</div>}
        </div>

        <form className="chat-page-form" onSubmit={submit}>
          <input
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder={selectedPlant ? "Escribe tu pregunta…" : "Primero identifica una planta"}
            disabled={!selectedPlant || busy || loadingHistory}
            aria-label="Mensaje para el asistente botánico"
          />
          <button className="btn" type="submit" disabled={!selectedPlant || busy || loadingHistory || !draft.trim()}>
            Enviar
          </button>
        </form>
      </div>
    </section>
  );
}
