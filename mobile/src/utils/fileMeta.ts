/** 转码状态中文标签（与 PC 端一致） */
export function transcodeLabel(status?: string): string {
  switch (status) {
    case 'PENDING':
    case 'PROCESSING':
      return '转码中'
    case 'DONE':
      return '已转码'
    case 'FAILED':
      return '转码失败'
    default:
      return ''
  }
}
