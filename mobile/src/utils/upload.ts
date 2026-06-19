import { uploadFile } from '@/api/http'

export async function uploadSimpleFile(
  filePath: string,
  fileName: string,
  folderId: number,
  onProgress?: (ratio: number) => void
) {
  return uploadFile({
    url: '/api/files/simple',
    filePath,
    name: 'file',
    formData: {
      folderId: String(folderId)
    },
    onProgress
  })
}
