import {useMemo,useState} from 'react'
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query'
import {Alert,Button,Card,Checkbox,Col,Descriptions,Divider,Drawer,Empty,Input,Progress,Row,Select,Space,Statistic,Table,Tabs,Tag,Typography,message} from 'antd'
import {AuditOutlined,CheckCircleOutlined,CloseCircleOutlined,DownloadOutlined,HistoryOutlined,PlayCircleOutlined,ReloadOutlined,SearchOutlined,WarningOutlined} from '@ant-design/icons'
import {api} from '../api/client'

type Scenario={id:string;module:string;title:string;description:string;expected:string;kind:'ROUTE'|'SQL'|'EXTERNAL';target:string;risk:string;coverage:string;source:string}
type Result={test_id:string;module:string;title:string;status:string;actual?:string;expected?:string;detail?:string;duration_ms?:number|null}
type Run={id:string;scope:string;requested_by:string;environment:string;git_commit:string;status:string;total:number;passed:number;failed:number;blocked:number;started_at:string;finished_at?:string;results?:Result[]}
type Overview={totalScenarios:number;runnableSafeScenarios:number;requiresIsolatedRunner:number;totalRuns:number;latestRun?:Run|null;recentRuns:Run[]}
const fa:Record<string,string>={infrastructure:'زیرساخت و دیتابیس',security:'امنیت و کاربران',casefile:'پرونده و اسناد',knowledge:'دانش و استخراج',calculations:'موتورهای محاسباتی',agents:'Agentها و هوش مصنوعی',frontend:'فرانت‌اند و تجربه کاربری',integration:'یکپارچگی و E2E',quality:'خود مرکز آزمون',monitoring:'پایش و اعلان',automation:'اتوماسیون'}
const states:Record<string,{color:string,label:string}>={PASSED:{color:'success',label:'موفق'},FAILED:{color:'error',label:'ناموفق'},BLOCKED:{color:'warning',label:'اجرانشده / نیازمند Runner'},QUEUED:{color:'default',label:'در صف'},RUNNING:{color:'processing',label:'در حال اجرا'},COMPLETED:{color:'success',label:'پایان اجرا'},INTERRUPTED:{color:'error',label:'ناتمام'}}
function Status({value}:{value:string}){const s=states[value]??{color:'default',label:value};return <Tag color={s.color}>{s.label}</Tag>}
const date=(x?:string)=>x?new Date(x).toLocaleString('fa-IR'):'—'
const failures=(r?:Run|null)=>r?.failed??0

export function QualityCenterPage(){
  const qc=useQueryClient()
  const [module,setModule]=useState('all')
  const [search,setSearch]=useState('')
  const [selected,setSelected]=useState<string[]>([])
  const [detail,setDetail]=useState<Scenario|null>(null)
  const [activeId,setActiveId]=useState<string|null>(null)
  const [compareId,setCompareId]=useState<string|null>(null)
  const [busyExport,setBusyExport]=useState(false)
  const [msg,ctx]=message.useMessage()
  const catalog=useQuery({queryKey:['quality-catalog'],queryFn:()=>api<Scenario[]>('/api/v1/admin/quality/catalog')})
  const scenarioHistory=useQuery({queryKey:['quality-test-history',detail?.id],queryFn:()=>api<{run_id:string;started_at:string;status:string;actual?:string;scope:string}[]>(`/api/v1/admin/quality/tests/${detail?.id}/history`),enabled:!!detail?.id})
  const overview=useQuery({queryKey:['quality-overview'],queryFn:()=>api<Overview>('/api/v1/admin/quality/overview'),refetchInterval:5000})
  const history=useQuery({queryKey:['quality-history'],queryFn:()=>api<Run[]>('/api/v1/admin/quality/runs?limit=60'),refetchInterval:5000})
  const active=useQuery({queryKey:['quality-run',activeId],queryFn:()=>api<Run>(`/api/v1/admin/quality/runs/${activeId}`),enabled:!!activeId,refetchInterval:q=>{const d=q.state.data;return d?.status==='COMPLETED'||d?.status==='INTERRUPTED'?false:2200}})
  const compared=useQuery({queryKey:['quality-run',compareId],queryFn:()=>api<Run>(`/api/v1/admin/quality/runs/${compareId}`),enabled:!!compareId})
  const launch=useMutation({mutationFn:(v:{ids?:string[],module?:string})=>api<Run>('/api/v1/admin/quality/runs',{method:'POST',body:JSON.stringify({...v,requestKey:crypto.randomUUID()})}),onSuccess:r=>{setActiveId(r.id);void qc.invalidateQueries({queryKey:['quality-history']});msg.success('درخواست اجرا ثبت شد')},onError:e=>msg.error(e instanceof Error?e.message:'خطا در شروع اجرا')})
  const all=catalog.data??[]
  const modules=useMemo(()=>Array.from(new Set(all.map(x=>x.module))),[all])
  const filtered=all.filter(x=>(module==='all'||x.module===module)&&(!search||`${x.title} ${x.id} ${x.description}`.toLocaleLowerCase().includes(search.toLocaleLowerCase())))
  const runs=history.data??[]
  const latest=active.data??(activeId?runs.find(r=>r.id===activeId):null)??overview.data?.latestRun
  const selectedRun=activeId?(active.data??null):(latest??null)
  const currentFailures=(selectedRun?.results??[]).filter(r=>r.status==='FAILED')
  const previousFailures=(compared.data?.results??[]).filter(r=>r.status==='FAILED')
  const fixed=previousFailures.filter(r=>(selectedRun?.results??[]).some(x=>x.test_id===r.test_id&&x.status==='PASSED'))
  const newFailures=currentFailures.filter(r=>!(compared.data?.results??[]).some(x=>x.test_id===r.test_id&&x.status==='FAILED'))
  const launchSelected=()=>{if(!selected.length)return msg.warning('سناریو انتخاب کنید');launch.mutate({ids:selected})}
  const download=async(id:string,format:'json'|'csv'|'html')=>{
    setBusyExport(true)
    try{
      const ext=format==='json'?'':`.${format}`
      const res=await fetch(`/api/v1/admin/quality/runs/${id}/report${ext}`,{credentials:'include',cache:'no-store'})
      if(!res.ok)throw new Error(`HTTP ${res.status}`)
      const blob=await res.blob()
      const url=URL.createObjectURL(blob)
      const a=document.createElement('a');a.href=url;a.download=`sakhtyar-quality-${id}.${format}`;a.click()
      URL.revokeObjectURL(url)
    }catch(e){msg.error(e instanceof Error?e.message:'دریافت گزارش ناموفق بود')}
    finally{setBusyExport(false)}
  }
  const pct=(r?:Run|null)=>r?.total?Math.round((r.passed/r.total)*100):0
  const historyColumns=[
    {title:'تاریخ',dataIndex:'started_at',render:(v:string)=>date(v)},
    {title:'مجموعه',dataIndex:'scope',render:(v:string)=>v.startsWith('module:')?(fa[v.slice(7)]??v):v==='all'?'کل نرم‌افزار':'انتخابی'},
    {title:'نسخه',dataIndex:'git_commit',render:(v:string)=><code dir="ltr">{v&&v!=='unknown'?v.slice(0,9):'نامشخص'}</code>},
    {title:'نتیجه',render:(_:unknown,r:Run)=><Space size={3}><Tag color="success">{r.passed} موفق</Tag><Tag color="error">{r.failed} ناموفق</Tag><Tag color="warning">{r.blocked} اجرا‌نشده</Tag></Space>},
    {title:'وضعیت',dataIndex:'status',render:(v:string)=><Status value={v}/>},
    {title:'عملیات',render:(_:unknown,r:Run)=><Space wrap><Button size="small" onClick={()=>setActiveId(r.id)}>جزئیات</Button><Button size="small" onClick={()=>setCompareId(r.id)}>مقایسه</Button><Button size="small" loading={busyExport} onClick={()=>void download(r.id,'json')} icon={<DownloadOutlined/>}>JSON</Button></Space>},
  ]
  return <div className="sakhtyar-page-stack" dir="rtl">{ctx}
    <Card style={{borderRadius:18,background:'linear-gradient(125deg, #113b52, #17526a 70%, #1d6375)',color:'white'}}>
      <Space style={{display:'flex',justifyContent:'space-between',alignItems:'center'}} wrap>
        <div><Typography.Title level={2} style={{color:'white',marginBottom:4}}>مرکز آزمون و تضمین کیفیت ساخت‌یار</Typography.Title><Typography.Text style={{color:'#d5e9ee'}}>SakhtYar Quality & Test Center | تاریخچه، سناریوها، نتایج و گزارش‌ها</Typography.Text></div>
        <Button size="large" icon={<ReloadOutlined/>} onClick={()=>{void qc.invalidateQueries({queryKey:['quality-overview']});void qc.invalidateQueries({queryKey:['quality-history']})}}>به‌روزرسانی</Button>
      </Space>
    </Card>
    <Alert showIcon type="info" message="تمایز مهم: آزمون‌های SQL و ROUTE صرفاً کنترل واقعی زیرساخت یا ثبت مسیرند. سناریوهای عملکردی تا زمانی که Runner ایزوله اجرا نشده باشد، BLOCKED گزارش می‌شوند و موفق محسوب نخواهند شد." />
    {catalog.isError||overview.isError?<Alert type="error" showIcon message="دسترسی به API مرکز آزمون برقرار نیست. Migration، مجوز ADMIN و راه‌اندازی مجدد بک‌اند را بررسی کنید."/>:null}
    <Row gutter={[12,12]}>
      <Col xs={12} lg={6}><Card><Statistic title="کل سناریوها" value={overview.data?.totalScenarios??0} prefix={<AuditOutlined/>}/></Card></Col>
      <Col xs={12} lg={6}><Card><Statistic title="کنترل‌های قابل اجرا" value={overview.data?.runnableSafeScenarios??0} prefix={<CheckCircleOutlined/>}/></Card></Col>
      <Col xs={12} lg={6}><Card><Statistic title="نیازمند Runner ایزوله" value={overview.data?.requiresIsolatedRunner??0} prefix={<WarningOutlined/>}/></Card></Col>
      <Col xs={12} lg={6}><Card><Statistic title="تعداد اجراهای ثبت‌شده" value={overview.data?.totalRuns??0} prefix={<HistoryOutlined/>}/></Card></Col>
    </Row>
    <Tabs defaultActiveKey="scenarios" items={[
      {key:'scenarios',label:'سناریوها و اجرای تست',children:<Space direction="vertical" size={14} style={{width:'100%'}}>
        <Card><Space wrap style={{width:'100%',justifyContent:'space-between'}}>
          <Select style={{width:250}} value={module} onChange={setModule} options={[{value:'all',label:'تمام بخش‌های نرم‌افزار'},...modules.map(m=>({value:m,label:fa[m]??m}))]}/>
          <Input style={{width:260}} value={search} onChange={e=>setSearch(e.target.value)} placeholder="جستجوی عنوان یا شناسه" prefix={<SearchOutlined/>}/>
          <Button type="primary" loading={launch.isPending} onClick={()=>launch.mutate({module})} icon={<PlayCircleOutlined/>}>اجرای کل {module==='all'?'نرم‌افزار':(fa[module]??module)}</Button>
          <Button disabled={!selected.length} loading={launch.isPending} onClick={launchSelected}>اجرای {selected.length} سناریوی انتخاب‌شده</Button>
        </Space></Card>
        <Table<Scenario> rowKey="id" pagination={{pageSize:15,showSizeChanger:true}} scroll={{x:900}} dataSource={filtered}
          rowSelection={{selectedRowKeys:selected,onChange:keys=>setSelected(keys.map(String)),preserveSelectedRowKeys:true}}
          columns={[
            {title:'شناسه',dataIndex:'id',width:115,render:(v:string)=><code dir="ltr">{v}</code>},
            {title:'نام سناریو',dataIndex:'title',render:(v:string,r:Scenario)=><Button type="link" onClick={()=>setDetail(r)}>{v}</Button>},
            {title:'ماژول',dataIndex:'module',render:(v:string)=>fa[v]??v,width:160},
            {title:'نوع آزمون',dataIndex:'kind',render:(v:string)=><Tag color={v==='EXTERNAL'?'gold':'blue'}>{v==='EXTERNAL'?'نیازمند Runner':v==='SQL'?'SQL واقعی':'کنترل Route'}</Tag>,width:160},
            {title:'عملیات',width:135,render:(_:unknown,r:Scenario)=><Button size="small" loading={launch.isPending} onClick={()=>launch.mutate({ids:[r.id]})}>اجرای همین تست</Button>},
          ]}/>
      </Space>},
      {key:'history',label:'تاریخچه و روند',children:<Space direction="vertical" size={16} style={{width:'100%'}}>
        <Row gutter={[12,12]}><Col xs={24} lg={16}><Card title="روند تعداد مشکلات در اجراهای اخیر">
          {runs.length?<div style={{display:'flex',alignItems:'flex-end',gap:10,minHeight:150,overflowX:'auto',paddingBottom:8}}>
            {runs.slice(0,14).reverse().map((r,i)=><div key={r.id} onClick={()=>setActiveId(r.id)} title={`${date(r.started_at)} | ${r.failed} شکست | ${r.blocked} Blocked`} style={{minWidth:35,cursor:'pointer',textAlign:'center'}}>
              <div style={{fontSize:11,marginBottom:6}}>{r.failed}</div><div style={{height:Math.max(5,Math.round(105*r.failed/Math.max(1,...runs.map(x=>x.failed)))) ,background:r.failed?'#e05d64':'#53a487',borderRadius:5}}/>
              <Typography.Text type="secondary" style={{fontSize:10}}>{i+1}</Typography.Text>
            </div>)}
          </div>:<Empty description="هنوز اجرای ثبت‌شده وجود ندارد"/>}
          <Typography.Text type="secondary">هر ستون تعداد تست‌های ناموفق در یک اجرا است؛ تعداد BLOCKED جداگانه در جدول ثبت می‌شود. برای مقایسه منصفانه، Scope و تعداد تست را هم بررسی کنید.</Typography.Text>
        </Card></Col><Col xs={24} lg={8}><Card title="آخرین نتیجه"><Statistic title="موفق از مجموع" value={overview.data?.latestRun?.passed??0} suffix={`/ ${overview.data?.latestRun?.total??0}`}/><Progress percent={pct(overview.data?.latestRun)} status="normal"/><Space wrap><Tag color="red">{failures(overview.data?.latestRun)} خطا</Tag><Tag color="gold">{overview.data?.latestRun?.blocked??0} اجرا‌نشده</Tag></Space></Card></Col></Row>
        <Table<Run> rowKey="id" dataSource={runs} scroll={{x:1050}} pagination={{pageSize:12}} columns={historyColumns}/>
      </Space>},
      {key:'results',label:'نتایج و مقایسه',children:<Space direction="vertical" size={15} style={{width:'100%'}}>
        <Card title="انتخاب دو اجرا برای مقایسه"><Space wrap>
          <Select style={{width:350}} placeholder="اجرای اصلی" value={activeId??selectedRun?.id} onChange={setActiveId} options={runs.map(r=>({value:r.id,label:`${date(r.started_at)} | ${r.scope} | ${r.failed} خطا`}))}/>
          <Select style={{width:350}} allowClear placeholder="مقایسه با اجرای قبلی" value={compareId} onChange={setCompareId} options={runs.filter(r=>r.id!==activeId).map(r=>({value:r.id,label:`${date(r.started_at)} | ${r.scope} | ${r.failed} خطا`}))}/>
        </Space></Card>
        {selectedRun?<Card title={<Space>نتیجه اجرای انتخاب‌شده <Status value={selectedRun.status}/></Space>} extra={<Space wrap><Button disabled={busyExport} onClick={()=>void download(selectedRun.id,'json')}>JSON</Button><Button disabled={busyExport} onClick={()=>void download(selectedRun.id,'csv')}>CSV</Button><Button disabled={busyExport} onClick={()=>void download(selectedRun.id,'html')}>HTML / چاپ PDF</Button></Space>}>
          <Row gutter={[12,12]}><Col xs={8}><Statistic title="موفق" value={selectedRun.passed} valueStyle={{color:'#389e63'}}/></Col><Col xs={8}><Statistic title="ناموفق" value={selectedRun.failed} valueStyle={{color:'#cf444c'}}/></Col><Col xs={8}><Statistic title="اجرانشده" value={selectedRun.blocked}/></Col></Row>
          <Descriptions size="small" column={{xs:1,lg:2}} style={{marginTop:14}}><Descriptions.Item label="زمان">{date(selectedRun.started_at)}</Descriptions.Item><Descriptions.Item label="محیط">{selectedRun.environment}</Descriptions.Item><Descriptions.Item label="Commit"><code dir="ltr">{selectedRun.git_commit??'unknown'}</code></Descriptions.Item><Descriptions.Item label="مجری">{selectedRun.requested_by}</Descriptions.Item></Descriptions>
          {compared.data?<Alert type="info" showIcon message={`مقایسه با اجرای انتخابی: ${fixed.length} سناریوی دارای خطای قبلی که در اجرای فعلی خطای مشابه ندارد؛ ${newFailures.length} سناریوی ناموفق جدید.`} description="این مقایسه فقط وقتی دقیق است که هر دو اجرا سناریوهای مشترک داشته باشند. موارد BLOCKED یا غایب، رفع قطعی خطا به‌شمار نمی‌روند."/>:null}
          <Divider/><Table<Result> rowKey="test_id" dataSource={selectedRun.results??[]} pagination={{pageSize:15}} scroll={{x:1000}} columns={[
            {title:'شناسه',dataIndex:'test_id',width:110},
            {title:'آزمون',dataIndex:'title',width:230},
            {title:'نتیجه',dataIndex:'status',render:(v:string)=><Status value={v}/>,width:160},
            {title:'خروجی واقعی',dataIndex:'actual',width:300},
            {title:'جزئیات فنی',dataIndex:'detail',width:450},
          ]}/>
        </Card>:<Empty description="یک اجرا را برای مشاهده نتایج انتخاب کنید"/>}
      </Space>},
      {key:'help',label:'راهنما و دامنه تست‌ها',children:<Card title="نحوه استفاده صحیح"><Typography.Paragraph>۱) یک بخش یا چند سناریو را انتخاب کنید. ۲) اجرا را آغاز کنید. ۳) از تب «نتایج و مقایسه»، وضعیت هر سناریو و خروجی واقعی را مشاهده کنید. ۴) با اجرای دوباره، تاریخچه حفظ می‌شود و روند خطاها به‌روزرسانی می‌شود.</Typography.Paragraph><Typography.Paragraph>موفقیت SQL و ROUTE به معنی سلامت کل فرآیند محصول نیست. سناریوهای امنیتی، محاسباتی، دانش و مرورگر نیازمند Fixture و اجرای واقعی در محیط ایزوله هستند و تا تکمیل Runner با وضعیت BLOCKED ثبت می‌شوند.</Typography.Paragraph><Typography.Paragraph>گزارش JSON/CSV/HTML متعلق به همان Run است. برای دریافت PDF، فایل HTML را باز کرده و از Print / Save as PDF استفاده کنید.</Typography.Paragraph><Typography.Paragraph>نسخه کد باید هنگام راه‌اندازی بک‌اند از طریق APP_QUALITY_GIT_COMMIT ثبت شود؛ در غیر این صورت «unknown» باقی می‌ماند.</Typography.Paragraph></Card>},
    ]}/>
    <Drawer title={detail?`${detail.id} — ${detail.title}`:'شرح سناریو'} open={!!detail} onClose={()=>setDetail(null)} width={Math.min(630,typeof window==='undefined'?630:window.innerWidth-30)} destroyOnHidden>
      {detail&&<Space direction="vertical" style={{width:'100%'}} size={16}><Space><Tag>{fa[detail.module]??detail.module}</Tag><Tag>{detail.coverage}</Tag><Tag>{detail.risk}</Tag></Space><Card size="small" title="هدف و مراحل آزمون">{detail.description}</Card><Card size="small" title="نتیجه مورد انتظار">{detail.expected}</Card><Descriptions column={1} size="small"><Descriptions.Item label="نوع">{detail.kind}</Descriptions.Item><Descriptions.Item label="هدف فنی"><code dir="ltr">{detail.target||'نیازمند Runner و Fixture'}</code></Descriptions.Item><Descriptions.Item label="دامنه">{detail.coverage}</Descriptions.Item></Descriptions><Card size="small" title="تاریخچه همین سناریو"><Table size="small" rowKey="run_id" dataSource={scenarioHistory.data??[]} pagination={{pageSize:5}} columns={[{title:'زمان',dataIndex:'started_at',render:(v:string)=>date(v)},{title:'وضعیت',dataIndex:'status',render:(v:string)=><Status value={v}/>},{title:'عملیات',render:(_:unknown,r:{run_id:string})=><Button size="small" onClick={()=>{setActiveId(r.run_id);setDetail(null)}}>مشاهده اجرا</Button>}]}/></Card><Alert type={detail.kind==='EXTERNAL'?'warning':'info'} showIcon message={detail.kind==='EXTERNAL'?'برای اجرای این سناریو Runner ایزوله لازم است':'این سناریو کنترل فنی سبک است و معادل E2E نیست'}/><Button type="primary" block loading={launch.isPending} onClick={()=>{launch.mutate({ids:[detail.id]});setDetail(null)}} icon={<PlayCircleOutlined/>}>ثبت اجرای این سناریو</Button></Space>}
    </Drawer>
  </div>
}
