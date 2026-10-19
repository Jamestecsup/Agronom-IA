import { describe, expect, it } from 'vitest'
import { detectImageMime, MAX_IMAGE_BYTES, MAX_IMAGE_COUNT, MAX_TOTAL_IMAGE_BYTES, prepareImage } from './imageAttachments'

describe('image attachment contract', () => {
  it('detects JPEG, PNG, WebP, and GIF from their byte signatures', () => {
    expect(detectImageMime(new Uint8Array([0xff, 0xd8, 0xff, 0]))).toBe('image/jpeg')
    expect(detectImageMime(new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))).toBe('image/png')
    expect(detectImageMime(new TextEncoder().encode('RIFF0000WEBP'))).toBe('image/webp')
    expect(detectImageMime(new TextEncoder().encode('GIF89a'))).toBe('image/gif')
  })

  it('rejects file types that are not supported by the provider adapters', () => {
    expect(detectImageMime(new TextEncoder().encode('BM...'))).toBeNull()
    expect(detectImageMime(new TextEncoder().encode('<svg/>'))).toBeNull()
  })

  it('uses the same upload limits as the backend', () => {
    expect(MAX_IMAGE_COUNT).toBe(5)
    expect(MAX_IMAGE_BYTES).toBe(15 * 1024 * 1024)
    expect(MAX_TOTAL_IMAGE_BYTES).toBe(50 * 1024 * 1024)
  })

  it('rejects empty files and declared MIME types that disagree with file bytes before previewing', async () => {
    await expect(prepareImage(new File([], 'empty.png', { type: 'image/png' }))).rejects.toThrow('está vacía')
    const pngBytes = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
    await expect(prepareImage(new File([pngBytes], 'disguised.jpg', { type: 'image/jpeg' })))
      .rejects.toThrow('no coincide con su contenido')
  })
})
