'use strict';
// Local presentation catalog. Never translate model objects, form values or imported user data.
(()=>{
 const tags=['ru','en','de','fr','es','pt-BR','zh-Hans','kk'];
 const tag=window.MGymNative?.language?.()||'ru', index=tags.indexOf(tag)-1;
 const catalog=window.MGymCatalog||{};
 const escape=s=>s.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
 const patterns=Object.entries(catalog).filter(([s])=>/\{\d+}/.test(s)).map(([s,v])=>[new RegExp('^'+s.split(/\{\d+}/).map(escape).join('(.+?)')+'$','s'),v,s.length]).sort((a,b)=>b[2]-a[2]);
 function text(source,depth=0){
  if(index<0||depth>12||typeof source!=='string')return source;
  if(catalog[source])return catalog[source][index];
  if(source.includes('; '))return source.split('; ').map(s=>text(s,depth+1)).join('; ');
  for(const [r,v] of patterns){const m=source.match(r);if(m)return v[index].replace(/\{(\d+)}/g,(_,n)=>text(m[Number(n)+1],depth+1));}
  const trim=source.trim();if(trim!==source&&trim)return source.replace(trim,text(trim,depth+1));
  if(source.includes('·'))return source.split(/(\s*·\s*)/).map((s,i)=>i%2?s:text(s,depth+1)).join('');
  const sentences=source.split(/(?<=[.!?])\s+(?=[А-ЯЁ])/);if(sentences.length>1)return sentences.map(s=>text(s,depth+1)).join(' ');
  for(const delimiter of ['\n',': '])if(source.includes(delimiter))return source.split(delimiter).map(s=>text(s,depth+1)).join(delimiter);
  if(/^[✓—] /.test(source))return source.slice(0,2)+text(source.slice(2),depth+1);
  if(/\.$/.test(source))return text(source.slice(0,-1),depth+1)+'.';
  return source;
 }
 const kkMonths=['қаңтар','ақпан','наурыз','сәуір','мамыр','маусым','шілде','тамыз','қыркүйек','қазан','қараша','желтоқсан'];
 const date=s=>{const d=new Date(s+'T12:00:00Z');return tag==='kk'?`${d.getUTCDate()} ${kkMonths[d.getUTCMonth()]} ${d.getUTCFullYear()}`:new Intl.DateTimeFormat(tag,{day:'numeric',month:'long',year:'numeric',timeZone:'UTC'}).format(d);};
 window.MGymI18n={tag,text,date,months:tag==='kk'?kkMonths.map(s=>s.slice(0,3)):Array.from({length:12},(_,m)=>new Intl.DateTimeFormat(tag,{month:'short',timeZone:'UTC'}).format(new Date(Date.UTC(2026,m,1))))};document.documentElement.lang=tag;
 if(index<0)return;
 const originals=new WeakMap();
 function translate(root){
  const walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);let node;
  while(node=walker.nextNode()){
   if(node.parentElement?.closest('script,style,textarea,[translate="no"],#plan-select option:not([value="original"])'))continue;
   const old=originals.get(node),source=old&&node.data===old.rendered?old.source:node.data;
   const rendered=text(source);originals.set(node,{source,rendered});if(node.data!==rendered)node.data=rendered;
  }
  for(const el of root.querySelectorAll?.('[aria-label],[placeholder],[title],img[alt]')||[]){
   for(const attr of ['aria-label','placeholder','title','alt'])if(el.hasAttribute(attr)){const value=el.getAttribute(attr),translated=text(value);if(value!==translated)el.setAttribute(attr,translated);}
  }
 }
 let queued=false;const observer=new MutationObserver(()=>{if(queued)return;queued=true;queueMicrotask(()=>{queued=false;observer.disconnect();translate(document.body);observer.observe(document.body,{subtree:true,childList:true,characterData:true});});});
 document.addEventListener('DOMContentLoaded',()=>{for(const a of document.querySelectorAll('a[href]')){if(/\/(privacy|support)\.html$/.test(a.href))a.href=a.href.replace(/\.html$/,'-en.html');}translate(document.body);observer.observe(document.body,{subtree:true,childList:true,characterData:true});});
})();
