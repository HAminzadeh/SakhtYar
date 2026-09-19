import createCache from '@emotion/cache'
import { CacheProvider } from '@emotion/react'
import { CssBaseline, ThemeProvider, createTheme } from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import rtlPlugin from 'stylis-plugin-rtl'
import App from './App'
import { AuthProvider } from './auth/AuthProvider'
import './styles/global.css'

const rtlCache = createCache({
  key: 'mui-rtl',
  stylisPlugins: [rtlPlugin],
})

const theme = createTheme({
  direction: 'rtl',
  typography: {
    fontFamily:
      '"Vazirmatn", Tahoma, Arial, system-ui, -apple-system, sans-serif',
  },
  palette: {
    primary: {
      main: '#1d4ed8',
    },
  },
  shape: {
    borderRadius: 12,
  },
})

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 10_000,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <CacheProvider value={rtlCache}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <AuthProvider>
              <App />
            </AuthProvider>
          </BrowserRouter>
        </QueryClientProvider>
      </ThemeProvider>
    </CacheProvider>
  </StrictMode>,
)
