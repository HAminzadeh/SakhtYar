import { Alert, Button, Card, Checkbox, Descriptions, Empty, Form, Input, InputNumber, Select, Space, Table, Tag, Typography } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../api/client'

type Obj = Record<string, unknown>
type Item = Obj & { id: string }
type Scenario = Item & { nameFa: string; status: string; assumptions?: Obj }
type Assessment = Item & { scenarioId: string; status: string; readinessScore: number; scenarioCost: number; urbanEvaluationId: string }
type Financial = Item & { scenarioId: string; grossRevenue: number; totalProjectCost: number; projectedProfit: number; roiPercent: number; currencyCode: string }
type Evidence = Item & { rule_id: string; stable_key: string; original_name: string; source_page: number; source_authority: string; release_key: string; scope_city: string; scope_district?: string; numeric_value: string; unit_code: string }
const post = <T,>(url: string, body: unknown) => api<T>(url, { method: 'POST', body: JSON.stringify(body) })
const asText = (v: unknown) => v === undefined || v === null ? '—' : String(v)
const err = (e: unknown) => e instanceof Error ? e.message : 'خطا در دریافت یا ثبت اطلاعات'

export function UrbanAnalysisPanel({ caseId }: { caseId: string }) {
  const qc = useQueryClient()
  const prop = useQuery({ queryKey: ['phase515-property',caseId], queryFn: () => api<Item>(`/api/v1/cases/${caseId}/property`) })
  const evidence = useQuery({ queryKey: ['phase515-evidence',caseId], queryFn: () => api<Evidence[]>(`/api/v1/regulations/cases/${caseId}/verified-evidence`) })
  const evaluations = useQuery({ queryKey: ['phase515-evaluations',prop.data?.id], enabled: Boolean(prop.data?.id), queryFn: () => api<Item[]>(`/api/v1/regulations/properties/${prop.data!.id}/evaluations`) })
  const [scenarioId,setScenarioId] = useState<string | undefined>()
  const scenarios = useQuery({ queryKey: ['phase515-scenarios'],queryFn: () => api<Scenario[]>('/api/v1/scenarios') })
  const evalMutation = useMutation({ mutationFn: () => post<Item>('/api/v1/regulations/evaluate',{ propertyId: prop.data!.id, scenarioId: scenarioId ?? null }),onSuccess: () => void qc.invalidateQueries({queryKey:['phase515-evaluations',prop.data?.id]}) })
  return <Space direction="vertical" style={{width:'100%'}} size="middle">
    <Alert type="info" showIcon message="فقط قوانین تأییدشده و منتشرشده معتبر تلقی می‌شوند" description="وجود یک عبارت عددی در PDF به معنی ضابطه قانونی نیست. هر Rule باید به منبع، صفحه، نسخه و تأیید کارشناس متصل شود." />
    {evidence.isError && <Alert type="error" showIcon message={err(evidence.error)}/>}
    <Card title="شواهد قانونی معتبر برای موقعیت ملک"><Table<Evidence> rowKey="rule_id" size="small" loading={evidence.isLoading} dataSource={evidence.data ?? []} pagination={{pageSize:7}} scroll={{x:900}} columns={[
      {title:'قاعده',dataIndex:'stable_key'}, {title:'مقدار',render:(_,r)=>`${asText(r.numeric_value)} ${asText(r.unit_code)}`},
      {title:'محدوده',render:(_,r)=>`${asText(r.scope_city)} / ${asText(r.scope_district)}`},
      {title:'منبع',render:(_,r)=>`${r.original_name}، صفحه ${r.source_page}`},
      {title:'مرجع',dataIndex:'source_authority'},{title:'نسخه',dataIndex:'release_key'}
    ]}/>{evidence.data?.length === 0 && <Alert type="warning" showIcon message="هیچ قاعده تأییدشده مرتبط با این ملک پیدا نشد. نتیجه امکان‌سنجی قطعی نیست."/>}</Card>
    <Card title="ارزیابی شهرسازی">
      <Space wrap><Select style={{minWidth:220}} allowClear placeholder="سناریو (اختیاری)" value={scenarioId} onChange={setScenarioId} options={(scenarios.data??[]).map(s=>({label:s.nameFa??s.id,value:s.id}))}/>
        <Button type="primary" loading={evalMutation.isPending} disabled={!prop.data?.id || !evidence.data?.length} onClick={()=>evalMutation.mutate()}>ارزیابی با قواعد معتبر</Button></Space>
      {evalMutation.isError && <Alert style={{marginTop:12}} type="error" message={err(evalMutation.error)}/>}
      <Table<Item> style={{marginTop:14}} rowKey="id" size="small" dataSource={evaluations.data??[]} pagination={{pageSize:5}} columns={[{title:'وضعیت',dataIndex:'status',render:v=><Tag color={v==='COMPLIANT'?'green':'orange'}>{asText(v)}</Tag>},{title:'قوانین اعمال‌شده',dataIndex:'ruleCount'},{title:'نیازمند بررسی',dataIndex:'reviewCount'},{title:'تاریخ',dataIndex:'evaluatedAt',render:v=>asText(v)}]}/>
      {evaluations.data?.some(x => Number(x.ruleCount)===0) && <Alert type="warning" message="ارزیابی بدون قانون نباید مجوز ساخت تلقی شود." showIcon/>}
    </Card>
  </Space>
}

export function ConstructionCostPanel({caseId}: {caseId:string}) {
  const scenarios=useQuery({queryKey:['phase515-scenarios'],queryFn:()=>api<Scenario[]>('/api/v1/scenarios')})
  const [scenarioId,setScenarioId]=useState<string>()
  const [currency,setCurrency]=useState('IRR')
  const [priceType,setPriceType]=useState('MARKET_QUOTE')
  const qc=useQueryClient()
  const snapshots=useQuery({queryKey:['phase515-costs',scenarioId],enabled:!!scenarioId,queryFn:()=>api<Item[]>(`/api/v1/scenarios/${scenarioId}/snapshots`)})
  const items=useQuery({queryKey:['phase515-scenario-items',scenarioId],enabled:!!scenarioId,queryFn:()=>api<Item[]>(`/api/v1/scenarios/${scenarioId}/items`)})
  const mutation=useMutation({mutationFn:()=>post<Item>(`/api/v1/scenarios/${scenarioId}/calculate`,{priceType,currencyCode:currency,city:'تهران',province:'تهران'}),onSuccess:()=>void qc.invalidateQueries({queryKey:['phase515-costs',scenarioId]})})
  return <Space direction="vertical" style={{width:'100%'}} size="middle"><Alert type="info" showIcon message="هزینه‌ها از Assembly و قیمت مصالح موجود محاسبه می‌شوند" description="قیمت خالی، منقضی یا نامعتبر را به‌صورت خودکار جایگزین نمی‌کنیم. ابتدا اقلام سناریو را در API مربوط ثبت کنید."/>
    <Card title="انتخاب سناریو و محاسبه هزینه"><Space wrap><Select value={scenarioId} onChange={setScenarioId} placeholder="سناریو" style={{minWidth:250}} options={(scenarios.data??[]).map(x=>({label:x.nameFa,value:x.id}))}/><Select value={priceType} onChange={setPriceType} style={{width:175}} options={['MARKET_QUOTE','RETAIL','WHOLESALE','CONTRACTOR','OFFICIAL_INDEX'].map(value=>({value,label:value}))}/><Select value={currency} onChange={setCurrency} style={{width:105}} options={['IRR','TOMAN','USD'].map(value=>({value,label:value}))}/><Button loading={mutation.isPending} disabled={!scenarioId||!items.data?.length} onClick={()=>mutation.mutate()} type="primary">محاسبه هزینه</Button></Space>
      <Typography.Paragraph type="secondary" style={{marginTop:12}}>تعداد آیتم‌های سناریو: {items.data?.length ?? 0}. انتخاب سناریو مستقل از پرونده است؛ شناسه Snapshot در مرحله امکان‌سنجی مصرف می‌شود.</Typography.Paragraph>
      {mutation.isError&&<Alert showIcon type="error" message={err(mutation.error)}/>}
    </Card><Card title="نسخه‌های محاسبات هزینه"><Table<Item> rowKey="id" dataSource={snapshots.data??[]} size="small" scroll={{x:750}} columns={[{title:'Snapshot',dataIndex:'id',render:v=><Typography.Text copyable>{asText(v)}</Typography.Text>},{title:'هزینه کل',dataIndex:'totalCost'},{title:'ارز',dataIndex:'currencyCode'},{title:'نسخه موتور',dataIndex:'algorithmVersion'}]}/></Card>
    <Typography.Text type="secondary">شناسه پرونده: {caseId}</Typography.Text>
  </Space>
}

export function FeasibilityPanel({caseId}:{caseId:string}) {
  const scenarios=useQuery({queryKey:['phase515-scenarios'],queryFn:()=>api<Scenario[]>('/api/v1/scenarios')})
  const prop=useQuery({queryKey:['phase515-property',caseId],queryFn:()=>api<Item>(`/api/v1/cases/${caseId}/property`)})
  const [scenarioId,setScenarioId]=useState<string>()
  const [costId,setCostId]=useState<string>()
  const [urbanId,setUrbanId]=useState<string>()
  const qc=useQueryClient()
  const cost=useQuery({queryKey:['phase515-costs',scenarioId],enabled:!!scenarioId,queryFn:()=>api<Item[]>(`/api/v1/scenarios/${scenarioId}/snapshots`)})
  const urban=useQuery({queryKey:['phase515-evaluations',prop.data?.id],enabled:!!prop.data?.id,queryFn:()=>api<Item[]>(`/api/v1/regulations/properties/${prop.data!.id}/evaluations`)})
  const assessments=useQuery({queryKey:['phase515-assessments',caseId],queryFn:()=>api<Assessment[]>(`/api/v1/feasibility/cases/${caseId}/assessments`)})
  const mutation=useMutation({mutationFn:()=>post<Assessment>('/api/v1/feasibility/assessments',{caseId,scenarioId,scenarioCostSnapshotId:costId,urbanEvaluationId:urbanId}),onSuccess:()=>void qc.invalidateQueries({queryKey:['phase515-assessments',caseId]})})
  const currentUrban=urban.data?.find(x=>x.id===urbanId)
  return <Space direction="vertical" size="middle" style={{width:'100%'}}><Alert showIcon type="info" message="ارزیابی فقط با Snapshotهای مشخص انجام می‌شود" description="سناریو، هزینه و نتیجه شهرسازی باید مربوط به همان ملک و نسخه باشند."/>
    <Card title="اجرای امکان‌سنجی"><Space direction="vertical" style={{width:'100%'}}><Select placeholder="سناریوی ساخت" value={scenarioId} onChange={v=>{setScenarioId(v);setCostId(undefined);setUrbanId(undefined)}} options={(scenarios.data??[]).map(x=>({label:x.nameFa,value:x.id}))}/><Select placeholder="Snapshot هزینه" value={costId} onChange={setCostId} options={(cost.data??[]).map(x=>({label:`${asText(x.totalCost)} ${asText(x.currencyCode)} - ${x.id}`,value:x.id}))}/><Select placeholder="ارزیابی شهرسازی" value={urbanId} onChange={setUrbanId} options={(urban.data??[]).filter(x=>!scenarioId||x.scenarioId===scenarioId).map(x=>({label:`${asText(x.status)} / ${asText(x.ruleCount)} قوانین / ${x.id}`,value:x.id}))}/><Button type="primary" loading={mutation.isPending} disabled={!scenarioId||!costId||!urbanId||Number(currentUrban?.ruleCount??0)<1||currentUrban?.status!=='COMPLIANT'} onClick={()=>mutation.mutate()}>ایجاد Snapshot امکان‌سنجی</Button>
    {currentUrban && currentUrban.status!=='COMPLIANT' && <Alert type="warning" showIcon message="ارزیابی شهرسازی تأیید کامل ندارد؛ ادامه تحلیل مالی قطعی مجاز نیست."/>}{mutation.isError&&<Alert type="error" showIcon message={err(mutation.error)}/>}</Space></Card>
    <Card title="نتایج امکان‌سنجی"><Table<Assessment> rowKey="id" dataSource={assessments.data??[]} scroll={{x:800}} columns={[{title:'وضعیت',dataIndex:'status'},{title:'آمادگی',dataIndex:'readinessScore'},{title:'هزینه',dataIndex:'scenarioCost'},{title:'شناسه خروجی',dataIndex:'id',render:v=><Typography.Text copyable>{asText(v)}</Typography.Text>}]} /></Card>
  </Space>
}

export function FinancialAnalysisPanel({caseId}:{caseId:string}) {
  const qc=useQueryClient()
  const [confirmed,setConfirmed]=useState(false)
  const [form]=Form.useForm()
  const assessments=useQuery({queryKey:['phase515-assessments',caseId],queryFn:()=>api<Assessment[]>(`/api/v1/feasibility/cases/${caseId}/assessments`)})
  const results=useQuery({queryKey:['phase515-finance',caseId],queryFn:()=>api<Financial[]>(`/api/v1/finance/cases/${caseId}/analyses`)})
  const mutation=useMutation({mutationFn:(v:Obj)=>{
    const o=Number(v.ownerPercent),ownerId=String(v.ownerId),builderId=String(v.builderId)
    const participants=[{participantType:'OWNER',participantRefId:ownerId,participantLabel:'مالک',valueSharePercent:o,costSharePercent:0,metadata:{source:'EXPLICIT_USER_ASSUMPTION'}},{participantType:'BUILDER',participantRefId:builderId,participantLabel:'سازنده',valueSharePercent:100-o,costSharePercent:100,metadata:{source:'EXPLICIT_USER_ASSUMPTION'}}]
    return post<Financial>('/api/v1/finance/analyses',{feasibilityAssessmentId:v.assessmentId,sellableAreaM2:v.sellableAreaM2,expectedSalePricePerM2:v.salePricePerM2,currencyCode:v.currencyCode,otherRevenue:0,additionalCost:v.additionalCost??0,financingCost:v.financingCost??0,taxesAndFees:v.taxesAndFees??0,participants})
  },onSuccess:()=>void qc.invalidateQueries({queryKey:['phase515-finance',caseId]})})
  return <Space direction="vertical" size="middle" style={{width:'100%'}}><Alert type="warning" showIcon message="ارزش فروش و درصد مشارکت نباید از مدل زبانی حدس زده شود" description="تمام قیمت‌ها و سهم‌ها فرضیه کارشناسی هستند و پیش‌بینی قطعی، قرارداد یا قیمت بازار محسوب نمی‌شوند."/>
    <Card title="ورودی‌های صریح مالی"><Form form={form} layout="vertical" onFinish={v=>mutation.mutate(v)} initialValues={{currencyCode:'IRR',additionalCost:0,financingCost:0,taxesAndFees:0,ownerPercent:50}}>
      <Form.Item name="assessmentId" label="امکان‌سنجی تأییدشده" rules={[{required:true}]}><Select options={(assessments.data??[]).filter(x=>x.status==='FEASIBLE'&&Number(x.readinessScore)>0).map(x=>({label:`${x.status} ${x.id}`,value:x.id}))}/></Form.Item>
      <Form.Item name="sellableAreaM2" label="متراژ مفید فروش (تأییدشده)" rules={[{required:true}]}><InputNumber min={0.01} style={{width:'100%'}}/></Form.Item>
      <Form.Item name="salePricePerM2" label="قیمت فروش هر متر بر اساس ارزیابی کارشناسی" rules={[{required:true}]}><InputNumber min={0} style={{width:'100%'}}/></Form.Item>
      <Form.Item name="currencyCode" label="ارز"><Select options={['IRR','TOMAN'].map(value=>({value,label:value}))}/></Form.Item>
      <Form.Item name="ownerId" label="شناسه UUID مالک پرونده" rules={[{required:true}]}><Input placeholder="Owner UUID"/></Form.Item>
      <Form.Item name="builderId" label="شناسه UUID سازنده متصل به پرونده" rules={[{required:true}]}><Input placeholder="Project Builder UUID"/></Form.Item>
      <Form.Item name="ownerPercent" label="درصد سهم ارزش مالک" rules={[{required:true}]}><InputNumber min={0} max={100} style={{width:'100%'}}/></Form.Item>
      <Form.Item name="additionalCost" label="هزینه جانبی"><InputNumber min={0} style={{width:'100%'}}/></Form.Item>
      <Form.Item name="financingCost" label="هزینه تأمین مالی"><InputNumber min={0} style={{width:'100%'}}/></Form.Item>
      <Form.Item name="taxesAndFees" label="عوارض و مالیات"><InputNumber min={0} style={{width:'100%'}}/></Form.Item>
      <Checkbox checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}>تأیید می‌کنم تمامی فرضیات مالی از منبع مستقل یا نظر کارشناس وارد شده و خروجی صرفاً سناریوی تحلیلی است.</Checkbox>
      <Form.Item style={{marginTop:16}}><Button type="primary" htmlType="submit" disabled={!confirmed} loading={mutation.isPending}>محاسبه مالی و سهم مشارکت</Button></Form.Item>
      {mutation.isError&&<Alert showIcon type="error" message={err(mutation.error)}/>}</Form></Card>
    <Card title="سوابق تحلیل مالی"><Table<Financial> rowKey="id" size="small" dataSource={results.data??[]} scroll={{x:850}} columns={[{title:'درآمد',dataIndex:'grossRevenue'},{title:'هزینه کل',dataIndex:'totalProjectCost'},{title:'سود',dataIndex:'projectedProfit'},{title:'ROI',dataIndex:'roiPercent'},{title:'ارز',dataIndex:'currencyCode'},{title:'شناسه',dataIndex:'id',render:v=><Typography.Text copyable>{asText(v)}</Typography.Text>}]} /></Card>
  </Space>
}

export function ScenarioComparisonPanel({caseId}:{caseId:string}) {
  const scenarios=useQuery({queryKey:['phase515-scenarios'],queryFn:()=>api<Scenario[]>('/api/v1/scenarios')})
  const assessments=useQuery({queryKey:['phase515-assessments',caseId],queryFn:()=>api<Assessment[]>(`/api/v1/feasibility/cases/${caseId}/assessments`)})
  const results=useQuery({queryKey:['phase515-finance',caseId],queryFn:()=>api<Financial[]>(`/api/v1/finance/cases/${caseId}/analyses`)})
  const rows=(assessments.data??[]).map(a=>({id:a.id,scenario:scenarios.data?.find(s=>s.id===a.scenarioId)?.nameFa??a.scenarioId,readiness:a.readinessScore,status:a.status,cost:a.scenarioCost,financial:results.data?.find(f=>f.scenarioId===a.scenarioId)}))
  return <Card title="مقایسه نسخه‌دار سناریوها"><Alert showIcon type="info" message="نتایج تنها از Snapshotهای ذخیره‌شده خوانده می‌شوند" description="عدم وجود خروجی مالی به معنی سود صفر نیست. سناریوهای فاقد مدارک یا قیمت معتبر، نیازمند بررسی هستند."/><Table rowKey="id" dataSource={rows} style={{marginTop:16}} locale={{emptyText:<Empty description="هنوز سناریوی محاسبه‌شده‌ای برای این پرونده وجود ندارد"/>}} scroll={{x:850}} columns={[{title:'سناریو',dataIndex:'scenario'},{title:'وضعیت',dataIndex:'status'},{title:'آمادگی',dataIndex:'readiness'},{title:'هزینه',dataIndex:'cost'},{title:'درآمد',render:(_,r)=>asText(r.financial?.grossRevenue)},{title:'سود',render:(_,r)=>asText(r.financial?.projectedProfit)},{title:'ROI',render:(_,r)=>asText(r.financial?.roiPercent)}]}/><Descriptions size="small" bordered column={1}><Descriptions.Item label="نوع تحلیل">برآورد غیرقراردادی؛ مشروط به صحت منابع و ورودی‌ها</Descriptions.Item></Descriptions></Card>
}
