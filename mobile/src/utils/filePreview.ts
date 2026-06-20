const TEXT_EXTENSIONS = new Set([
  'txt', 'md', 'log', 'csv', 'json', 'xml', 'yaml', 'yml', 'ini', 'properties',
  'html', 'htm', 'css', 'js', 'ts', 'jsx', 'tsx', 'vue', 'java', 'py', 'go', 'rs',
  'c', 'cpp', 'h', 'hpp', 'cs', 'sql', 'sh', 'bat', 'conf', 'cfg', 'toml', 'svg'
])

export function isTextFile(mime?: string | null, name?: string): boolean {
  const m = (mime || '').toLowerCase()
  if (m.startsWith('text/')) return true
  if (m.includes('json') || m.includes('xml') || m.includes('javascript')) return true
  const ext = (name || '').split('.').pop()?.toLowerCase() || ''
  return TEXT_EXTENSIONS.has(ext)
}
