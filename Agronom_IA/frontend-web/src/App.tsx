import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  ArrowDown, ArrowUp, ChevronDown, Leaf, Menu, MessageSquare, MessageSquarePlus,
  Moon, PanelLeftClose, PanelLeftOpen, Paperclip, RefreshCw, Search,
  Send, Sprout, Sun, Trash2, X,
} from 'lucide-react'
import { loadModels, streamChat, type ChatRequestMessage } from './api'
import { fileToBase64, MAX_IMAGE_COUNT, MAX_IMAGE_BYTES, MAX_TOTAL_IMAGE_BYTES, prepareImage, type ValidatedImage } from './imageAttachments'
import { deleteConversationImages, loadImageAttachments, saveImageAttachments } from './imageStore'
import type { ChatMessage, Conversation, ModelOption } from './types'

const Markdown = lazy(() => import('./Markdown'))

const STORAGE_KEY = 'agronom-ia-web-v1'
const SUGGESTIONS = [
  '¿Cómo identifico si estoy regando demasiado?',
  '¿Qué señales indican que una planta necesita más luz?',
  '¿Cómo preparo un sustrato con buen drenaje?',
]

interface SavedWorkspace {
  conversations: Conversation[]
  activeId: string
  theme: 'light' | 'dark'
  collapsed: boolean
}

function id() {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function emptyConversation(providerId = 'qwen-local'): Conversation {
  const now = Date.now()
  return { id: id(), title: 'Nueva conversación', providerId, updatedAt: now, messages: [] }
}

function restoreWorkspace(): SavedWorkspace {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      const data = JSON.parse(saved) as Partial<SavedWorkspace>
      // Storage-only migration; the legacy id is never sent to the backend.
      const conversations = Array.isArray(data.conversations) ? data.conversations.map((chat) =>
        chat.providerId === 'space-bunny' ? { ...chat, providerId: 'deepseek-opencode' } : chat,
      ) : []
      const activeId = conversations.some((chat) => chat.id === data.activeId) ? data.activeId! : conversations[0]?.id ?? ''
      const activeTheme = data.theme === 'dark' ? 'dark' : 'light'
      if (conversations.length) return { conversations, activeId, theme: activeTheme, collapsed: Boolean(data.collapsed) }
    }
  } catch {
    localStorage.removeItem(STORAGE_KEY)
  }
  const initial = emptyConversation()
  return { conversations: [initial], activeId: initial.id, theme: 'light', collapsed: false }
}

function formatTime(timestamp: number) {
  return new Intl.DateTimeFormat('es-CO', { hour: '2-digit', minute: '2-digit' }).format(timestamp)
}

export default function App() {
  const initial = useMemo(restoreWorkspace, [])
  const [conversations, setConversations] = useState(initial.conversations)
  const [activeId, setActiveId] = useState(initial.activeId)
  const [theme, setTheme] = useState<'light' | 'dark'>(initial.theme)
  const [collapsed, setCollapsed] = useState(initial.collapsed)
  const [mobileSidebar, setMobileSidebar] = useState(false)
  const [search, setSearch] = useState('')
  const [draft, setDraft] = useState('')
  const [attachments, setAttachments] = useState<ValidatedImage[]>([])
  const [isDragActive, setIsDragActive] = useState(false)
  const [isDragInvalid, setIsDragInvalid] = useState(false)
  const [isPreparingImages, setIsPreparingImages] = useState(false)
  const [models, setModels] = useState<ModelOption[]>([])
  const [modelsLoading, setModelsLoading] = useState(true)
  const [modelsError, setModelsError] = useState('')
  const [isGenerating, setIsGenerating] = useState(false)
  const [showScrollButton, setShowScrollButton] = useState(false)
  const [notice, setNotice] = useState('')
  const threadRef = useRef<HTMLDivElement>(null)
  const composerRef = useRef<HTMLTextAreaElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const attachmentsRef = useRef<ValidatedImage[]>([])
  const preparingImagesRef = useRef(false)
  const attachedFilesRef = useRef(new Map<string, Map<string, File>>())
  const dragDepthRef = useRef(0)
  const abortRef = useRef<AbortController | null>(null)
  const scrollPinnedRef = useRef(true)

  const active = conversations.find((chat) => chat.id === activeId) ?? conversations[0]
  const filteredChats = useMemo(() => {
    const query = search.trim().toLocaleLowerCase('es')
    const recent = [...conversations].sort((a, b) => b.updatedAt - a.updatedAt)
    return query ? recent.filter((chat) => chat.title.toLocaleLowerCase('es').includes(query)) : recent
  }, [conversations, search])
  const selectedModel = models.find((model) => model.id === active?.providerId)

  const refreshModels = useCallback(async () => {
    setModelsLoading(true)
    setModelsError('')
    try {
      setModels(await loadModels())
    } catch (error) {
      setModelsError(error instanceof Error ? error.message : 'No se pudo consultar el backend.')
    } finally {
      setModelsLoading(false)
    }
  }, [])

  useEffect(() => { void refreshModels() }, [refreshModels])
  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ conversations, activeId, theme, collapsed } satisfies SavedWorkspace))
  }, [conversations, activeId, theme, collapsed])
  useEffect(() => {
    if (scrollPinnedRef.current) threadRef.current?.scrollTo({ top: threadRef.current.scrollHeight, behavior: 'smooth' })
  }, [active?.messages, isGenerating])
  useEffect(() => {
    const textarea = composerRef.current
    if (!textarea) return
    textarea.style.height = '0px'
    textarea.style.height = `${Math.min(textarea.scrollHeight, 220)}px`
  }, [draft])
  useEffect(() => {
    if (!notice) return
    const timeout = window.setTimeout(() => setNotice(''), 2600)
    return () => window.clearTimeout(timeout)
  }, [notice])
  useEffect(() => () => abortRef.current?.abort(), [])
  useEffect(() => () => { attachedFilesRef.current.clear() }, [])

  const addImageFiles = useCallback(async (files: File[]) => {
    if (!files.length) return
    if (preparingImagesRef.current) {
      setNotice('Espera a que termine la validación actual antes de adjuntar más imágenes.')
      return
    }
    if (!selectedModel?.supportsImages) {
      setNotice(selectedModel?.imageUnavailableReason ?? 'El modelo seleccionado no admite análisis de imágenes.')
      return
    }
    const unique = files.filter((file) => !attachmentsRef.current.some((item) =>
      item.fileName === file.name && item.size === file.size && item.file.lastModified === file.lastModified))
    const availableSlots = MAX_IMAGE_COUNT - attachmentsRef.current.length
    if (unique.length > availableSlots) {
      setNotice(`Puedes adjuntar hasta ${MAX_IMAGE_COUNT} imágenes por mensaje.`)
      unique.splice(availableSlots)
    }
    let total = attachmentsRef.current.reduce((sum, item) => sum + item.size, 0)
    const prepared: ValidatedImage[] = []
    preparingImagesRef.current = true
    setIsPreparingImages(true)
    try {
      for (const file of unique) {
        if (file.size > MAX_IMAGE_BYTES) {
          setNotice(`${file.name || 'La imagen'} supera el límite de 15 MiB.`)
          continue
        }
        if (total + file.size > MAX_TOTAL_IMAGE_BYTES) {
          setNotice('Las imágenes superan el límite conjunto de 50 MiB.')
          continue
        }
        try {
          const image = await prepareImage(file)
          prepared.push(image)
          total += image.size
        } catch (error) {
          setNotice(error instanceof Error ? error.message : 'No se pudo validar una imagen.')
        }
      }
      if (prepared.length) {
        const next = [...attachmentsRef.current, ...prepared]
        attachmentsRef.current = next
        setAttachments(next)
      }
    } finally {
      preparingImagesRef.current = false
      setIsPreparingImages(false)
    }
  }, [selectedModel])

  const removeAttachment = (attachmentId: string) => {
    const next = attachmentsRef.current.filter((image) => image.id !== attachmentId)
    attachmentsRef.current = next
    setAttachments(next)
  }

  const onPasteImages = (event: React.ClipboardEvent<HTMLTextAreaElement>) => {
    const images = Array.from(event.clipboardData.items)
      .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
      .map((item) => item.getAsFile()).filter((file): file is File => file !== null)
    if (!images.length) return
    event.preventDefault()
    const pastedText = event.clipboardData.getData('text/plain')
    if (pastedText) {
      const textarea = event.currentTarget
      const start = textarea.selectionStart
      const end = textarea.selectionEnd
      setDraft((value) => value.slice(0, start) + pastedText + value.slice(end))
      requestAnimationFrame(() => textarea.setSelectionRange(start + pastedText.length, start + pastedText.length))
    }
    void addImageFiles(images)
  }

  const hasDraggedFiles = (event: React.DragEvent<HTMLElement>) => Array.from(event.dataTransfer.types).includes('Files')
  const dragContainsUnsupportedType = (event: React.DragEvent<HTMLElement>) => {
    const accepted = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
    return !selectedModel?.supportsImages || Array.from(event.dataTransfer.items)
      .some((item) => item.kind === 'file' && item.type && !accepted.includes(item.type))
  }
  const onFilesDrop = (event: React.DragEvent<HTMLElement>) => {
    if (!hasDraggedFiles(event)) return
    event.preventDefault()
    dragDepthRef.current = 0
    setIsDragActive(false)
    setIsDragInvalid(false)
    void addImageFiles(Array.from(event.dataTransfer.files))
  }

  const patchConversation = useCallback((conversationId: string, update: (conversation: Conversation) => Conversation) => {
    setConversations((previous) => previous.map((chat) => chat.id === conversationId ? update(chat) : chat))
  }, [])

  const startNewChat = () => {
    if (isGenerating) return
    const next = emptyConversation(active?.providerId ?? 'qwen-local')
    setConversations((previous) => [next, ...previous])
    setActiveId(next.id)
    setDraft('')
    setMobileSidebar(false)
    composerRef.current?.focus()
  }

  const changeModel = (providerId: string) => {
    if (!active || isGenerating) return
    patchConversation(active.id, (chat) => ({ ...chat, providerId, updatedAt: Date.now() }))
    const model = models.find((option) => option.id === providerId)
    if (model) setNotice(`Modelo activo: ${model.name} · ${model.provider}`)
  }

  const runChat = useCallback(async (conversationId: string, providerId: string, messages: ChatMessage[], assistantId: string) => {
    const controller = new AbortController()
    abortRef.current = controller
    setIsGenerating(true)
    setNotice('')
    try {
      const providerSupportsImages = models.find((model) => model.id === providerId)?.supportsImages ?? false
      const imageMessageIds = new Set<string>()
      let remainingImageBytes = MAX_TOTAL_IMAGE_BYTES
      let omittedImageContext = false
      if (providerSupportsImages) {
        for (const candidate of [...messages].reverse()) {
          if (!candidate.images?.length) continue
          const candidateSize = candidate.images.reduce((sum, image) => sum + image.size, 0)
          if (candidateSize <= remainingImageBytes) {
            imageMessageIds.add(candidate.id)
            remainingImageBytes -= candidateSize
          } else omittedImageContext = true
        }
      } else omittedImageContext = messages.some((message) => Boolean(message.images?.length))
      if (omittedImageContext) setNotice(providerSupportsImages
        ? 'Se omitieron algunas imágenes antiguas para respetar el límite de tamaño del proveedor.'
        : 'El modelo activo solo admite texto; no se enviaron las imágenes anteriores.')

      const requestMessages: ChatRequestMessage[] = await Promise.all(messages.map(async (message) => {
        if (!message.images?.length) return { role: message.role, content: message.content }
        if (!imageMessageIds.has(message.id)) return { role: message.role, content: message.content }
        let filesById = attachedFilesRef.current.get(message.id)
        if (!filesById?.size) {
          try { filesById = await loadImageAttachments(message.images.map((image) => image.id)) }
          catch { filesById = new Map() }
          if (filesById.size) attachedFilesRef.current.set(message.id, filesById)
        }
        const images = (await Promise.all(message.images.map(async (image) => {
          const file = filesById?.get(image.id)
          return file ? { fileName: image.fileName, mimeType: image.mimeType, data: await fileToBase64(file) } : null
        }))).filter((image): image is NonNullable<typeof image> => image !== null)
        if (images.length !== message.images.length) setNotice('No se pudieron recuperar algunas imágenes antiguas; se enviará el texto disponible.')
        return { role: message.role, content: message.content, ...(images ? { images } : {}) }
      }))
      await streamChat(providerId, requestMessages, controller.signal, (delta) => {
        patchConversation(conversationId, (chat) => ({
          ...chat,
          updatedAt: Date.now(),
          messages: chat.messages.map((message) => message.id === assistantId
            ? { ...message, content: message.content + delta }
            : message),
        }))
      })
      patchConversation(conversationId, (chat) => ({ ...chat, messages: chat.messages.filter((message) => message.id !== assistantId || message.content.length > 0) }))
    } catch (error) {
      if (controller.signal.aborted) {
        patchConversation(conversationId, (chat) => ({ ...chat, messages: chat.messages.filter((message) => message.id !== assistantId || message.content.length > 0) }))
        setNotice('Generación detenida.')
      } else {
        const message = error instanceof Error ? error.message : 'No se pudo completar la solicitud.'
        patchConversation(conversationId, (chat) => ({
          ...chat,
          messages: chat.messages.map((item) => item.id === assistantId ? { ...item, content: item.content || message, error: true } : item),
        }))
      }
    } finally {
      if (abortRef.current === controller) abortRef.current = null
      setIsGenerating(false)
    }
  }, [models, patchConversation])

  const sendMessage = useCallback((content = draft.trim()) => {
    const cleanContent = content.trim()
    const selectedImages = attachmentsRef.current
    if (!active || (!cleanContent && !selectedImages.length) || isGenerating || isPreparingImages || abortRef.current) return
    if (!selectedModel?.available) {
      setNotice(selectedModel?.unavailableReason ?? 'Elige un modelo disponible para continuar.')
      return
    }
    if (selectedImages.length && !selectedModel.supportsImages) {
      setNotice(selectedModel.imageUnavailableReason ?? 'El modelo seleccionado no admite análisis de imágenes.')
      return
    }
    const now = Date.now()
    const userId = id()
    const userMessage: ChatMessage = {
      id: userId, role: 'user', content: cleanContent, createdAt: now,
      ...(selectedImages.length ? { images: selectedImages.map(({ id: imageId, fileName, mimeType, size, previewUrl }) => ({ id: imageId, fileName, mimeType, size, previewUrl })) } : {}),
    }
    if (selectedImages.length) attachedFilesRef.current.set(userId, new Map(selectedImages.map((image) => [image.id, image.file])))
    const assistantMessage: ChatMessage = { id: id(), role: 'assistant', content: '', modelId: active.providerId, createdAt: now + 1 }
    const nextMessages = [...active.messages, userMessage, assistantMessage]
    const titleSeed = cleanContent || `Imagen: ${selectedImages[0]?.fileName ?? 'adjunta'}`
    const title = active.messages.length === 0 ? titleSeed.replace(/\s+/g, ' ').slice(0, 48) : active.title
    patchConversation(active.id, (chat) => ({ ...chat, title, updatedAt: now, messages: nextMessages }))
    setDraft('')
    if (selectedImages.length) {
      attachedFilesRef.current.set(userId, new Map(selectedImages.map((image) => [image.id, image.file])))
      void saveImageAttachments(active.id, userId, selectedImages.map((image) => ({ id: image.id, file: image.file })))
        .catch(() => setNotice('El mensaje se envió, pero el archivo no quedó disponible para futuras preguntas tras recargar.'))
    }
    void runChat(active.id, active.providerId, nextMessages.slice(0, -1), assistantMessage.id)
    attachmentsRef.current = []
    setAttachments([])
  }, [active, draft, isGenerating, isPreparingImages, patchConversation, runChat, selectedModel])

  const retryLast = () => {
    if (!active || isGenerating) return
    const messages = active.messages.filter((message) => !(message.role === 'assistant' && message.error))
    const lastUser = [...messages].reverse().find((message) => message.role === 'user')
    if (!lastUser) return
    const assistantMessage: ChatMessage = { id: id(), role: 'assistant', content: '', modelId: active.providerId, createdAt: Date.now() }
    const next = [...messages, assistantMessage]
    patchConversation(active.id, (chat) => ({ ...chat, messages: next }))
    void runChat(active.id, active.providerId, next.slice(0, -1), assistantMessage.id)
  }

  const stopGeneration = () => abortRef.current?.abort()

  const onThreadScroll = () => {
    const thread = threadRef.current
    if (!thread) return
    const distance = thread.scrollHeight - thread.scrollTop - thread.clientHeight
    scrollPinnedRef.current = distance < 100
    setShowScrollButton(distance > 220)
  }

  const removeConversation = (chat: Conversation) => {
    if (isGenerating && chat.id === activeId) return
    if (!window.confirm(`¿Eliminar “${chat.title}” de este navegador?`)) return
    chat.messages.forEach((message) => attachedFilesRef.current.delete(message.id))
    void deleteConversationImages(chat.id).catch(() => setNotice('No se pudieron limpiar todas las imágenes guardadas en este navegador.'))
    const remaining = conversations.filter((item) => item.id !== chat.id)
    if (remaining.length) {
      setConversations(remaining)
      if (activeId === chat.id) setActiveId(remaining[0].id)
    } else {
      const fresh = emptyConversation()
      setConversations([fresh])
      setActiveId(fresh.id)
    }
  }

  const modelMenu = (
    <label className="model-select-wrap">
      <span className="sr-only">Modelo de inteligencia artificial</span>
      <select aria-label="Modelo de inteligencia artificial" value={active?.providerId ?? ''} disabled={isGenerating || modelsLoading || models.length === 0}
        onChange={(event) => changeModel(event.target.value)}>
        {models.length === 0 && <option value="">Modelos no disponibles</option>}
        {models.map((model) => <option key={model.id} value={model.id} disabled={!model.available}>{model.name} · {model.provider}{model.available ? '' : ' · No disponible'}</option>)}
      </select>
      <ChevronDown size={15} aria-hidden="true" />
    </label>
  )

  return (
    <div className={`app-shell theme-${theme}`} data-theme={theme}>
      {mobileSidebar && <button className="mobile-scrim" aria-label="Cerrar menú" onClick={() => setMobileSidebar(false)} />}
      <aside className={`sidebar ${collapsed ? 'sidebar-collapsed' : ''} ${mobileSidebar ? 'sidebar-mobile-open' : ''}`} aria-label="Conversaciones">
        <div className="sidebar-brand">
          <div className="brand-mark" aria-hidden="true"><Sprout size={20} strokeWidth={2.1} /></div>
          {!collapsed && <div className="brand-copy"><strong>Agronom-IA</strong><span>Asistente botánico</span></div>}
          <button className="icon-button sidebar-collapse" type="button" onClick={() => setCollapsed((value) => !value)} aria-label={collapsed ? 'Expandir barra lateral' : 'Contraer barra lateral'}>
            {collapsed ? <PanelLeftOpen size={18} /> : <PanelLeftClose size={18} />}
          </button>
        </div>

        <button className={`new-chat-button ${collapsed ? 'new-chat-icon' : ''}`} type="button" onClick={startNewChat} disabled={isGenerating}>
          <MessageSquarePlus size={18} aria-hidden="true" />{!collapsed && <span>Nueva conversación</span>}
        </button>

        {!collapsed && <label className="search-box">
          <Search size={16} aria-hidden="true" />
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar conversaciones" aria-label="Buscar conversaciones" />
          {search && <button className="search-clear" type="button" onClick={() => setSearch('')} aria-label="Limpiar búsqueda"><X size={14} /></button>}
        </label>}

        <div className="sidebar-section-label">{!collapsed && <span>Recientes</span>}</div>
        <nav className="conversation-list" aria-label="Conversaciones recientes">
          {filteredChats.map((chat) => (
            <div className={`conversation-row ${chat.id === activeId ? 'conversation-active' : ''}`} key={chat.id}>
              <button className="conversation-button" type="button" onClick={() => { setActiveId(chat.id); setMobileSidebar(false) }} title={collapsed ? chat.title : undefined} aria-current={chat.id === activeId ? 'page' : undefined}>
                <MessageIcon active={chat.id === activeId} />
                {!collapsed && <span>{chat.title}</span>}
              </button>
              {!collapsed && <button className="conversation-delete" type="button" onClick={() => removeConversation(chat)} aria-label={`Eliminar ${chat.title}`} title="Eliminar conversación"><Trash2 size={15} /></button>}
            </div>
          ))}
          {!collapsed && filteredChats.length === 0 && <p className="sidebar-empty">No hay coincidencias.</p>}
        </nav>

        <div className="sidebar-bottom">
          {!collapsed && <div className={`connection-card ${modelsError ? 'connection-error' : ''}`}>
            <span className="connection-dot" aria-hidden="true" />
            <div><strong>{modelsError ? 'Backend desconectado' : 'Conexión local'}</strong><span>{modelsError ? 'No se pudieron consultar modelos' : `${models.filter((model) => model.available).length} modelos disponibles`}</span></div>
            <button className="icon-button connection-refresh" type="button" onClick={() => void refreshModels()} aria-label="Actualizar estado de conexión"><RefreshCw size={15} className={modelsLoading ? 'spin' : ''} /></button>
          </div>}
          <button className="sidebar-footer-action" type="button" onClick={() => setTheme((value) => value === 'light' ? 'dark' : 'light')} aria-label={theme === 'light' ? 'Activar tema oscuro' : 'Activar tema claro'} title={theme === 'light' ? 'Tema oscuro' : 'Tema claro'}>
            {theme === 'light' ? <Moon size={18} /> : <Sun size={18} />}{!collapsed && <span>{theme === 'light' ? 'Tema oscuro' : 'Tema claro'}</span>}
          </button>
        </div>
      </aside>

      <main className="main-panel"
        onDragEnter={(event) => { if (!hasDraggedFiles(event)) return; event.preventDefault(); dragDepthRef.current += 1; setIsDragActive(true); setIsDragInvalid(dragContainsUnsupportedType(event)) }}
        onDragOver={(event) => { if (hasDraggedFiles(event)) { event.preventDefault(); const invalid = dragContainsUnsupportedType(event); setIsDragInvalid(invalid); event.dataTransfer.dropEffect = invalid ? 'none' : 'copy' } }}
        onDragLeave={(event) => { if (!hasDraggedFiles(event)) return; dragDepthRef.current = Math.max(0, dragDepthRef.current - 1); if (!dragDepthRef.current) { setIsDragActive(false); setIsDragInvalid(false) } }}
        onDrop={onFilesDrop}>
        <header className="topbar">
          <div className="topbar-left">
            <button className="icon-button mobile-menu-button" type="button" onClick={() => setMobileSidebar(true)} aria-label="Abrir conversaciones"><Menu size={20} /></button>
            <div className="mobile-brand-mark" aria-hidden="true"><Sprout size={19} /></div>
            <div className="topbar-title"><span className="eyebrow">ASISTENTE BOTÁNICO</span><h1>{active?.title ?? 'Nueva conversación'}</h1></div>
          </div>
          <div className="topbar-actions">
            {modelMenu}
            <div className="provider-status" title={selectedModel?.unavailableReason ?? (selectedModel?.available ? `${selectedModel.provider} conectado` : 'Comprobando conexión')}>
              <span className={`status-dot ${selectedModel?.available ? 'status-online' : ''}`} />
              <span>{modelsLoading ? 'Conectando' : selectedModel?.available ? 'En línea' : 'Sin conexión'}</span>
            </div>
          </div>
        </header>

        <section className="thread" ref={threadRef} onScroll={onThreadScroll} aria-label="Mensajes de la conversación" aria-live="off">
          {active?.messages.length === 0 ? (
            <div className="welcome-state">
              <div className="welcome-orbit"><div className="welcome-icon"><Leaf size={28} strokeWidth={1.7} /></div></div>
              <p className="welcome-kicker">{selectedModel?.name ?? 'Asistente de IA'}</p>
              <p className="welcome-copy welcome-greeting">{selectedModel?.welcomeMessage ?? 'Consulta sobre tus plantas y recibe orientación cuando un modelo esté disponible.'}</p>
              <div className="suggestion-grid">
                {SUGGESTIONS.map((suggestion, index) => (
                  <button className="suggestion-card" key={suggestion} type="button" onClick={() => sendMessage(suggestion)} disabled={!selectedModel?.available || isGenerating}>
                    <span>{['01', '02', '03'][index]}</span><p>{suggestion}</p><ArrowUp size={16} aria-hidden="true" />
                  </button>
                ))}
              </div>
              {modelsError && <div className="inline-alert" role="alert"><span>{modelsError}</span><button type="button" onClick={() => void refreshModels()}>Reintentar conexión</button></div>}
              {!modelsLoading && !modelsError && models.every((model) => !model.available) && <div className="inline-alert" role="status">Inicia al menos un proveedor local para comenzar.</div>}
            </div>
          ) : (
            <div className="messages-wrap">
              {active?.messages.map((message) => (
                <article className={`message-row message-${message.role}`} key={message.id}>
                  {message.role === 'assistant' && <div className="assistant-avatar" aria-hidden="true"><Sprout size={17} /></div>}
                  <div className="message-content-wrap">
                    <div className="message-meta">
                      <strong>{message.role === 'user' ? 'Tú' : 'Agronom-IA'}</strong>
                      {message.role === 'assistant' && message.modelId && <span>{models.find((model) => model.id === message.modelId)?.name ?? 'Asistente'}</span>}
                      <time dateTime={new Date(message.createdAt).toISOString()}>{formatTime(message.createdAt)}</time>
                    </div>
                    <div className={`message-content ${message.error ? 'message-error' : ''}`}>
                      {message.content ? <Suspense fallback={<p className="markdown-loading">Mostrando respuesta…</p>}><Markdown content={message.content} /></Suspense> : message.role === 'assistant' ? <TypingIndicator /> : null}
                      {message.images?.length ? <div className="message-images">
                        {message.images.map((image) => <figure className="message-image" key={image.id}>
                          <img src={image.previewUrl} alt={`Imagen adjunta: ${image.fileName}`} loading="lazy" />
                          <figcaption>{image.fileName}</figcaption>
                        </figure>)}
                      </div> : null}
                    </div>
                    {message.error && <button className="retry-button" type="button" onClick={retryLast} disabled={isGenerating}><RefreshCw size={14} /> Reintentar respuesta</button>}
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>

        {showScrollButton && <button className="scroll-latest" type="button" aria-label="Ir a los mensajes recientes" onClick={() => { scrollPinnedRef.current = true; threadRef.current?.scrollTo({ top: threadRef.current.scrollHeight, behavior: 'smooth' }) }}><ArrowDown size={17} /></button>}

        {isDragActive && <div className={`drop-overlay ${isDragInvalid ? 'drop-overlay-invalid' : ''}`} aria-live="polite">
          <div><Paperclip size={22} aria-hidden="true" /><strong>{!selectedModel?.supportsImages ? 'Este modelo no admite imágenes' : isDragInvalid ? 'Tipo de archivo no compatible' : 'Suelta las imágenes para adjuntarlas'}</strong>
            <span>{!selectedModel?.supportsImages ? selectedModel?.imageUnavailableReason : isDragInvalid ? 'Se aceptan JPEG, PNG, WebP o GIF.' : 'Se validará el contenido antes del envío · hasta 5 imágenes'}</span></div>
        </div>}

        <footer className="composer-area">
          {notice && <div className="status-notice" role="status">{notice}</div>}
          {modelsError && active?.messages.length ? <div className="composer-error" role="alert">{modelsError} <button type="button" onClick={() => void refreshModels()}>Reintentar</button></div> : null}
          {selectedModel && !selectedModel.available && !modelsLoading && !modelsError && <div className="composer-hint" role="status">{selectedModel.unavailableReason}</div>}
          {selectedModel?.available && !selectedModel.supportsImages && <div className="composer-hint" role="status">{selectedModel.imageUnavailableReason ?? 'Este modelo solo admite mensajes de texto.'}</div>}
          <div className={`composer ${isGenerating ? 'composer-generating' : ''}`}>
            {attachments.length > 0 && <div className="attachment-list" aria-label="Imágenes preparadas para enviar">
              {attachments.map((image) => <figure className="attachment-card" key={image.id}>
                <img src={image.previewUrl} alt={`Vista previa: ${image.fileName}`} />
                <figcaption title={image.fileName}>{image.fileName}</figcaption>
                <button type="button" onClick={() => removeAttachment(image.id)} aria-label={`Quitar ${image.fileName}`}><X size={14} /></button>
              </figure>)}
            </div>}
            <textarea ref={composerRef} rows={1} value={draft} onChange={(event) => setDraft(event.target.value)}
              onPaste={onPasteImages}
              onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); sendMessage() } }}
              aria-label="Escribe una pregunta sobre tus plantas" aria-describedby="composer-help"
              placeholder="Escribe tu pregunta sobre plantas…" disabled={isGenerating || !selectedModel?.available} />
            <div className="composer-bottom">
              <span id="composer-help">Enter para enviar · Shift + Enter para nueva línea</span>
              <div className="composer-controls">
                <input ref={fileInputRef} className="sr-only" type="file" accept="image/jpeg,image/png,image/webp,image/gif" multiple
                  aria-label="Seleccionar imágenes" onChange={(event) => { void addImageFiles(Array.from(event.target.files ?? [])); event.currentTarget.value = '' }} />
                <button className="attach-button" type="button" onClick={() => fileInputRef.current?.click()}
                  disabled={!selectedModel?.supportsImages || isGenerating || isPreparingImages || attachments.length >= MAX_IMAGE_COUNT}
                  aria-label="Adjuntar imágenes" title={selectedModel?.supportsImages ? 'Adjuntar imágenes' : selectedModel?.imageUnavailableReason ?? 'El modelo no admite imágenes'}>
                  <Paperclip size={17} aria-hidden="true" />
                </button>
                {isGenerating && <span className="generating-label"><span className="mini-spinner" /> Generando respuesta</span>}
                {isGenerating ? (
                  <button className="send-button stop-button" type="button" onClick={stopGeneration} aria-label="Detener generación"><span className="stop-square" /></button>
                ) : (
                  <button className="send-button" type="button" onClick={() => sendMessage()} disabled={(!draft.trim() && attachments.length === 0) || !selectedModel?.available || (attachments.length > 0 && !selectedModel.supportsImages) || isPreparingImages} aria-label="Enviar mensaje"><Send size={17} /></button>
                )}
              </div>
            </div>
          </div>
          {isPreparingImages && <p className="composer-hint" role="status">Validando y preparando imágenes…</p>}
          {selectedModel?.supportsImages && !isPreparingImages && <p className="attachment-help">Adjunta, arrastra o pega con Ctrl/Cmd + V · máximo 5, 15 MiB cada una.</p>}
          {selectedModel?.supportsImages && <p className="attachment-help">{attachments.length} de {MAX_IMAGE_COUNT} imágenes preparadas</p>}
          <p className="disclaimer">Las respuestas pueden contener errores. Verifica información importante sobre toxicidad y cuidado.</p>
        </footer>
      </main>
      <div className="sr-only" aria-live="polite" aria-atomic="true">{isGenerating ? 'El asistente está generando una respuesta.' : ''}</div>
    </div>
  )
}

function MessageIcon({ active }: { active: boolean }) {
  return active ? <Leaf size={17} aria-hidden="true" /> : <MessageSquare size={17} aria-hidden="true" />
}

function TypingIndicator() {
  return <div className="typing-indicator" role="status" aria-label="Generando respuesta"><span /><span /><span /></div>
}
