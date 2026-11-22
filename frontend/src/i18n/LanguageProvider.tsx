import { createContext,useContext,useEffect,useMemo,useState,type ReactNode } from 'react'
import enUS from 'antd/locale/en_US'
import faIR from 'antd/locale/fa_IR'
import { messages,type LanguageCode,type MessageKey } from './messages'
import { RuntimeUiLocalizer } from './RuntimeUiLocalizer'
import { buildAntTheme,resolveTheme,type ThemeCode,type ResolvedTheme } from '../ui/themePresets'

type UserUiPreference={languageCode?:string|null;theme?:string|null}
type Value={
  language:LanguageCode;direction:'rtl'|'ltr';antdLocale:typeof faIR;setLanguage:(value:LanguageCode)=>void;
  t:(key:MessageKey)=>string;locale:string;theme:ThemeCode;resolvedTheme:ResolvedTheme;
  setTheme:(value:ThemeCode)=>void;antdTheme:ReturnType<typeof buildAntTheme>;
  applyUserPreference:(preference:UserUiPreference)=>void;
}
const Ctx=createContext<Value|null>(null)
const LANGUAGE_KEY='sakhtyar.language'
const THEME_KEY='sakhtyar.theme'
function validTheme(value:unknown):value is ThemeCode{
  return ['SYSTEM','LIGHT','DARK','OCEAN','EMERALD','SUNSET','MIDNIGHT'].includes(String(value))
}
export function LanguageProvider({children}:{children:ReactNode}){
  const [language,setLanguageState]=useState<LanguageCode>(()=>{
    const saved=localStorage.getItem(LANGUAGE_KEY);return saved==='en'?'en':'fa'
  })
  const [theme,setThemeState]=useState<ThemeCode>(()=>{
    const saved=localStorage.getItem(THEME_KEY);return validTheme(saved)?saved:'SYSTEM'
  })
  const [systemDark,setSystemDark]=useState(()=>window.matchMedia?.('(prefers-color-scheme: dark)').matches??false)
  useEffect(()=>{
    const media=window.matchMedia?.('(prefers-color-scheme: dark)')
    if(!media)return
    const handler=(event:MediaQueryListEvent)=>setSystemDark(event.matches)
    media.addEventListener?.('change',handler)
    return()=>media.removeEventListener?.('change',handler)
  },[])
  const setLanguage=(value:LanguageCode)=>{localStorage.setItem(LANGUAGE_KEY,value);setLanguageState(value)}
  const setTheme=(value:ThemeCode)=>{localStorage.setItem(THEME_KEY,value);setThemeState(value)}
  const applyUserPreference=(preference:UserUiPreference)=>{
    if(preference.languageCode==='fa'||preference.languageCode==='en')setLanguage(preference.languageCode)
    if(validTheme(preference.theme))setTheme(preference.theme)
  }
  const direction=language==='fa'?'rtl':'ltr'
  const locale=language==='fa'?'fa-IR':'en-US'
  const resolvedTheme=resolveTheme(theme,systemDark)
  useEffect(()=>{document.documentElement.lang=language;document.documentElement.dir=direction},[language,direction])
  useEffect(()=>{
    document.documentElement.dataset.theme=theme.toLowerCase()
    document.documentElement.dataset.themeResolved=resolvedTheme.toLowerCase()
    document.documentElement.style.colorScheme=resolvedTheme==='DARK'||resolvedTheme==='MIDNIGHT'?'dark':'light'
  },[theme,resolvedTheme])
  const antdTheme=useMemo(()=>buildAntTheme(resolvedTheme),[resolvedTheme])
  const value=useMemo<Value>(()=>({
    language,direction,locale,antdLocale:language==='fa'?faIR:enUS,setLanguage,t:(key)=>messages[language][key],
    theme,resolvedTheme,setTheme,antdTheme,applyUserPreference,
  }),[language,direction,locale,theme,resolvedTheme,antdTheme])
  return <Ctx.Provider value={value}><RuntimeUiLocalizer language={language}/>{children}</Ctx.Provider>
}
export function useI18n(){const value=useContext(Ctx);if(!value)throw new Error('useI18n must be used inside LanguageProvider');return value}