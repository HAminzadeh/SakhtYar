import { QuestionCircleOutlined } from '@ant-design/icons'
import { Button, Drawer, Tabs, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { useI18n } from '../i18n/LanguageProvider'
export type HelpArticle={titleFa:string;titleEn:string;bodyFa:string;bodyEn:string}
export type HelpTab={key:string;labelFa:string;labelEn:string;articles:HelpArticle[]}
export function ContextHelpButton({titleFa,titleEn,tabs}:{titleFa:string;titleEn:string;tabs:HelpTab[]}) {
 const [open,setOpen]=useState(false);const {language}=useI18n();const fa=language==='fa'
 const items=useMemo(()=>tabs.map(t=>({key:t.key,label:fa?t.labelFa:t.labelEn,children:<div className="sakhtyar-help-content">{t.articles.map((a,i)=><section className="sakhtyar-help-article" key={`${t.key}-${i}`}><Typography.Title level={4}>{fa?a.titleFa:a.titleEn}</Typography.Title><Typography.Paragraph style={{whiteSpace:'pre-line'}}>{fa?a.bodyFa:a.bodyEn}</Typography.Paragraph></section>)}</div>})),[fa,tabs])
 return <><Button icon={<QuestionCircleOutlined/>} onClick={()=>setOpen(true)}>{fa?'راهنما و آموزش':'Help & Training'}</Button><Drawer open={open} onClose={()=>setOpen(false)} width={760} title={fa?titleFa:titleEn}><Tabs items={items}/></Drawer></>
}