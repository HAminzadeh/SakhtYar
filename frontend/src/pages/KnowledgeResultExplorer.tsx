import {Alert,Card,Collapse,Descriptions,Progress,Row,Col,Statistic,Table,Tag,Typography} from 'antd'
import {useQuery} from '@tanstack/react-query'
import {api} from '../api/client'

type ResultData={
 execution:Record<string,unknown>
 documents:Array<Record<string,unknown>>
 pages:Array<Record<string,unknown>>
 rules:Array<Record<string,unknown>>
 facts:Array<Record<string,unknown>>
 catalogCandidates:Array<Record<string,unknown>>
 graph:{nodes:number;edges:number;communities:number}
 release?:Record<string,unknown>|null
}
export function KnowledgeResultExplorer({executionId,status}:{executionId:string;status?:string}){
 const q=useQuery({queryKey:['knowledge-result',executionId],queryFn:()=>api<ResultData>(`/api/v1/knowledge/admin/pipeline/${executionId}/result`),enabled:!!executionId,refetchInterval:status==='RUNNING'?3000:false})
 const d=q.data
 if(!d)return <Card loading={q.isLoading} title="نتایج واقعی پردازش دانش"><Alert type="info" showIcon message="نتیجه هنوز آماده نشده است."/></Card>
 const normalized=d.pages.filter(x=>x.artifact_type==='NORMALIZED_PAGE').length
 const ocr=d.pages.filter(x=>x.artifact_type==='OCR_PAGE').length
 return <Card title="نتایج واقعی پردازش دانش">
  <Alert type={d.release?'success':'info'} showIcon message={d.release?`Knowledge Release منتشر شد: ${String(d.release.release_key)}`:'پردازش در حال ساخت Artifactهای واقعی است.'} description="تمام نتایج زیر مستقیماً از Source Store، Knowledge DB، Graph و Release خوانده می‌شوند؛ مرحله Placeholder نمایش داده نمی‌شود."/>
  <Row gutter={[12,12]} style={{marginTop:16}}>
   <Col xs={12} md={4}><Statistic title="اسناد" value={d.documents.length}/></Col>
   <Col xs={12} md={4}><Statistic title="صفحات نرمال" value={normalized}/></Col>
   <Col xs={12} md={4}><Statistic title="صفحات OCR" value={ocr}/></Col>
   <Col xs={12} md={4}><Statistic title="Rule" value={d.rules.length}/></Col>
   <Col xs={12} md={4}><Statistic title="Graph Nodes" value={d.graph?.nodes??0}/></Col>
   <Col xs={12} md={4}><Statistic title="Communities" value={d.graph?.communities??0}/></Col>
  </Row>
  <Collapse style={{marginTop:16}} items={[
   {key:'docs',label:`اسناد پردازش‌شده (${d.documents.length})`,children:<Table size="small" rowKey={r=>String(r.document_id??r.relative_path)} dataSource={d.documents} columns={[{title:'عنوان',dataIndex:'detected_title'},{title:'دسته',dataIndex:'detected_category'},{title:'صفحات',dataIndex:'page_count'},{title:'مشکوک/OCR',dataIndex:'suspicious_pages'},{title:'SHA-256',dataIndex:'sha256',ellipsis:true}]}/>},
   {key:'pages',label:`Source Store / صفحات (${d.pages.length})`,children:<Table size="small" rowKey={(r,i)=>String(r.document_id)+String(r.page_no)+String(r.artifact_type)+i} pagination={{pageSize:10}} dataSource={d.pages} columns={[{title:'صفحه',dataIndex:'page_no'},{title:'Artifact',dataIndex:'artifact_type',render:v=><Tag>{String(v)}</Tag>},{title:'پردازشگر',dataIndex:'processor_id'},{title:'کیفیت',dataIndex:'quality_score',render:v=><Progress size="small" percent={Math.round(Number(v??0)*100)}/>},{title:'پیش‌نمایش متن',dataIndex:'preview',ellipsis:true}]}/>},
   {key:'rules',label:`Knowledge DB / Rules (${d.rules.length})`,children:<Table size="small" rowKey="id" pagination={{pageSize:10}} dataSource={d.rules} columns={[{title:'کلید',dataIndex:'stable_key',ellipsis:true},{title:'عملگر',dataIndex:'operator'},{title:'مقدار',dataIndex:'numeric_value'},{title:'صفحه',dataIndex:'page_from'},{title:'Confidence',dataIndex:'confidence'},{title:'Evidence',dataIndex:'evidence',ellipsis:true}]}/>},
   {key:'catalog',label:`Catalog Candidates (${d.catalogCandidates.length})`,children:<Table size="small" rowKey={(r,i)=>String(r.normalized_term)+i} pagination={{pageSize:10}} dataSource={d.catalogCandidates} columns={[{title:'عبارت',dataIndex:'display_term'},{title:'Normalized',dataIndex:'normalized_term'},{title:'تکرار',dataIndex:'occurrence_count'},{title:'Confidence',dataIndex:'confidence'},{title:'وضعیت',dataIndex:'status'}]}/>},
   {key:'graph',label:'Knowledge Graph + GraphRAG',children:<Descriptions bordered size="small" column={{xs:1,md:3}}><Descriptions.Item label="Nodes">{d.graph?.nodes??0}</Descriptions.Item><Descriptions.Item label="Edges">{d.graph?.edges??0}</Descriptions.Item><Descriptions.Item label="Communities">{d.graph?.communities??0}</Descriptions.Item></Descriptions>},
   {key:'release',label:'Knowledge Release / Lineage',children:d.release?<Descriptions bordered size="small" column={1}>{Object.entries(d.release).map(([k,v])=><Descriptions.Item key={k} label={k}><Typography.Text code>{String(v??'—')}</Typography.Text></Descriptions.Item>)}</Descriptions>:<Alert type="warning" message="Release هنوز منتشر نشده است."/>}
  ]}/>
 </Card>
}
