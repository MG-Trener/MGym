const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('fs'),path=require('path'),http=require('http'),assert=require('assert/strict');
const root=path.resolve(__dirname,'../app/src/main/assets/bench-planner');
fs.mkdirSync('outputs',{recursive:true});const server=http.createServer((req,res)=>{const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!file.startsWith(root+path.sep)&&file!==root){res.writeHead(403).end();return;}const actual=file===root?path.join(root,'index.html'):file;try{res.setHeader('Content-Type',({'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.webp':'image/webp'})[path.extname(actual)]||'text/plain');res.end(fs.readFileSync(actual));}catch{res.writeHead(404).end();}});
(async()=>{await new Promise(r=>server.listen(8766,'127.0.0.1',r));const browser=await chromium.launch({executablePath:process.env.CHROME_PATH,headless:true});let missing=new Set();try{
for(const language of ['ru','en','de','fr','es','pt-BR','zh-Hans','kk']){
 const page=await browser.newPage({viewport:{width:360,height:800}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(tag=>{window.MGymNative={language:()=>tag,read:k=>localStorage.getItem(k),write:(k,v)=>{localStorage.setItem(k,v);return true},exportBackup:()=>true,importBackup:()=>{}}},language);
 const collect=async()=>{if(['ru','kk'].includes(language))return;for(const text of await page.evaluate(()=>{const walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT),result=[];let n;while(n=walker.nextNode()){const p=n.parentElement;if(p&&!p.closest('script,style,option,[translate="no"]')&&p.getClientRects().length&&/[А-Яа-яЁё]/.test(n.data))result.push(n.data.trim())}return result}))if(text)missing.add(text)};
 await page.goto('http://127.0.0.1:8766');await page.locator('#detail table').waitFor();await collect();
 for(const [discipline,kg] of [['bench','120'],['squat','160'],['deadlift','190']]){
  await page.locator('#discipline-select').selectOption(discipline);await page.locator('#new-plan').click();
  const f=page.locator('#generator-form');for(const [name,value] of Object.entries({name:'User <saved> 日本語',age:'35',bodyweight:'90',goal:'200',start:'2026-10-12',target:'2026-12-05',resultWeight:kg,resultDate:'2026-10-10'}))await f.locator(`[name=${name}]`).fill(value);
  await f.locator('[name=experience]').selectOption('trained');await f.locator('[name=resultType]').selectOption('max');
  for(const v of ['0','1','2','3','4','5','6'])await f.locator(`[name=weekday][value="${v}"]`).setChecked(['1','4'].includes(v));
  await f.locator('button[type=submit]').click();await page.locator('#preview').waitFor({state:'visible'});await collect();await page.locator('#save-plan').click();await collect();
  const old=JSON.parse(await page.evaluate(()=>localStorage.getItem('mg150-planner-v1')));const p=old.plans.at(-1);assert.equal(p.profile.discipline,discipline);assert.equal(p.name,'User <saved> 日本語');assert.equal(p.sessions[0].exercises[0].name,{bench:'Жим лёжа с паузой 1–2 с',squat:'Приседания',deadlift:'Становая тяга'}[discipline]);
  await page.locator('#detail button').click();await page.locator('#log-dialog').waitFor({state:'visible'});await collect();await page.locator('[name=kg-0]').fill('20');await page.locator('[name=reps-0]').fill('10');await page.locator('#log-form [name=status]').selectOption('done');await page.locator('#log-form [name=verified]').check();await page.locator('button[form=log-form]').click();await page.locator('#review-dialog').waitFor({state:'visible'});await collect();await page.locator('#review-dialog [data-close]').click();
 }
 await page.locator('.info summary').click();await page.locator('.info-content button').last().click();await page.locator('#schedule-form [name=target]').fill('2027-02-06');await page.locator('#schedule-form button[type=submit]').click();await collect();await page.locator('#apply-schedule').click();
 const before=await page.evaluate(()=>localStorage.getItem('mg150-planner-v1'));await page.reload();await page.locator('#detail table').waitFor();assert.equal(await page.evaluate(()=>localStorage.getItem('mg150-planner-v1')),before);
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);assert.deepEqual(errors,[]);await page.screenshot({path:`outputs/MGym-language-${language}.png`,fullPage:true});
 await page.goto('http://127.0.0.1:8766/method.html');await collect();assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 console.log('PASS',language,'create/log/replan/reload, canonical data and 360px layout');await page.close();
}
assert.deepEqual([...missing],[],'Untranslated Russian text in translated UI');
}finally{await browser.close();server.close()}})().catch(e=>{console.error(e);server.close();process.exitCode=1});