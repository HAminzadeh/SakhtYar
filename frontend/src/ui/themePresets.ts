import { theme as antdTheme, type ThemeConfig } from 'antd'

export type ThemeCode =
  | 'SYSTEM'
  | 'LIGHT'
  | 'DARK'
  | 'OCEAN'
  | 'EMERALD'
  | 'SUNSET'
  | 'MIDNIGHT'

export type ResolvedTheme = Exclude<ThemeCode, 'SYSTEM'>

export const themeChoices: Array<{
  code: ThemeCode
  labelFa: string
  labelEn: string
  swatches: [string, string, string]
}> = [
  { code: 'SYSTEM', labelFa: 'سیستم', labelEn: 'System', swatches: ['#2563EB', '#F3F7FC', '#FFFFFF'] },
  { code: 'LIGHT', labelFa: 'روشن', labelEn: 'Light', swatches: ['#2563EB', '#F7FAFF', '#FFFFFF'] },
  { code: 'DARK', labelFa: 'تیره', labelEn: 'Dark', swatches: ['#60A5FA', '#0B1220', '#111827'] },
  { code: 'OCEAN', labelFa: 'اقیانوس', labelEn: 'Ocean', swatches: ['#0284C7', '#EFFAFF', '#FFFFFF'] },
  { code: 'EMERALD', labelFa: 'زمردی', labelEn: 'Emerald', swatches: ['#059669', '#F0FDF8', '#FFFFFF'] },
  { code: 'SUNSET', labelFa: 'غروب', labelEn: 'Sunset', swatches: ['#EA580C', '#FFF7ED', '#FFFFFF'] },
  { code: 'MIDNIGHT', labelFa: 'نیمه‌شب', labelEn: 'Midnight', swatches: ['#8B5CF6', '#090A14', '#121425'] },
]

const baseFont =
  '"B Nazanin Local", "B Nazanin", "Vazirmatn", Tahoma, Arial, sans-serif'

export function resolveTheme(code: ThemeCode, systemDark: boolean): ResolvedTheme {
  return code === 'SYSTEM' ? (systemDark ? 'DARK' : 'LIGHT') : code
}

export function buildAntTheme(theme: ResolvedTheme): ThemeConfig {
  const dark = theme === 'DARK' || theme === 'MIDNIGHT'
  const palette: Record<ResolvedTheme, {
    primary: string
    info: string
    bg: string
    container: string
    text: string
    secondary: string
    border: string
  }> = {
    LIGHT: { primary:'#2563EB',info:'#2563EB',bg:'#F3F7FC',container:'#FFFFFF',text:'#10203B',secondary:'#6B7A90',border:'#E2E8F0' },
    DARK: { primary:'#60A5FA',info:'#60A5FA',bg:'#0B1220',container:'#111827',text:'#E5ECF7',secondary:'#94A3B8',border:'#293548' },
    OCEAN: { primary:'#0284C7',info:'#0EA5E9',bg:'#EFFAFF',container:'#FFFFFF',text:'#123247',secondary:'#52758A',border:'#CFEAF4' },
    EMERALD: { primary:'#059669',info:'#10B981',bg:'#F0FDF8',container:'#FFFFFF',text:'#123D32',secondary:'#5E7F75',border:'#D3EEE4' },
    SUNSET: { primary:'#EA580C',info:'#F97316',bg:'#FFF7ED',container:'#FFFFFF',text:'#4A2B1D',secondary:'#856959',border:'#F4D8C2' },
    MIDNIGHT: { primary:'#8B5CF6',info:'#A78BFA',bg:'#090A14',container:'#121425',text:'#F2EEFF',secondary:'#AAA2C4',border:'#2B2A45' },
  }
  const p = palette[theme]
  return {
    algorithm: dark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: {
      colorPrimary:p.primary,colorInfo:p.info,colorSuccess:'#16A34A',colorWarning:'#D97706',
      colorError:'#DC2626',colorBgLayout:p.bg,colorBgContainer:p.container,colorText:p.text,
      colorTextSecondary:p.secondary,colorBorder:p.border,borderRadius:11,borderRadiusLG:18,
      controlHeight:44,controlHeightLG:50,fontSize:16,fontFamily:baseFont,
    },
    components: {
      Button:{borderRadius:11,fontWeight:700},
      Card:{borderRadiusLG:18},
      Input:{borderRadius:11},
      InputNumber:{borderRadius:11},
      Select:{borderRadius:11},
      Modal:{borderRadiusLG:20},
      Table:{headerBg:dark?'#171B2C':undefined,rowHoverBg:dark?'#171B2C':undefined,borderColor:p.border},
      Tabs:{horizontalItemGutter:14,itemSelectedColor:p.primary,itemHoverColor:p.primary,inkBarColor:p.primary},
    },
  }
}