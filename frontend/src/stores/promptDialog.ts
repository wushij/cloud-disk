import { ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'

export interface PromptDialogOptions {
  title: string
  message?: string
  placeholder?: string
  confirmText?: string
  defaultValue?: string
  maxlength?: number
  required?: boolean
  icon?: 'folder' | 'edit'
}

export const usePromptDialogStore = defineStore('promptDialog', () => {
  const visible = ref(false)
  const title = ref('')
  const message = ref('')
  const placeholder = ref('请输入')
  const confirmText = ref('确定')
  const maxlength = ref(64)
  const required = ref(true)
  const icon = ref<'folder' | 'edit'>('folder')
  const value = ref('')

  let resolver: ((result: string | null) => void) | null = null

  function open(options: PromptDialogOptions): Promise<string | null> {
    title.value = options.title
    message.value = options.message ?? ''
    placeholder.value = options.placeholder ?? '请输入'
    confirmText.value = options.confirmText ?? '确定'
    maxlength.value = options.maxlength ?? 64
    required.value = options.required ?? true
    icon.value = options.icon ?? 'folder'
    value.value = options.defaultValue ?? ''
    visible.value = true

    return new Promise((resolve) => {
      resolver = resolve
    })
  }

  function finish(result: string | null) {
    visible.value = false
    resolver?.(result)
    resolver = null
  }

  function confirm() {
    const trimmed = value.value.trim()
    if (required.value && !trimmed) {
      ElMessage.warning('请输入内容')
      return
    }
    finish(trimmed || null)
  }

  function cancel() {
    finish(null)
  }

  return {
    visible,
    title,
    message,
    placeholder,
    confirmText,
    maxlength,
    icon,
    value,
    open,
    confirm,
    cancel
  }
})
