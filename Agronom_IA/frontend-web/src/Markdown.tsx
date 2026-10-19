import { useState } from 'react'
import type { ComponentProps, ReactElement, ReactNode } from 'react'
import ReactMarkdown from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import remarkGfm from 'remark-gfm'

function plainText(node: ReactNode): string {
  if (typeof node === 'string' || typeof node === 'number') return String(node)
  if (Array.isArray(node)) return node.map(plainText).join('')
  if (node && typeof node === 'object' && 'props' in node) return plainText((node as ReactElement<{ children?: ReactNode }>).props.children)
  return ''
}

function CodeBlock({ children, ...props }: ComponentProps<'pre'>) {
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(plainText(children))
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1600)
    } catch {
      setCopied(false)
    }
  }
  return (
    <div className="code-block">
      <button className="code-copy" type="button" onClick={copy} aria-label="Copiar código">
        {copied ? <span>Copiado</span> : <span>Copiar</span>}
      </button>
      <pre {...props}>{children}</pre>
    </div>
  )
}

export default function Markdown({ content }: { content: string }) {
  return <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]} components={{ pre: CodeBlock }}>{content}</ReactMarkdown>
}
