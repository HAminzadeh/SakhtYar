import { App as AntApp,ConfigProvider } from 'antd'
import { QueryClient,QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './auth/AuthProvider'
import { LanguageProvider,useI18n } from './i18n/LanguageProvider'
import { ApiFeedbackBridge } from './ui/ApiFeedbackBridge'
import './styles/global.css'

const queryClient=new QueryClient({defaultOptions:{queries:{retry:1,staleTime:10_000}}})
function LocalizedApp(){
  const {direction,antdLocale,antdTheme}=useI18n()
  return <ConfigProvider direction={direction} locale={antdLocale} theme={antdTheme}>
    <AntApp><ApiFeedbackBridge/><QueryClientProvider client={queryClient}><BrowserRouter><AuthProvider><App/></AuthProvider></BrowserRouter></QueryClientProvider></AntApp>
  </ConfigProvider>
}
createRoot(document.getElementById('root')!).render(<StrictMode><LanguageProvider><LocalizedApp/></LanguageProvider></StrictMode>)