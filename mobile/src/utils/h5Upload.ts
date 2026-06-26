export interface H5PickedFile {
  path: string
  name: string
  size: number
  mimeType: string
  file: File
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

/** 规范化 H5 选中的 File（补全扩展名，保留原生 File 引用供上传使用） */
export function normalizeH5Pick(file: File & { path?: string }): H5PickedFile {
  let name = (file.name || '').trim() || 'file'
  if (!/\.\w{2,5}$/i.test(name)) {
    const ext =
      MIME_EXT[file.type] ||
      (file.type.startsWith('video/') ? 'mp4' : file.type.startsWith('image/') ? 'jpg' : '')
    if (ext) name = `${name}.${ext}`
  }
  const path = file.path || URL.createObjectURL(file)
  return {
    path,
    name,
    size: file.size || 0,
    mimeType: file.type || '',
    file
  }
}
