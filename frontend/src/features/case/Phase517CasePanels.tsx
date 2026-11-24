import { Alert, Button, Card, Form, Input, InputNumber, Space, Table, Tag, Typography } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { ContextHelp } from '../../ui/ContextHelp'
import { useState } from 'react'

type Match = {builderId:string;legalName:string;tradeName?:string;score:number;reasons?:string[];evidence?:Record<string,unknown>}
type Matches = {caseId:string;candidateCount:number;matches:Match[]}
type ProjectBuilder = {id:string;builderId:string;role?:string;status?:string;primary?:boolean;proposedSharePercent?:number}
type Invitation = {id:string;caseId:string;builderId:string;status:string;expiresAt:string;createdAt:string}
type ProposalVersion = {id:string;proposalId:string;revisionNo:number;snapshot:Record<string,unknown>;recordedAt:string}
type Proposal = {id:string;caseId:string;builderId:string;invitationId:string;status:string;revisionNo:number;proposedSharePercent:number;durationMonths:number;terms?:Record<string,unknown>}
type Contract = {id:string;contractCode:string;status:string;title:string;contractType:string;schemaVersion:string;createdAt?:string}
type Review = {contractId:string;completeness:number;presentClauses:string[];missingClauses:string[];disclaimer?:string}

export function Phase517BuilderMatchPanel({caseId}:{caseId:string}) {
  const {hasPermission}=useAuth()
  const qc=useQueryClient()
  const canWrite=hasPermission('CASE_WRITE')
  const [historyFor,setHistoryFor]=useState<string|null>(null)
  const links=useQuery<ProjectBuilder[]>({queryKey:['phase517-case-builders',caseId],queryFn:()=>api<ProjectBuilder[]>(`/api/v1/builders/cases/${caseId}`)})
  const invitations=useQuery<Invitation[]>({queryKey:['builder-invitations',caseId],queryFn:()=>api<Invitation[]>(`/api/v1/builder-workflow/cases/${caseId}/invitations`)})
  const proposals=useQuery<Proposal[]>({queryKey:['builder-proposals',caseId],queryFn:()=>api<Proposal[]>(`/api/v1/builder-workflow/cases/${caseId}/proposals`)})
  const match=useMutation<Matches>({mutationFn:()=>api<Matches>(`/api/v1/builders/cases/${caseId}/matches`,{method:'POST',body:'{}'})})
  const invite=useMutation({mutationFn:(builderId:string)=>api<Invitation>(`/api/v1/builder-workflow/cases/${caseId}/invitations`,{
    method:'POST',body:JSON.stringify({builderId,validDays:14})}),onSuccess:()=>void qc.invalidateQueries({queryKey:['builder-invitations',caseId]})})
  const decide=useMutation({mutationFn:({id,action}:{id:string;action:string})=>api<Proposal>(`/api/v1/builder-workflow/proposals/${id}/decision`,{
    method:'POST',body:JSON.stringify({action})}),onSuccess:()=>{
      void qc.invalidateQueries({queryKey:['builder-proposals',caseId]});void qc.invalidateQueries({queryKey:['phase517-case-builders',caseId]})
    }})
  const history=useQuery<ProposalVersion[]>({queryKey:['builder-proposal-history',historyFor],enabled:Boolean(historyFor),
    queryFn:()=>api<ProposalVersion[]>(`/api/v1/builder-workflow/proposals/${historyFor}/versions`),retry:0})
  const draft=useMutation({mutationFn:(id:string)=>api<Contract>(`/api/v1/builder-workflow/proposals/${id}/contract-draft`,{method:'POST'}),
    onSuccess:()=>void qc.invalidateQueries({queryKey:['phase517-case-contracts',caseId]})})
  const cancel=useMutation({mutationFn:(id:string)=>api<Invitation>(`/api/v1/builder-workflow/invitations/${id}/cancel`,{method:'POST'}),
    onSuccess:()=>void qc.invalidateQueries({queryKey:['builder-invitations',caseId]})})
  return <Space direction="vertical" size="middle" style={{width:'100%'}}>
    <ContextHelp topic="builder" />
    <Alert showIcon type="info" message="تطبیق صرفاً رتبه‌بندی براساس اطلاعات ثبت‌شده و بعضاً خوداظهاری است؛ دعوت، پیشنهاد و پذیرش به معنای امضای قرارداد نیست." />
    <Card title="تطبیق و دعوت سازندگان">
      <Button type="primary" onClick={()=>match.mutate()} loading={match.isPending}>یافتن سازندگان مناسب</Button>
      {match.isError&&<Alert style={{marginTop:12}} type="error" message="تطبیق ناموفق بود" description={String(match.error)}/>}
      {invite.isError&&<Alert style={{marginTop:12}} type="error" message="دعوت ناموفق بود؛ اتصال حساب یا بررسی پروفایل را کنترل کنید" description={String(invite.error)}/>}
      <Table<Match> style={{marginTop:16}} rowKey="builderId" dataSource={match.data?.matches??[]} scroll={{x:850}} pagination={{pageSize:10}}
        columns={[{title:'سازنده',dataIndex:'legalName'},{title:'امتیاز / ۱۰۰',dataIndex:'score'},
          {title:'نسخه معیارها',render:(_,r)=>String(r.evidence?.criteriaVersion??'نامشخص')},
          {title:'دلایل',dataIndex:'reasons',render:(v:string[]|undefined)=><Typography.Text>{(v??[]).join('، ')||'ثبت نشده'}</Typography.Text>},
          {title:'اقدام',render:(_,r)=><Button disabled={!canWrite||invite.isPending} onClick={()=>invite.mutate(r.builderId)}>دعوت ۱۴ روزه</Button>}]} />
    </Card>
    <Card title="دعوت‌های پرونده" loading={invitations.isLoading}>
      {invitations.isError&&<Alert type="warning" message="دریافت دعوت‌ها ناموفق بود" description={String(invitations.error)}/>}
      <Table<Invitation> rowKey="id" dataSource={invitations.data??[]} scroll={{x:650}} columns={[
        {title:'شناسه سازنده',dataIndex:'builderId',ellipsis:true},
        {title:'وضعیت',dataIndex:'status',render:(v:string)=><Tag>{v}</Tag>},
        {title:'اعتبار تا',dataIndex:'expiresAt',render:(v:string)=>new Date(v).toLocaleDateString('fa-IR')},
        {title:'اقدام',render:(_,r)=>r.status==='PENDING'&&canWrite?<Button danger size="small" loading={cancel.isPending} onClick={()=>cancel.mutate(r.id)}>لغو</Button>:null}
      ]}/>
    </Card>
    <Card title="مقایسه پیشنهادهای دریافت‌شده" loading={proposals.isLoading}>
      {proposals.isError&&<Alert type="warning" message="دریافت پیشنهادها ناموفق بود" description={String(proposals.error)}/>}
      {decide.isError&&<Alert type="error" message="ثبت تصمیم ناموفق بود" description={String(decide.error)}/>}
      {draft.isError&&<Alert type="error" message="ایجاد پیش‌نویس ناموفق بود؛ امکان دارد قبلاً ایجاد شده باشد" description={String(draft.error)}/>}
      <Table<Proposal> rowKey="id" dataSource={proposals.data??[]} scroll={{x:1000}} columns={[
        {title:'سازنده',dataIndex:'builderId',ellipsis:true},{title:'سهم پیشنهادی %',dataIndex:'proposedSharePercent'},
        {title:'مدت (ماه)',dataIndex:'durationMonths'},{title:'نسخه',dataIndex:'revisionNo'},
        {title:'شرایط و تعهدات',dataIndex:'terms',render:(value:Record<string,unknown>|undefined)=><Space direction="vertical" size={0}>
          {Object.entries(value??{}).slice(0,8).map(([key,content])=><Typography.Text key={key}>
            {key}: {typeof content==='string'?content:JSON.stringify(content)}</Typography.Text>)}
        </Space>},
        {title:'وضعیت',dataIndex:'status',render:(v:string)=><Tag>{v}</Tag>},
        {title:'اقدامات',render:(_,r)=><Space wrap>
          <Button size="small" onClick={()=>setHistoryFor(r.id)}>تاریخچه نسخه‌ها</Button>
          {canWrite&&r.status==='ACCEPTED'?<Button size="small" type="primary"
          loading={draft.isPending} onClick={()=>draft.mutate(r.id)}>ایجاد پیش‌نویس بدون امضا</Button>:
          canWrite&&r.status==='SUBMITTED'?<Space wrap>
          <Button type="primary" size="small" loading={decide.isPending} onClick={()=>decide.mutate({id:r.id,action:'ACCEPT'})}>انتخاب سازنده</Button>
          <Button size="small" loading={decide.isPending} onClick={()=>decide.mutate({id:r.id,action:'REQUEST_CHANGES'})}>درخواست اصلاح</Button>
          <Button size="small" danger loading={decide.isPending} onClick={()=>decide.mutate({id:r.id,action:'REJECT'})}>رد</Button>
        </Space>:null}
        </Space>}
      ]}/>
      {historyFor&&<Card size="small" title="تاریخچه تغییرات پیشنهاد" extra={<Button onClick={()=>setHistoryFor(null)}>بستن</Button>} loading={history.isLoading}>
        {history.isError&&<Alert type="warning" message="دریافت تاریخچه ناموفق بود"/>}
        <Table<ProposalVersion> rowKey="id" size="small" dataSource={history.data??[]} columns={[
          {title:'نسخه',dataIndex:'revisionNo'},
          {title:'ثبت‌شده در',dataIndex:'recordedAt',render:(v:string)=>new Date(v).toLocaleString('fa-IR')},
          {title:'محتوا',dataIndex:'snapshot',render:(v:Record<string,unknown>)=><Typography.Text code>{JSON.stringify(v)}</Typography.Text>}
        ]}/>
      </Card>}
    </Card>
    <Card title="سازندگان متصل به پرونده" loading={links.isLoading}>
      <Table<ProjectBuilder> rowKey="id" dataSource={links.data??[]} scroll={{x:700}} columns={[
        {title:'شناسه سازنده',dataIndex:'builderId',ellipsis:true},{title:'نقش',dataIndex:'role'},
        {title:'وضعیت',dataIndex:'status',render:(v:string)=><Tag>{v}</Tag>},
        {title:'سازنده اصلی',dataIndex:'primary',render:(v:boolean)=>v?'بله':'خیر'}]}/>
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
    <Alert type="warning" showIcon message="ثبت پیش‌نویس یا بررسی ساختاری به معنی تأیید حقوقی یا امضای قرارداد نیست."/>
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
      <Typography.Paragraph>درصد کامل‌بودن: {Math.round(Number(review.data.completeness)*100)}٪</Typography.Paragraph>
      <Typography.Paragraph>بندهای حاضر: {(review.data.presentClauses??[]).join('، ')||'نامشخص'}</Typography.Paragraph>
      <Typography.Paragraph>بندهای مفقود: {(review.data.missingClauses??[]).join('، ')||'ندارد'}</Typography.Paragraph>
      <Alert type="info" message={review.data.disclaimer??'بازبینی مستقل حقوقی الزامی است.'}/>
    </Card>}
    {review.isError&&<Alert type="warning" message="بررسی ساختاری فعلاً در دسترس نیست."/>}
  </Space>
}
