import { alpha, createTheme } from '@mui/material/styles'
import { uiTokens } from './tokens'

export const appTheme = createTheme({
  direction: 'rtl',
  palette: {
    mode: 'light',
    primary: {
      main: uiTokens.color.brand,
      dark: uiTokens.color.brandDark,
      contrastText: '#FFFFFF',
    },
    secondary: {
      main: uiTokens.color.accent,
    },
    success: { main: uiTokens.color.success },
    warning: { main: uiTokens.color.warning },
    error: { main: uiTokens.color.error },
    background: {
      default: uiTokens.color.bg,
      paper: uiTokens.color.surface,
    },
    text: {
      primary: uiTokens.color.text,
      secondary: uiTokens.color.textMuted,
    },
    divider: uiTokens.color.border,
  },
  shape: {
    borderRadius: uiTokens.radius.md,
  },
  typography: {
    fontFamily:
      '"Vazirmatn", "IRANSansX", Tahoma, Arial, system-ui, -apple-system, sans-serif',
    h4: { fontWeight: 800, letterSpacing: '-0.02em' },
    h5: { fontWeight: 800, letterSpacing: '-0.015em' },
    h6: { fontWeight: 750 },
    button: { fontWeight: 700, textTransform: 'none' },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          backgroundColor: uiTokens.color.bg,
          color: uiTokens.color.text,
        },
        '*': {
          boxSizing: 'border-box',
        },
        '::selection': {
          backgroundColor: alpha(uiTokens.color.brand, 0.18),
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          border: `1px solid ${uiTokens.color.border}`,
          boxShadow: '0 1px 2px rgba(16,24,40,0.03)',
          backgroundImage: 'none',
        },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: {
          minHeight: uiTokens.control.minHeight,
          borderRadius: uiTokens.radius.sm,
          paddingInline: 16,
        },
      },
    },
    MuiTextField: {
      defaultProps: {
        size: 'small',
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderRadius: uiTokens.radius.sm,
          minHeight: uiTokens.control.minHeight,
          backgroundColor: '#FFFFFF',
          '&:hover .MuiOutlinedInput-notchedOutline': {
            borderColor: '#B9C2CF',
          },
          '&.Mui-focused': {
            boxShadow: `0 0 0 3px ${alpha(uiTokens.color.brand, 0.10)}`,
          },
        },
      },
    },
    MuiTabs: {
      styleOverrides: {
        root: {
          minHeight: 48,
        },
        indicator: {
          height: 3,
          borderRadius: 999,
        },
      },
    },
    MuiTab: {
      styleOverrides: {
        root: {
          minHeight: 48,
          fontWeight: 700,
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          borderRadius: uiTokens.radius.pill,
          fontWeight: 650,
        },
      },
    },
    MuiDialog: {
      styleOverrides: {
        paper: {
          borderRadius: uiTokens.radius.lg,
        },
      },
    },
  },
})
