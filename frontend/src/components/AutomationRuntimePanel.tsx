import {Alert,Button,Card,Col,Progress,Row,Space,Statistic,Tag,Typography} from 'antd'
import {CheckCircleOutlined,CloudDownloadOutlined,ReloadOutlined,ToolOutlined,WarningOutlined} from '@ant-design/icons'
import {useEffect,useMemo,useState} from 'react'
import {api} from '../api/client'
import {useI18n} from '../i18n/LanguageProvider'
export default function AutomationRuntimePanel(){
 const {language}=useI18n(),fa=language==='fa';const [data,setData]=useState<any>(null),[busy,setBusy]=useState(false)
 const load=async()=>setData(await api('/api/v1/admin/automation/status'));useEffect(()=>{void load()},[])
 const run=async(kind:'check'|'repair')=>{setBusy(true);try{setData(await api(`/api/v1/admin/automation/${kind}`,{method:'POST'}))}finally{setBusy(false)}}
 const log=String(data?.log||data?.console||'');const stats=useMemo(()=>({ok:(log.match(/\[OK\]/g)||[]).length,issues:(log.match(/\[(MISSING|FAIL)\]/g)||[]).length,warn:(log.match(/\[WARN\]/g)||[]).length}),[log]);const total=stats.ok+stats.issues+stats.warn,health=total?Math.round(stats.ok*100/total):0
 return <div className="sy-auto-panel"><div className="sy-auto-intro"><div><Typography.Title level={4}>{fa?'اتوماسیون و محیط اجرای ساخت‌یار':'SakhtYar Automation & Runtime'}</Typography.Title><Typography.Paragraph>{fa?'بررسی، کش، دانلود، اعتبارسنجی، نصب و تعمیر وابستگی‌ها با مخزن محلی پروژه.':'Detect, cache, download, verify, install and repair dependencies using the project-local repository.'}</Typography.Paragraph></div><Progress type="circle" size={74} percent={health}/></div>
 {stats.issues>0&&<Alert showIcon type="warning" message={fa?`${stats.issues} مورد نیاز به بررسی یا نصب دارد.`:`${stats.issues} item(s) need attention or installation.`}/>}
 <Row gutter={[12,12]} className="sy-auto-stats"><Col xs={12} lg={6}><Card><Statistic prefix={<CheckCircleOutlined/>} title={fa?'بررسی سالم':'Healthy checks'} value={stats.ok}/></Card></Col><Col xs={12} lg={6}><Card><Statistic prefix={<WarningOutlined/>} title={fa?'مشکل':'Issues'} value={stats.issues}/></Card></Col><Col xs={12} lg={6}><Card><Statistic title={fa?'هشدار':'Warnings'} value={stats.warn}/></Card></Col><Col xs={12} lg={6}><Card><Statistic title={fa?'سلامت':'Health'} value={health} suffix="%"/></Card></Col></Row>
 <Card className="sy-auto-repo" title={fa?'مخزن محلی وابستگی‌ها':'Local dependency repository'}><code dir="ltr">{data?.root?`${data.root}\\.sakhtyar\\repository`:'.sakhtyar/repository'}</code><Space wrap><Tag>Offline-first</Tag><Tag>Project-local cache</Tag><Tag>Auditable</Tag></Space></Card>
 <Space wrap className="sy-auto-actions"><Button icon={<ReloadOutlined/>} loading={busy} onClick={()=>void run('check')}>{fa?'بررسی همه':'Check all'}</Button><Button type="primary" icon={<ToolOutlined/>} loading={busy} onClick={()=>void run('repair')}>{fa?'تعمیر و نصب موارد ناقص':'Repair / install missing'}</Button><Button icon={<CloudDownloadOutlined/>} onClick={()=>void load()}>{fa?'بازخوانی گزارش':'Refresh log'}</Button></Space>
 <Card title={fa?'گزارش اتوماسیون':'Automation log'}><pre className="sy-auto-log">{log||(fa?'هنوز گزارشی ثبت نشده است.':'No automation run recorded yet.')}</pre></Card></div>
}
