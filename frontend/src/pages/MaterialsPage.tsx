import { useState, type FormEvent, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthProvider'
import '../styles/materials.css'

type JsonObject = Record<string, unknown>
type Material = { id: string; code: string; nameFa: string; nameEn?: string; category: string; unitCode: string; knowledgeTermId?: string; active: boolean; metadata: JsonObject }
type Variant = { id: string; brand?: string; model?: string; grade?: string; manufacturer?: string; countryCode?: string; attributes: JsonObject; active: boolean }
type Unit = { code: string; dimension: string }
type Currency = { code: string; nameEn: string }
type Source = { id: string; code: string; name: string; trust: string }
type Decimal = string | number
type Observation = { id: string; variantId?: string; priceType: string; amount: Decimal; quantityBasis: Decimal; currencyCode: string; unitCode: string; province?: string; city?: string; sourceId: string; sourceUrl?: string; observedAt: string; confidence?: Decimal; reviewStatus: string; origin: string; rawPayload: JsonObject; createdBy: string; reviewedBy?: string; reviewedAt?: string; reviewNote?: string }
type Aggregate = { id: string; variantId?: string; priceType: string; currencyCode: string; unitCode: string; province?: string; city?: string; sampleCount: number; minAmount: Decimal; maxAmount: Decimal; averageAmount: Decimal; medianAmount: Decimal; periodStart: string; periodEnd: string; algorithmVersion: string; calculatedAt: string }
type History = { aggregate: Aggregate; usable: boolean; expiresAt?: string; evidenceIds: string[] }
type Anomaly = { id: string; observationId: string; anomalyType: string; severity: string; score: Decimal; status: string; reason: string; reviewedBy?: string; reviewedAt?: string; reviewNote?: string }
const ROOT='/api/v1/materials'
const types: [string,string][] = [['RETAIL','خرده‌فروشی'],['WHOLESALE','عمده‌فروشی'],['CONTRACTOR','پیمانکاری'],['FACTORY_GATE','درب کارخانه'],['MARKET_QUOTE','نرخ بازار'],['OFFICIAL_INDEX','شاخص رسمی']]
const status: Record<string,string> = { PENDING_REVIEW:'در انتظار بررسی',APPROVED:'تأیید شده',REJECTED:'رد شده / ابطال',OPEN:'باز',ACCEPTED:'ناهنجاری تأیید شده',DISMISSED:'هشدار رد شده',HIGH:'شدید',MEDIUM:'متوسط' }
const tabs = [['catalog','کاتالوگ و گونه‌ها'],['quotes','ثبت و بازبینی قیمت'],['anomalies','ناهنجاری‌ها'],['current','قیمت مرجع'],['history','تاریخچه و شواهد']] as const
type Tab = typeof tabs[number][0]
const labels=(s:string)=>status[s] ?? types.find(t=>t[0]===s)?.[1] ?? s
const val=(f:FormData,k:string)=>String(f.get(k)??'').trim()
const opt=(f:FormData,k:string)=>val(f,k)||null
const iso=(s?:string)=>s?new Date(s).toLocaleString('fa-IR'):'—'
const display=(v:Decimal|undefined)=>v==null?'—':(typeof v==='number' && Math.abs(v)>=1e14 ? '≈ '+String(v) : String(v))
function decimal(f:FormData,k:string,nullable=false):string|null {
  const raw=val(f,k).replace(/[۰-۹]/g,c=>String('۰۱۲۳۴۵۶۷۸۹'.indexOf(c))).replace(/[٠-٩]/g,c=>String('٠١٢٣٤٥٦٧٨٩'.indexOf(c))).replace('٫','.')
  if(!raw && nullable)return null
  if(!/^\d{1,18}(\.\d{1,6})?$/.test(raw))throw new Error('عدد اعشاری معتبر وارد کنید؛ بدون جداکننده هزارگان.')
  return raw // keep the literal; backend owns precision and rounding
}
function Field({label,children}:{label:string;children:ReactNode}){return <label className="mat-field"><span>{label}</span>{children}</label>}
function Badge({value}:{value:string}){return <span className={'mat-badge mat-'+value.toLowerCase()}>{labels(value)}</span>}
function Empty({message='داده‌ای برای نمایش وجود ندارد.'}:{message?:string}){return <p className="mat-empty">{message}</p>}
function GridTable({headers,children}:{headers:string[];children:ReactNode}){return <div className="mat-table-wrap"><table><thead><tr>{headers.map(h=><th key={h} scope="col">{h}</th>)}</tr></thead><tbody>{children}</tbody></table></div>}
function PriceRows({rows,variantName}:{rows:Aggregate[];variantName:(id?:string)=>string}){return rows.length?<GridTable headers={['نوع و گونه','استان / شهر','ارز / واحد','نمونه','کمینه','میانگین','میانه','بیشینه','بازه مشاهده','نسخه']}>
  {rows.map(a=><tr key={a.id}><td>{labels(a.priceType)}<small>{variantName(a.variantId)}</small></td><td>{a.province??'—'} / {a.city??'—'}</td><td dir="ltr">{a.currencyCode}/{a.unitCode}</td><td>{a.sampleCount}</td><td>{display(a.minAmount)}</td><td>{display(a.averageAmount)}</td><td><strong>{display(a.medianAmount)}</strong></td><td>{display(a.maxAmount)}</td><td>{iso(a.periodStart)}<small>{iso(a.periodEnd)}</small></td><td>{a.algorithmVersion}<small>{iso(a.calculatedAt)}</small></td></tr>)}
</GridTable>:<Empty message="قیمت مرجع واجد شرایط وجود ندارد. محاسبه را با قیمت ساختگی ادامه ندهید."/>}

export function MaterialsPage(){
  const {user,hasPermission}=useAuth()
  const admin=hasPermission('USER_MANAGE'), allowed=admin||hasPermission('CASE_READ')
  const qc=useQueryClient(), principal=user?.username??''
  const [query,setQuery]=useState(''),[category,setCategory]=useState(''),[selected,setSelected]=useState(''),[tab,setTab]=useState<Tab>('catalog')
  const [editing,setEditing]=useState(false),[variantEdit,setVariantEdit]=useState<string|null>(null),[busy,setBusy]=useState(false),[message,setMessage]=useState(''),[error,setError]=useState('')
  const [review,setReview]=useState<{path:string;status:string;title:string}|null>(null)
  const [observationFilter,setObservationFilter]=useState('ALL'),[anomalyFilter,setAnomalyFilter]=useState('ALL')
  const baseKey=['phase18-materials',principal]
  const list=useQuery({queryKey:[...baseKey,'list',query,category],queryFn:()=>api<Material[]>(`${ROOT}?${new URLSearchParams({query,category})}`),enabled:allowed,retry:false})
  const item=list.data?.find(x=>x.id===selected)
  const id=item?.id??''
  const units=useQuery({queryKey:[...baseKey,'units'],queryFn:()=>api<Unit[]>(`${ROOT}/units`),enabled:allowed,retry:false})
  const variants=useQuery({queryKey:[...baseKey,id,'variants'],queryFn:()=>api<Variant[]>(`${ROOT}/${id}/variants`),enabled:allowed&&!!id,retry:false})
  const observations=useQuery({queryKey:[...baseKey,id,'prices'],queryFn:()=>api<Observation[]>(`${ROOT}/${id}/prices`),enabled:allowed&&!!id&&(tab==='quotes'||tab==='anomalies'||tab==='history'),retry:false})
  const current=useQuery({queryKey:[...baseKey,id,'current'],queryFn:()=>api<Aggregate[]>(`${ROOT}/${id}/aggregates`),enabled:allowed&&!!id&&tab==='current',retry:false})
  const history=useQuery({queryKey:[...baseKey,id,'history'],queryFn:()=>api<History[]>(`${ROOT}/${id}/aggregates/history`),enabled:allowed&&!!id&&tab==='history',retry:false})
  const anomalies=useQuery({queryKey:[...baseKey,'anomalies'],queryFn:()=>api<Anomaly[]>(`${ROOT}/anomalies`),enabled:allowed&&!!id&&tab==='anomalies',retry:false})
  const sources=useQuery({queryKey:[...baseKey,id,'sources'],queryFn:()=>api<Source[]>(`${ROOT}/${id}/price-sources`),enabled:admin&&!!id&&tab==='quotes',retry:false})
  const currencies=useQuery({queryKey:[...baseKey,'currencies'],queryFn:()=>api<Currency[]>('/api/v1/global/catalog/currencies'),enabled:admin&&tab==='quotes',retry:false})
  const variantName=(v?:string)=>v?variants.data?.find(x=>x.id===v)?.brand||variants.data?.find(x=>x.id===v)?.model||v:'عمومی'
  const chosenVariant=variants.data?.find(v=>v.id===variantEdit)
  const quoteIds=new Set(observations.data?.map(o=>o.id)??[])
  const visibleAnomalies=(anomalies.data??[]).filter(a=>quoteIds.has(a.observationId)&&(anomalyFilter==='ALL'||a.status===anomalyFilter))
  const setSelection=(value:string)=>{setSelected(value);setEditing(false);setVariantEdit(null);setReview(null);setMessage('');setError('')}
  const switchTab=(value:Tab)=>{setTab(value);setReview(null);setError('');setMessage('')}
  async function mutate(path:string,method:string,payload?:unknown,onDone?:(response:unknown)=>void){
    if(!admin||busy)return
    setBusy(true);setError('');setMessage('')
    try{const response=await api<unknown>(path,{method,...(payload!==undefined?{body:JSON.stringify(payload)}:{})});await qc.invalidateQueries({queryKey:baseKey});onDone?.(response);setMessage('درخواست با موفقیت در سرور ثبت شد. داده‌ها دوباره خوانده شدند.')}
    catch(e){setError(e instanceof Error?e.message:'درخواست ناموفق بود.');}
    finally{setBusy(false)}
  }
  function submit(e:FormEvent<HTMLFormElement>,fn:(f:FormData)=>void){e.preventDefault();if(!admin||busy)return;try{fn(new FormData(e.currentTarget))}catch(e){setError(e instanceof Error?e.message:'ورودی نامعتبر است.')}}
  const catalogForm=(m?:Material)=><form className="mat-form" key={m?.id??'new'} onSubmit={e=>submit(e,f=>{
    if(m&&Object.keys(m.metadata??{}).length)throw new Error('برای حفاظت از دقت متادیتای سفارشی، ویرایش این رکورد از API تخصصی انجام شود.');const body={code:m?.code??val(f,'code'),nameFa:val(f,'nameFa'),nameEn:opt(f,'nameEn'),category:val(f,'category'),unitCode:m?.unitCode??val(f,'unitCode'),knowledgeTermId:opt(f,'term'),active:f.get('active')==='on',metadata:m?.metadata??{}}
    void mutate(m?`${ROOT}/${m.id}`:ROOT,m?'PUT':'POST',body,res=>{if(res&&typeof res==='object'&&'id' in res)setSelected(String(res.id));setEditing(false)})
  })}>
    <Field label="کد یکتا"><input name="code" defaultValue={m?.code} required maxLength={120} disabled={!!m}/></Field><Field label="نام فارسی"><input name="nameFa" defaultValue={m?.nameFa} required maxLength={400}/></Field>
    <Field label="نام انگلیسی"><input name="nameEn" defaultValue={m?.nameEn} maxLength={400}/></Field><Field label="دسته‌بندی"><input name="category" defaultValue={m?.category} required maxLength={160}/></Field>
    <Field label="واحد پایه (پس از ایجاد ثابت)">{m?<input value={m.unitCode} disabled/>:<select name="unitCode" required defaultValue=""><option value="">انتخاب واحد</option>{units.data?.map(u=><option key={u.code} value={u.code}>{u.code} — {u.dimension}</option>)}</select>}</Field>
    <Field label="شناسه اصطلاح تأییدشده دانش (اختیاری)"><input name="term" defaultValue={m?.knowledgeTermId} maxLength={36}/></Field>
    <label className="mat-checkbox"><input type="checkbox" name="active" defaultChecked={m?.active??true}/> فعال</label><button type="submit" disabled={busy||(!m&&!units.data?.length)}>ذخیره مصالح</button>
  </form>
  if(!allowed)return <p role="alert">شما مجوز دسترسی به مصالح را ندارید.</p>
  const errors=[list.error,units.error,variants.error,observations.error,current.error,history.error,anomalies.error,sources.error,currencies.error].filter(Boolean)
  return <section className="materials-page" dir="rtl" aria-label="مدیریت مصالح و قیمت ساخت">
    <header className="mat-hero"><div><span className="mat-eyebrow">بازار و همکاران / مصالح</span><h1>مصالح و قیمت ساخت</h1><p>کاتالوگ، شواهد قیمت، بازبینی و قیمت مرجع قابل ردیابی</p></div><span className="mat-badge">{admin?'مدیریت مجاز':'فقط مشاهده'}</span></header>
    <div className="mat-notice">داده‌های واقعی ذخیره‌شده در سرور؛ این صفحه محاسبه مالی انجام نمی‌دهد. برای محاسبه، فقط قیمت مرجع معتبر مصرف می‌شود. نمایش اعداد بزرگ ممکن است تقریبی باشد؛ شواهد اصلی در سرور حفظ می‌شوند.</div>
    {error&&<p role="alert" className="mat-error">{error}</p>}{message&&<p role="status" className="mat-success">{message}</p>}{errors.map((e,i)=><p key={i} role="alert" className="mat-error">{String(e)}</p>)}
    <div className="mat-layout"><aside className="mat-panel mat-sidebar"><h2>فهرست مصالح</h2><form className="mat-filters" onSubmit={e=>{e.preventDefault();const f=new FormData(e.currentTarget);setQuery(val(f,'query'));setCategory(val(f,'category'));setSelection('')}}><Field label="جستجو"><input name="query" defaultValue={query} placeholder="نام یا کد"/></Field><Field label="دسته"><input name="category" defaultValue={category}/></Field><button type="submit" disabled={busy}>جستجو</button></form>
      {admin&&<button className="mat-secondary" disabled={busy} onClick={()=>{setSelection('');setEditing(true);setTab('catalog')}}>+ مصالح جدید</button>}
      {list.isFetching&&<p role="status">در حال دریافت…</p>}{!list.isFetching&&!list.data?.length&&<Empty/>}
      <ul className="mat-items">{list.data?.map(m=><li key={m.id}><button type="button" aria-pressed={m.id===id} disabled={busy} onClick={()=>setSelection(m.id)}><strong>{m.nameFa}</strong><small>{m.code} · {m.unitCode} · {m.active?'فعال':'غیرفعال'}</small></button></li>)}</ul>
    </aside><main className="mat-panel mat-main">
      {item?<div className="mat-title"><div><h2>{item.nameFa}</h2><p>{item.code} · {item.category} · {item.unitCode}</p></div><span className="mat-badge">{item.active?'فعال':'غیرفعال'}</span></div>:<h2>{editing?'ایجاد مصالح':'یک مصالح را انتخاب کنید'}</h2>}
      {!item&&editing&&admin&&catalogForm()}
      {item&&<><nav className="mat-tabs" aria-label="مراحل مدیریت قیمت">{tabs.map(([key,label])=><button key={key} type="button" aria-pressed={tab===key} disabled={busy} onClick={()=>switchTab(key)}>{label}</button>)}</nav>
        {tab==='catalog'&&<><div className="mat-section-head"><h3>مشخصات کاتالوگ</h3>{admin&&<button className="mat-secondary" disabled={busy} onClick={()=>setEditing(!editing)}>{editing?'بستن ویرایش':'ویرایش / غیرفعال‌سازی'}</button>}</div>{editing&&admin&&catalogForm(item)}<p>واحد پایه ثابت است؛ برای تغییر ماهیت مصالح، رکورد جدید بسازید.</p><h3>گونه‌ها (Variant)</h3>{variants.isFetching?<p>در حال دریافت…</p>:!variants.data?.length?<Empty/>:<GridTable headers={['برند','مدل','رده','سازنده','کشور','وضعیت',...(admin?['عملیات']:[])]}>{variants.data.map(v=><tr key={v.id}><td>{v.brand??'—'}</td><td>{v.model??'—'}</td><td>{v.grade??'—'}</td><td>{v.manufacturer??'—'}</td><td>{v.countryCode??'—'}</td><td>{v.active?'فعال':'غیرفعال'}</td>{admin&&<td><button className="mat-secondary" disabled={busy} onClick={()=>setVariantEdit(v.id)}>ویرایش</button></td>}</tr>)}</GridTable>}
        {admin&&<><button className="mat-secondary" disabled={busy||!item.active} onClick={()=>setVariantEdit('new')}>+ گونه جدید</button>{variantEdit&&<form key={variantEdit} className="mat-form" onSubmit={e=>submit(e,f=>{if(chosenVariant&&Object.keys(chosenVariant.attributes??{}).length)throw new Error('برای حفظ ویژگی‌های سفارشی، از API تخصصی استفاده کنید.');void mutate(`${ROOT}/${id}/variants${chosenVariant?'/'+chosenVariant.id:''}`,chosenVariant?'PUT':'POST',{brand:opt(f,'brand'),model:opt(f,'model'),grade:opt(f,'grade'),manufacturer:opt(f,'manufacturer'),countryCode:opt(f,'country'),attributes:chosenVariant?.attributes??{},active:f.get('active')==='on'},()=>setVariantEdit(null))})}>
          {([['brand','برند'],['model','مدل'],['grade','رده'],['manufacturer','سازنده']] as const).map(([key,label])=><Field key={key} label={label}><input name={key} defaultValue={chosenVariant?.[key]}/></Field>)}<Field label="کد کشور (دو حرف)"><input name="country" maxLength={2} defaultValue={chosenVariant?.countryCode}/></Field><label className="mat-checkbox"><input name="active" type="checkbox" defaultChecked={chosenVariant?.active??true}/> فعال</label><button disabled={busy}>ذخیره گونه</button><button type="button" className="mat-secondary" onClick={()=>setVariantEdit(null)}>انصراف</button></form>}</>}</>}
        {tab==='quotes'&&<><h3>مشاهدات قیمت</h3>{admin&&item.active&&<details className="mat-details"><summary>ثبت مشاهده جدید</summary><form className="mat-form" key={id} onSubmit={e=>submit(e,f=>{const time=val(f,'observedAt');if(!time||Number.isNaN(new Date(time).valueOf()))throw new Error('زمان مشاهده نامعتبر است.');const raw=val(f,'evidence');const rawPayload:JsonObject=raw?{submittedEvidenceText:raw}:{};void mutate(`${ROOT}/${id}/prices`,'POST',{variantId:opt(f,'variant'),priceType:val(f,'priceType'),amount:decimal(f,'amount'),quantityBasis:decimal(f,'quantity'),currencyCode:val(f,'currency'),unitCode:val(f,'unit'),province:opt(f,'province'),city:opt(f,'city'),sourceId:val(f,'source'),sourceUrl:opt(f,'url'),observedAt:new Date(time).toISOString(),confidence:decimal(f,'confidence',true),origin:'ADMIN',rawPayload})})}>
          <Field label="گونه"><select name="variant"><option value="">عمومی</option>{variants.data?.filter(v=>v.active).map(v=><option key={v.id} value={v.id}>{variantName(v.id)}</option>)}</select></Field>
          <Field label="نوع قیمت"><select name="priceType">{types.map(([v,l])=><option key={v} value={v}>{l}</option>)}</select></Field><Field label="مبلغ کل خرید"><input name="amount" inputMode="decimal" required/></Field><Field label="مقدار خرید"><input name="quantity" inputMode="decimal" required/></Field><Field label="ارز"><select name="currency" required defaultValue=""><option value="">انتخاب ارز فعال</option>{currencies.data?.map(c=><option key={c.code} value={c.code}>{c.code} — {c.nameEn}</option>)}</select></Field><Field label="واحد خرید"><select name="unit" defaultValue={item.unitCode} required>{units.data?.map(u=><option key={u.code} value={u.code}>{u.code}</option>)}</select></Field>
          <Field label="استان"><input name="province" maxLength={160}/></Field><Field label="شهر"><input name="city" maxLength={160}/></Field><Field label="منبع فعال"><select name="source" required defaultValue=""><option value="">انتخاب منبع موجود</option>{sources.data?.map(s=><option key={s.id} value={s.id}>{s.name} — {s.code} ({s.trust})</option>)}</select></Field><Field label="نشانی شاهد"><input name="url" type="url" maxLength={3000}/></Field><Field label="زمان مشاهده (زمان محلی دستگاه)"><input type="datetime-local" name="observedAt" required/></Field><Field label="اطمینان ۰ تا ۱"><input name="confidence" inputMode="decimal" placeholder="0.90"/></Field><Field label="متن شواهد خام (اختیاری؛ عین متن ثبت می‌شود)"><textarea name="evidence" rows={3} maxLength={20000}/></Field><button type="submit" disabled={busy||!sources.data?.length||!currencies.data?.length}>ثبت برای بررسی</button></form></details>}
          <Field label="فیلتر وضعیت"><select value={observationFilter} onChange={e=>setObservationFilter(e.target.value)}><option value="ALL">همه</option>{['PENDING_REVIEW','APPROVED','REJECTED'].map(s=><option key={s} value={s}>{labels(s)}</option>)}</select></Field>
          {observations.isFetching?<p>در حال دریافت…</p>:!(observations.data??[]).some(o=>observationFilter==='ALL'||o.reviewStatus===observationFilter)?<Empty/>:<GridTable headers={['قیمت خام','نوع / منطقه','منبع و تاریخ','اطمینان','وضعیت','شواهد',...(admin?['تصمیم']:[])]}>{observations.data?.filter(o=>observationFilter==='ALL'||o.reviewStatus===observationFilter).map(o=><tr key={o.id}><td>{display(o.amount)} {o.currencyCode}<small>برای {display(o.quantityBasis)} {o.unitCode} · {variantName(o.variantId)}</small></td><td>{labels(o.priceType)}<small>{o.province??'—'} / {o.city??'—'}</small></td><td>{sources.data?.find(s=>s.id===o.sourceId)?.name??o.sourceId}<small>{iso(o.observedAt)}</small></td><td>{display(o.confidence)}</td><td><Badge value={o.reviewStatus}/><small>{o.reviewedBy} {iso(o.reviewedAt)}</small></td><td><details><summary>جزئیات</summary><p dir="ltr">{o.id}</p><p>{o.sourceUrl??'بدون URL'}</p><p>{o.origin} · {o.createdBy}</p><p>{o.reviewNote}</p><pre>{JSON.stringify(o.rawPayload,null,2)}</pre></details></td>{admin&&<td>{o.reviewStatus==='PENDING_REVIEW'&&<button disabled={busy} onClick={()=>setReview({path:`${ROOT}/prices/${o.id}/review`,status:'APPROVED',title:'تأیید مشاهده'})}>تأیید</button>}{['PENDING_REVIEW','APPROVED'].includes(o.reviewStatus)&&<button className="mat-danger" disabled={busy} onClick={()=>setReview({path:`${ROOT}/prices/${o.id}/review`,status:'REJECTED',title:o.reviewStatus==='APPROVED'?'ابطال تأیید':'رد مشاهده'})}>{o.reviewStatus==='APPROVED'?'ابطال':'رد'}</button>}</td>}</tr>)}</GridTable>}</>}
        {tab==='anomalies'&&<><h3>صف بازبینی ناهنجاری</h3><Field label="وضعیت"><select value={anomalyFilter} onChange={e=>setAnomalyFilter(e.target.value)}><option value="ALL">همه</option>{['OPEN','ACCEPTED','DISMISSED'].map(s=><option key={s} value={s}>{labels(s)}</option>)}</select></Field>{(anomalies.isFetching||observations.isFetching)?<p>در حال دریافت…</p>:!visibleAnomalies.length?<Empty/>:<GridTable headers={['مشاهده','شدت','امتیاز','دلیل','وضعیت',...(admin?['تصمیم']:[])]}>{visibleAnomalies.map(a=><tr key={a.id}><td dir="ltr">{a.observationId}</td><td><Badge value={a.severity}/></td><td>{display(a.score)}</td><td>{a.reason}<small>{a.reviewNote}</small></td><td><Badge value={a.status}/></td>{admin&&<td>{a.status==='OPEN'&&<><button className="mat-danger" disabled={busy} onClick={()=>setReview({path:`${ROOT}/anomalies/${a.id}/review`,status:'ACCEPTED',title:'تأیید ناهنجاری و حذف از قیمت مرجع'})}>تأیید ناهنجاری</button><button className="mat-secondary" disabled={busy} onClick={()=>setReview({path:`${ROOT}/anomalies/${a.id}/review`,status:'DISMISSED',title:'رد هشدار ناهنجاری'})}>رد هشدار</button></>}</td>}</tr>)}</GridTable>}</>}
        {review&&admin&&<form className="mat-review" onSubmit={e=>submit(e,f=>void mutate(review.path,'POST',{status:review.status,note:val(f,'note')},()=>setReview(null)))}><h3>{review.title}</h3><p>این تصمیم ثبت و ممیزی می‌شود؛ پس از آن قیمت مرجع را مجدداً منتشر کنید.</p><Field label="دلیل مستند تصمیم"><textarea name="note" required maxLength={4000}/></Field><button disabled={busy}>ثبت تصمیم</button><button type="button" className="mat-secondary" onClick={()=>setReview(null)}>انصراف</button></form>}
        {tab==='current'&&<><div className="mat-section-head"><h3>قیمت‌های مرجع جاری</h3>{admin&&<button disabled={busy||!item.active} onClick={()=>{if(window.confirm('نسخه مرجع با مشاهدات واجد شرایط بازسازی شود؟'))void mutate(`${ROOT}/${id}/aggregates/rebuild`,'POST')}}>بازسازی و انتشار مرجع</button>}</div><p>آمار هر گروه مستقل است؛ قیمت ارزها، گونه‌ها یا مناطق مختلف با هم جمع نمی‌شوند.</p>{current.isFetching?<p>در حال دریافت…</p>:<PriceRows rows={current.data??[]} variantName={variantName}/>}</>}
        {tab==='history'&&<><h3>تاریخچه انتشار و شواهد</h3><p>نسخه‌های تاریخی برای ردیابی برآوردها حفظ می‌شوند و الزاماً قابل مصرف نیستند.</p>{history.isFetching?<p>در حال دریافت…</p>:!history.data?.length?<Empty/>:<GridTable headers={['زمان انتشار','نوع / گونه','ارز / واحد','منطقه','میانه','نمونه','اعتبار','شواهد']}>{history.data.map(h=><tr key={h.aggregate.id}><td>{iso(h.aggregate.calculatedAt)}</td><td>{labels(h.aggregate.priceType)}<small>{variantName(h.aggregate.variantId)}</small></td><td dir="ltr">{h.aggregate.currencyCode}/{h.aggregate.unitCode}</td><td>{h.aggregate.province??'—'} / {h.aggregate.city??'—'}</td><td>{display(h.aggregate.medianAmount)}</td><td>{h.aggregate.sampleCount}</td><td>{h.usable?'قابل مصرف':'تاریخی / نامعتبر'}<small>{iso(h.expiresAt)}</small></td><td><details><summary>نسخه {h.aggregate.algorithmVersion}</summary><p dir="ltr">{h.aggregate.id}</p>{h.evidenceIds.map(x=><p dir="ltr" key={x}>{x}</p>)}</details></td></tr>)}</GridTable>}</>}
      </>}
    </main></div>
  </section>
}
