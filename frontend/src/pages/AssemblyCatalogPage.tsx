import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthProvider'
import '../styles/materials.css'

const ROOT = '/api/v1/assemblies'
const TITLE = 'آنالیز بهای عملیات ساختمانی'
type Assembly = {id:string;code:string;nameFa:string;category:string;outputUnitCode:string;active:boolean}
type DefinitionResponse = {revision:string|null;definitionJson:string|null;historical:boolean;available:boolean}
type Component = {id:string;materialItemId:string;variantId?:string|null;quantity:string;wasteFactor:string;unitCode:string;priceBasis:string;sortOrder:number;note?:string|null}
type Definition = {assemblyId:string;nameFa:string;code:string;outputUnitCode:string;category:string;description?:string|null;active:boolean;components:Component[]}
const basis:Record<string,string>={MIN:'کمینه',MAX:'بیشینه',AVERAGE:'میانگین',MEDIAN:'میانه'}

export function AssemblyCatalogPage(){
  const {user,hasPermission}=useAuth()
  const allowed=hasPermission('CASE_READ')||hasPermission('USER_MANAGE')
  const principal=user?.username??''
  const [query,setQuery]=useState(''),[category,setCategory]=useState(''),[selected,setSelected]=useState(''),[revision,setRevision]=useState(''),[before,setBefore]=useState('')
  const key=['phase19-assembly',principal]
  const list=useQuery({queryKey:[...key,'list',query,category],queryFn:()=>api<Assembly[]>(`${ROOT}?${new URLSearchParams({query,category})}`),enabled:allowed,retry:false})
  const item=list.data?.find(a=>a.id===selected)
  const id=item?.id??''
  const current=useQuery({queryKey:[...key,id,'definition',revision],queryFn:()=>api<DefinitionResponse>(`${ROOT}/${id}/${revision?`revisions/${encodeURIComponent(revision)}`:'definition'}`),enabled:allowed&&!!id,retry:false})
  const history=useQuery({queryKey:[...key,id,'history',before],queryFn:()=>api<DefinitionResponse[]>(`${ROOT}/${id}/revisions?${new URLSearchParams({limit:'20',...(before?{beforeRevision:before}:{})})}`),enabled:allowed&&!!id,retry:false})
  let definition:Definition|null=null
  let invalid=false
  if(current.data?.definitionJson){try{const d=JSON.parse(current.data.definitionJson) as Definition;if(d.assemblyId!==id||!Array.isArray(d.components))throw new Error('Invalid definition');definition=d}catch{invalid=true}}
  const errors=[list.error,current.error,history.error].filter(Boolean)
  const select=(value:string)=>{setSelected(value);setRevision('');setBefore('')}
  if(!allowed)return <p role="alert">شما مجوز مشاهده آنالیز بها را ندارید.</p>
  return <section className="materials-page" dir="rtl" aria-label={TITLE}>
    <header className="mat-hero"><div><span className="mat-eyebrow">بازار و همکاران / آنالیز بها</span><h1>{TITLE}</h1><p>کاتالوگ عملیات، اجزای مصرفی و نسخه‌های ثبت‌شده تعریف آنالیز بها</p></div><span className="mat-badge">مشاهده</span></header>
    <div className="mat-notice">این صفحه زیرساخت مشاهده نسخه‌هاست. نرخ یا برآورد ساختگی نمایش داده نمی‌شود. ثبت و ویرایش از API مجاز انجام می‌شود؛ رابط کامل مدیریت و محاسبه در زیرفاز ۱۹-C تکمیل می‌شود.</div>
    {!!errors.length&&<p role="alert">خواندن اطلاعات ناموفق بود. مجوز، اتصال سرور و اجرای مهاجرت V72 را بررسی کنید.</p>}
    <div className="mat-form"><label className="mat-field"><span>جست‌وجوی کد یا عنوان</span><input value={query} maxLength={400} onChange={e=>{setQuery(e.target.value);select('')}}/></label><label className="mat-field"><span>دسته‌بندی</span><input value={category} maxLength={160} onChange={e=>{setCategory(e.target.value);select('')}}/></label><button type="button" onClick={()=>{void list.refetch();if(id){void current.refetch();void history.refetch()}}}>به‌روزرسانی</button></div>
    {list.isLoading&&<p role="status">در حال دریافت کاتالوگ…</p>}
    {list.data?.length===0&&<p className="mat-empty">آنالیز بهایی یافت نشد.</p>}
    {!!list.data?.length&&<div className="mat-table-wrap"><table><thead><tr>{['کد','نام عملیات','دسته','واحد خروجی','وضعیت','انتخاب'].map(h=><th scope="col" key={h}>{h}</th>)}</tr></thead><tbody>{list.data.map(a=><tr key={a.id}><td dir="ltr">{a.code}</td><td>{a.nameFa}</td><td>{a.category}</td><td dir="ltr">{a.outputUnitCode}</td><td>{a.active?'فعال':'غیرفعال'}</td><td><button type="button" aria-pressed={id===a.id} onClick={()=>select(a.id)}>مشاهده</button></td></tr>)}</tbody></table></div>}
    {id&&<section aria-label="تعریف و تاریخچه آنالیز بها"><h2>{item?.nameFa}</h2><div className="mat-form"><label className="mat-field"><span>نسخه تعریف</span><select value={revision} onChange={e=>setRevision(e.target.value)}><option value="">نسخه جاری</option>{revision&&!history.data?.some(r=>r.revision===revision)&&<option value={revision}>{revision}</option>}{history.data?.map(r=>r.revision&&<option key={r.revision} value={r.revision}>نسخه {r.revision}</option>)}</select></label><button type="button" onClick={()=>{const rows=history.data??[];const last=rows[rows.length-1]?.revision;if(last)setBefore(last)}} disabled={(history.data?.length??0)<20}>نسخه‌های قدیمی‌تر</button><button type="button" onClick={()=>setBefore('')} disabled={!before}>ابتدای تاریخچه</button></div>
      {current.isLoading&&<p role="status">در حال دریافت تعریف…</p>}{invalid&&<p role="alert">ساختار تعریف دریافتی معتبر نیست.</p>}
      {definition&&<><p>نسخه {current.data?.revision} — {current.data?.historical?'تاریخی (فقط خواندنی)':'جاری'} — {definition.active?'فعال':'غیرفعال'}</p><p>{definition.nameFa} | {definition.category} | واحد: <bdi>{definition.outputUnitCode}</bdi></p>{definition.description&&<p>{definition.description}</p>}{!definition.components.length?<p className="mat-empty">در این نسخه جزئی تعریف نشده است.</p>:<div className="mat-table-wrap"><table><thead><tr>{['ترتیب','شناسه مصالح','گونه','مقدار پایه','ضریب پرت','واحد','مبنای قیمت','یادداشت'].map(h=><th scope="col" key={h}>{h}</th>)}</tr></thead><tbody>{definition.components.map(c=><tr key={c.id}><td>{c.sortOrder}</td><td><bdi>{c.materialItemId}</bdi></td><td><bdi>{c.variantId??'—'}</bdi></td><td dir="ltr">{c.quantity}</td><td dir="ltr">{c.wasteFactor}</td><td>{c.unitCode}</td><td>{basis[c.priceBasis]??c.priceBasis}</td><td>{c.note??'—'}</td></tr>)}</tbody></table></div>}</>}
      {history.data?.length===0&&<p className="mat-empty">نسخه آرشیوشده‌ای در این صفحه وجود ندارد.</p>}
    </section>}
  </section>
}
