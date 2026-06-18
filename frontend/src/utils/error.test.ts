import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import { getApiErrorMessage, showErrorToast, toUserMessage } from '@/utils/error'

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn() }
}))

describe('error utils', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('reads backend Chinese error field', () => {
    const err = {
      isAxiosError: true,
      response: { status: 400, data: { error: '用户名或密码错误', code: 'BUSINESS_ERROR' } }
    }
    vi.spyOn(axios, 'isAxiosError').mockReturnValue(true)
    expect(getApiErrorMessage(err)).toBe('用户名或密码错误')
  })

  it('maps Network Error to Chinese', () => {
    expect(toUserMessage('Network Error')).toContain('网络')
  })

  it('maps axios status code message to Chinese', () => {
    expect(toUserMessage('Request failed with status code 404')).toBe('请求的资源不存在')
  })

  it('hides raw English technical messages', () => {
    expect(toUserMessage('Unexpected token in JSON')).toBe('操作失败')
  })

  it('dedupes toast within window', async () => {
    const { ElMessage } = await import('element-plus')
    showErrorToast('重复错误')
    showErrorToast('重复错误')
    expect(ElMessage.error).toHaveBeenCalledTimes(1)
  })
})
