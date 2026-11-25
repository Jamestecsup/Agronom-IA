/* Carga síncrona antes del CSS. Los valores se limitan a tokens declarados. */
(() => {
  const options = {
    mode: ["system", "light", "dark"], accent: ["green", "blue", "plum"],
    font: ["sans", "serif", "accessible"], scale: ["normal", "large", "extra"],
    density: ["comfortable", "compact"], motion: ["system", "off"],
    garden: ["cards", "list"],
  };
  let saved;
  try { saved = JSON.parse(localStorage.getItem("agronomia.appearance")); } catch {}
  for (const [key, values] of Object.entries(options)) {
    document.documentElement.dataset[key] = values.includes(saved?.[key]) ? saved[key] : values[0];
  }
})();
