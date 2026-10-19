import type { ModelOption } from './types'

export interface ChatImagePayload {
  fileName: string
  mimeType: string
  data: string
}

export interface ChatRequestMessage {
  role: 'user' | 'assistant'
  content: string
  images?: ChatImagePayload[]
}

export async function loadModels(signal?: AbortSignal): Promise<ModelOption[]> {
  const response = await fetch('/api/ai/models', { signal, headers: { Accept: 'application/json' } })
  if (!response.ok) throw new Error('No se pudo consultar el estado de los modelos.')
  const data = (await response.json()) as { models?: ModelOption[] }
  if (!Array.isArray(data.models)) throw new Error('El backend devolvió una lista de modelos no válida.')
  return data.models
}

export async function streamChat(
  providerId: string,
  messages: ChatRequestMessage[],
  signal: AbortSignal,
  onDelta: (text: string) => void,
): Promise<void> {
  const response = await fetch('/api/ai/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ providerId, messages }),
    signal,
  })

  if (!response.ok) {
    const payload = (await response.json().catch(() => null)) as { message?: string } | null
    throw new Error(payload?.message ?? `El servidor rechazó la solicitud (HTTP ${response.status}).`)
  }
  if (!response.body) throw new Error('El navegador no pudo abrir el flujo de respuesta.')

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let finished = false
  let receivedText = false
  try {
    while (!finished) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true }).replaceAll('\r\n', '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        const event = block.split('\n').find((line) => line.startsWith('event:'))?.slice(6).trim()
        const data = block.split('\n').filter((line) => line.startsWith('data:')).map((line) => line.slice(5).trim()).join('\n')
        if (data) {
          const payload = JSON.parse(data) as { text?: string; providerId?: string; message?: string }
          if (event === 'delta' && payload.text) {
            receivedText = true
            onDelta(payload.text)
          }
          if (event === 'error') throw new Error(payload.message ?? 'El modelo no pudo completar la respuesta.')
          if (event === 'done') finished = true
        }
        boundary = buffer.indexOf('\n\n')
      }
    }
  } finally {
    await reader.cancel().catch(() => undefined)
  }
  if (!finished && !signal.aborted) throw new Error('La conexión se interrumpió antes de completar la respuesta.')
  if (finished && !receivedText && !signal.aborted) throw new Error('El modelo terminó sin generar una respuesta.')
}
