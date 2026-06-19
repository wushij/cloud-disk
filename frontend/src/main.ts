import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/theme.css'
import './styles/layout-sidebar.css'
import './styles/layout-page.css'
import './styles/page-shell.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { setupGlobalErrorHandlers } from '@/plugins/error-handler'

const app = createApp(App)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
const pinia = createPinia()
app.use(pinia)
useAuthStore(pinia).restore()
useThemeStore(pinia).init()
app.use(router)
app.use(ElementPlus, { locale: zhCn })

setupGlobalErrorHandlers(app, router)

app.mount('#app')
