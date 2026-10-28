import { useEffect, useRef, useState } from "react";
import Markdown from "./Markdown";
import { sendChat } from "../api";
import { chatSessionId } from "../storage";

/**
 * Panel de chat flotante. Manda el contexto de la planta detectada para que
 * las respuestas sean sobre esa especie y no genéricas.
 */
export default function ChatPanel({ plant, onClose }) {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const bodyRef = useRef(null);

  const plantName = plant ? plant.common_name || plant.scientific_name : "";

  useEffect(() => {
    // Al llegar un mensaje nuevo se baja para leer la respuesta.
    if (bodyRef.current) {
      bodyRef.current.scrollTop = bodyRef.current.scrollHeight;
    }
  }, [messages, busy]);

  const suggestions = plant
    ? ["¿Cómo la riego?", "¿Qué luz necesita?", "¿Cómo la abono?"]
    : ["¿Qué puedo sembrar en maceta?", "¿Cómo cuido un tomate?"];

  async function send(text) {
    const message = (text ?? input).trim();
    if (!message || busy) return;

    setError("");
    setInput("");
    setMessages((previous) => [...previous, { role: "user", content: message }]);
    setBusy(true);
    try {
      const data = await sendChat(message, contextOf(plant), chatSessionId());
      setMessages((previous) => [
        ...previous,
        { role: "assistant", content: data.reply },
      ]);
    } catch (problem) {
      setError(problem.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="chat-panel" aria-label="Chat botánico">
      <header className="chat-head">
        <div className="brand-mark" style={{ width: 34, height: 34, fontSize: 17 }}>
          🌿
        </div>
        <div>
          <strong>Asistente botánico</strong>
          <span>{plantName || "Sin planta detectada"}</span>
        </div>
        <button className="close" onClick={onClose} aria-label="Cerrar chat">
          ✕
        </button>
      </header>

      <div className="chat-body" ref={bodyRef}>
        {messages.length === 0 && (
          <div className="bubble assistant">
            <Markdown
              text={
                plant
                  ? `Hola, puedo ayudarte con tu **${plantName}**. Pregúntame por riego, luz, suelo, plagas o poda.`
                  : "Hola, soy tu asistente botánico. Escanea una planta para darte consejos sobre ella o pregúntame en general."
              }
            />
          </div>
        )}

        {messages.map((message, index) => (
          <div key={index} className={`bubble ${message.role}`}>
            {message.role === "assistant" ? (
              <Markdown text={message.content} />
            ) : (
              message.content
            )}
          </div>
        ))}

        {busy && (
          <div className="bubble assistant">
            <span className="muted">Escribiendo…</span>
          </div>
        )}

        {error && <div className="alert error">{error}</div>}
      </div>

      {messages.length === 0 && (
        <div className="suggestions">
          {suggestions.map((suggestion) => (
            <button key={suggestion} onClick={() => send(suggestion)}>
              {suggestion}
            </button>
          ))}
        </div>
      )}

      <form
        className="chat-form"
        onSubmit={(event) => {
          event.preventDefault();
          send();
        }}
      >
        <input
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder="Escribe tu pregunta…"
          aria-label="Mensaje"
        />
        <button type="submit" disabled={busy || !input.trim()} aria-label="Enviar">
          ➤
        </button>
      </form>
    </section>
  );
}

function contextOf(plant) {
  if (!plant) return "";
  return [
    `Nombre científico: ${plant.scientific_name || ""}`,
    `Nombre común: ${plant.common_name || ""}`,
    `Familia: ${plant.family || ""}`,
    `Similitud: ${Math.round((plant.confidence || 0) * 100)}%`,
  ].join("\n");
}
