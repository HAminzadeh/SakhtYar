import {Alert, Button, Card, Input, Pagination, Select, Space, Spin, Table, Tag, Typography, Image, App} from 'antd'
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query'
import {useEffect, useState} from 'react'
import {api} from '../api/client'

type PageReview = {
  execution_id:string; document_id:string; page_no:number; status:string; reason:string;
  relative_path:string; native_chars:number; ocr_chars:number; review_text?:string;
  reviewed_by?:string; reviewed_at?:string
}
type PageList = {items:PageReview[];total:number;page:number;pageSize:number}
type ImageData = {dataUrl:string}
const keyOf=(r:PageReview)=>`${r.execution_id}:${r.document_id}:${r.page_no}`
const pathOf=(r:PageReview)=>`/api/v1/knowledge/admin/ocr-review/pages/${r.execution_id}/${r.document_id}/${r.page_no}`

export function KnowledgeOcrPageReviewPanel({executionId}:{executionId?:string}) {
  const {message}=App.useApp()
  const qc=useQueryClient()
  const [page,setPage]=useState(1)
  const [status,setStatus]=useState('PENDING')
  const [picked,setPicked]=useState<PageReview|null>(null)
  const [manualText,setManualText]=useState('')
  useEffect(()=>{setPage(1);setPicked(null)},[executionId])
  const query=useQuery({
    queryKey:['knowledge-ocr-reviews',executionId,status,page],
    queryFn:()=>api<PageList>(`/api/v1/knowledge/admin/ocr-review/pages?status=${status}&page=${page}&pageSize=15${executionId?`&executionId=${encodeURIComponent(executionId)}`:''}`),
    refetchInterval:7000,
  })
  const image=useQuery({
    queryKey:['knowledge-ocr-page-image',picked&&keyOf(picked)],
    enabled:!!picked,
    retry:1,
    queryFn:()=>api<ImageData>(`${pathOf(picked!)}/image`),
  })
  const submit=useMutation({
    mutationFn:({item,decision,text}:{item:PageReview;decision:string;text?:string})=>
      api<{status:string;pendingCount:number}>(`${pathOf(item)}/decision`,{
        method:'POST',body:JSON.stringify({decision,text}),
      }),
    onSuccess:r=>{
      message.success(r.pendingCount===0?'همه صفحات این اجرا تعیین تکلیف شدند؛ حالا روی ادامه بزنید':'تصمیم انسانی ثبت شد')
      setPicked(null);setManualText('')
      void qc.invalidateQueries({queryKey:['knowledge-ocr-reviews']})
      void qc.invalidateQueries({queryKey:['kp-state']})
    },
    onError:e=>message.error(e instanceof Error?e.message:'ثبت تصمیم انجام نشد'),
  })
  const open=(r:PageReview)=>{setPicked(r);setManualText(r.review_text??'')}
  return <Space direction="vertical" style={{width:'100%'}} size="middle">
    <Alert type="info" showIcon message="بازبینی انسانی صفحات ناموفق یا کم‌کیفیت OCR"
      description="موتور OCR بقیه صفحات را پردازش می‌کند و صفحه‌های ناخوانا را در این صف می‌گذارد. تصویر اصلی PDF را بررسی کنید: متن دقیق را ثبت کنید یا «مهم نیست» بزنید. تا تعیین تکلیف همه صفحات، ساخت Dataset و استخراج قوانین از این اجرا ادامه نمی‌یابد. ثبت متن، تأیید قانونی بودن آن نیست." />
    <Card title="صفحات نیازمند تصمیم" extra={<Space>
      <Select value={status} style={{width:175}} options={[
        {value:'PENDING',label:'در انتظار تصمیم'},
        {value:'TEXT_PROVIDED',label:'متن انسانی ثبت‌شده'},
        {value:'NOT_IMPORTANT',label:'مهم نیست'},
        {value:'ALL',label:'همه وضعیت‌ها'},
      ]} onChange={v=>{setStatus(v);setPage(1);setPicked(null)}}/>
      <Button onClick={()=>void query.refetch()} loading={query.isFetching}>تازه‌سازی</Button>
    </Space>}>
      {query.isError&&<Alert type="error" showIcon message="خطا در خواندن فهرست" description={query.error instanceof Error?query.error.message:'خطای سرویس'} style={{marginBottom:12}}/>}
      <Table<PageReview> rowKey={keyOf} size="small" loading={query.isPending} dataSource={query.data?.items??[]}
        pagination={false} scroll={{x:850}} columns={[
          {title:'سند',dataIndex:'relative_path',ellipsis:true,width:300},
          {title:'صفحه PDF',dataIndex:'page_no',width:90},
          {title:'کاراکتر Native / OCR',width:175,render:(_,r)=>`${r.native_chars} / ${r.ocr_chars}`},
          {title:'وضعیت',dataIndex:'status',width:155,render:s=><Tag color={s==='PENDING'?'orange':s==='TEXT_PROVIDED'?'blue':'default'}>{s==='PENDING'?'نیازمند بازبینی':s==='TEXT_PROVIDED'?'متن انسانی ثبت شد':'مهم نیست'}</Tag>},
          {title:'تصویر و تصمیم',width:130,render:(_,r)=><Button size="small" onClick={()=>open(r)}>مشاهده صفحه</Button>},
        ]}/>
      <Pagination style={{marginTop:16,textAlign:'center'}} current={page} total={query.data?.total??0}
        pageSize={15} onChange={setPage} showSizeChanger={false}/>
    </Card>
    {picked&&<Card title={`بررسی صفحه ${picked.page_no} — ${picked.relative_path}`}
      extra={<Button onClick={()=>setPicked(null)}>بستن</Button>}>
      <Space direction="vertical" style={{width:'100%'}} size="middle">
        <Typography.Text type="secondary">شناسه اجرا: {picked.execution_id} | دلیل ارجاع: {picked.reason}</Typography.Text>
        {image.isPending&&<Spin tip="بارگذاری تصویر صفحه"><div style={{height:140}}/></Spin>}
        {image.isError&&<Alert type="error" showIcon message="تصویر صفحه باز نشد" description={image.error instanceof Error?image.error.message:'خطای تولید تصویر'}/>}
        {image.data?.dataUrl&&<div style={{width:'100%',maxHeight:720,overflow:'auto',background:'#f4f6f8',padding:16,textAlign:'center'}}>
          <Image src={image.data.dataUrl} alt={`تصویر صفحه ${picked.page_no}`} style={{maxWidth:'100%',height:'auto'}}/>
        </div>}
        <Typography.Text strong>اگر صفحه مهم است، متن دقیق قابل مشاهده در تصویر را وارد کنید:</Typography.Text>
        <Input.TextArea rows={7} showCount maxLength={100000} value={manualText} onChange={e=>setManualText(e.target.value)}
          placeholder="متن کامل و صحیح صفحه را از روی تصویر وارد کنید. متن ثبت‌شده در ادامه استخراج دانش استفاده می‌شود؛ اما به‌عنوان قانون تأییدشده منتشر نمی‌شود."/>
        {picked.status!=='PENDING'&&<Alert type="warning" showIcon message="این صفحه قبلاً تعیین تکلیف شده است. ویرایش آن فقط تا پیش از ادامه Pipeline امکان‌پذیر است."/>}
        <Space wrap>
          <Button type="primary" loading={submit.isPending} disabled={manualText.trim().length<10}
            onClick={()=>submit.mutate({item:picked,decision:'TEXT_PROVIDED',text:manualText.trim()})}>ثبت متن صفحه</Button>
          <Button danger loading={submit.isPending} onClick={()=>submit.mutate({item:picked,decision:'NOT_IMPORTANT'})}>مهم نیست؛ از این صفحه عبور کن</Button>
        </Space>
        <Typography.Text type="secondary">پس از تعیین تکلیف همه صفحات، به تب «وضعیت و پیشرفت» برگردید و «ادامه» را فقط یک بار بزنید.</Typography.Text>
      </Space>
    </Card>}
  </Space>
}
