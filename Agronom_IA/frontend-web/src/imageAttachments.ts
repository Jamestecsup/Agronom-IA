export const MAX_IMAGE_COUNT = 5
export const MAX_IMAGE_BYTES = 15 * 1024 * 1024
export const MAX_TOTAL_IMAGE_BYTES = 50 * 1024 * 1024
const SIGNATURES: Record<string, string> = {
  'image/jpeg': 'jpeg',
  'image/png': 'png',
  'image/webp': 'webp',
  'image/gif': 'gif',
}

export interface ValidatedImage {
  id: string
  file: File
  fileName: string
  mimeType: string
  size: number
  previewUrl: string
}

export function detectImageMime(bytes: Uint8Array): string | null {
  if (bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return 'image/jpeg'
  if (bytes.length >= 8 && [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a].every((value, index) => bytes[index] === value)) return 'image/png'
  if (bytes.length >= 12 && String.fromCharCode(...bytes.slice(0, 4)) === 'RIFF' && String.fromCharCode(...bytes.slice(8, 12)) === 'WEBP') return 'image/webp'
  if (bytes.length >= 6) {
    const header = String.fromCharCode(...bytes.slice(0, 6))
    if (header === 'GIF87a' || header === 'GIF89a') return 'image/gif'
  }
  return null
}

export async function prepareImage(file: File): Promise<ValidatedImage> {
  if (file.size === 0) throw new Error(`${file.name || 'La imagen'} está vacía.`)
  if (file.size > MAX_IMAGE_BYTES) throw new Error(`${file.name || 'La imagen'} supera el límite de 15 MiB.`)
  const bytes = new Uint8Array(await file.slice(0, 16).arrayBuffer())
  const mimeType = detectImageMime(bytes)
  if (!mimeType || !SIGNATURES[mimeType]) throw new Error(`${file.name || 'El archivo'} no tiene un formato de imagen compatible. Usa JPEG, PNG, WebP o GIF.`)
  if (file.type.startsWith('image/') && file.type !== mimeType)
    throw new Error(`${file.name || 'La imagen'} declara un MIME que no coincide con su contenido.`)

  const sourceUrl = URL.createObjectURL(file)
  try {
    const image = await decodeImage(sourceUrl)
    if (image.naturalWidth < 16 || image.naturalHeight < 16 || image.naturalWidth > 4096 || image.naturalHeight > 4096)
      throw new Error(`${file.name || 'La imagen'} debe medir entre 16 y 4 096 píxeles por lado.`)
    const maxSide = 320
    const scale = Math.min(1, maxSide / Math.max(image.naturalWidth, image.naturalHeight))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(image.naturalWidth * scale))
    canvas.height = Math.max(1, Math.round(image.naturalHeight * scale))
    const context = canvas.getContext('2d')
    if (!context) throw new Error('El navegador no pudo preparar la vista previa de la imagen.')
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.drawImage(image, 0, 0, canvas.width, canvas.height)
    const previewUrl = canvas.toDataURL('image/jpeg', 0.7)
    const normalizedFile = file.type ? file : new File([file], file.name || 'imagen', { type: mimeType, lastModified: file.lastModified })
    return {
      id: globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      file: normalizedFile,
      fileName: file.name || 'imagen',
      mimeType,
      size: file.size,
      previewUrl,
    }
  } finally {
    URL.revokeObjectURL(sourceUrl)
  }
}

function decodeImage(source: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('La imagen está dañada o este navegador no puede decodificarla.'))
    image.src = source
  })
}

export async function fileToBase64(file: File): Promise<string> {
  const dataUrl = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => typeof reader.result === 'string' ? resolve(reader.result) : reject(new Error('No se pudo leer una imagen.'))
    reader.onerror = () => reject(new Error('No se pudo leer una imagen.'))
    reader.readAsDataURL(file)
  })
  const separator = dataUrl.indexOf(',')
  if (separator < 0) throw new Error('El navegador devolvió una imagen en un formato no válido.')
  return dataUrl.slice(separator + 1)
}
