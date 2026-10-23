import React from "react";

/**
 * Render seguro (sin innerHTML) del Markdown sencillo que devuelve Gemini:
 * títulos, viñetas, listas numeradas, negrita, cursiva y código.
 */
export default function Markdown({ text }) {
  const blocks = parseBlocks(text);
  return (
    <div className="md">
      {blocks.map((block, index) => {
        if (block.type === "heading") {
          return <h4 key={index}>{inline(block.text)}</h4>;
        }
        if (block.type === "rule") {
          return <hr key={index} />;
        }
        if (block.type === "bullet" || block.type === "number") {
          return (
            <p className="md-item" key={index}>
              <span className="md-dot">
                {block.type === "bullet" ? "•" : `${block.marker}.`}
              </span>
              <span>{inline(block.text)}</span>
            </p>
          );
        }
        return <p key={index}>{inline(block.text)}</p>;
      })}
    </div>
  );
}

const NUMBERED = /^(\d+)[.)]\s+(.*)$/;

function parseBlocks(raw) {
  const blocks = [];
  let paragraph = "";

  function flush() {
    if (paragraph.trim()) {
      blocks.push({ type: "paragraph", text: paragraph.trim() });
    }
    paragraph = "";
  }

  String(raw || "")
    .split(/\r?\n/)
    .forEach((line) => {
      const trimmed = line.trim();
      if (!trimmed) {
        flush();
        return;
      }
      if (trimmed === "---" || trimmed === "***" || trimmed === "___") {
        flush();
        blocks.push({ type: "rule" });
        return;
      }
      if (trimmed.startsWith("#")) {
        flush();
        blocks.push({
          type: "heading",
          text: trimmed.replace(/^#+\s*/, ""),
        });
        return;
      }
      if (/^[-*•]\s+/.test(trimmed)) {
        flush();
        blocks.push({ type: "bullet", text: trimmed.slice(2).trim() });
        return;
      }
      const numbered = NUMBERED.exec(trimmed);
      if (numbered) {
        flush();
        blocks.push({
          type: "number",
          marker: numbered[1],
          text: numbered[2].trim(),
        });
        return;
      }
      paragraph += (paragraph ? " " : "") + trimmed.replace(/^>\s*/, "");
    });

  flush();
  return blocks;
}

function inline(text) {
  const nodes = [];
  const pattern = /(\*\*[^*]+\*\*|__[^_]+__|\*[^*]+\*|`[^`]+`)/g;
  let last = 0;
  let match;
  let key = 0;

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) {
      nodes.push(text.slice(last, match.index));
    }
    const token = match[0];
    if (token.startsWith("**") || token.startsWith("__")) {
      nodes.push(<strong key={key++}>{token.slice(2, -2)}</strong>);
    } else if (token.startsWith("`")) {
      nodes.push(<code key={key++}>{token.slice(1, -1)}</code>);
    } else {
      nodes.push(<em key={key++}>{token.slice(1, -1)}</em>);
    }
    last = match.index + token.length;
  }
  if (last < text.length) {
    nodes.push(text.slice(last));
  }
  return nodes;
}
