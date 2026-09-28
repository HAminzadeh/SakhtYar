import type { ThemeConfig } from 'antd'

export const sakhtYarAntTheme: ThemeConfig = {
  token: {
    colorPrimary: '#2563EB',
    colorSuccess: '#16A34A',
    colorWarning: '#D97706',
    colorError: '#DC2626',
    colorBgLayout: '#F6F8FC',
    colorBgContainer: '#FFFFFF',
    colorText: '#101828',
    colorTextSecondary: '#667085',
    colorBorder: '#E4E7EC',
    borderRadius: 10,
    controlHeight: 40,
    fontFamily: '"Vazirmatn", "IRANSansX", Tahoma, Arial, system-ui, sans-serif',
  },
  components: {
    Button: {
      controlHeight: 40,
      borderRadius: 9,
      fontWeight: 700,
    },
    Input: {
      controlHeight: 40,
      borderRadius: 9,
    },
    Select: {
      controlHeight: 40,
      borderRadius: 9,
    },
    Card: {
      borderRadiusLG: 14,
    },
    Modal: {
      borderRadiusLG: 16,
    },
  },
}