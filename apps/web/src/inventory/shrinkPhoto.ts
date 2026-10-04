/** Longest side of a photo once shrunk: enough to tell foods apart, small enough to send quickly. */
const MAX_SIDE = 1280

/**
 * A smaller JPEG copy of a photo, so that what is sent is a fraction of what a phone camera takes. Where the
 * browser cannot draw it, the photo goes as it is (the server takes up to 5 MB).
 */
export async function shrinkPhoto(photo: File): Promise<Blob> {
  if (typeof createImageBitmap !== 'function') return photo
  try {
    const bitmap = await createImageBitmap(photo)
    const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(bitmap.width * scale)
    canvas.height = Math.round(bitmap.height * scale)
    canvas.getContext('2d')?.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    bitmap.close()
    const shrunk = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.85))
    return shrunk ?? photo
  } catch {
    return photo
  }
}
