import { Alert, Button, Card, Form, Input, Space, Table, Tag, Typography } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { ContextHelp } from '../../ui/ContextHelp'
type Match = {builderId:string;legalName:string;tradeName?:string;score:number;reasons?:string[]}
type Matches = {caseId:string;candidateCount:number;matches:Match[]}
type ProjectBuilder = {id:string;builderId:string;role?:string;status?:string;proposedSharePercent?:number}
type Contract = {id:string;contractCode:string;status:string;title:string;contractType:string;schemaVersion:string;createdAt?:string}
type Review = {contractId:string;completeness:number;presentClauses:string[];missingClauses:string[];disclaimer?:string}

export function Phase517BuilderMatchPanel({caseId}:{caseId:string}) {
  const {hasPermission}=useAuth()
  const qc=useQueryClient()
  const links=useQuery<ProjectBuilder[]>({queryKey:['phase517-case-builders',caseId],queryFn:()=>api<ProjectBuilder[]>(`/api/v1/builders/cases/${caseId}`)})
  const match=useMutation<Matches>({mutationFn:()=>api<Matches>(`/api/v1/builders/cases/${caseId}/matches`,{method:'POST',body:'{}'})})
  const link=useMutation({mutationFn:(builderId:string)=>api<ProjectBuilder>(`/api/v1/builders/cases/${caseId}`,{
    method:'POST',body:JSON.stringify({builderId,role:'GENERAL_CONTRACTOR',status:'PROPOSED',primary:false,metadata:{source:'PHASE517_CASE_UI'}})
  }),onSuccess:()=>void qc.invalidateQueries({queryKey:['phase517-case-builders',caseId]})})
  return <Space direction="vertical" size="middle" style={{width:'100%'}}>
    <ContextHelp topic="builder" />
    <Alert showIcon type="info" message="امتیاز تطبیق سازنده صرفاً پیشنهاد قابل بررسی است، نه تأیید صلاحیت یا تصمیم نهایی."/>
    <Card title="تطبیق سازندگان"><Button type="primary" onClick={()=>match.mutate()} loading={match.isPending}>جست‌وجوی سازندگان مناسب این پرونده</Button>
      {match.isError&&<Alert style={{marginTop:12}} type="error" message="اجرای تطبیق ناموفق بود" description={String(match.error)}/>}
      {link.isError&&<Alert style={{marginTop:12}} type="error" message="اتصال سازنده ناموفق بود" description={String(link.error)}/>}
      <Table<Match> style={{marginTop:16}} rowKey="builderId" dataSource={match.data?.matches??[]} scroll={{x:750}} pagination={{pageSize:10}}
        columns={[{title:'نام سازنده',dataIndex:'legalName'},{title:'امتیاز',dataIndex:'score'},
          {title:'دلایل پیشنهاد',dataIndex:'reasons',render:(v:string[]|undefined)=><Typography.Text>{(v??[]).join('، ')||'ذکر نشده'}</Typography.Text>},
          {title:'اقدام',render:(_,r)=><Button disabled={!hasPermission('USER_MANAGE')||link.isPending} onClick={()=>link.mutate(r.builderId)}>اتصال پیشنهادی</Button>}]}/>
    </Card>
    <Card title="سازندگان مرتبط با پرونده" loading={links.isLoading}>
      <Table<ProjectBuilder> rowKey="id" dataSource={links.data??[]} scroll={{x:550}} columns={[
        {title:'شناسه سازنده',dataIndex:'builderId',ellipsis:true},{title:'نقش',dataIndex:'role'},
        {title:'وضعیت',dataIndex:'status',render:(v:string)=><Tag>{v}</Tag>}]}/>
    </Card>
  </Space>
}

export function Phase517ContractsPanel({caseId}:{caseId:string}) {
  const {hasPermission}=useAuth()
  const qc=useQueryClient()
  const contracts=useQuery<Contract[]>({queryKey:['phase517-case-contracts',caseId],queryFn:()=>api<Contract[]>(`/api/v1/contracts/cases/${caseId}`)})
  const review=useQuery<Review>({queryKey:['phase517-contract-review',caseId],enabled:Boolean(contracts.data?.length),queryFn:()=>api<Review>(`/api/v1/contracts/cases/${caseId}/review`),retry:0})
  const create=useMutation({mutationFn:(v:{contractCode:string;title:string})=>api<Contract>(`/api/v1/contracts/cases/${caseId}`,{
    method:'POST',body:JSON.stringify({contractCode:v.contractCode.trim(),title:v.title.trim(),contractType:'PARTICIPATION',status:'DRAFT',schemaVersion:'1.0',terms:{},metadata:{source:'PHASE517_UI',requiresLegalReview:true}})
  }),onSuccess:()=>{void qc.invalidateQueries({queryKey:['phase517-case-contracts',caseId]});void qc.invalidateQueries({queryKey:['phase517-contract-review',caseId]})}})
  return <Space direction="vertical" size="middle" style={{width:'100%'}}>
    <ContextHelp topic="builder" />
    <Alert type="warning" showIcon message="ثبت پیش‌نویس یا بررسی ساختاری به معنی تأیید حقوقی قرارداد نیست."/>
    {hasPermission('CASE_WRITE')&&<Card title="ایجاد پیش‌نویس قرارداد">
      <Form layout="inline" onFinish={v=>create.mutate(v as {contractCode:string;title:string})}>
        <Form.Item name="contractCode" label="کد قرارداد" rules={[{required:true}]}><Input maxLength={80}/></Form.Item>
        <Form.Item name="title" label="عنوان" rules={[{required:true}]}><Input maxLength={300}/></Form.Item>
        <Form.Item><Button type="primary" htmlType="submit" loading={create.isPending}>ثبت پیش‌نویس</Button></Form.Item>
      </Form>{create.isError&&<Alert type="error" message="ثبت قرارداد ناموفق بود" description={String(create.error)}/>}
    </Card>}
    <Card title="سوابق قرارداد" loading={contracts.isLoading}>
      {contracts.isError&&<Alert type="error" message="دریافت سوابق قرارداد ناموفق بود"/>}
      <Table<Contract> rowKey="id" dataSource={contracts.data??[]} scroll={{x:680}} columns={[
        {title:'کد',dataIndex:'contractCode'},{title:'عنوان',dataIndex:'title'},
        {title:'نوع',dataIndex:'contractType'}, {title:'وضعیت',dataIndex:'status'},
        {title:'نسخه',dataIndex:'schemaVersion'}]}/>
    </Card>
    {review.data&&<Card title="بررسی ساختاری قرارداد">
      <Typography.Paragraph>درصد کامل‌بودن طبق موتور Backend: {Math.round(Number(review.data.completeness)*100)}٪</Typography.Paragraph>
      <Typography.Paragraph>بندهای حاضر: {(review.data.presentClauses??[]).join('، ')||'نامشخص'}</Typography.Paragraph>
      <Typography.Paragraph>بندهای مفقود: {(review.data.missingClauses??[]).join('، ')||'ندارد'}</Typography.Paragraph>
      <Alert type="info" message={review.data.disclaimer??'بازبینی مستقل حقوقی الزامی است.'}/>
    </Card>}
    {review.isError&&<Alert type="warning" message="بررسی ساختاری فعلاً در دسترس نیست."/>}
  </Space>
}
