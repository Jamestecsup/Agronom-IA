import { useEffect, useRef, useState } from "react";
import { confirmCandidate, identify, uploadImages } from "../api";

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
  const [candidates, setCandidates] = useState([]);
  const [batchId, setBatchId] = useState("");

  const inputRef = useRef(null);
  const filesRef = useRef(files);
  const busy = phase !== "idle";

  useEffect(() => {
    filesRef.current = files;
  }, [files]);

  useEffect(() => {
    // Libera las miniaturas locales al salir de la pantalla.
    return () => filesRef.current.forEach((item) => URL.revokeObjectURL(item.url));
  }, []);

  function addFiles(list) {
    const selected = Array.from(list || []);
    if (selected.length === 0) return;
    setError("");
    setMessage("");
    setCandidates([]);

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
    files.forEach((item) => URL.revokeObjectURL(item.url));
    setFiles([]);
    setOrgan("auto");
    setError("");
    setMessage("");
    setCandidates([]);
    setBatchId("");
  }

  async function handleIdentify() {
    if (files.length === 0 || busy) return;
    setError("");
    setMessage("");
    setCandidates([]);

    try {
      setPhase("uploading");
      const upload = await uploadImages(files.map((item) => item.file));
      if (!upload || !upload.batch_id) {
        throw new Error("El servidor no devolvió el lote de fotos.");
      }
      setBatchId(upload.batch_id);

      setPhase("analyzing");
      const data = await identify(upload.batch_id, organ);

      if (data.status === "identified" && data.plant) {
        onResult(data.plant, files[0].file);
      } else if (data.status === "ambiguous") {
        setCandidates(data.candidates || []);
      } else if (data.status === "unidentified") {
        setMessage(data.message || "No se encontraron coincidencias.");
      } else {
        setError(data.error || "No se pudo identificar la planta.");
      }
    } catch (problem) {
      setError(problem.message);
    } finally {
      setPhase("idle");
    }
  }

  async function choose(candidate) {
    setError("");
    setPhase("confirming");
    try {
      const data = await confirmCandidate(batchId, candidate.scientific_name);
      if (!data.plant) {
        throw new Error("No se pudo generar la ficha de la planta elegida.");
      }
      onResult(data.plant, files[0].file);
    } catch (problem) {
      setError(problem.message);
    } finally {
      setPhase("idle");
    }
  }

  const phaseLabel =
    phase === "uploading"
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
              inputRef.current?.click();
            }
          }}
        >
          <div className="icon">📷</div>
          <strong>Arrastra tus fotos o haz clic para elegirlas</strong>
          <span>JPG o PNG · hasta {MAX_FILES} fotos · 8 MB por foto</span>
        </div>

        <input
          ref={inputRef}
          type="file"
          accept="image/*"
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
            {busy ? phaseLabel : "Identificar"}
          </button>
          {files.length > 0 && (
            <button className="btn ghost" onClick={reset} disabled={busy}>
              Limpiar
            </button>
          )}
          <span className="hint">
            {files.length} de {MAX_FILES} fotos
          </span>
        </div>

        {error && (
          <div className="alert error" style={{ marginTop: 16, marginBottom: 0 }}>
            {error}
          </div>
        )}

        {message && (
          <div className="alert ok" style={{ marginTop: 16 }}>
            <strong>No se pudo identificar.</strong> {message}
            <ul className="steps">
              <li>Acércate a la flor o a la hoja y evita el contraluz.</li>
              <li>Usa luz natural y espera a que la cámara enfoque.</li>
              <li>Prueba con 2 o 3 órganos distintos (flor, hoja, fruto).</li>
            </ul>
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
                <div className="candidate" key={candidate.scientific_name}>
                  <div className="names">
                    <strong>
                      {(candidate.common_names || []).slice(0, 3).join(", ") ||
                        candidate.scientific_name}
                    </strong>
                    <em>{candidate.scientific_name}</em>
                    <div className="meta">
                      Familia: {candidate.family || "sin dato"}
                    </div>
                  </div>
                  <div className="similarity">
                    {Math.round((candidate.confidence || 0) * 100)}%
                  </div>
                  <button
                    className="btn small"
                    onClick={() => choose(candidate)}
                    disabled={busy}
                  >
                    Elegir esta planta
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </>
  );
}
