export type Role = 'user' | 'assistant'

export interface ChatMessage {
  id: string
  role: Role
  content: string
  modelId?: string
  createdAt: number
  error?: boolean
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
}
