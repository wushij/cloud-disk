import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import uviewPlus from 'uview-plus'
import App from './App.vue'
import { setupGlobalErrorHandlers } from '@/utils/errorHandler'

export function createApp() {
  const app = createSSRApp(App)
  app.use(createPinia())
  app.use(uviewPlus)
  setupGlobalErrorHandlers(app)
  return { app }
}
