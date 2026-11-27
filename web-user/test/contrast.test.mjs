import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

function luminance(hex) {
  const digits = hex.length === 3 ? [...hex].map((digit) => digit.repeat(2)).join("") : hex;
  return [0.2126, 0.7152, 0.0722].reduce((sum, weight, index) => {
    const channel = parseInt(digits.slice(index * 2, index * 2 + 2), 16) / 255;
    return sum + weight * (channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4);
  }, 0);
}

test("accent pairs meet AA across all themes", async () => {
  const css = await readFile(new URL("../src/theme.css", import.meta.url), "utf8");
  const palette = (block) => Object.fromEntries([...block.matchAll(/--([\w-]+):\s*#([a-f\d]+);/gi)]
    .map(([, name, value]) => [name, luminance(value)]));
  const base = palette(css.match(/:root \{([^}]+)\}/)[1]);
  const dark = palette(css.match(/:root:where\(\[data-mode="dark"\]\) \{([^}]+)\}/)[1]);
  for (const accent of ["green", "blue", "plum"]) {
    const colors = accent === "green" ? base : {
      ...base, ...palette(css.match(new RegExp(`:root\\[data-accent="${accent}"\\] \\{([^}]+)\\}`))[1]),
    };
    for (const mode of ["light", "dark"]) {
      const theme = mode === "light" ? colors : { ...colors, ...dark };
      const link = `accent-link-${mode}`, soft = `accent-soft-${mode}`;
      for (const [foreground, background] of [
        [link, "bg"], [link, "surface"], [link, soft], ["text", soft], ["muted", soft],
        ["on-accent", `accent-fill-${mode}`], ["on-accent", `accent-hover-${mode}`],
      ]) {
        const values = [theme[foreground], theme[background]];
        const ratio = (Math.max(...values) + 0.05) / (Math.min(...values) + 0.05);
        assert.ok(ratio >= 4.5, `${accent}/${mode} ${foreground} sobre ${background}: ${ratio.toFixed(2)}`);
      }
    }
  }
});
