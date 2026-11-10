/**
 * Convierte un texto en fragmentos con las palabras del glosario resaltadas.
 * Se usa tanto en la ficha como en las descripciones de las candidatas.
 */
export function highlightTerms(body, terms, onTerm) {
  const text = (body || "").replace(/\*\*/g, "");
  const words = (terms || [])
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
