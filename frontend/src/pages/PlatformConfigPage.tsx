import AutomationRuntimePanel from '../components/AutomationRuntimePanel';
import {
  Alert, App as AntdApp, Badge, Button, Card, Col, Drawer, Empty, Form, Input, InputNumber,
  Modal, Progress, Row, Segmented, Select, Space, Statistic, Switch, Table, Tabs, Tag, Tooltip, Typography
} from 'antd'
import {
  ApiOutlined, CheckCircleOutlined, CloudServerOutlined, DatabaseOutlined, EditOutlined,
  EyeInvisibleOutlined, EyeOutlined, HistoryOutlined, KeyOutlined, MedicineBoxOutlined,
  ReloadOutlined, RocketOutlined, SafetyCertificateOutlined, SearchOutlined, SettingOutlined,
  ThunderboltOutlined
} from '@ant-design/icons'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useI18n } from '../i18n/LanguageProvider'
import './PlatformConfigPage.css'

type Definition={key:string;category:string;labelFa:string;labelEn:string;descriptionFa:string;descriptionEn:string;dataType:string;secret:boolean;applyMode:string;bootstrap:boolean;required?:boolean}
type Effective={key:string;value:string|null;source:string;secret:boolean;bootstrap:boolean;required:boolean;configured:boolean}
type Version={id:string;version_number:number;status:string;environment:string;reason:string;created_by?:string;created_at?:string;published_at?:string}
type State={definitions:Definition[];categories:string[];effective:Effective[];versions:Version[];active:any[]}
type RuntimeState={key:string;pendingOverride:boolean;pendingRestart:boolean;state:string}
type TestResult={key:string;ok:boolean;latencyMs:number;message:string}

const categoryMeta:any={
 Database:{fa:'پایگاه داده',en:'Database',icon:<DatabaseOutlined/>,descFa:'PostgreSQL و Connection Pool',descEn:'PostgreSQL and connection pool'},
 Redis:{fa:'ردیس',en:'Redis',icon:<ThunderboltOutlined/>,descFa:'Cache و Session runtime',descEn:'Cache and session runtime'},
 Storage:{fa:'ذخیره‌سازی',en:'Storage',icon:<CloudServerOutlined/>,descFa:'MinIO و Object Storage',descEn:'MinIO and object storage'},
 AI:{fa:'هوش مصنوعی',en:'AI',icon:<RocketOutlined/>,descFa:'Ollama، مدل و پارامترهای اجرا',descEn:'Ollama, model and runtime'},
 Integrations:{fa:'یکپارچه‌سازی‌ها',en:'Integrations',icon:<ApiOutlined/>,descFa:'سرویس‌های خارجی و APIها',descEn:'External services and APIs'},
 Observability:{fa:'پایش',en:'Observability',icon:<MedicineBoxOutlined/>,descFa:'Grafana، Loki، Tempo و Prometheus',descEn:'Grafana, Loki, Tempo and Prometheus'},
 Security:{fa:'امنیت',en:'Security',icon:<SafetyCertificateOutlined/>,descFa:'JWT، Cookie و CORS',descEn:'JWT, cookies and CORS'},
 Bootstrap:{fa:'راه‌اندازی اولیه',en:'Bootstrap',icon:<KeyOutlined/>,descFa:'مقادیر لازم پیش از شروع سرویس',descEn:'Values required before service startup'},
 Runtime:{fa:'محیط اجرا',en:'Runtime',icon:<SettingOutlined/>,descFa:'تنظیمات فرآیند Backend',descEn:'Backend process settings'}
}

export default function PlatformConfigPage(){
 const {message}=AntdApp.useApp()
 const {language}=useI18n()
 const fa=language==='fa'
 const navigate=useNavigate()
 const [data,setData]=useState<State>({definitions:[],categories:[],effective:[],versions:[],active:[]})
 const [runtime,setRuntime]=useState<RuntimeState[]>([])
 const [doctor,setDoctor]=useState<any[]>([])
 const [loading,setLoading]=useState(false)
 const [query,setQuery]=useState('')
 const [filter,setFilter]=useState('ALL')
 const [category,setCategory]=useState<string>('Database')
 const [revealed,setRevealed]=useState<Record<string,string>>({})
 const [editor,setEditor]=useState<Definition|null>(null)
 const [secretTarget,setSecretTarget]=useState<Definition|null>(null)
 const [passwordOpen,setPasswordOpen]=useState(false)
 const [draftOpen,setDraftOpen]=useState(false)
 const [publishing,setPublishing]=useState(false)
 const [testing,setTesting]=useState<string|null>(null)
 const [testResults,setTestResults]=useState<Record<string,TestResult>>({})
 const [editForm]=Form.useForm()
 const [secretForm]=Form.useForm()
 const [passwordForm]=Form.useForm()
 const [draftForm]=Form.useForm()

 const load=async()=>{
  setLoading(true)
  try{
   const [s,r,d]=await Promise.all([
    api<State>('/api/v1/admin/config'),
    api<RuntimeState[]>('/api/v1/admin/config/runtime-status'),
    api<any[]>('/api/v1/admin/config/doctor')
   ])
   setData(s);setRuntime(r);setDoctor(d)
   if(!s.categories.includes(category)&&s.categories.length)setCategory(s.categories[0])
  }catch(e:any){message.error(e.message)}
  finally{setLoading(false)}
 }
 useEffect(()=>{void load()},[])
 const eff=useMemo(()=>Object.fromEntries(data.effective.map(x=>[x.key,x])),[data.effective])
 const run=useMemo(()=>Object.fromEntries(runtime.map(x=>[x.key,x])),[runtime])
 const draft=data.versions.find(x=>x.status==='DRAFT')
 const active=data.active[0]
 const restartCount=runtime.filter(x=>x.pendingRestart).length
 const doctorFails=doctor.filter(x=>x.status==='FAIL').length
 const unconfigured=data.effective.filter(x=>x.required&&!x.configured).length

 const label=(x:Definition)=>fa?x.labelFa:x.labelEn
 const description=(x:Definition)=>fa?x.descriptionFa:x.descriptionEn
 const categoryName=(c:string)=>categoryMeta[c]?.[fa?'fa':'en']||c
 const sourceLabel=(s?:string)=>s==='CONFIG_VERSION'?(fa?'نسخه پیکربندی':'Config version'):s==='SPRING_ENVIRONMENT'?(fa?'محیط اجرا':'Environment'):(fa?'پیش‌فرض':'Default')
 const supportsTest=(x:Definition)=>x.key==='database.url'||x.key==='redis.host'||x.key==='ai.ollama.baseUrl'||x.key.endsWith('baseUrl')||x.key.endsWith('.endpoint')||x.key.startsWith('operations.')

 const visible=data.definitions.filter(x=>{
  if(x.category!==category)return false
  const q=query.trim().toLowerCase()
  if(q&&!`${x.key} ${x.labelFa} ${x.labelEn} ${x.descriptionFa} ${x.descriptionEn}`.toLowerCase().includes(q))return false
  if(filter==='SECRET'&&!x.secret)return false
  if(filter==='RESTART'&&x.applyMode!=='RESTART_REQUIRED')return false
  if(filter==='PENDING'&&!run[x.key]?.pendingRestart)return false
  if(filter==='MISSING'&&eff[x.key]?.configured)return false
  return true
 })

 const reveal=async(x:Definition)=>{
  if(revealed[x.key]!==undefined){setRevealed(v=>{const n={...v};delete n[x.key];return n});return}
  try{
   const r=await api<{value:string}>(`/api/v1/admin/config/secrets/${encodeURIComponent(x.key)}/reveal`,{method:'POST'})
   setRevealed(v=>({...v,[x.key]:r.value||''}))
   window.setTimeout(()=>setRevealed(v=>{const n={...v};delete n[x.key];return n}),30000)
  }catch(e:any){message.error(e.message)}
 }
 const ensureDraft=async():Promise<string>=>{
  if(draft)return draft.id
  const r=await api<{id:string}>('/api/v1/admin/config/versions',{method:'POST',body:JSON.stringify({environment:active?.environment||'local',reason:fa?'ویرایش از کنسول پیکربندی':'Edit from configuration console',basedOn:active?.id||null})})
  return r.id
 }
 const openEditor=(x:Definition)=>{
  setEditor(x)
  const value=eff[x.key]?.value
  editForm.setFieldsValue({value:x.dataType==='BOOLEAN'?String(value).toLowerCase()==='true':value})
 }
 const saveEdit=async()=>{
  if(!editor)return
  try{
   const values=await editForm.validateFields()
   const id=await ensureDraft()
   const value=editor.dataType==='BOOLEAN'?String(Boolean(values.value)):String(values.value??'')
   await api(`/api/v1/admin/config/versions/${id}/values/${encodeURIComponent(editor.key)}`,{method:'PUT',body:JSON.stringify({value,scopeType:'GLOBAL',scopeKey:''})})
   message.success(fa?'تغییر در پیش‌نویس ذخیره شد.':'Change saved to draft.')
   setEditor(null);await load()
  }catch(e:any){if(e?.errorFields)return;message.error(e.message)}
 }
 const replaceSecret=async()=>{
  if(!secretTarget)return
  const v=await secretForm.validateFields()
  if(v.value!==v.confirm){message.error(fa?'تکرار Secret یکسان نیست.':'Secret confirmation does not match.');return}
  try{
   await api(`/api/v1/admin/config/secrets/${encodeURIComponent(secretTarget.key)}`,{method:'PUT',body:JSON.stringify({value:v.value})})
   setSecretTarget(null);secretForm.resetFields()
   message.success(fa?'Secret برای راه‌اندازی بعدی ذخیره شد.':'Secret saved for the next startup.')
   await load()
  }catch(e:any){message.error(e.message)}
 }
 const changePassword=async()=>{
  const v=await passwordForm.validateFields()
  if(v.newPassword!==v.confirmPassword){message.error(fa?'تکرار رمز یکسان نیست.':'Password confirmation does not match.');return}
  try{
   await api('/api/v1/auth/change-password',{method:'POST',body:JSON.stringify({currentPassword:v.currentPassword,newPassword:v.newPassword})})
   message.success(fa?'رمز تغییر کرد؛ دوباره وارد شوید.':'Password changed; sign in again.')
   window.setTimeout(()=>window.location.assign('/login'),700)
  }catch(e:any){message.error(e.message)}
 }
 const test=async(x:Definition)=>{
  setTesting(x.key)
  try{
   const r=await api<TestResult>(`/api/v1/admin/config/test/${encodeURIComponent(x.key)}`,{method:'POST'})
   setTestResults(v=>({...v,[x.key]:r}))
   r.ok?message.success(`${r.message} (${r.latencyMs} ms)`):message.warning(r.message)
  }catch(e:any){message.error(e.message)}
  finally{setTesting(null)}
 }
 const publish=async()=>{
  if(!draft)return
  setPublishing(true)
  try{
   const validation=await api<any[]>(`/api/v1/admin/config/versions/${draft.id}/validate`)
   if(validation.some(x=>x.status==='ERROR')){message.error(fa?'اعتبارسنجی پیش‌نویس ناموفق است.':'Draft validation failed.');return}
   await api(`/api/v1/admin/config/versions/${draft.id}/publish`,{method:'POST'})
   message.success(fa?'نسخه منتشر شد.':'Configuration published.')
   await load()
  }catch(e:any){message.error(e.message)}
  finally{setPublishing(false)}
 }
 const createDraft=async()=>{
  const v=await draftForm.validateFields()
  try{
   await api('/api/v1/admin/config/versions',{method:'POST',body:JSON.stringify({environment:v.environment,reason:v.reason,basedOn:active?.id||null})})
   setDraftOpen(false);draftForm.resetFields();await load()
  }catch(e:any){message.error(e.message)}
 }

 const valueNode=(x:Definition)=>{
  const e=eff[x.key]
  if(x.secret){
   if(x.key==='bootstrap.adminPassword')return <span className="sy-secret-mask">{fa?'Hash امن — قابل بازیابی نیست':'Secure hash — not recoverable'}</span>
   if(revealed[x.key]!==undefined)return <Space><code className="sy-code sy-revealed">{revealed[x.key]||'—'}</code><Tooltip title={fa?'مخفی کردن':'Hide'}><Button type="text" icon={<EyeInvisibleOutlined/>} onClick={()=>void reveal(x)}/></Tooltip></Space>
   return <Space><span className="sy-secret-dots">••••••••</span><Tooltip title={fa?'نمایش برای ۳۰ ثانیه':'Reveal for 30 seconds'}><Button type="text" icon={<EyeOutlined/>} disabled={!e?.configured} onClick={()=>void reveal(x)}/></Tooltip></Space>
  }
  if(x.dataType==='BOOLEAN')return <Tag color={String(e?.value).toLowerCase()==='true'?'green':'default'}>{String(e?.value).toLowerCase()==='true'?(fa?'فعال':'Enabled'):(fa?'غیرفعال':'Disabled')}</Tag>
  return <code className="sy-code" dir="ltr">{String(e?.value??'—')}</code>
 }

 const renderInput=()=>{
  if(!editor)return null
  const common={name:'value',label:fa?'مقدار جدید':'New value',rules:[{required:true,message:fa?'مقدار الزامی است.':'Value is required.'}]}
  if(editor.dataType==='BOOLEAN')return <Form.Item {...common} valuePropName="checked"><Switch checkedChildren={fa?'فعال':'On'} unCheckedChildren={fa?'غیرفعال':'Off'}/></Form.Item>
  if(editor.dataType==='INTEGER'||editor.dataType==='DECIMAL')return <Form.Item {...common}><InputNumber style={{width:'100%'}} step={editor.dataType==='DECIMAL'?0.1:1}/></Form.Item>
  return <Form.Item {...common}><Input dir="ltr" allowClear/></Form.Item>
 }

 return <div className="sy-config-console" dir={fa?'rtl':'ltr'} data-i18n-ignore="true">
  <div className="sy-config-hero">
   <div>
    <Space size={10}><div className="sy-hero-icon"><SettingOutlined/></div><div><Typography.Title level={2}>{fa?'مرکز پیکربندی ساخت‌یار':'SakhtYar Configuration Center'}</Typography.Title><Typography.Text>{fa?'کنترل، نسخه‌بندی، امنیت و سلامت تنظیمات پلتفرم':'Control, version, secure and verify platform configuration'}</Typography.Text></div></Space>
   </div>
   <Space wrap>
    <Button icon={<ReloadOutlined/>} loading={loading} onClick={()=>void load()}>{fa?'بازخوانی':'Refresh'}</Button>
    <Button onClick={()=>navigate('/settings')}>{fa?'تنظیمات':'Settings'}</Button>
    {draft?<Button type="primary" icon={<RocketOutlined/>} loading={publishing} onClick={()=>void publish()}>{fa?`انتشار v${draft.version_number}`:`Publish v${draft.version_number}`}</Button>:<Button type="primary" onClick={()=>setDraftOpen(true)}>{fa?'ایجاد پیش‌نویس':'Create draft'}</Button>}
   </Space>
  </div>

  <Row gutter={[14,14]} className="sy-stats">
   <Col xs={12} md={6}><Card><Statistic title={fa?'نسخه فعال':'Active version'} value={active?`v${active.version_number}`:'—'} prefix={<CheckCircleOutlined/>}/></Card></Col>
   <Col xs={12} md={6}><Card><Statistic title={fa?'پیش‌نویس':'Draft'} value={draft?`v${draft.version_number}`:(fa?'ندارد':'None')} prefix={<EditOutlined/>}/></Card></Col>
   <Col xs={12} md={6}><Card><Statistic title={fa?'در انتظار Restart':'Pending restart'} value={restartCount} prefix={<ReloadOutlined/>}/></Card></Col>
   <Col xs={12} md={6}><Card><Statistic title={fa?'هشدار سلامت':'Health issues'} value={doctorFails+unconfigured} prefix={<MedicineBoxOutlined/>}/></Card></Col>
  </Row>

  {(restartCount>0||doctorFails>0)&&<Alert className="sy-top-alert" type={doctorFails?'warning':'info'} showIcon message={doctorFails?(fa?`${doctorFails} خطای Config Doctor نیاز به بررسی دارد.`:`${doctorFails} Config Doctor issue(s) need attention.`):(fa?`${restartCount} تغییر پس از راه‌اندازی مجدد اعمال می‌شود.`:`${restartCount} change(s) will apply after restart.`)}/>}

  <div className="sy-config-workspace">
   <aside className="sy-config-sidebar">
    <Input prefix={<SearchOutlined/>} placeholder={fa?'جست‌وجوی تنظیمات...':'Search settings...'} value={query} onChange={e=>setQuery(e.target.value)} allowClear/>
    <div className="sy-category-list">
     {data.categories.map(c=>{
      const count=data.definitions.filter(x=>x.category===c).length
      return <button key={c} className={`sy-category ${category===c?'active':''}`} onClick={()=>setCategory(c)}>
       <span className="sy-category-icon">{categoryMeta[c]?.icon||<SettingOutlined/>}</span>
       <span className="sy-category-copy"><strong>{categoryName(c)}</strong><small>{fa?categoryMeta[c]?.descFa:categoryMeta[c]?.descEn}</small></span>
       <Badge count={count} showZero color={category===c?'#1677ff':'#8c8c8c'}/>
      </button>
     })}
    </div>
   </aside>

   <main className="sy-config-main">
    <div className="sy-section-head">
     <div><Typography.Title level={4}>{categoryName(category)}</Typography.Title><Typography.Text type="secondary">{fa?categoryMeta[category]?.descFa:categoryMeta[category]?.descEn}</Typography.Text></div>
     <Segmented value={filter} onChange={v=>setFilter(String(v))} options={[
      {label:fa?'همه':'All',value:'ALL'},{label:fa?'Secret':'Secrets',value:'SECRET'},{label:fa?'Restart':'Restart',value:'RESTART'},
      {label:fa?'در انتظار':'Pending',value:'PENDING'},{label:fa?'ناقص':'Missing',value:'MISSING'}
     ]}/>
    </div>

    <div className="sy-setting-list">
     {visible.length===0?<Empty description={fa?'تنظیمی با این فیلتر پیدا نشد.':'No settings match this filter.'}/>:visible.map(x=>{
      const e=eff[x.key], rs=run[x.key], tr=testResults[x.key]
      return <Card key={x.key} className={`sy-setting-card ${rs?.pendingRestart?'pending':''}`} hoverable={false}>
       <div className="sy-setting-grid">
        <div className="sy-setting-info">
         <Space wrap><Typography.Text strong>{label(x)}</Typography.Text>{x.required&&<Tag color="red">{fa?'الزامی':'Required'}</Tag>}{x.secret&&<Tag icon={<SafetyCertificateOutlined/>}>Secret</Tag>}</Space>
         <Typography.Paragraph type="secondary">{description(x)}</Typography.Paragraph>
         <code className="sy-key" dir="ltr">{x.key}</code>
        </div>
        <div className="sy-setting-value">
         <div className="sy-value-label">{fa?'مقدار مؤثر':'Effective value'}</div>
         {valueNode(x)}
         <Space wrap className="sy-meta">
          <Tag>{sourceLabel(e?.source)}</Tag>
          <Tag color={x.applyMode==='HOT_RELOAD'?'green':'orange'}>{x.applyMode==='HOT_RELOAD'?(fa?'اعمال زنده':'Hot reload'):(fa?'نیازمند Restart':'Restart required')}</Tag>
          {rs?.pendingRestart&&<Tag color="gold">{fa?'در انتظار Restart':'Pending restart'}</Tag>}
          {e?.configured?<Badge status="success" text={fa?'تنظیم شده':'Configured'}/>:<Badge status="warning" text={fa?'تنظیم نشده':'Not configured'}/>}
         </Space>
         {tr&&<div className={`sy-test-result ${tr.ok?'ok':'bad'}`}><Badge status={tr.ok?'success':'error'}/>{tr.message} {tr.latencyMs>0&&`· ${tr.latencyMs} ms`}</div>}
        </div>
        <div className="sy-setting-actions">
         {x.key==='bootstrap.adminPassword'?<Button icon={<KeyOutlined/>} onClick={()=>setPasswordOpen(true)}>{fa?'تغییر رمز':'Change password'}</Button>:
          x.secret?<Button icon={<KeyOutlined/>} onClick={()=>{setSecretTarget(x);secretForm.resetFields()}}>{fa?'جایگزینی Secret':'Replace secret'}</Button>:
          <Button icon={<EditOutlined/>} disabled={x.bootstrap} onClick={()=>openEditor(x)}>{fa?'ویرایش':'Edit'}</Button>}
         {supportsTest(x)&&<Button icon={<ApiOutlined/>} loading={testing===x.key} onClick={()=>void test(x)}>{fa?'تست اتصال':'Test connection'}</Button>}
        </div>
       </div>
      </Card>
     })}
    </div>
   </main>
  </div>

  <Card className="sy-bottom-card">
   <Tabs items={[
    {key:'automation',label:<Space><SettingOutlined/>{fa?'Ø§ØªÙˆÙ…Ø§Ø³ÛŒÙˆÙ† Ùˆ Ù…Ø­ÛŒØ· Ø§Ø¬Ø±Ø§':'Automation & Runtime'}</Space>,children:<AutomationRuntimePanel/>},
    {key:'doctor',label:<Space><MedicineBoxOutlined/>{fa?'دکتر پیکربندی':'Config Doctor'}</Space>,children:<div className="sy-doctor-list">{doctor.map((x,i)=><div className="sy-doctor-row" key={i}><Badge status={x.status==='OK'?'success':x.status==='FAIL'?'error':'warning'}/><strong>{x.name}</strong><Tag>{x.status}</Tag><span>{x.message}</span></div>)}</div>},
    {key:'effective',label:<Space><SettingOutlined/>{fa?'پیکربندی مؤثر':'Effective config'}</Space>,children:<Table rowKey="key" scroll={{x:900}} dataSource={data.effective} pagination={{pageSize:12}} columns={[
     {title:fa?'کلید':'Key',dataIndex:'key',render:(v:string)=><code dir="ltr">{v}</code>},
     {title:fa?'مقدار':'Value',render:(_:any,x:Effective)=>x.secret?'••••••••':<code dir="ltr">{String(x.value??'—')}</code>},
     {title:fa?'منبع':'Source',dataIndex:'source',render:(v:string)=><Tag>{sourceLabel(v)}</Tag>},
     {title:fa?'وضعیت':'Status',render:(_:any,x:Effective)=><Badge status={x.configured?'success':'warning'} text={x.configured?(fa?'تنظیم شده':'Configured'):(fa?'تنظیم نشده':'Not configured')}/>}
    ]}/>},
    {key:'history',label:<Space><HistoryOutlined/>{fa?'تاریخچه نسخه‌ها':'Version history'}</Space>,children:<Table rowKey="id" scroll={{x:900}} dataSource={data.versions} pagination={{pageSize:10}} columns={[
     {title:fa?'نسخه':'Version',dataIndex:'version_number',render:(v:number)=>`v${v}`},{title:fa?'وضعیت':'Status',dataIndex:'status',render:(v:string)=><Tag color={v==='PUBLISHED'?'green':v==='DRAFT'?'blue':'default'}>{v}</Tag>},
     {title:fa?'محیط':'Environment',dataIndex:'environment'},{title:fa?'دلیل تغییر':'Reason',dataIndex:'reason'},{title:fa?'ایجاد':'Created',dataIndex:'created_at'}
    ]}/>}
   ]}/>
  </Card>

  <Drawer width={fa?520:500} title={editor?`${fa?'ویرایش':'Edit'} — ${label(editor)}`:''} open={!!editor} onClose={()=>setEditor(null)} extra={<Space><Button onClick={()=>setEditor(null)}>{fa?'انصراف':'Cancel'}</Button><Button type="primary" onClick={()=>void saveEdit()}>{fa?'ذخیره در پیش‌نویس':'Save to draft'}</Button></Space>}>
   {editor&&<><Alert type="info" showIcon message={editor.applyMode==='HOT_RELOAD'?(fa?'این تنظیم برای اعمال زنده تعریف شده است.':'This setting is marked for hot reload.'):(fa?'پس از انتشار، راه‌اندازی مجدد سرویس لازم است.':'A service restart is required after publishing.')}/><DescriptionsBlock x={editor} e={eff[editor.key]} fa={fa}/><Form form={editForm} layout="vertical">{renderInput()}</Form></>}
  </Drawer>

  <Modal title={secretTarget?`${fa?'جایگزینی Secret':'Replace secret'} — ${label(secretTarget)}`:''} open={!!secretTarget} onOk={()=>void replaceSecret()} onCancel={()=>{setSecretTarget(null);secretForm.resetFields()}} okText={fa?'ذخیره Secret':'Save secret'}>
   <Alert type="warning" showIcon message={fa?'مقدار جدید خارج از Git ذخیره می‌شود. Secret فعلی وارد تاریخچه نسخه‌ها نمی‌شود.':'The new value is stored outside Git. Plaintext secrets are never added to version history.'} style={{marginBottom:16}}/>
   <Form form={secretForm} layout="vertical"><Form.Item name="value" label={fa?'Secret جدید':'New secret'} rules={[{required:true}]}><Input.Password autoComplete="new-password"/></Form.Item><Form.Item name="confirm" label={fa?'تکرار Secret':'Confirm secret'} rules={[{required:true}]}><Input.Password autoComplete="new-password"/></Form.Item></Form>
  </Modal>

  <Modal title={fa?'تغییر رمز عبور مدیر':'Change administrator password'} open={passwordOpen} onOk={()=>void changePassword()} onCancel={()=>{setPasswordOpen(false);passwordForm.resetFields()}}>
   <Alert type="info" showIcon message={fa?'رمز فعلی Hash شده و قابل بازیابی نیست؛ فقط می‌توان آن را تغییر داد.':'The current password is hashed and cannot be recovered; it can only be changed.'} style={{marginBottom:16}}/>
   <Form form={passwordForm} layout="vertical"><Form.Item name="currentPassword" label={fa?'رمز فعلی':'Current password'} rules={[{required:true}]}><Input.Password/></Form.Item><Form.Item name="newPassword" label={fa?'رمز جدید':'New password'} rules={[{required:true},{min:10}]}><Input.Password/></Form.Item><Form.Item name="confirmPassword" label={fa?'تکرار رمز':'Confirm password'} rules={[{required:true}]}><Input.Password/></Form.Item></Form>
  </Modal>

  <Modal title={fa?'ایجاد پیش‌نویس پیکربندی':'Create configuration draft'} open={draftOpen} onOk={()=>void createDraft()} onCancel={()=>setDraftOpen(false)}>
   <Form form={draftForm} layout="vertical" initialValues={{environment:active?.environment||'local'}}><Form.Item name="environment" label={fa?'محیط':'Environment'} rules={[{required:true}]}><Select options={['local','dev','test','staging','prod'].map(v=>({value:v,label:v}))}/></Form.Item><Form.Item name="reason" label={fa?'دلیل تغییر':'Change reason'} rules={[{required:true}]}><Input.TextArea rows={3}/></Form.Item></Form>
  </Modal>
 </div>
}

function DescriptionsBlock({x,e,fa}:{x:Definition,e?:Effective,fa:boolean}){
 return <div className="sy-editor-summary">
  <div><span>{fa?'کلید':'Key'}</span><code dir="ltr">{x.key}</code></div>
  <div><span>{fa?'مقدار فعلی':'Current value'}</span><code dir="ltr">{x.secret?'••••••••':String(e?.value??'—')}</code></div>
  <div><span>{fa?'منبع':'Source'}</span><strong>{e?.source||'DEFAULT'}</strong></div>
 </div>
}