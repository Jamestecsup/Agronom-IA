import { useEffect } from "react";

/** Ventana modal con el significado de una palabra del glosario. */
export default function TermDialog({ term, onClose }) {
  useEffect(() => {
    if (!term) return undefined;
    function onKey(event) {
      if (event.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [term, onClose]);

  if (!term) return null;

  return (
    <div className="term-dialog" onClick={onClose}>
      <div className="term-card" onClick={(event) => event.stopPropagation()}>
        <h3>{term.word}</h3>
        <p>{term.meaning}</p>
        <button className="btn small" onClick={onClose}>
          Entendido
        </button>
      </div>
    </div>
  );
}
