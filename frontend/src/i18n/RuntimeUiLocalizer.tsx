import { useEffect } from 'react'
import type { LanguageCode } from './messages'
import { translateRuntimeText } from './runtimeCatalog'

const textOriginal=new WeakMap<Text,string>()
const textApplied=new WeakMap<Text,string>()
const attrOriginal=new WeakMap<Element,Map<string,string>>()
const attrApplied=new WeakMap<Element,Map<string,string>>()
const attrs=['placeholder','title','aria-label'] as const

function ignored(node:Node){
  const parent=node instanceof Element?node:node.parentElement
  return Boolean(parent?.closest('script,style,code,pre,[data-i18n-ignore="true"]'))
}
function localizeText(node:Text,language:LanguageCode){
  if(ignored(node))return
  const current=node.nodeValue??''
  const last=textApplied.get(node)
  if(!textOriginal.has(node)||(last!=null&&current!==last))textOriginal.set(node,current)
  const original=textOriginal.get(node)??current
  const next=language==='en'?translateRuntimeText(original):original
  if(next!==current)node.nodeValue=next
  textApplied.set(node,next)
}
function localizeElement(el:Element,language:LanguageCode){
  if(ignored(el))return
  let originals=attrOriginal.get(el)
  let applied=attrApplied.get(el)
  if(!originals){originals=new Map();attrOriginal.set(el,originals)}
  if(!applied){applied=new Map();attrApplied.set(el,applied)}
  for(const name of attrs){
    const current=el.getAttribute(name)
    if(current==null)continue
    const last=applied.get(name)
    if(!originals.has(name)||(last!=null&&current!==last))originals.set(name,current)
    const original=originals.get(name)??current
    const next=language==='en'?translateRuntimeText(original):original
    if(next!==current)el.setAttribute(name,next)
    applied.set(name,next)
  }
}
function walk(root:Node,language:LanguageCode){
  if(root instanceof Text){localizeText(root,language);return}
  if(root instanceof Element)localizeElement(root,language)
  const walker=document.createTreeWalker(root,NodeFilter.SHOW_ELEMENT|NodeFilter.SHOW_TEXT)
  let node=walker.nextNode()
  while(node){
    if(node instanceof Text)localizeText(node,language)
    else if(node instanceof Element)localizeElement(node,language)
    node=walker.nextNode()
  }
}
export function RuntimeUiLocalizer({language}:{language:LanguageCode}){
  useEffect(()=>{
    walk(document.body,language)
    const observer=new MutationObserver(items=>{
      for(const item of items){
        if(item.type==='characterData')localizeText(item.target as Text,language)
        else if(item.type==='attributes'&&item.target instanceof Element)localizeElement(item.target,language)
        else for(const node of item.addedNodes)walk(node,language)
      }
    })
    observer.observe(document.body,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:[...attrs]})
    return()=>observer.disconnect()
  },[language])
  return null
}