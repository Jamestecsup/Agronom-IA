const DATABASE_NAME = 'agronom-ia-images-v1'
const STORE_NAME = 'attachments'

interface StoredImage {
  id: string
  conversationId: string
  messageId: string
  file: File
}

function database(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE_NAME, 1)
    request.onupgradeneeded = () => {
      const store = request.result.createObjectStore(STORE_NAME, { keyPath: 'id' })
      store.createIndex('conversationId', 'conversationId', { unique: false })
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error ?? new Error('No se pudo abrir el almacenamiento local de imágenes.'))
  })
}

export async function saveImageAttachments(conversationId: string, messageId: string, images: { id: string; file: File }[]): Promise<void> {
  if (!images.length || !('indexedDB' in globalThis)) return
  const db = await database()
  try {
    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction(STORE_NAME, 'readwrite')
      images.forEach((image) => transaction.objectStore(STORE_NAME).put({ ...image, conversationId, messageId } satisfies StoredImage))
      transaction.oncomplete = () => resolve()
      transaction.onerror = () => reject(transaction.error ?? new Error('No se pudieron guardar las imágenes de la conversación.'))
      transaction.onabort = () => reject(transaction.error ?? new Error('Se canceló el guardado local de las imágenes.'))
    })
  } finally { db.close() }
}

export async function loadImageAttachments(ids: string[]): Promise<Map<string, File>> {
  const found = new Map<string, File>()
  if (!ids.length || !('indexedDB' in globalThis)) return found
  const db = await database()
  try {
    await Promise.all(ids.map(async (id) => {
      const file = await new Promise<File | null>((resolve, reject) => {
        const request = db.transaction(STORE_NAME, 'readonly').objectStore(STORE_NAME).get(id)
        request.onsuccess = () => resolve((request.result as StoredImage | undefined)?.file ?? null)
        request.onerror = () => reject(request.error ?? new Error('No se pudo recuperar una imagen.'))
      })
      if (file) found.set(id, file)
    }))
  } finally { db.close() }
  return found
}

export async function deleteConversationImages(conversationId: string): Promise<void> {
  if (!('indexedDB' in globalThis)) return
  const db = await database()
  try {
    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction(STORE_NAME, 'readwrite')
      const store = transaction.objectStore(STORE_NAME)
      const request = store.index('conversationId').openKeyCursor(IDBKeyRange.only(conversationId))
      request.onsuccess = () => {
        const cursor = request.result
        if (!cursor) return
        store.delete(cursor.primaryKey)
        cursor.continue()
      }
      transaction.oncomplete = () => resolve()
      transaction.onerror = () => reject(transaction.error ?? new Error('No se pudieron eliminar las imágenes locales.'))
    })
  } finally { db.close() }
}
