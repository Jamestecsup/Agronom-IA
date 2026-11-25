import { useEffect, useState } from "react";
import { getPlant, getPlantCoverUrl } from "../api";
import Link from "../components/Link";
import Result from "./Result";
import { navigate } from "../route";

export default function Plant({ id }) {
  const [entry, setEntry] = useState(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    let photo;
    getPlant(id).then((data) => {
      if (!active) return;
      setEntry({ plant: data.plant, createdAt: data.summary.created_at });
      return getPlantCoverUrl(id).then((url) => {
        photo = url;
        if (active) setEntry((current) => ({ ...current, photo }));
        else if (photo) URL.revokeObjectURL(photo);
      });
    }).catch((error) => { if (active) setError(error.message); });
    return () => { active = false; if (photo) URL.revokeObjectURL(photo); };
  }, [id]);
  if (error) return <p role="alert">{error}</p>;
  if (!entry) return <p role="status">Cargando la ficha…</p>;
  return <><Link className="btn ghost" to={"/chat?plant=" + id}>Abrir conversación</Link>
    <Result entry={entry} onBack={() => navigate("/garden")} /></>;
}
