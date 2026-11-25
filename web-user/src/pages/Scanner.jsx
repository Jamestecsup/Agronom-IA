import { useEffect, useRef, useState } from "react";
import { confirmCandidate, identify, uploadImages } from "../api";
import CandidateCard from "../components/CandidateCard";
import TermDialog from "../components/TermDialog";
import { preparePhoto } from "../image";

const MAX_FILES = 5;
const MAX_BYTES = 8 * 1024 * 1024;

const ORGANS = [
  { value: "auto", label: "Automático" },
  { value: "flower", label: "Flor" },
  { value: "leaf", label: "Hoja" },
  { value: "fruit", label: "Fruto" },
  { value: "bark", label: "Corteza" },
  { value: "habit", label: "Planta entera" },
];

/**
 * Escáner: sube 1-5 fotos, indica el órgano y pide la identificación a Django.
 * Si hay dudas muestra las candidatas para que el agricultor elija.
 */
export default function Scanner({ onResult }) {
  const [files, setFiles] = useState([]);
  const [organ, setOrgan] = useState("auto");
  const [phase, setPhase] = useState("idle");
  const [drag, setDrag] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [messageTitle, setMessageTitle] = useState("No se pudo identificar.");
  const [candidates, setCandidates] = useState([]);
  const [batchId, setBatchId] = useState("");
  const [selectedTerm, setSelectedTerm] = useState(null);
  const [elapsed, setElapsed] = useState(0);

  const inputRef = useRef(null);
  const filesRef = useRef(files);
  const operation = useRef(null);
  const lastBatch = useRef(null);
  const busy = phase !== "idle";

  useEffect(() => {
    filesRef.current = files;
  }, [files]);

  useEffect(() => {
    // Libera las miniaturas locales al salir de la pantalla.
    return () => {
      operation.current?.abort();
      filesRef.current.forEach((item) => URL.revokeObjectURL(item.url));
    };
  }, []);

  useEffect(() => {
    if (!busy) return;
    const started = Date.now();
    setElapsed(0);
    const tick = setInterval(() => setElapsed(Math.floor((Date.now() - started) / 1000)), 500);
    return () => clearInterval(tick);
  }, [busy]);

  function addFiles(list) {
    if (operation.current) return;
    const selected = Array.from(list || []);
    if (selected.length === 0) return;
    setError("");
    setMessage("");
    setMessageTitle("No se pudo identificar.");
    setCandidates([]);
    setSelectedTerm(null);

    const unsupported = selected.find((file) => {
      const supportedMime = ["image/jpeg", "image/png", "image/webp"].includes(file.type);
      const supportedExtension = /\.(jpe?g|png|webp)$/i.test(file.name);
      return !supportedMime && !supportedExtension;
    });
    if (unsupported) {
      setError(`"${unsupported.name}" no es compatible. Elige JPG, PNG o WebP.`);
      return;
    }

    const tooBig = selected.find((file) => file.size > MAX_BYTES);
    if (tooBig) {
      setError(`"${tooBig.name}" pesa más de 8 MB. Usa una foto más ligera.`);
      return;
    }

    setFiles((previous) => {
      const room = MAX_FILES - previous.length;
      if (room <= 0) {
        setError(`Máximo ${MAX_FILES} fotos por identificación.`);
        return previous;
      }
      const added = selected.slice(0, room).map((file) => ({
        file,
        url: URL.createObjectURL(file),
      }));
      if (selected.length > room) {
        setError(`Solo se añadieron ${room} fotos: el máximo es ${MAX_FILES}.`);
      }
      return [...previous, ...added];
    });
  }

  function removeFile(index) {
    setFiles((previous) => {
      const item = previous[index];
      if (item) URL.revokeObjectURL(item.url);
      return previous.filter((_, position) => position !== index);
    });
  }

  function reset() {
    lastBatch.current = null;
    files.forEach((item) => URL.revokeObjectURL(item.url));
    setFiles([]);
    setOrgan("auto");
    setError("");
    setMessage("");
    setMessageTitle("No se pudo identificar.");
    setCandidates([]);
    setSelectedTerm(null);
    setBatchId("");
  }

  async function handleIdentify() {
    if (files.length === 0 || operation.current) return;
    const controller = new AbortController();
    operation.current = controller;
    setError("");
    setMessage("");
    setMessageTitle("No se pudo identificar.");
    setCandidates([]);
    setSelectedTerm(null);

    try {
      let upload = lastBatch.current?.files === files ? lastBatch.current.upload : null;
      if (!upload) {
        setPhase("preparing");
        const prepared = [];
        for (const item of files) {
          prepared.push(await preparePhoto(item.file));
          controller.signal.throwIfAborted();
        }
        setPhase("uploading");
        upload = await uploadImages(prepared, controller.signal);
        lastBatch.current = { files, upload };
      }
      if (!upload || !upload.batch_id) {
        throw new Error("No pudimos recibir las fotos. Inténtalo de nuevo.");
      }
      setBatchId(upload.batch_id);

      setPhase("analyzing");
      const data = await identify(upload.batch_id, organ, controller.signal);

      if (data.status === "identified" && data.plant) {
        onResult(data.plant, files[0].file);
      } else if (data.status === "ambiguous") {
        setCandidates(data.candidates || []);
      } else if (data.status === "unidentified") {
        setMessageTitle("No se pudo identificar.");
        setMessage(data.message || "No se encontraron coincidencias.");
      } else if (data.status === "insufficient") {
        setMessageTitle("Información insuficiente.");
        setMessage(
          data.message ||
            "Identificamos la planta, pero no encontramos información suficiente sobre sus cuidados."
        );
      } else {
        setError("No pudimos analizar la planta. Revisa la foto e inténtalo de nuevo.");
      }
    } catch (problem) {
      if (problem.name !== "AbortError") setError(problem.message);
    } finally {
      operation.current = null;
      setPhase("idle");
    }
  }

  async function choose(candidate) {
    if (operation.current) return;
    const controller = new AbortController();
    operation.current = controller;
    setError("");
    setMessage("");
    setPhase("confirming");
    try {
      const data = await confirmCandidate(batchId, candidate.scientific_name, controller.signal);
      if (data.status === "insufficient") {
        setMessageTitle("Información insuficiente.");
        setMessage(
          data.message ||
            "Identificamos la planta, pero no encontramos información suficiente sobre sus cuidados."
        );
        return;
      }
      if (!data.plant) {
        throw new Error("No se pudo generar la ficha de la planta elegida.");
      }
      onResult(data.plant, files[0].file);
    } catch (problem) {
      if (problem.name !== "AbortError") setError(problem.message);
    } finally {
      operation.current = null;
      setPhase("idle");
    }
  }

  const phaseLabel =
    phase === "preparing" ? "Preparando fotos…"
      : phase === "uploading"
      ? "Subiendo fotos…"
      : phase === "analyzing"
      ? "Analizando la planta…"
      : phase === "confirming"
      ? "Preparando la ficha…"
      : "";

  return (
    <>
      <section className="hero">
        <h2>Identifica tu planta</h2>
        <p>
          Sube hasta {MAX_FILES} fotos de la flor, la hoja o la planta entera.
          Cuanta mejor luz y detalle, mejor será la ficha.
        </p>
      </section>

      <div className="card" style={{ padding: 22 }}>
        <div
          className={`dropzone ${drag ? "drag" : ""}`}
          onClick={() => inputRef.current?.click()}
          onDragOver={(event) => {
            event.preventDefault();
            setDrag(true);
          }}
          onDragLeave={() => setDrag(false)}
          onDrop={(event) => {
            event.preventDefault();
            setDrag(false);
            addFiles(event.dataTransfer.files);
          }}
          role="button"
          tabIndex={0}
          onKeyDown={(event) => {
            if (event.key === "Enter" || event.key === " ") {
              event.preventDefault();
              inputRef.current?.click();
            }
          }}
        >
          <div className="icon">📷</div>
          <strong>Arrastra tus fotos o haz clic para elegirlas</strong>
          <span>JPG, PNG o WebP · hasta {MAX_FILES} fotos · 8 MB por foto</span>
        </div>

        <input
          ref={inputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp"
          multiple
          hidden
          onChange={(event) => {
            addFiles(event.target.files);
            event.target.value = "";
          }}
        />

        {files.length > 0 && (
          <div className="previews">
            {files.map((item, index) => (
              <div className="preview" key={item.url}>
                <img src={item.url} alt={`Foto ${index + 1}`} />
                <button
                  className="remove"
                  onClick={() => removeFile(index)}
                  disabled={busy}
                  aria-label={`Quitar foto ${index + 1}`}
                >
                  ✕
                </button>
              </div>
            ))}
          </div>
        )}

        <p className="hint">
          ¿Qué se ve en la foto? Elegir el órgano mejora la identificación.
        </p>
        <div className="organ-row">
          {ORGANS.map((option) => (
            <button
              key={option.value}
              className={`chip ${organ === option.value ? "active" : ""}`}
              onClick={() => setOrgan(option.value)}
              disabled={busy}
            >
              {option.label}
            </button>
          ))}
        </div>

        <div className="actions">
          <button
            className="btn"
            onClick={handleIdentify}
            disabled={files.length === 0 || busy}
          >
            {busy && <span className="spinner" />}
            {busy ? phaseLabel : error ? "Reintentar identificación" : "Identificar"}
          </button>
          {busy && <button type="button" className="btn ghost" onClick={() => {
            operation.current?.abort();
            setMessageTitle("Espera cancelada.");
            setMessage("Si el servidor ya estaba procesando la planta, la ficha puede aparecer en Mi jardín.");
          }}>Cancelar espera</button>}
          {files.length > 0 && (
            <button className="btn ghost" onClick={reset} disabled={busy}>
              Limpiar
            </button>
          )}
          <span className="hint">
            {files.length} de {MAX_FILES} fotos
          </span>
        </div>
        {busy && <p className="scan-progress"><span role="status">{phaseLabel}</span> <span>{elapsed} s</span></p>}

        {error && (
          <div className="alert error" role="alert" style={{ marginTop: 16, marginBottom: 0 }}>
            {error}
          </div>
        )}

        {message && (
          <div
            className={`alert ${
              messageTitle === "Información insuficiente." ? "warn" : "ok"
            }`}
            style={{ marginTop: 16 }}
          >
            <strong>{messageTitle}</strong> {message}
            {messageTitle !== "Información insuficiente." && (
              <ul className="steps">
                <li>Acércate a la flor o a la hoja y evita el contraluz.</li>
                <li>Usa luz natural y espera a que la cámara enfoque.</li>
                <li>Prueba con 2 o 3 órganos distintos (flor, hoja, fruto).</li>
              </ul>
            )}
          </div>
        )}

        {candidates.length > 0 && (
          <div style={{ marginTop: 20 }}>
            <h3 style={{ margin: "0 0 4px" }}>¿Cuál es tu planta?</h3>
            <p className="hint" style={{ marginTop: 0 }}>
              La app encontró varias especies parecidas. Elige la que coincida
              con la tuya:
            </p>
            <div className="candidates">
              {candidates.map((candidate) => (
                <CandidateCard
                  key={candidate.scientific_name}
                  candidate={candidate}
                  busy={busy}
                  onChoose={choose}
                  onTerm={setSelectedTerm}
                />
              ))}
            </div>
          </div>
        )}
      </div>
      <TermDialog term={selectedTerm} onClose={() => setSelectedTerm(null)} />
    </>
  );
}
