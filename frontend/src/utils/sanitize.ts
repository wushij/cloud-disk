export function sanitizeHighlight(html: string | null | undefined): string {
  if (!html) return ''
  return html
    .replace(/<em[^>]*>/gi, '__EM_START__')
    .replace(/<\/em>/gi, '__EM_END__')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
    .replace(/__EM_START__/g, '<em class="highlight">')
    .replace(/__EM_END__/g, '</em>')
}
