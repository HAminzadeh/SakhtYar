import { Alert, Button, Card, Form, Input, InputNumber, Select, Space, Table, Tag, Typography } from 'antd'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/client'
import { ContextHelp } from '../ui/ContextHelp'
import { useAuth } from '../auth/AuthProvider'

type Builder = {id:string;legalName:string;tradeName?:string;city?:string;province?:string;verified:boolean;createdAt?:string}
type BuilderPage = {items:Builder[];total:number;page:number;size:number}
type Invitation = {id:string;caseId:string;builderId:string;status:string;expiresAt:string}
type Proposal = {id:string;status:string;revisionNo:number}
function InvitationOffer({invitationId}:{invitationId:string}) {
  const qc=useQueryClient()
  const proposal=useQuery<Proposal>({queryKey:['builder-own-proposal',invitationId],
    queryFn:()=>api<Proposal>(`/api/v1/builder-workflow/invitations/${invitationId}/proposal`),retry:0})
  const send=useMutation({mutationFn:(v:{share:number;duration:number;notes?:string;financialConditions?:string;guarantees?:string;obligations?:string})=>api<Proposal>(`/api/v1/builder-workflow/invitations/${invitationId}/proposal`,{
    method:'POST',body:JSON.stringify({proposedSharePercent:v.share,durationMonths:v.duration,terms:Object.fromEntries(
      (['notes','financialConditions','guarantees','obligations'] as const)
       .map(key=>[key,(v[key]??'').trim()] as const).filter(([,value])=>value.length>0))})}),
    onSuccess:()=>void qc.invalidateQueries({queryKey:['builder-own-proposal',invitationId]})})
  const withdraw=useMutation({mutationFn:()=>api<Proposal>(`/api/v1/builder-workflow/proposals/${proposal.data?.id}/withdraw`,{method:'POST'}),
    onSuccess:()=>void qc.invalidateQueries({queryKey:['builder-own-proposal',invitationId]})})
  const canSubmit=!proposal.data || proposal.data.status==='CHANGES_REQUESTED'
  return <Space direction="vertical">
    {proposal.data&&<Tag color={proposal.data.status==='ACCEPTED'?'green':'blue'}>
      پیشنهاد نسخه {proposal.data.revisionNo}: {proposal.data.status}</Tag>}
    {proposal.data?.status==='SUBMITTED'&&<Button danger size="small" loading={withdraw.isPending} onClick={()=>withdraw.mutate()}>انصراف از پیشنهاد</Button>}
    {canSubmit&&<Form layout="vertical" onFinish={v=>send.mutate({share:Number(v.share),duration:Number(v.duration),
      notes:String(v.notes??''),financialConditions:String(v.financialConditions??''),guarantees:String(v.guarantees??''),obligations:String(v.obligations??'')})}>
      <Form.Item name="share" rules={[{required:true}]}><InputNumber placeholder="سهم %" min={0} max={100} precision={4}/></Form.Item>
      <Form.Item name="duration" rules={[{required:true}]}><InputNumber placeholder="مدت (ماه)" min={1} max={240} precision={0}/></Form.Item>
      <Form.Item name="financialConditions" label="شرایط مالی"><Input.TextArea maxLength={1000} rows={2}/></Form.Item>
      <Form.Item name="obligations" label="تعهدات اجرایی"><Input.TextArea maxLength={1000} rows={2}/></Form.Item>
      <Form.Item name="guarantees" label="تضمین‌های پیشنهادی"><Input.TextArea maxLength={1000} rows={2}/></Form.Item>
      <Form.Item name="notes" label="توضیحات"><Input.TextArea maxLength={1000} rows={2}/></Form.Item>
      <Button htmlType="submit" size="small" loading={send.isPending}>ارسال پیشنهاد</Button>
    </Form>}
    {send.isError&&<Alert type="error" message="ارسال پیشنهاد ناموفق بود" description={String(send.error)}/>}
    {withdraw.isError&&<Alert type="error" message="ثبت انصراف ناموفق بود"/>}
  </Space>
}

export function Phase517BuildersPage() {
  const {hasPermission}=useAuth()
  const qc=useQueryClient()
  const [page,setPage]=useState(0)
  const [city,setCity]=useState('')
  const q=useQuery<BuilderPage>({queryKey:['phase517-builders',page,city],
    queryFn:()=>api<BuilderPage>(`/api/v1/builders/page?page=${page}&size=20${city?`&city=${encodeURIComponent(city)}`:''}`),retry:1})
  const inbox=useQuery<Invitation[]>({queryKey:['builder-inbox'],queryFn:()=>api<Invitation[]>('/api/v1/builder-workflow/inbox'),retry:0})
  const respond=useMutation({mutationFn:({id,action}:{id:string;action:'accept'|'decline'})=>api<Invitation>(`/api/v1/builder-workflow/invitations/${id}/${action}`,{method:'POST'}),
    onSuccess:()=>void qc.invalidateQueries({queryKey:['builder-inbox']})})
  const create=useMutation({mutationFn:(v:{legalName:string;city?:string;nationalIdentifier?:string})=>api<Builder>('/api/v1/builders',{
    method:'POST',body:JSON.stringify({...v,verified:false,metadata:{}})}),onSuccess:()=>void qc.invalidateQueries({queryKey:['phase517-builders']})})
  const review=useMutation({mutationFn:(v:{builderId:string;approved:boolean;evidenceReference:string})=>api<Builder>('/api/v1/builder-workflow/admin/review',{
    method:'POST',body:JSON.stringify(v)}),onSuccess:()=>void qc.invalidateQueries({queryKey:['phase517-builders']})})
  const bind=useMutation({mutationFn:(v:{builderId:string;username:string;reviewReference:string})=>api<void>('/api/v1/builder-workflow/admin/bind',{
    method:'POST',body:JSON.stringify(v)}),onSuccess:()=>void qc.invalidateQueries({queryKey:['builder-inbox']})})
  const builders=q.data?.items??[]
  return <Space direction="vertical" size="large" style={{width:'100%'}}>
    <ContextHelp topic="builder" />
    <div><Typography.Title level={2}>سازندگان و دعوت‌های مشارکت</Typography.Title>
      <Typography.Paragraph type="secondary">فهرست سازندگان بررسی‌شده، اطلاعات عمومی و صندوق دعوت حساب متصل به سازنده</Typography.Paragraph></div>
    <Alert type="info" message="رتبه‌بندی و تأیید مدیریتی به معنی گواهی صلاحیت رسمی یا ضمانت اجرای قرارداد نیست." showIcon/>
    <Card title="فهرست سازندگان" loading={q.isLoading}>
      <Input.Search allowClear placeholder="فیلتر شهر (دقیق)" onSearch={v=>{setCity(v.trim());setPage(0)}} style={{maxWidth:360,marginBottom:16}} />
      {q.isError&&<Alert type="error" message="بارگذاری سازندگان ناموفق بود" description={String(q.error)}/>}
      <Table<Builder> rowKey="id" scroll={{x:650}} dataSource={builders}
        pagination={{current:page+1,pageSize:20,total:q.data?.total??0,onChange:v=>setPage(v-1),showSizeChanger:false}} columns={[
        {title:'نام حقوقی',dataIndex:'legalName'},{title:'نام تجاری',dataIndex:'tradeName'},
        {title:'شهر',dataIndex:'city'}, {title:'استان',dataIndex:'province'},
        {title:'بررسی مدیریتی',dataIndex:'verified',render:(v:boolean)=>v?<Tag color="green">بررسی‌شده</Tag>:<Tag>تأییدنشده</Tag>}]} />
    </Card>
    <Card title="صندوق دعوت‌های سازنده" loading={inbox.isLoading}>
      {inbox.isError&&<Alert type="info" showIcon message="برای دریافت دعوت، مدیر باید حساب شما را پس از بررسی به پروفایل سازنده متصل کند." />}
      {respond.isError&&<Alert type="error" message="پاسخ به دعوت ثبت نشد" description={String(respond.error)}/>}
      <Table<Invitation> dataSource={inbox.data??[]} rowKey="id" scroll={{x:800}} columns={[
        {title:'پرونده',dataIndex:'caseId',ellipsis:true},
        {title:'وضعیت',dataIndex:'status',render:(v:string)=><Tag>{v}</Tag>},
        {title:'تاریخ انقضا',dataIndex:'expiresAt',render:(v:string)=>new Date(v).toLocaleDateString('fa-IR')},
        {title:'پاسخ',render:(_,r)=>r.status==='PENDING'?<Space>
          <Button size="small" type="primary" loading={respond.isPending} onClick={()=>respond.mutate({id:r.id,action:'accept'})}>پذیرش</Button>
          <Button size="small" danger loading={respond.isPending} onClick={()=>respond.mutate({id:r.id,action:'decline'})}>رد</Button>
        </Space>:null},
        {title:'ثبت / اصلاح پیشنهاد',render:(_,r)=>r.status==='ACCEPTED'?<InvitationOffer invitationId={r.id}/>:null}
      ]}/>
    </Card>
    {hasPermission('USER_MANAGE')&&<Card title="مدیریت پروفایل و بررسی سازندگان">
      <Alert type="warning" showIcon message="تأیید مدیریتی تنها پس از بررسی واقعی شاهد انجام شود؛ این فرایند گواهی رسمی صادر نمی‌کند."/>
      <Typography.Title level={5}>ثبت پروفایل اولیه (تأییدنشده)</Typography.Title>
      <Form layout="inline" onFinish={v=>create.mutate(v as {legalName:string;city?:string;nationalIdentifier?:string})}>
        <Form.Item name="legalName" label="نام حقوقی" rules={[{required:true}]}><Input maxLength={250}/></Form.Item>
        <Form.Item name="city" label="شهر"><Input maxLength={100}/></Form.Item>
        <Form.Item name="nationalIdentifier" label="شناسه ثبتی"><Input maxLength={30}/></Form.Item>
        <Button htmlType="submit" type="primary" loading={create.isPending}>ثبت</Button>
      </Form>
      {create.isError&&<Alert type="error" message="ثبت ناموفق بود" description={String(create.error)}/>}
      <Typography.Title level={5} style={{marginTop:24}}>ثبت تصمیم بررسی مدیریتی</Typography.Title>
      <Form layout="inline" onFinish={v=>review.mutate(v as {builderId:string;approved:boolean;evidenceReference:string})}>
        <Form.Item name="builderId" label="سازنده" rules={[{required:true}]}><Select showSearch style={{width:220}}
          options={builders.map(b=>({label:b.legalName,value:b.id}))}/></Form.Item>
        <Form.Item name="approved" label="تصمیم" rules={[{required:true}]}>
          <Select style={{width:130}} options={[{label:'تأیید بررسی',value:true},{label:'رد بررسی',value:false}]}/></Form.Item>
        <Form.Item name="evidenceReference" label="مرجع شاهد" rules={[{required:true}]}><Input maxLength={500}/></Form.Item>
        <Button htmlType="submit" loading={review.isPending}>ثبت بررسی</Button>
      </Form>
      {review.isError&&<Alert type="error" message="ثبت بررسی ناموفق بود" description={String(review.error)}/>}
      <Typography.Title level={5} style={{marginTop:24}}>اتصال حساب موجود به سازنده</Typography.Title>
      <Form layout="inline" onFinish={v=>bind.mutate(v as {builderId:string;username:string;reviewReference:string})}>
        <Form.Item name="builderId" label="سازنده" rules={[{required:true}]}><Select showSearch style={{width:220}}
          options={builders.map(b=>({label:b.legalName,value:b.id}))}/></Form.Item>
        <Form.Item name="username" label="حساب موجود" rules={[{required:true}]}><Input maxLength={100}/></Form.Item>
        <Form.Item name="reviewReference" label="شناسه بررسی هویت" rules={[{required:true}]}><Input maxLength={500}/></Form.Item>
        <Button htmlType="submit" loading={bind.isPending}>ثبت اتصال</Button>
      </Form>
      {bind.isError&&<Alert type="error" message="اتصال حساب ناموفق بود" description={String(bind.error)}/>}
      {bind.isSuccess&&<Alert type="success" message="اتصال حساب ثبت شد؛ دسترسی‌ها همچنان تابع مجوزهای سرور هستند."/>}
    </Card>}
  </Space>
}
