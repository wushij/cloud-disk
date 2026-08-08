import http from './http'

export const officeApi = {
  // 获取 OnlyOffice 文档在线编辑器配置
  config: (fileId: number, mode?: string) =>
    http.get<Record<string, unknown>>(`/api/files/${fileId}/onlyoffice`, { params: { mode } }),
}
