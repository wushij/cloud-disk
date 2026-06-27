export interface H5PickedFile {
  path: string
  name: string
  size: number
  mimeType: string
  /** 原生 File；部分 H5 环境只有 blob path，此时为 undefined */
  file?: File
}

/** uni.chooseFile 在 H5 上常返回 { path, name, size, type, file?: File } */
export type H5ChooseFileItem = {
  path?: string
  name?: string
  size?: number
  type?: string
  file?: File
}

const MIME_EXT: Record<string, string> = {
  'video/mp4': 'mp4',
  'video/webm': 'webm',
  'video/quicktime': 'mov',
  'video/x-matroska': 'mkv',
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/gif': 'gif',
  'image/webp': 'webp',
  'image/bmp': 'bmp'
}

function resolveNativeFile(item: H5ChooseFileItem | File): File | undefined {
  if (item instanceof File) return item
  if (item.file instanceof File) return item.file
  return undefined
}

/** 规范化 H5 选中的文件（与 cafd240 一致：优先 blob path + fetch 上传） */
export function normalizeH5Pick(item: H5ChooseFileItem | File): H5PickedFile {
  const native = resolveNativeFile(item)
  const meta = item instanceof File ? item : item

  let name = (native?.name || meta.name || '').trim() || 'file'
  const mimeType = native?.type || meta.type || ''
  const size = native?.size ?? meta.size ?? 0

  if (!/\.\w{2,5}$/i.test(name)) {
    const ext =
      MIME_EXT[mimeType] ||
      (mimeType.startsWith('video/') ? 'mp4' : mimeType.startsWith('image/') ? 'jpg' : '')
    if (ext) name = `${name}.${ext}`
  }

  const path =
    (!(item instanceof File) && meta.path) ||
    (native ? URL.createObjectURL(native) : '')

  if (!path) {
    throw new Error('无法读取所选文件')
  }

  return { path, name, size, mimeType, file: native }
}
