import { Alert, Badge, Button, Card, Descriptions, Form, Input, Modal, Space, Table, Tabs, Tag, Typography, App as AntdApp } from 'antd'
import { EditOutlined, EyeInvisibleOutlined, EyeOutlined, KeyOutlined } from '@ant-design/icons'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useI18n } from '../i18n/LanguageProvider'

type Definition={key:string;category:string;labelFa:string;labelEn:string;descriptionFa:string;descriptionEn:string;dataType:string;secret:boolean;applyMode:string;bootstrap:boolean}
type Effective={key:string;value:string|null;source:string;secret:boolean;bootstrap:boolean;required:boolean;configured:boolean}
type State={definitions:Definition[];categories:string[];effective:Effective[];versions:any[];active:any[]}
const cats:any={fa:{Database:'پایگاه داده',Redis:'ردیس',Storage:'ذخیره‌سازی',AI:'هوش مصنوعی',Integrations:'یکپارچه‌سازی‌ها',Observability:'پایش',Security:'امنیت',Bootstrap:'راه‌اندازی اولیه',Runtime:'محیط اجرا'},en:{Database:'Database',Redis:'Redis',Storage:'Storage',AI:'AI',Integrations:'Integrations',Observability:'Observability',Security:'Security',Bootstrap:'Bootstrap',Runtime:'Runtime'}}

export default function PlatformConfigPage(){
 const {message}=AntdApp.useApp()
 const {language}=useI18n()
 const fa=language==='fa'
 const navigate=useNavigate()
 const [data,setData]=useState<State>({definitions:[],categories:[],effective:[],versions:[],active:[]})
 const [doctor,setDoctor]=useState<any[]>([])
 const [open,setOpen]=useState(false)
 const [passwordOpen,setPasswordOpen]=useState(false)
 const [changingPassword,setChangingPassword]=useState(false)
 const [revealed,setRevealed]=useState<Record<string,string>>({})
 const [secretTarget,setSecretTarget]=useState<Definition|null>(null)
 const [secretOpen,setSecretOpen]=useState(false)
 const [secretForm]=Form.useForm()
 const [form]=Form.useForm()
 const [passwordForm]=Form.useForm()

 const load=async()=>{try{setData(await api<State>('/api/v1/admin/config'))}catch(e:any){message.error(e.message)}}
 const check=async()=>{try{setDoctor(await api<any[]>('/api/v1/admin/config/doctor'))}catch(e:any){message.error(e.message)}}
 useEffect(()=>{void load();void check()},[])
 const eff=useMemo(()=>Object.fromEntries(data.effective.map(x=>[x.key,x])),[data.effective])
 const create=async()=>{const v=await form.validateFields();await api('/api/v1/admin/config/versions',{method:'POST',body:JSON.stringify(v)});setOpen(false);form.resetFields();await load()}

 const changeAdminPassword=async()=>{
   const v=await passwordForm.validateFields()
   if(v.newPassword!==v.confirmPassword){message.error(fa?'تکرار رمز عبور با رمز جدید یکسان نیست.':'Password confirmation does not match.');return}
   setChangingPassword(true)
   try{
     await api<void>('/api/v1/auth/change-password',{method:'POST',body:JSON.stringify({currentPassword:v.currentPassword,newPassword:v.newPassword})})
     message.success(fa?'رمز عبور مدیر تغییر کرد. برای امنیت، دوباره وارد شوید.':'Administrator password changed. Please sign in again.')
     window.setTimeout(()=>window.location.assign('/login'),700)
   }catch(e:any){message.error(e.message)}
   finally{setChangingPassword(false)}
 }

 const reveal=async(x:Definition)=>{if(revealed[x.key]!==undefined){setRevealed(v=>{const n={...v};delete n[x.key];return n});return}try{const r=await api<{value:string;configured:boolean}>(`/api/v1/admin/config/secrets/${encodeURIComponent(x.key)}/reveal`,{method:'POST'});setRevealed(v=>({...v,[x.key]:r.value||''}))}catch(e:any){message.error(e.message)}}
 const replaceSecret=async()=>{if(!secretTarget)return;const v=await secretForm.validateFields();if(v.value!==v.confirm){message.error(fa?'تکرار Secret یکسان نیست.':'Secret confirmation does not match.');return}try{await api(`/api/v1/admin/config/secrets/${encodeURIComponent(secretTarget.key)}`,{method:'PUT',body:JSON.stringify({value:v.value})});setSecretOpen(false);secretForm.resetFields();setRevealed(s=>{const n={...s};delete n[secretTarget.key];return n});message.success(fa?'Secret ذخیره شد؛ برای اعمال کامل سرویس را Restart کنید.':'Secret saved; restart the service to fully apply it.')}catch(e:any){message.error(e.message)}}
 const columns:any=[
  {title:fa?'کلید':'Key',dataIndex:'key',width:230},
  {title:fa?'عنوان':'Title',render:(_:any,x:Definition)=>fa?x.labelFa:x.labelEn},
  {title:fa?'توضیح':'Description',render:(_:any,x:Definition)=>fa?x.descriptionFa:x.descriptionEn},
  {title:fa?'مقدار مؤثر':'Effective value',width:230,render:(_:any,x:Definition)=>{
    const e=eff[x.key]
    if(!x.secret)return <code>{String(e?.value??'—')}</code>
    if(x.key==='bootstrap.adminPassword')return <Tag>{fa?'Hash امن؛ قابل بازیابی نیست':'Secure hash; not recoverable'}</Tag>
    if(revealed[x.key]!==undefined)return <Space><code>{revealed[x.key]||'—'}</code><Button type="text" icon={<EyeInvisibleOutlined/>} onClick={()=>void reveal(x)}/></Space>
    return <Space><span>••••••••</span><Button type="text" icon={<EyeOutlined/>} disabled={!e?.configured} onClick={()=>void reveal(x)}/></Space>
  }},
  {title:fa?'منبع':'Source',render:(_:any,x:Definition)=><Tag>{eff[x.key]?.source||'DEFAULT'}</Tag>},
  {title:fa?'اعمال':'Apply',render:(_:any,x:Definition)=><Tag color={x.applyMode==='HOT_RELOAD'?'green':'orange'}>{x.applyMode==='HOT_RELOAD'?(fa?'اعمال زنده':'Hot reload'):(fa?'نیازمند راه‌اندازی مجدد':'Restart required')}</Tag>},
  {title:fa?'عملیات':'Actions',width:190,render:(_:any,x:Definition)=>x.key==='bootstrap.adminPassword'
    ?<Button icon={<KeyOutlined/>} onClick={()=>setPasswordOpen(true)}>{fa?'تغییر رمز':'Change password'}</Button>
    :x.secret?<Button icon={<EditOutlined/>} onClick={()=>{setSecretTarget(x);secretForm.resetFields();setSecretOpen(true)}}>{fa?'جایگزینی Secret':'Replace secret'}</Button>
    :<Button disabled icon={<EditOutlined/>}>{fa?'ویرایش در نسخه':'Edit in version'}</Button>}
 ]
 const categoryTabs=data.categories.map(c=>({
   key:c,label:cats[language]?.[c]||c,
   children:<Space direction="vertical" size={12} style={{width:'100%'}}>
    {c==='Bootstrap'?<Alert type="warning" showIcon message={fa?'رمز عبور واقعی مدیر قابل بازیابی و نمایش نیست؛ سیستم فقط Hash امن را نگهداری می‌کند. برای مشاهده هنگام ورود از آیکون چشم استفاده کنید و برای تغییر، دکمه زیر را بزنید.':'The current administrator password cannot be recovered or displayed because only a secure hash is stored. Use the eye icon while typing and the button below to change it.'} action={<Button type="primary" icon={<KeyOutlined/>} onClick={()=>setPasswordOpen(true)}>{fa?'تغییر رمز عبور مدیر':'Change administrator password'}</Button>}/>:null}
    <Table rowKey="key" scroll={{x:1100}} pagination={false} dataSource={data.definitions.filter(x=>x.category===c)} columns={columns}/>
   </Space>
 }))

 return <div dir={fa?'rtl':'ltr'} style={{padding:24,maxWidth:1600,margin:'0 auto'}} data-i18n-ignore="true">
  <Space direction="vertical" size={16} style={{width:'100%'}}>
   <div><Typography.Title level={2}>{fa?'مرکز پیکربندی ساخت‌یار':'SakhtYar Configuration Center'}</Typography.Title><Typography.Text type="secondary">{fa?'پیکربندی یکپارچه، نسخه‌دار و قابل ممیزی پلتفرم':'Unified, versioned and auditable platform configuration'}</Typography.Text></div>
   <Alert type="info" showIcon message={fa?'تنظیمات راه‌اندازی اولیه و Secretها از Environment یا Secret Provider خوانده می‌شوند و مقدار خام Secret در تاریخچه ذخیره یا نمایش داده نمی‌شود.':'Bootstrap settings and secrets are resolved from Environment or a Secret Provider; plaintext secrets are never stored in history or displayed.'}/>
   <Card><Space wrap><Button type="primary" onClick={()=>setOpen(true)}>{fa?'نسخه جدید':'New version'}</Button><Button onClick={()=>navigate('/settings')}>{fa?'بازگشت به تنظیمات':'Back to settings'}</Button>{data.active.map(x=><Tag color="green" key={x.id}>v{x.version_number} / {x.environment}</Tag>)}</Space></Card>
   <Tabs items={[...categoryTabs,
    {key:'effective',label:fa?'پیکربندی مؤثر':'Effective config',children:<Table rowKey="key" dataSource={data.effective} pagination={{pageSize:20}} columns={[{title:fa?'کلید':'Key',dataIndex:'key'},{title:fa?'مقدار':'Value',dataIndex:'value',render:(v:any,x:Effective)=>x.secret?'••••••••':String(v??'—')},{title:fa?'منبع':'Source',dataIndex:'source',render:(v:string)=><Tag>{v}</Tag>},{title:fa?'وضعیت':'Status',render:(_:any,x:Effective)=><Badge status={x.configured?'success':'warning'} text={x.configured?(fa?'تنظیم شده':'Configured'):(fa?'تنظیم نشده':'Not configured')}/>} ]}/>},
    {key:'doctor',label:fa?'دکتر پیکربندی':'Config Doctor',children:<Card extra={<Button onClick={()=>void check()}>{fa?'بررسی مجدد':'Recheck'}</Button>}><Descriptions bordered column={1} items={doctor.map((x,i)=>({key:String(i),label:x.name,children:<Space><Badge status={x.status==='OK'?'success':x.status==='FAIL'?'error':'warning'}/><Tag>{x.status}</Tag><span>{x.message}</span></Space>}))}/></Card>},
    {key:'history',label:fa?'تاریخچه نسخه‌ها':'Version history',children:<Table rowKey="id" dataSource={data.versions} columns={[{title:fa?'نسخه':'Version',dataIndex:'version_number',render:(v:any)=>'v'+v},{title:fa?'وضعیت':'Status',dataIndex:'status'},{title:fa?'محیط':'Environment',dataIndex:'environment'},{title:fa?'دلیل تغییر':'Reason',dataIndex:'reason'},{title:fa?'ایجاد':'Created',dataIndex:'created_at'},{title:fa?'انتشار':'Published',dataIndex:'published_at'}]}/>}
   ]}/>
  </Space>

  <Modal title={fa?'ایجاد پیش‌نویس پیکربندی':'Create configuration draft'} open={open} onOk={create} onCancel={()=>setOpen(false)} okText={fa?'ایجاد':'Create'} cancelText={fa?'انصراف':'Cancel'}>
   <Form form={form} layout="vertical" initialValues={{environment:'local'}}><Form.Item name="environment" label={fa?'محیط':'Environment'} rules={[{required:true}]}><Input/></Form.Item><Form.Item name="reason" label={fa?'دلیل تغییر':'Change reason'} rules={[{required:true}]}><Input.TextArea/></Form.Item></Form>
  </Modal>

  <Modal title={fa?'تغییر رمز عبور مدیر':'Change administrator password'} open={passwordOpen} onOk={()=>void changeAdminPassword()} confirmLoading={changingPassword} onCancel={()=>{setPasswordOpen(false);passwordForm.resetFields()}} okText={fa?'تغییر رمز عبور':'Change password'} cancelText={fa?'انصراف':'Cancel'}>
   <Alert style={{marginBottom:16}} type="info" showIcon message={fa?'برای امنیت، پس از تغییر رمز همه نشست‌های این حساب بسته می‌شوند.':'For security, all sessions for this account are revoked after the password is changed.'}/>
   <Form form={passwordForm} layout="vertical">
    <Form.Item name="currentPassword" label={fa?'رمز عبور فعلی':'Current password'} rules={[{required:true}]}><Input.Password autoComplete="current-password"/></Form.Item>
    <Form.Item name="newPassword" label={fa?'رمز عبور جدید':'New password'} rules={[{required:true},{min:10,message:fa?'حداقل ۱۰ کاراکتر وارد کنید.':'Use at least 10 characters.'}]}><Input.Password autoComplete="new-password"/></Form.Item>
    <Form.Item name="confirmPassword" label={fa?'تکرار رمز عبور جدید':'Confirm new password'} rules={[{required:true}]}><Input.Password autoComplete="new-password"/></Form.Item>
   </Form>
  </Modal>

  <Modal title={fa?`جایگزینی Secret — ${secretTarget?.labelFa??''}`:`Replace secret — ${secretTarget?.labelEn??''}`} open={secretOpen} onOk={()=>void replaceSecret()} onCancel={()=>{setSecretOpen(false);secretForm.resetFields()}} okText={fa?'ذخیره Secret جدید':'Save new secret'} cancelText={fa?'انصراف':'Cancel'}>
   <Alert style={{marginBottom:16}} type="warning" showIcon message={fa?'Secret جدید در فایل محلی خارج از Git ذخیره می‌شود. برای موارد Restart Required، سرویس را Restart کنید. Credential سرویس خارجی مثل PostgreSQL/MinIO نیز باید با این مقدار هماهنگ باشد.':'The new secret is stored in a local file outside Git. Restart for restart-required settings. External service credentials such as PostgreSQL/MinIO must also match this value.'}/>
   <Form form={secretForm} layout="vertical"><Form.Item name="value" label={fa?'Secret جدید':'New secret'} rules={[{required:true}]}><Input.Password/></Form.Item><Form.Item name="confirm" label={fa?'تکرار Secret':'Confirm secret'} rules={[{required:true}]}><Input.Password/></Form.Item></Form>
  </Modal>
 </div>
}