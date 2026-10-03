import { Alert, Button, Card, Form, Select, Space, Typography } from 'antd'
import { useMutation, useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { useI18n } from '../i18n/LanguageProvider'

type Item={id:string;code?:string;isoAlpha2?:string;nameEn:string;nameNative?:string|null}
type Pref={languageId?:string|null;countryId?:string|null;currencyId?:string|null;timezone?:string|null;theme:string}

export function PreferencesPage(){
  const {t,setLanguage}=useI18n()
  const languages=useQuery({queryKey:['global-languages'],queryFn:()=>api<Item[]>('/api/v1/global/catalog/languages')})
  const countries=useQuery({queryKey:['global-countries'],queryFn:()=>api<Item[]>('/api/v1/global/catalog/countries')})
  const currencies=useQuery({queryKey:['global-currencies'],queryFn:()=>api<Item[]>('/api/v1/global/catalog/currencies')})
  const pref=useQuery({queryKey:['preferences-me'],queryFn:()=>api<Pref>('/api/v1/global/preferences/me')})
  const save=useMutation({
    mutationFn:(v:Pref)=>api<Pref>('/api/v1/global/preferences/me',{method:'PUT',body:JSON.stringify(v)}),
    onSuccess:(v:any)=>{
      const lang=(languages.data??[]).find(x=>x.id===v.languageId)?.code
      if(lang==='fa'||lang==='en')setLanguage(lang)
      pref.refetch()
    }
  })
  if(!pref.data)return <Card loading />
  return <Card>
    <Typography.Title level={2}>{t('settings')}</Typography.Title>
    {save.isSuccess?<Alert type="success" showIcon message={t('saved')} style={{marginBottom:12}}/>:null}
    <Form layout="vertical" initialValues={pref.data} onFinish={(v)=>save.mutate(v as Pref)}>
      <Form.Item name="languageId" label={t('language')}>
        <Select options={(languages.data??[]).map((x:any)=>({value:x.id,label:x.nameNative||x.nameEn}))}/>
      </Form.Item>
      <Form.Item name="countryId" label={t('country')}>
        <Select showSearch optionFilterProp="label" options={(countries.data??[]).map((x:any)=>({value:x.id,label:x.nameNative||x.nameEn}))}/>
      </Form.Item>
      <Form.Item name="currencyId" label={t('currency')}>
        <Select showSearch optionFilterProp="label" options={(currencies.data??[]).map((x:any)=>({value:x.id,label:`${x.code} — ${x.nameEn}`}))}/>
      </Form.Item>
      <Form.Item name="timezone" label={t('timezone')}><Select showSearch options={Intl.supportedValuesOf('timeZone').map(x=>({value:x,label:x}))}/></Form.Item>
      <Form.Item name="theme" label={t('theme')}><Select options={['SYSTEM','LIGHT','DARK'].map(x=>({value:x,label:x}))}/></Form.Item>
      <Space><Button type="primary" htmlType="submit" loading={save.isPending}>{t('save')}</Button></Space>
    </Form>
  </Card>
}