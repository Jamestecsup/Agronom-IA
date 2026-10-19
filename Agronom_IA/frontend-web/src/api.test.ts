import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadModels, streamChat } from './api'

afterEach(() => vi.unstubAllGlobals())

describe('AI chat API client', () => {
  it('reads only the public model catalog and uses the local backend', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ models: [
      { id: 'qwen-local', name: 'Qwen', provider: 'Open WebUI', modelId: 'local-id', available: true, unavailableReason: null },
    ] }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    const models = await loadModels()

    expect(fetchMock).toHaveBeenCalledWith('/api/ai/models', expect.objectContaining({ headers: { Accept: 'application/json' } }))
    expect(models[0].id).toBe('qwen-local')
    expect(JSON.stringify(models)).not.toContain('api-key')
  })

  it('processes provider deltas from a server-sent event stream', async () => {
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        const encoder = new TextEncoder()
        controller.enqueue(encoder.encode('event: delta\ndata: {"text":"Hola "}\n\n'))
        controller.enqueue(encoder.encode('event: delta\ndata: {"text":"mundo"}\n\n'))
        controller.enqueue(encoder.encode('event: done\ndata: {"providerId":"qwen-local"}\n\n'))
        controller.close()
      },
    })
    const fetchMock = vi.fn().mockResolvedValue(new Response(stream, { status: 200, headers: { 'Content-Type': 'text/event-stream' } }))
    vi.stubGlobal('fetch', fetchMock)
    const chunks: string[] = []

    await streamChat('qwen-local', [{ role: 'user', content: 'Hola' }], new AbortController().signal, (text) => chunks.push(text))

    expect(chunks.join('')).toBe('Hola mundo')
    const request = fetchMock.mock.calls[0][1] as RequestInit
    expect(JSON.parse(String(request.body))).toMatchObject({ providerId: 'qwen-local', messages: [{ role: 'user' }] })
  })

  it('surfaces a structured backend/provider error instead of success', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'El modelo local no está disponible.' }), {
      status: 503, headers: { 'Content-Type': 'application/json' },
    })))

    await expect(streamChat('qwen-local', [{ role: 'user', content: 'Hola' }], new AbortController().signal, () => undefined))
      .rejects.toThrow('El modelo local no está disponible.')
  })

  it('rejects a completed stream that contains no generated text', async () => {
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('event: done\ndata: {"providerId":"qwen-local"}\n\n'))
        controller.close()
      },
    })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(stream, {
      status: 200, headers: { 'Content-Type': 'text/event-stream' },
    })))

    await expect(streamChat('qwen-local', [{ role: 'user', content: 'Hola' }], new AbortController().signal, () => undefined))
      .rejects.toThrow('El modelo terminó sin generar una respuesta.')
  })
})
