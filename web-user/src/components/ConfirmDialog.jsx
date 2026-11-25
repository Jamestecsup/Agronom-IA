import { useEffect, useId, useRef } from "react";

export default function ConfirmDialog({ title, text, confirmLabel, onConfirm, onClose, busy = false }) {
  const dialog = useRef(null);
  const heading = useId();
  const description = useId();
  useEffect(() => {
    const node = dialog.current;
    const previous = document.activeElement;
    node.showModal();
    return () => {
      node.close();
      if (previous?.isConnected) previous.focus();
    };
  }, []);
  return <dialog ref={dialog} className="settings-dialog" aria-labelledby={heading} aria-describedby={description}
    onCancel={(event) => { event.preventDefault(); if (!busy) onClose(); }}>
    <h2 id={heading}>{title}</h2>
    <p id={description}>{text}</p>
    <div className="actions">
      <button className="btn ghost" type="button" autoFocus disabled={busy} onClick={onClose}>Cancelar</button>
      <button className="btn" type="button" disabled={busy} onClick={onConfirm}>{confirmLabel}</button>
    </div>
  </dialog>;
}
