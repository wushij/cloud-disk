import SparkMD5 from 'spark-md5'

const CHUNK_SIZE = 2 * 1024 * 1024

export function calcFileMd5(file: File, onProgress?: (ratio: number) => void): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunks = Math.ceil(file.size / CHUNK_SIZE)
    let current = 0
    const spark = new SparkMD5.ArrayBuffer()
    const reader = new FileReader()

    reader.onload = (e) => {
      spark.append(e.target?.result as ArrayBuffer)
      current++
      onProgress?.(current / chunks)
      if (current < chunks) loadNext()
      else resolve(spark.end())
    }
    reader.onerror = () => reject(new Error('MD5 计算失败'))

    function loadNext() {
      const start = current * CHUNK_SIZE
      const end = Math.min(start + CHUNK_SIZE, file.size)
      reader.readAsArrayBuffer(file.slice(start, end))
    }
    loadNext()
  })
}

export function fmtSize(n: number): string {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}

export function pickChunkSize(fileSize: number): number {
  const MB = 1024 * 1024
  if (fileSize > 12 * 1024 * MB) return 32 * MB
  if (fileSize > 3 * 1024 * MB) return 16 * MB
  return 8 * MB
}
