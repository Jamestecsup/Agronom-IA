export type Role = 'user' | 'assistant'

export interface ImagePreview {
  id: string
  fileName: string
  mimeType: string
  size: number
  previewUrl: string
}

export interface ChatMessage {
  id: string
  role: Role
  content: string
  modelId?: string
  createdAt: number
  error?: boolean
  images?: ImagePreview[]
}

export interface Conversation {
  id: string
  title: string
  providerId: string
  updatedAt: number
  messages: ChatMessage[]
}

export interface ModelOption {
  id: string
  name: string
  provider: string
  modelId: string
  available: boolean
  unavailableReason: string | null
  welcomeMessage: string
  supportsImages: boolean
  imageUnavailableReason: string | null
}
