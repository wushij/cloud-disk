import { request } from './http'

export const officeApi = {
  // 获取 OnlyOffice 协同编辑与预览配置
  config: (fileId: number, mode?: string) =>
    request<{ documentServerUrl: string; config: Record<string, unknown> }>({
      url: `/api/files/${fileId}/onlyoffice`,
      data: { mode }
    }),
}
