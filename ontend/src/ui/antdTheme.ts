import type { ThemeConfig } from 'antd'

export const sakhtYarAntTheme: ThemeConfig = {
  token: {
    colorPrimary: '#2563EB',
    colorInfo: '#2563EB',
    colorSuccess: '#16A34A',
    colorWarning: '#D97706',
    colorError: '#DC2626',
    colorBgLayout: '#F3F7FC',
    colorBgContainer: '#FFFFFF',
    colorText: '#10203B',
    colorTextSecondary: '#6B7A90',
    colorBorder: '#E2E8F0',
    borderRadius: 11,
    borderRadiusLG: 18,
    controlHeight: 44,
    controlHeightLG: 50,
    fontSize: 16,
    fontFamily: '"B Nazanin Local", "B Nazanin", "Vazirmatn", Tahoma, Arial, sans-serif',
  },
  components: {
    Button: { borderRadius: 11, fontWeight: 700 },
    Card: { borderRadiusLG: 18 },
    Input: { borderRadius: 11 },
    InputNumber: { borderRadius: 11 },
    Select: { borderRadius: 11 },
    Modal: { borderRadiusLG: 20 },
    Table: { headerBg: '#F5F8FC', headerColor: '#304665', rowHoverBg: '#F8FBFF', borderColor: '#E8EDF4' },
    Tabs: { horizontalItemGutter: 14, itemSelectedColor: '#1D4ED8', itemHoverColor: '#2563EB', inkBarColor: '#2563EB' },
  },
}
