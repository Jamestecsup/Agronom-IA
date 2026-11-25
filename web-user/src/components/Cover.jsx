import { useEffect, useRef, useState } from "react";
import { getPlantCoverUrl } from "../api";

export default function Cover({ id, alt }) {
  const holder = useRef(null);
  const [url, setUrl] = useState(null);
  useEffect(() => {
    let active = true;
    let local = null;
    let observer;
    const load = () => getPlantCoverUrl(id).then((result) => {
      local = result;
      if (active) setUrl(result);
      else if (result) URL.revokeObjectURL(result);
    });
    if (typeof IntersectionObserver === "function") {
      observer = new IntersectionObserver(([entry], source) => {
        if (!entry.isIntersecting) return;
        source.disconnect();
        load();
      }, { rootMargin: "100px" });
      observer.observe(holder.current);
    } else load();
    return () => {
      active = false;
      observer?.disconnect();
      if (local) URL.revokeObjectURL(local);
    };
  }, [id]);
  return <div ref={holder} className="plant-cover">
    {url ? <img src={url} alt={alt} width="280" height="160" loading="lazy" /> : <span aria-hidden="true">✿</span>}
  </div>;
}
