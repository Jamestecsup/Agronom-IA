import { useEffect, useState } from "react";

const ACCENTS = {
  description: "#616161",
  light: "#B26A00",
  watering: "#1565C0",
  soil: "#6D4C41",
  climate: "#00838F",
  flowering: "#C2185B",
  uses: "#E65100",
  care: "#2E7D32",
};

const ICONS = {
  description: "📝",
  light: "☀️",
  watering: "💧",
  soil: "🪴",
  climate: "🌡️",
  flowering: "🌸",
  uses: "🍽️",
  care: "🌱",
};

/**
 * Ficha de la planta: cabecera con la foto y las 8 categorías. Las palabras
 * del glosario se pueden pulsar para ver su significado.
 */
export default function Result({ entry, onBack }) {
  const [term, setTerm] = useState(null);
  const plant = entry.plant;
  const confidence = Math.round((plant.confidence || 0) * 100);

  useEffect(() => {
    function onKey(event) {
      if (event.key === "Escape") setTerm(null);
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <>
      <div className="card" style={{ padding: 22 }}>
        <div className="result-head">
          {entry.photo ? (
            <img className="photo" src={entry.photo} alt={plant.common_name} />
          ) : (
            <div className="photo placeholder">🌿</div>
          )}

          <div>
            <h2>{plant.common_name || plant.scientific_name}</h2>
            <div className="scientific">
              {plant.scientific_name}
              {plant.authorship ? ` ${plant.authorship}` : ""}
            </div>
            <div className="family">
              Familia: {plant.family || "sin dato"}
            </div>
            <div className="confidence">
              <span>Confianza: {confidence}%</span>
              <span className="track">
                <span
                  className="fill"
                  style={{ width: `${Math.max(6, confidence)}%` }}
                />
              </span>
            </div>
          </div>
        </div>
      </div>

      <div className="sections">
        {(plant.sections || []).map((section) => (
          <article
            className="section"
            key={section.key}
            style={{ "--accent": ACCENTS[section.key] || "#2E7D32" }}
          >
            <h3>
              <span aria-hidden="true">{ICONS[section.key] || "🌿"}</span>
              {section.title}
            </h3>

            <p>
              {highlightTerms(section.body || "", section.terms || [], setTerm)}
            </p>

            {section.materials && section.materials.length > 0 && (
              <div className="materials">
                <h4>Materiales recomendados</h4>
                <ul>
                  {section.materials.map((material) => (
                    <li key={material.name}>
                      <strong>{material.name}:</strong> {material.detail}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </article>
        ))}
      </div>

      <div style={{ marginTop: 22, textAlign: "center" }}>
        <button className="btn ghost" onClick={onBack}>
          Escanear otra planta
        </button>
        <p className="hint" style={{ marginTop: 10 }}>
          Usa el botón «Preguntar» para consultar dudas sobre esta planta.
        </p>
      </div>

      {term && (
        <div className="term-dialog" onClick={() => setTerm(null)}>
          <div className="term-card" onClick={(event) => event.stopPropagation()}>
            <h3>{term.word}</h3>
            <p>{term.meaning}</p>
            <button className="btn small" onClick={() => setTerm(null)}>
              Entendido
            </button>
          </div>
        </div>
      )}
    </>
  );
}

/**
 * Convierte el cuerpo de la sección en texto con las palabras del glosario
 * resaltadas y pulsables. Se limpia el Markdown básico (**negrita**).
 */
function highlightTerms(body, terms, onTerm) {
  const text = body.replace(/\*\*/g, "");
  const words = terms
    .map((item) => item.word)
    .filter(Boolean)
    .sort((a, b) => b.length - a.length);

  if (words.length === 0) {
    return text;
  }

  const pattern = new RegExp(`(${words.map(escapeRegExp).join("|")})`, "gi");
  return text.split(pattern).map((part, index) => {
    const match = terms.find(
      (item) => item.word.toLowerCase() === part.toLowerCase()
    );
    if (!match) return part;
    return (
      <button
        type="button"
        className="term"
        key={`${part}-${index}`}
        onClick={() => onTerm(match)}
        title={match.meaning}
      >
        {part}
      </button>
    );
  });
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
