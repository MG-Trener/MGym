// node tools/sync-bench-planner.cjs ../Gym-150
// Copies a reviewed website snapshot; engine files remain byte-identical.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const source=path.resolve(process.argv[2]||'../Gym-150'),dest=path.resolve(__dirname,'../app/src/main/assets/bench-planner');
fs.mkdirSync(dest,{recursive:true});
const files=['index.html','app.js','style.css','generator.js','engine-v2.js','engine-v3.js','planner.js','method.html','plan.json'];
for(const f of files)fs.copyFileSync(path.join(source,f),path.join(dest,f));
fs.writeFileSync(path.join(dest,'plan.json'),fs.readFileSync(path.join(dest,'plan.json'),'utf8').trimEnd()+'\n');
fs.cpSync(path.join(source,'assets'),path.join(dest,'assets'),{recursive:true});
let html=fs.readFileSync(path.join(dest,'index.html'),'utf8');
html=html.replace('<script src="planner.js','<script src="mobile.js" defer></script>\n  <link rel="stylesheet" href="mobile.css">\n  <script src="planner.js');
html=html.replace('target="_blank"','').replace('результат без паузы получит дополнительный запас нагрузки.','результат без паузы уточняется контрольным жимом с паузой.');
html=html.replace('Планы и результаты хранятся в этом браузере.','Планы и результаты хранятся в MGym на этом устройстве.');
html=html.replace('Очистка данных браузера или режим инкогнито могут удалить записи. Скачайте резервную копию; её можно открыть на другом устройстве. Автоматической синхронизации с MGym и другими устройствами пока нет.','Удаление приложения или его данных удалит программы. Сохраните отдельную копию программ. Этот JSON совместим с сайтом: перенос выполняется через файл, автоматической синхронизации нет. Прежние записи из MGym 0.2.6 доступны кнопкой «Прежний план и записи». Копия дневника в настройках не включает программы генератора.');
html=html.replace('<label class="file-label">Загрузить копию<input id="import-data" type="file" accept="application/json,.json"></label>','<button type="button" onclick="" id="native-import">Загрузить копию</button><input id="import-data" type="file" hidden>');
html=html.replace(' onclick=""','');
fs.writeFileSync(path.join(dest,'index.html'),html);
let js=fs.readFileSync(path.join(dest,'planner.js'),'utf8').replaceAll('localStorage.','window.MGymStorage.').replaceAll('Сохранено в этом браузере','Сохранено в приложении').replaceAll('в браузере','в приложении');
js=js.replace("const data=broken?lastRaw:JSON.stringify(pending||state,null,2);const url=URL.createObjectURL(new Blob([data],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='mg150-'+localToday()+(broken?'-recovery':'')+'.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);", "const data=broken?lastRaw:JSON.stringify(pending||state,null,2);if(!window.MGymNative.exportBackup(data)){$('#data-message').textContent='Не удалось подготовить копию.';return;}");
js=js.replace("const url=URL.createObjectURL(new Blob([raw],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='mg150-before-v3.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);","if(!window.MGymNative.exportBackup(raw))throw Error('Не удалось подготовить копию.');");
js=js.replace("const url=URL.createObjectURL(new Blob([raw],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='mg150-before-progression.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);","if(!window.MGymNative.exportBackup(raw))throw Error('Не удалось подготовить копию.');");
js=js.replace("const url=URL.createObjectURL(new Blob([raw],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='mg150-before-progression-previous.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);","if(!window.MGymNative.exportBackup(raw))throw Error('Не удалось подготовить копию.');");
js=js.replace("const url=URL.createObjectURL(new Blob([raw],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='mg150-before-progression-v3.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);","if(!window.MGymNative.exportBackup(raw))throw Error('Не удалось подготовить копию.');");
js=js.replaceAll('Прежних программ в этом браузере не было.','Прежних программ на этом устройстве не было.');
js=js.replace("Скачан исходный повреждённый файл для восстановления.","Выберите место для сохранения исходного файла.");
js=js.replace("persist(next);activate(next.active);","if(persist(next))activate(next.active);");
js=js.replace('return {boot,openLog,sessionStatus:', `function receiveImport(text){
    incoming=null;$('#import-preview').hidden=true;$('#data-dialog').showModal();
    try{incoming=G.validateState(JSON.parse(text));$('#import-summary').textContent='В копии '+incoming.plans.length+' программ и '+Object.keys(incoming.logs).length+' записей. Они будут добавлены; существующие программы сохранятся.';$('#import-preview').hidden=false;$('#data-message').textContent='Копия проверена. Подтвердите добавление.';}
    catch(e){$('#data-message').textContent='Не удалось открыть копию: '+e.message;}
  }
  return {boot,openLog,receiveImport,sessionStatus:`);
fs.writeFileSync(path.join(dest,'planner.js'),js);
const manifest={repository:'MG-Trener/Gym-150',commit:process.argv[3]||'working-tree',algorithm:'bench-natural-3 / strength-natural-4',sha256:Object.fromEntries(['generator.js','engine-v2.js','engine-v3.js'].map(f=>[f,crypto.createHash('sha256').update(fs.readFileSync(path.join(dest,f))).digest('hex')]))};
fs.writeFileSync(path.join(dest,'source.json'),JSON.stringify(manifest,null,2)+'\n');
fs.mkdirSync(path.resolve(__dirname,'../tests/bench-planner'),{recursive:true});
for(const f of ['generator.test.cjs','engine-v2.test.cjs','engine-v3.test.cjs','disciplines.test.cjs','programming.test.cjs']){
 const test=fs.readFileSync(path.join(source,f),'utf8').replaceAll("require('./generator.js')","require('../../app/src/main/assets/bench-planner/generator.js')").replaceAll("require('./engine-v2.js')","require('../../app/src/main/assets/bench-planner/engine-v2.js')").replaceAll("require('./engine-v3.js')","require('../../app/src/main/assets/bench-planner/engine-v3.js')");
 fs.writeFileSync(path.resolve(__dirname,'../tests/bench-planner',f),test.trimEnd()+'\n');
}
console.log('Copied planner '+manifest.algorithm+'; calculation files unchanged.');
require('./localize-planner.cjs');
