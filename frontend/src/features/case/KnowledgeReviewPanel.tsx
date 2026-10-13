import { Alert, Button, Card, Form, Input, Select, Space, Table, Tag, Typography } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api } from '../../api/client'
type Rule = { id: string; stable_key: string; numeric_value: string | number; page_from: number; original_name: string; sha256: string }
type Release = { id: string; release_key: string; status: string; rules: number; approved: number }
const send = (url: string, payload?: unknown) => api<unknown>(url,{method:'POST',body:payload===undefined?undefined:JSON.stringify(payload)})
export function KnowledgeReviewPanel({reviewRule}: {reviewRule?: {id:string; original_name?:string; page_from?:number; text_value?:string; category?:string}}) {
  const qc=useQueryClient()
  const [id,setId]=useState<string>()
  useEffect(()=>{if(reviewRule?.id)setId(reviewRule.id)},[reviewRule?.id])
  const pending=useQuery({queryKey:['kp-governance-pending'],queryFn:()=>api<Rule[]>('/api/v1/knowledge/admin/governance/pending')})
  const releases=useQuery({queryKey:['kp-governance-releases'],queryFn:()=>api<Release[]>('/api/v1/knowledge/admin/governance/releases')})
  const invalidate=()=>{void qc.invalidateQueries({queryKey:['kp-governance-pending']});void qc.invalidateQueries({queryKey:['kp-governance-releases']})}
  const mutation=useMutation({mutationFn:({url,payload}:{url:string;payload?:unknown})=>send(url,payload),onSuccess:()=>{invalidate();setId(undefined)}})
  return <Space direction="vertical" style={{width:'100%'}} size="middle">
    <Alert type="warning" showIcon message="تأیید قانون یک اقدام حقوقی و کارشناسی است، نه تأیید کیفیت OCR" description="عنوان قانون، واحد، عملگر، حوزه جغرافیایی، تاریخ اعتبار، مرجع و صفحه را در سند اصلی کنترل کنید. انتشار فقط پس از تعیین‌تکلیف همه Ruleهای استخراج‌شده مجاز است."/>
    {pending.isError && <Alert type="error" showIcon
      message="خطا در دریافت فهرست قوانین در انتظار تأیید"
      description={pending.error instanceof Error ? pending.error.message : 'دسترسی به API بازبینی یا ساختار دیتابیس را بررسی کنید.'}
      style={{marginBottom:12}} />}
    {releases.isError && <Alert type="error" showIcon
      message="خطا در دریافت نسخه‌های دانش"
      description={releases.error instanceof Error ? releases.error.message : 'پایگاه داده و Migration را بررسی کنید.'}
      style={{marginBottom:12}} />}
    <Card title="Ruleهای منتظر بازبینی"><Table<Rule> rowKey="id" dataSource={pending.data??[]} size="small" pagination={{pageSize:8}} scroll={{x:850}} columns={[{title:'قاعده',dataIndex:'stable_key'},{title:'عدد استخراج‌شده',dataIndex:'numeric_value'},{title:'سند',dataIndex:'original_name'},{title:'صفحه',dataIndex:'page_from'},{title:'اقدام',render:(_,r)=><Button onClick={()=>setId(r.id)}>بازبینی</Button>}]}/></Card>
    {id && <Card title={`بازبینی ${id}`}>
      {reviewRule?.id===id && <Alert type="info" showIcon message={`${reviewRule.original_name ?? 'سند'} — صفحه ${reviewRule.page_from ?? '—'}`} description={reviewRule.text_value ?? 'شاهد متن را در PDF بررسی کنید.'} style={{marginBottom:16}} />}
      <Form layout="vertical" onFinish={v=>mutation.mutate({url:`/api/v1/knowledge/admin/governance/rules/${id}/review`,payload:v})} initialValues={{decision:'APPROVED',country:'IR',city:'تهران'}}>
      <Form.Item name="decision" label="تصمیم"><Select options={[{value:'APPROVED',label:'تأیید کارشناسی'},{value:'REJECTED',label:'رد نامعتبر'}]}/></Form.Item>
      <Form.Item name="subjectCode" label="نوع ضابطه (نام Enum موتور)" rules={[{required:true}]}><Select options={['MAX_FLOORS','MAX_FAR','MAX_COVERAGE_PERCENT','MIN_PASSAGE_WIDTH','MIN_FRONTAGE','MAX_HEIGHT'].map(value=>({value,label:value}))}/></Form.Item>
      <Form.Item name="operator" label="عملگر" rules={[{required:true}]}><Select options={['<=','>=','=','<','>'].map(value=>({value,label:value}))}/></Form.Item>
      <Form.Item name="unitCode" label="واحد" rules={[{required:true}]}><Select options={['FLOOR','RATIO','PERCENT','M'].map(value=>({value,label:value}))}/></Form.Item>
      <Form.Item name="country" label="کشور ISO" rules={[{required:true}]}><Input/></Form.Item>
      <Form.Item name="city" label="شهر" rules={[{required:true}]}><Input/></Form.Item>
      <Form.Item name="district" label="منطقه (اختیاری)"><Input/></Form.Item>
      <Form.Item name="sourceAuthority" label="مرجع رسمی سند (نیازمند بررسی انسانی)" rules={[{required:true}]}><Input/></Form.Item>
      <Form.Item name="validFrom" label="تاریخ شروع اعتبار - YYYY-MM-DD" rules={[{required:true}]}><Input/></Form.Item>
      <Form.Item name="validTo" label="تاریخ پایان اعتبار - YYYY-MM-DD"><Input/></Form.Item>
      <Form.Item name="notes" label="توضیح و توجیه کارشناسی"><Input.TextArea rows={3}/></Form.Item>
      <Space><Button type="primary" htmlType="submit" loading={mutation.isPending}>ثبت تصمیم</Button><Button onClick={()=>setId(undefined)}>انصراف</Button></Space>
    </Form></Card>}
    <Card title="نسخه‌های دانش و انتشار"><Table<Release> rowKey="id" dataSource={releases.data??[]} size="small" scroll={{x:720}} columns={[{title:'نسخه',dataIndex:'release_key'},{title:'وضعیت',dataIndex:'status',render:v=><Tag color={v==='PUBLISHED'?'green':'orange'}>{v}</Tag>},{title:'کل قواعد',dataIndex:'rules'},{title:'تأییدشده',dataIndex:'approved'},{title:'انتشار',render:(_,r)=><Button loading={mutation.isPending} disabled={r.status!=='DRAFT'||r.rules!==r.approved||r.approved<1} onClick={()=>mutation.mutate({url:`/api/v1/knowledge/admin/governance/releases/${r.id}/publish`})}>انتشار نسخه</Button>}]}/><Typography.Paragraph type="secondary">قواعد ردشده نیز باید تعیین‌تکلیف شوند. اگر تعداد کل با تعداد تأیید برابر نباشد، انتشار از UI غیرفعال است؛ API نیز تمام شروط را مستقلاً بررسی می‌کند.</Typography.Paragraph></Card>
    {mutation.isError&&<Alert type="error" showIcon message={mutation.error instanceof Error?mutation.error.message:'خطا در عملیات'}/>}
  </Space>
}
