import { ref, watch, type MaybeRefOrGetter, toValue } from 'vue'
import { loadEntityAvatarThumb, cacheEntityAvatarFromUrl } from '@/utils/entityAvatarCache'

export function useCachedEntityAvatar(
  cacheKey: MaybeRefOrGetter<string>,
  src: MaybeRefOrGetter<string>,
  version: MaybeRefOrGetter<number | string> = 0
) {
  const displaySrc = ref('')

  watch(
    () => [toValue(cacheKey), toValue(src), toValue(version)] as const,
    ([key, url, v]) => {
      if (!url) {
        displaySrc.value = ''
        return
      }
      // 当前登录用户头像走 auth 缓存，无需 entity 缓存 key
      if (!key) {
        displaySrc.value = url
        return
      }
      const cached = loadEntityAvatarThumb(key, v)
      displaySrc.value = cached || url
      if (!cached) {
        void cacheEntityAvatarFromUrl(key, v, url)
          .then((data) => {
            displaySrc.value = data
          })
          .catch(() => {})
      }
    },
    { immediate: true }
  )

  return displaySrc
}
