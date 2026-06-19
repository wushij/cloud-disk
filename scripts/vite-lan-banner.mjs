/**
 * Vite 启动时输出应用名称
 * 被 frontend/vite.config.ts 和 mobile/vite.config.ts 引用
 */

export function lanAccessBanner(label) {
  return {
    name: 'lan-access-banner',
    apply: 'serve',
    configureServer(server) {
      server.httpServer?.once('listening', () => {
        const c = (code) => `\x1b[${code}m`
        const reset = c(0)
        const bold = c(1)
        const cyan = c(36)
        console.log('')
        console.log(`  ${bold}${cyan}${label}${reset}`)
        console.log('')
      })
    }
  }
}
