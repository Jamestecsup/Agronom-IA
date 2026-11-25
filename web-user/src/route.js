import { useEffect, useState } from "react";

const currentPath = () => window.location.pathname + window.location.search;

export function useRoute() {
  const [path, setPath] = useState(currentPath);
  useEffect(() => {
    const onRoute = () => setPath(currentPath());
    window.addEventListener("popstate", onRoute);
    return () => window.removeEventListener("popstate", onRoute);
  }, []);
  return path;
}

export function navigate(path, replace = false) {
  if (currentPath() === path) return;
  window.history[replace ? "replaceState" : "pushState"](null, "", path);
  window.dispatchEvent(new PopStateEvent("popstate"));
}
