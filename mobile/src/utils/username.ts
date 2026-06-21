export const USERNAME_REG = /^[a-zA-Z0-9_]{4,12}$/

export const USERNAME_HINT = '4-12 位字母、数字或下划线'

/** 注册用户名校验，通过返回 null，失败返回错误文案 */
export function validateRegisterUsername(username: string): string | null {
  const u = username.trim()
  if (!u) return '用户名不能为空'
  if (u.length < 4 || u.length > 12) return '用户名长度 4-12 位'
  if (!USERNAME_REG.test(u)) return '用户名只能包含字母、数字和下划线'
  return null
}
