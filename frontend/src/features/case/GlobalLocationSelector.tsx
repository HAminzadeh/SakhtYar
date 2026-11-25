import { Button, Card, Select, Space } from 'antd'
import { useMutation, useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'
import { useI18n } from '../../i18n/LanguageProvider'
import { useState } from 'react'

type Country={id:string;nameEn:string;nameNative?:string|null}
type Division={id:string;nameEn:string;nameNative?:string|null}
type City={id:string;name:string}
type Context={countryId?:string|null;divisionId?:string|null;cityId?:string|null}

export function GlobalLocationSelector({caseId}:{caseId:string}){
  const {t}=useI18n()
  const ctx=useQuery({queryKey:['jurisdiction',caseId],queryFn:()=>api<Context>(`/api/v1/global/cases/${caseId}/jurisdiction`),retry:false})
  const [country,setCountry]=useState<string|undefined>()
  const [division,setDivision]=useState<string|null|undefined>(undefined)
  const [city,setCity]=useState<string|null|undefined>(undefined)
  const countryId=country??ctx.data?.countryId??undefined
  const divisionId=division===undefined?ctx.data?.divisionId??undefined:division??undefined
  const cityId=city===undefined?ctx.data?.cityId??undefined:city??undefined
  const countries=useQuery({queryKey:['countries'],queryFn:()=>api<Country[]>('/api/v1/global/catalog/countries')})
  const divisions=useQuery({queryKey:['divisions',countryId],enabled:Boolean(countryId),queryFn:()=>api<Division[]>(`/api/v1/global/catalog/countries/${countryId}/divisions`)})
  const cities=useQuery({queryKey:['cities',countryId,divisionId],enabled:Boolean(countryId),queryFn:()=>api<City[]>(`/api/v1/global/catalog/countries/${countryId}/cities${divisionId?`?divisionId=${divisionId}`:''}`)})
  const save=useMutation({
    mutationFn:()=>api<Context>(`/api/v1/global/cases/${caseId}/jurisdiction`,{
      method:'PUT',body:JSON.stringify({countryId,divisionId,cityId})
    }),
    onSuccess:()=>{setCountry(undefined);setDivision(undefined);setCity(undefined);void ctx.refetch()}
  })
  return <Card title={t('country')}>
    {ctx.isError&&<p role="alert" style={{color:'#ba3b3b'}}>دریافت اطلاعات مکانی پرونده ناموفق بود.</p>}
    {save.isError&&<p role="alert" style={{color:'#ba3b3b'}}>ذخیره انتخاب جغرافیایی مجاز نیست یا ناموفق بود.</p>}
    <Space wrap style={{width:'100%'}}>
      <Select showSearch optionFilterProp="label" value={countryId} placeholder={t('selectCountry')}
        onChange={v=>{setCountry(v);setDivision(null);setCity(null)}}
        options={(countries.data??[]).map(x=>({value:x.id,label:x.nameNative||x.nameEn}))} style={{minWidth:220}}/>
      <Select showSearch optionFilterProp="label" value={divisionId} placeholder={t('selectDivision')}
        onChange={v=>{setDivision(v);setCity(null)}}
        options={(divisions.data??[]).map(x=>({value:x.id,label:x.nameNative||x.nameEn}))} style={{minWidth:220}}/>
      <Select showSearch optionFilterProp="label" value={cityId} placeholder={t('selectCity')} onChange={setCity}
        options={(cities.data??[]).map(x=>({value:x.id,label:x.name}))} style={{minWidth:220}}/>
      <Button type="primary" disabled={!countryId||ctx.isLoading||save.isPending} loading={save.isPending} onClick={()=>save.mutate()}>{t('save')}</Button>
    </Space>
  </Card>
}