import { sm3, sm4 } from 'sm-crypto'

/** 获取密码学安全随机字节（仅用 Web Crypto，拒绝 Math.random 降级） */
function getCryptoBytes(len: number): Uint8Array {
  const bytes = new Uint8Array(len)
  if (typeof window !== 'undefined' && window.crypto?.getRandomValues) {
    window.crypto.getRandomValues(bytes)
  } else if (typeof globalThis.crypto?.getRandomValues === 'function') {
    globalThis.crypto.getRandomValues(bytes)
  } else {
    // 明确抛出而非降级到不安全的 Math.random
    throw new Error('当前环境不支持 crypto.getRandomValues，无法生成密码学安全随机数')
  }
  return bytes
}

/**
 * 生成 32 位密码学安全随机 Nonce 字符串（Hex）
 */
export function generateNonce(): string {
  const hex = '0123456789abcdef'
  const bytes = getCryptoBytes(32)
  let nonce = ''
  for (let i = 0; i < 32; i++) nonce += hex[bytes[i] % 16]
  return nonce
}

/**
 * 获取当前毫秒时间戳字符串
 */
export function getTimestamp(): string {
  return Date.now().toString()
}

/**
 * 生成 16 字节（32位 Hex）随机 IV 向量
 */
function generateRandomIvHex(): string {
  const bytes = getCryptoBytes(16)
  let hexStr = ''
  for (let i = 0; i < 16; i++) hexStr += bytes[i].toString(16).padStart(2, '0')
  return hexStr
}

/**
 * 将 SM4 密钥规范化为 32 字符 Hex（16 字节）格式。
 * 若已是 32 位 Hex 直接使用；否则视为 UTF-8 字符串，按字节转 Hex。
 */
function ensureSm4HexKey(rawKey: string): string {
  if (rawKey.length === 32 && /^[0-9a-fA-F]{32}$/.test(rawKey)) return rawKey
  let hex = ''
  for (let i = 0; i < rawKey.length; i++) {
    hex += rawKey.charCodeAt(i).toString(16).padStart(2, '0')
  }
  return hex
}

/** 带认证标签的 SM4-CBC 加密格式前缀（与后端 ApiSecurityFilter 保持一致） */
const SM4_AUTH_PREFIX = 'V1'

/**
 * 计算 HMAC-SM3 十六进制摘要。
 * @param content 待签名内容（UTF-8 字符串）
 * @param keyHex 32 位 Hex 密钥（16 字节），与后端 SM4 密钥一致
 */
function hmacSm3Hex(content: string, keyHex: string): string {
  return sm3(content, { key: keyHex })
}

/**
 * SM4 加密（CBC 模式 + 密码学安全随机 IV + PKCS7Padding + HMAC-SM3 完整性标签）
 * 输出格式：V1 + IV(32位Hex) + MAC(64位Hex) + SM4_CBC_Cipher(Hex)
 * MAC = HMAC-SM3(IV_hex + Cipher_hex, sm4Key)，用于服务端校验密文未被篡改。
 */
export function encryptSm4(plainText: string, secretKey: string): string {
  if (!plainText) return ''
  if (!secretKey) throw new Error('SM4 加密失败：密钥未下发')
  const hexKey = ensureSm4HexKey(secretKey)
  const hexIv = generateRandomIvHex()
  const cipherHex = sm4.encrypt(plainText, hexKey, {
    mode: 'cbc',
    iv: hexIv,
    padding: 'pkcs#7',
  } as any)
  const mac = hmacSm3Hex(hexIv + cipherHex, hexKey)
  return SM4_AUTH_PREFIX + hexIv + mac + cipherHex
}

/**
 * SM4 解密（仅支持 CBC 模式，不做 ECB 降级）
 * 输入支持两种格式：
 *  - 新版带认证标签：V1 + IV(32) + MAC(64) + Cipher(Hex)，先解密再校验 HMAC-SM3
 *  - 旧版兼容：IV(32) + Cipher(Hex)
 * CBC 失败或完整性校验失败时抛出异常，不静默返回原文（避免把密文当明文传给下游）
 */
export function decryptSm4(cipherText: string, secretKey: string): string {
  if (!cipherText) return ''
  if (!secretKey) throw new Error('SM4 解密失败：密钥未下发')

  const hexKey = ensureSm4HexKey(secretKey)
  const text = cipherText.trim()

  let hexIv: string
  let rawCipher: string

  if (text.startsWith(SM4_AUTH_PREFIX)) {
    // 新版带认证标签：V1 + IV(32) + MAC(64) + Cipher（V1 前缀非 hex，必须先剥离再校验）
    const body = text.substring(SM4_AUTH_PREFIX.length)
    if (body.length <= 32 + 64) {
      throw new Error('SM4 解密失败：认证密文长度非法')
    }
    hexIv = body.substring(0, 32)
    const macReceived = body.substring(32, 32 + 64)
    rawCipher = body.substring(32 + 64)
    if (!rawCipher || !/^[0-9a-fA-F]+$/.test(rawCipher)) {
      throw new Error('SM4 解密失败：认证密文正文格式非法')
    }
    // 校验完整性标签，防止 CBC bit-flipping 篡改
    const macExpected = hmacSm3Hex(hexIv + rawCipher, hexKey)
    if (macReceived.toLowerCase() !== macExpected.toLowerCase()) {
      throw new Error('SM4 解密失败：完整性校验失败，数据可能已被篡改')
    }
  } else {
    // 旧版兼容：IV(32) + Cipher
    if (text.length <= 32 || !/^[0-9a-fA-F]+$/.test(text)) {
      throw new Error('SM4 解密失败：密文格式非法（非 IV+HexCipher 格式）')
    }
    hexIv = text.substring(0, 32)
    rawCipher = text.substring(32)
  }

  const decrypted = sm4.decrypt(rawCipher, hexKey, {
    mode: 'cbc',
    iv: hexIv,
    padding: 'pkcs#7',
  } as any)

  if (!decrypted && decrypted !== '') {
    throw new Error('SM4-CBC 解密结果为空')
  }
  return decrypted
}

/**
 * HMAC-SM3 签名
 */
export function signHmacSm3(content: string, signKey: string): string {
  if (!content || !signKey) return ''
  // 与后端验签保持一致：signKey 为后端签发的 64 位 Hex 会话临时密钥。
  // ensureSm4HexKey 会把 64 Hex 转成 128 Hex（即原始 64 字符的 ASCII 码 hex），
  // sm-crypto 的 sm3(content, { key }) 把该 hex 解码为字节数组 = 后端 signKey.getBytes(UTF_8) 的 ASCII 字节，
  // 两者完全一致，故不能去掉 ensureSm4HexKey。
  const hexKey = ensureSm4HexKey(signKey)
  return sm3(content, { key: hexKey })
}
