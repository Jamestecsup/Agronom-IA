import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { fetchCandidateImage } from "../wikimedia";
import { highlightTerms } from "../utils/glossary";

/**
 * Tarjeta de una especie candidata: foto de referencia, nombres, familia,
 * rasgos para reconocerla, similitud y botón para elegirla.
 */
export default function CandidateCard({ candidate, busy, onChoose, onTerm }) {
  const [imageUrl, setImageUrl] = useState("");
  const [imageLoading, setImageLoading] = useState(true);
  const [imageOpen, setImageOpen] = useState(false);

  const commonNames = candidate.common_names || [];
  const displayName = commonNames[0] || candidate.scientific_name;
  const extraNames = commonNames.slice(1, 3);
  const confidence = Math.round((candidate.confidence || 0) * 100);
  const hint = (candidate.hint || "").trim();

  useEffect(() => {
    let active = true;
    setImageLoading(true);
    fetchCandidateImage(candidate.scientific_name).then((url) => {
      if (!active) return;
      setImageUrl(url);
      setImageLoading(false);
    });
    return () => {
      active = false;
    };
  }, [candidate.scientific_name]);

  useEffect(() => {
    if (!imageOpen) return undefined;
    function closeOnEscape(event) {
      if (event.key === "Escape") setImageOpen(false);
    }
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [imageOpen]);

  return (
    <article className="candidate">
      <div className="candidate-photo">
        {imageUrl ? (
          <button
            type="button"
            className="candidate-image-button"
            onClick={() => setImageOpen(true)}
            title="Ampliar foto de referencia"
          >
            <img
              src={imageUrl}
              alt={`Foto de referencia de ${displayName}`}
              loading="lazy"
              onError={() => setImageUrl("")}
            />
          </button>
        ) : (
          <div
            className={`candidate-photo-placeholder ${
              imageLoading ? "loading" : ""
            }`}
            aria-hidden="true"
          >
            {imageLoading ? "" : "🌿"}
          </div>
        )}
      </div>

      <div className="candidate-body">
        <div className="candidate-top">
          <div className="names">
            <strong>{displayName}</strong>
            {extraNames.length > 0 && (
              <span className="also">También: {extraNames.join(", ")}</span>
            )}
            <em>{candidate.scientific_name}</em>
            <div className="meta">
              Familia: {candidate.family || "sin dato"}
            </div>
          </div>
          <div className="similarity">{confidence}%</div>
        </div>

        {hint ? (
          <p className="candidate-hint">
            {highlightTerms(hint, candidate.terms || [], onTerm)}
          </p>
        ) : (
          <p className="candidate-hint muted">
            No se pudo generar la descripción de esta candidata.
          </p>
        )}

        <div
          className="candidate-progress"
          role="progressbar"
          aria-valuenow={confidence}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label={`Similitud con ${displayName}`}
        >
          <span style={{ width: `${Math.max(2, confidence)}%` }} />
        </div>

        <button
          className="btn small"
          onClick={() => onChoose(candidate)}
          disabled={busy}
        >
          Elegir esta planta
        </button>
      </div>

      {imageOpen && imageUrl && createPortal(
        <div
          className="candidate-image-dialog"
          role="dialog"
          aria-modal="true"
          aria-label={`Foto de referencia de ${displayName}`}
          onClick={() => setImageOpen(false)}
        >
          <button
            type="button"
            className="candidate-image-close"
            onClick={() => setImageOpen(false)}
            aria-label="Cerrar imagen"
          >
            ×
          </button>
          <img
            src={imageUrl}
            alt={`Foto de referencia ampliada de ${displayName}`}
            onClick={(event) => event.stopPropagation()}
          />
        </div>,
        document.body
      )}
    </article>
  );
}
