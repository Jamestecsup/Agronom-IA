import { useEffect, useMemo, useState } from "react";
import { listIdentifiedPlants, clearCache } from "../api";
import Cover from "../components/Cover";
import Link from "../components/Link";

export default function Garden({ layout }) {
  const [items, setItems] = useState(null);
  const [search, setSearch] = useState("");
  const [order, setOrder] = useState("recent");
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    listIdentifiedPlants().then((data) => {
      if (active) setItems(data.plants || []);
    }).catch((failure) => {
      if (active) setError(failure.message);
    });
    return () => { active = false; };
  }, [retry]);

  const filtered = useMemo(() => {
    const term = search.trim().toLocaleLowerCase("es");
    const result = (items || []).filter((plant) =>
      [plant.common_name, plant.scientific_name].some((name) => name?.toLocaleLowerCase("es").includes(term)));
    if (order === "name") result.sort((a,b) => name(a).localeCompare(name(b), "es"));
    else result.sort((a,b) => b.created_at.localeCompare(a.created_at));
    return result;
  }, [items, search, order]);

  return <section aria-labelledby="garden-heading">
    <div className="section-heading"><div><h1 id="garden-heading">Mi jardín</h1>
      <p>Las plantas identificadas en tu cuenta.</p></div>
      <Link className="btn" to="/">Identificar planta</Link></div>
    {error ? <div className="card inset"><p role="alert">{error}</p><button className="btn" onClick={() => {
      clearCache(); setError(""); setRetry((count) => count + 1);
    }}>Reintentar</button></div> : !items ? <p role="status">Cargando jardín…</p> : !items.length
      ? <div className="card inset"><h2>Tu jardín está vacío</h2><p>Identifica una planta para encontrarla aquí.</p></div>
      : <>
        <div className="garden-tools"><label>Buscar planta<input type="search" value={search}
          onChange={(event) => setSearch(event.target.value)} placeholder="Nombre común o científico" /></label>
          <label>Ordenar<select value={order} onChange={(event) => setOrder(event.target.value)}>
            <option value="recent">Más recientes</option><option value="name">Nombre</option>
          </select></label></div>
        <p className="muted" role="status">{filtered.length} de {items.length} plantas</p>
        {filtered.length === 0 ? <p>No hay plantas que coincidan con la búsqueda.</p> :
          <div className={layout === "list" ? "server-garden garden-list" : "server-garden"}>
            {filtered.map((plant) => <Link to={"/plants/" + plant.id} className="server-plant" key={plant.id}>
              <Cover id={plant.id} alt={name(plant)} /><div className="plant-meta">
              <strong>{name(plant)}</strong><i>{plant.scientific_name}</i>
              <small>{new Date(plant.created_at).toLocaleDateString("es-PE")}</small></div>
            </Link>)}
          </div>}
      </>}
  </section>;
}

function name(plant) { return plant.common_name || plant.scientific_name; }
