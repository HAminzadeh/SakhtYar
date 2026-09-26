import type { ThemeConfig } from 'antd'

export const sakhtYarAntTheme: ThemeConfig = {
  token: {
    borderRadius: 10,
    fontFamily: '"Vazirmatn", "IRANSansX", Tahoma, Arial, system-ui, sans-serif',
  },
  components: {
    Button: { controlHeight: 40 },
    Input: { controlHeight: 40 },
    Select: { controlHeight: 40 },
  },
}
