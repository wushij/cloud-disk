export type ThemePresetId =
  | 'slate'
  | 'indigo'
  | 'ocean'
  | 'teal'
  | 'emerald'
  | 'amber'
  | 'rose'
  | 'violet'

export type ThemeSelection = ThemePresetId | 'custom'

export interface ThemePreset {
  id: ThemePresetId
  label: string
  primary: string
  primaryHover: string
  primaryActive: string
  bg: string
  sidebarBg: string
}

export const THEME_PRESETS: ThemePreset[] = [
  {
    id: 'slate',
    label: '石墨',
    primary: '#010710',
    primaryHover: '#0f1a2e',
    primaryActive: '#000000',
    bg: '#f3f5f8',
    sidebarBg: '#ffffff'
  },
  {
    id: 'indigo',
    label: '靛蓝',
    primary: '#4f46e5',
    primaryHover: '#4338ca',
    primaryActive: '#3730a3',
    bg: '#f5f3ff',
    sidebarBg: '#ffffff'
  },
  {
    id: 'ocean',
    label: '海蓝',
    primary: '#2563eb',
    primaryHover: '#1d4ed8',
    primaryActive: '#1e40af',
    bg: '#eff6ff',
    sidebarBg: '#ffffff'
  },
  {
    id: 'teal',
    label: '青绿',
    primary: '#0d9488',
    primaryHover: '#0f766e',
    primaryActive: '#115e59',
    bg: '#f0fdfa',
    sidebarBg: '#ffffff'
  },
  {
    id: 'emerald',
    label: '翠绿',
    primary: '#059669',
    primaryHover: '#047857',
    primaryActive: '#065f46',
    bg: '#ecfdf5',
    sidebarBg: '#ffffff'
  },
  {
    id: 'amber',
    label: '琥珀',
    primary: '#d97706',
    primaryHover: '#b45309',
    primaryActive: '#92400e',
    bg: '#fffbeb',
    sidebarBg: '#ffffff'
  },
  {
    id: 'rose',
    label: '玫红',
    primary: '#e11d48',
    primaryHover: '#be123c',
    primaryActive: '#9f1239',
    bg: '#fff1f2',
    sidebarBg: '#ffffff'
  },
  {
    id: 'violet',
    label: '紫韵',
    primary: '#7c3aed',
    primaryHover: '#6d28d9',
    primaryActive: '#5b21b6',
    bg: '#f5f3ff',
    sidebarBg: '#ffffff'
  }
]

export const DEFAULT_THEME_ID: ThemePresetId = 'slate'

const THEME_MAP = Object.fromEntries(THEME_PRESETS.map((p) => [p.id, p])) as Record<
  ThemePresetId,
  ThemePreset
>

function hexToRgb(hex: string) {
  const h = hex.replace('#', '')
  const full = h.length === 3 ? h.split('').map((c) => c + c).join('') : h
  const n = parseInt(full, 16)
  return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 }
}

function clampChannel(value: number) {
  return Math.max(0, Math.min(255, Math.round(value)))
}

function rgbToHex(r: number, g: number, b: number) {
  return (
    '#' +
    [r, g, b]
      .map((v) => clampChannel(v).toString(16).padStart(2, '0'))
      .join('')
  )
}

export function normalizeHex(input: string, fallback = '#010710') {
  let hex = input.trim()
  if (!hex.startsWith('#')) hex = `#${hex}`
  if (/^#[0-9a-fA-F]{3}$/.test(hex)) {
    hex = `#${hex
      .slice(1)
      .split('')
      .map((c) => c + c)
      .join('')}`
  }
  if (!/^#[0-9a-fA-F]{6}$/.test(hex)) return fallback
  return hex.toLowerCase()
}

function rgba(hex: string, alpha: number) {
  const { r, g, b } = hexToRgb(hex)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

function darken(hex: string, ratio: number) {
  const { r, g, b } = hexToRgb(hex)
  const factor = 1 - ratio
  return rgbToHex(r * factor, g * factor, b * factor)
}

function tintBg(hex: string) {
  const { r, g, b } = hexToRgb(hex)
  const ratio = 0.93
  return rgbToHex(
    r + (255 - r) * ratio,
    g + (255 - g) * ratio,
    b + (255 - b) * ratio
  )
}

interface ThemeVars {
  id: string
  primary: string
  primaryHover: string
  primaryActive: string
  bg: string
  sidebarBg: string
}

function applyThemeVars(preset: ThemeVars) {
  const root = document.documentElement

  root.dataset.theme = preset.id
  root.style.setProperty('--theme-primary', preset.primary)
  root.style.setProperty('--theme-primary-hover', preset.primaryHover)
  root.style.setProperty('--theme-primary-active', preset.primaryActive)
  root.style.setProperty('--theme-primary-muted', rgba(preset.primary, 0.08))
  root.style.setProperty('--theme-primary-muted-strong', rgba(preset.primary, 0.14))
  root.style.setProperty('--theme-bg', preset.bg)
  root.style.setProperty('--theme-sidebar-bg', preset.sidebarBg)
  root.style.setProperty('--theme-text-base', '#1e293b')
  root.style.setProperty('--theme-text-secondary', '#64748b')
  root.style.setProperty('--theme-border', '#e2e8f0')
  root.style.setProperty('--el-color-primary', preset.primary)
}

/** 将主题预设应用到 document 根节点 CSS 变量 */
export function applyThemePreset(id: ThemePresetId) {
  const preset = THEME_MAP[id] ?? THEME_MAP[DEFAULT_THEME_ID]
  applyThemeVars(preset)
}

/** 应用用户自定义主色 */
export function applyCustomTheme(primary: string) {
  const color = normalizeHex(primary)
  applyThemeVars({
    id: 'custom',
    primary: color,
    primaryHover: darken(color, 0.14),
    primaryActive: darken(color, 0.24),
    bg: tintBg(color),
    sidebarBg: '#ffffff'
  })
  return color
}

export const PRESET_COLORS = THEME_PRESETS.map((p) => p.primary)

export function getThemePreset(id: ThemePresetId) {
  return THEME_MAP[id] ?? THEME_MAP[DEFAULT_THEME_ID]
}
