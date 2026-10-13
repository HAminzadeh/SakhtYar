import { Alert, Button, Card, Form, Input, Space, Table, Tag, Typography } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
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
    <Alert showIcon type="info" message="Ø§Ù…ØªÛŒØ§Ø² ØªØ·Ø¨ÛŒÙ‚ Ø³Ø§Ø²Ù†Ø¯Ù‡ ØµØ±ÙØ§Ù‹ Ù¾ÛŒØ´Ù†Ù‡Ø§Ø¯ Ù‚Ø§Ø¨Ù„ Ø¨Ø±Ø±Ø³ÛŒ Ø§Ø³ØªØŒ Ù†Ù‡ ØªØ£ÛŒÛŒØ¯ ØµÙ„Ø§Ø­ÛŒØª ÛŒØ§ ØªØµÙ…ÛŒÙ… Ù†Ù‡Ø§ÛŒÛŒ."/>
    <Card title="ØªØ·Ø¨ÛŒÙ‚ Ø³Ø§Ø²Ù†Ø¯Ú¯Ø§Ù†"><Button type="primary" onClick={()=>match.mutate()} loading={match.isPending}>Ø¬Ø³Øªâ€ŒÙˆØ¬ÙˆÛŒ Ø³Ø§Ø²Ù†Ø¯Ú¯Ø§Ù† Ù…Ù†Ø§Ø³Ø¨ Ø§ÛŒÙ† Ù¾Ø±ÙˆÙ†Ø¯Ù‡</Button>
      {match.isError&&<Alert style={{marginTop:12}} type="error" message="Ø§Ø¬Ø±Ø§ÛŒ ØªØ·Ø¨ÛŒÙ‚ Ù†Ø§Ù…ÙˆÙÙ‚ Ø¨ÙˆØ¯" description={String(match.error)}/>}
      {link.isError&&<Alert style={{marginTop:12}} type="error" message="Ø§ØªØµØ§Ù„ Ø³Ø§Ø²Ù†Ø¯Ù‡ Ù†Ø§Ù…ÙˆÙÙ‚ Ø¨ÙˆØ¯" description={String(link.error)}/>}
      <Table<Match> style={{marginTop:16}} rowKey="builderId" dataSource={match.data?.matches??[]} scroll={{x:750}} pagination={{pageSize:10}}
        columns={[{title:'Ù†Ø§Ù… Ø³Ø§Ø²Ù†Ø¯Ù‡',dataIndex:'legalName'},{title:'Ø§Ù…ØªÛŒØ§Ø²',dataIndex:'score'},
          {title:'Ø¯Ù„Ø§ÛŒÙ„ Ù¾ÛŒØ´Ù†Ù‡Ø§Ø¯',dataIndex:'reasons',render:(v:string[]|undefined)=><Typography.Text>{(v??[]).join('ØŒ ')||'Ø°Ú©Ø± Ù†Ø´Ø¯Ù‡'}</Typography.Text>},
          {title:'Ø§Ù‚Ø¯Ø§Ù…',render:(_,r)=><Button disabled={!hasPermission('USER_MANAGE')||link.isPending} onClick={()=>link.mutate(r.builderId)}>Ø§ØªØµØ§Ù„ Ù¾ÛŒØ´Ù†Ù‡Ø§Ø¯ÛŒ</Button>}]}/>
    </Card>
    <Card title="Ø³Ø§Ø²Ù†Ø¯Ú¯Ø§Ù† Ù…Ø±ØªØ¨Ø· Ø¨Ø§ Ù¾Ø±ÙˆÙ†Ø¯Ù‡" loading={links.isLoading}>
      <Table<ProjectBuilder> rowKey="id" dataSource={links.data??[]} scroll={{x:550}} columns={[
        {title:'Ø´Ù†Ø§Ø³Ù‡ Ø³Ø§Ø²Ù†Ø¯Ù‡',dataIndex:'builderId',ellipsis:true},{title:'Ù†Ù‚Ø´',dataIndex:'role'},
        {title:'ÙˆØ¶Ø¹ÛŒØª',dataIndex:'status',render:(v:string)=><Tag>{v}</Tag>}]}/>
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
    <Alert type="warning" showIcon message="Ø«Ø¨Øª Ù¾ÛŒØ´â€ŒÙ†ÙˆÛŒØ³ ÛŒØ§ Ø¨Ø±Ø±Ø³ÛŒ Ø³Ø§Ø®ØªØ§Ø±ÛŒ Ø¨Ù‡ Ù…Ø¹Ù†ÛŒ ØªØ£ÛŒÛŒØ¯ Ø­Ù‚ÙˆÙ‚ÛŒ Ù‚Ø±Ø§Ø±Ø¯Ø§Ø¯ Ù†ÛŒØ³Øª."/>
    {hasPermission('CASE_WRITE')&&<Card title="Ø§ÛŒØ¬Ø§Ø¯ Ù¾ÛŒØ´â€ŒÙ†ÙˆÛŒØ³ Ù‚Ø±Ø§Ø±Ø¯Ø§Ø¯">
      <Form layout="inline" onFinish={v=>create.mutate(v as {contractCode:string;title:string})}>
        <Form.Item name="contractCode" label="Ú©Ø¯ Ù‚Ø±Ø§Ø±Ø¯Ø§Ø¯" rules={[{required:true}]}><Input maxLength={80}/></Form.Item>
        <Form.Item name="title" label="Ø¹Ù†ÙˆØ§Ù†" rules={[{required:true}]}><Input maxLength={300}/></Form.Item>
        <Form.Item><Button type="primary" htmlType="submit" loading={create.isPending}>Ø«Ø¨Øª Ù¾ÛŒØ´â€ŒÙ†ÙˆÛŒØ³</Button></Form.Item>
      </Form>{create.isError&&<Alert type="error" message="Ø«Ø¨Øª Ù‚Ø±Ø§Ø±Ø¯Ø§Ø¯ Ù†Ø§Ù…ÙˆÙÙ‚ Ø¨ÙˆØ¯" description={String(create.error)}/>}
    </Card>}
    <Card title="Ø³ÙˆØ§Ø¨Ù‚ Ù‚Ø±Ø§Ø±Ø¯Ø§Ø¯" loading={contracts.isLoading}>
      {contracts.isError&&<Alert type="error" message="Ø¯Ø±ÛŒØ§ÙØª Ø³ÙˆØ§Ø¨Ù‚ Ù‚Ø±Ø§Ø±Ø¯Ø§Ø¯ Ù†Ø§Ù…ÙˆÙÙ‚ Ø¨ÙˆØ¯"/>}
      <Table<Contract> rowKey="id" dataSource={contracts.data??[]} scroll={{x:680}} columns={[
        {title:'Ú©Ø¯',dataIndex:'contractCode'},{title:'Ø¹Ù†ÙˆØ§Ù†',dataIndex:'title'},
        {title:'Ù†ÙˆØ¹',dataIndex:'contractType'}, {title:'ÙˆØ¶Ø¹ÛŒØª',dataIndex:'status'},
        {title:'Ù†Ø³Ø®Ù‡',dataIndex:'schemaVersion'}]}/>
    </Card>
    {review.data&&<Card title="Ø¨Ø±Ø±Ø³ÛŒ Ø³Ø§Ø®ØªØ§Ø±ÛŒ Ù‚Ø±Ø§Ø±Ø¯Ø§Ø¯">
      <Typography.Paragraph>Ø¯Ø±ØµØ¯ Ú©Ø§Ù…Ù„â€ŒØ¨ÙˆØ¯Ù† Ø·Ø¨Ù‚ Ù…ÙˆØªÙˆØ± Backend: {Math.round(Number(review.data.completeness)*100)}Ùª</Typography.Paragraph>
      <Typography.Paragraph>Ø¨Ù†Ø¯Ù‡Ø§ÛŒ Ø­Ø§Ø¶Ø±: {(review.data.presentClauses??[]).join('ØŒ ')||'Ù†Ø§Ù…Ø´Ø®Øµ'}</Typography.Paragraph>
      <Typography.Paragraph>Ø¨Ù†Ø¯Ù‡Ø§ÛŒ Ù…ÙÙ‚ÙˆØ¯: {(review.data.missingClauses??[]).join('ØŒ ')||'Ù†Ø¯Ø§Ø±Ø¯'}</Typography.Paragraph>
      <Alert type="info" message={review.data.disclaimer??'Ø¨Ø§Ø²Ø¨ÛŒÙ†ÛŒ Ù…Ø³ØªÙ‚Ù„ Ø­Ù‚ÙˆÙ‚ÛŒ Ø§Ù„Ø²Ø§Ù…ÛŒ Ø§Ø³Øª.'}/>
    </Card>}
    {review.isError&&<Alert type="warning" message="Ø¨Ø±Ø±Ø³ÛŒ Ø³Ø§Ø®ØªØ§Ø±ÛŒ ÙØ¹Ù„Ø§Ù‹ Ø¯Ø± Ø¯Ø³ØªØ±Ø³ Ù†ÛŒØ³Øª."/>}
  </Space>
}