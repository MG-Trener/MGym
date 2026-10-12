'use strict';
window.Planner=(()=>{
  const G=window.BenchGenerator, KEY='mg150-planner-v1', BACKUP=KEY+'-before-v3';
  const $=s=>document.querySelector(s), form=$('#generator-form');
  let state={version:1,active:'original',plans:[],logs:{}},original,draft,incoming,lastRaw=null,broken=false,pending=null;
  let discipline=new URLSearchParams(location.search).get('discipline')||'bench';if(!G.disciplines[discipline])discipline='bench';
  const localToday=()=>{const d=new Date();return [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');};
  const status=(message,error=false)=>{const el=$('#storage-status');el.textContent=message;el.classList.toggle('error',error);};
  const active=()=>state.active==='original'?original:state.plans.find(p=>p.id===state.active);
  const logKey=(id=selected)=>state.active+'|'+id;
  function persist(next){
    pending=next;
    try{
      if(broken)throw Error('Повреждённая копия сохранена для восстановления. Откройте «Мои данные» и загрузите исправную копию.');
      if(window.MGymStorage.getItem(KEY)!==lastRaw)throw Error('Данные изменены в другой вкладке. Обновите страницу перед сохранением.');
      next={...next,plans:G.coordinate(next.plans,next.logs,localToday(),next.current)};G.validateState(next);
      const raw=JSON.stringify(next);window.MGymStorage.setItem(KEY,raw);lastRaw=raw;state=next;pending=null;
      status('Сохранено в приложении');return true;
    }catch(e){status('Не сохранено: '+e.message+' Скачайте резервную копию.',true);return false;}
  }
  function activate(id){
    if(!original)return;
    plan=id==='original'?original:state.plans.find(p=>p.id===id);if(!plan)return;
    state.active=id;discipline=plan.profile?.discipline||'bench';$('#discipline-select').value=discipline;$('#empty-plan').hidden=true;$('#weeks').parentElement.hidden=false;$('#detail').hidden=false;
    const today=localToday();selected=plan.sessions.find(s=>'#'+s.id===location.hash)?.id||plan.sessions.find(s=>s.date>=today)?.id||plan.sessions.at(-1).id;
    const select=$('#plan-select');select.replaceChildren(...(discipline==='bench'?[new Option('Исходный план жима · 2026','original')]:[]),...state.plans.filter(p=>(p.profile.discipline||'bench')===discipline).map(p=>new Option(p.name+' · '+p.sessions.at(-1).date,p.id)));select.value=id;
    $('#plan-summary').textContent=plan.meta?summary(plan):'Исходный план на 150 кг. 132,5 × 3 выполнялись без паузы; максимум с паузой не подтверждён.';
    $('#rules').replaceChildren(...plan.rules.map(t=>{const li=document.createElement('li');li.textContent=t;return li;}));
    verified.lastChild.textContent=discipline==='bench'?'Пауза соблюдена, рабочая часть выполнена без боли':'Техника и амплитуда соблюдены, рабочая часть выполнена без боли';renderCalendar();renderDetail();
  }
  function summary(p){const m=p.meta;return fullDate(p.sessions[0].date)+' — '+fullDate(p.sessions.at(-1).date)+' · '+p.sessions.length+' занятий. '+(m.base===null?'Подбор нагрузки без оценки максимума.':'Оценка силы: '+weight(Number(m.base.toFixed(1)))+' (не проверенный 1ПМ).')+' Группа: '+m.ageGroup+'. '+(['bench-natural-3','strength-natural-4'].includes(p.algorithm)?'Адаптивный расчёт. '+(m.lastAdjustment||''):'Прежний расчёт.');}
  function readProfile(){
    const f=new FormData(form),p=Object.fromEntries(f);for(const k of ['age','bodyweight','goal','bar','step','resultWeight','reps'])p[k]=Number(f.get(k));
    p.weekdays=f.getAll('weekday').map(Number);p.resultRpe=f.get('resultRpe')?Number(f.get('resultRpe')):null;p.supervised=f.get('supervised')==='yes';delete p.weekday;p.pain=p.pain==='yes';if(p.resultType==='max')p.reps=1;return p;
  }
  function resultMode(){const mode=form.elements.resultType.value;$('#result-fields').hidden=mode==='unknown';for(const input of $('#result-fields').querySelectorAll('input,select'))input.disabled=mode==='unknown';form.elements.reps.readOnly=mode==='max';if(mode==='max')form.elements.reps.value=1;}
  function openGenerator(){
    form.reset();const p=(active()?.profile?.discipline||'bench')===discipline?active()?.profile:null;
    if(p){for(const [k,v] of Object.entries(p)){const el=form.elements.namedItem(k);if(el&&k!=='weekday'&&k!=='weekdays'&&k!=='pain')el.value=v;}form.elements.pain.value=p.pain?'yes':'no';form.elements.supervised.value=p.supervised?'yes':'no';for(const el of form.querySelectorAll('[name=weekday]'))el.checked=p.weekdays.includes(Number(el.value));}
    else {form.elements.start.value=localToday();form.elements.target.value=G.iso(Date.parse(localToday()+'T00:00:00Z')+56*86400000);form.elements.resultDate.value=localToday();}
    form.elements.discipline.value=discipline;updateTechnique();draft=null;$('#preview').hidden=true;$('#generator-error').textContent='';resultMode();$('#generator-dialog').showModal();
  }
  function openLog(){
    const s=plan.sessions.find(s=>s.id===selected), saved=state.logs[logKey()];
    $('#log-title').textContent=fullDate(s.date)+' · результат';const box=$('#log-sets');box.replaceChildren();let idx=0;
    for(const [ei,ex] of s.exercises.entries()){
      const heading=document.createElement('h3');heading.textContent=ex.name;box.append(heading);
      const table=document.createElement('table');table.className='log-table';table.innerHTML='<thead><tr><th>План</th><th>Факт, кг</th><th>Повт.</th><th>RPE</th></tr></thead><tbody></tbody>';
      ex.sets.forEach((v,j)=>{
        const row=document.createElement('tr'),label=document.createElement('td');label.textContent=(j+1)+'. '+(v.weight===null?'подбор':weight(v.weight))+' × '+v.reps+(v.optional?' *':'');label.title=v.kind;row.append(label);
        const value=saved?.sets[idx];
        for(const [key,max,step] of [['kg',500,.25],['reps',100,1]]){const td=document.createElement('td'),input=document.createElement('input');input.type='number';input.min='0';input.max=String(max);input.step=String(step);input.inputMode='decimal';input.name=key+'-'+idx;input.setAttribute('aria-label',ex.name+', подход '+(j+1)+(key==='kg'?', фактический вес':', фактические повторы'));input.value=value?.[key]??'';td.append(input);row.append(td);}
        const td=document.createElement('td');
        if(v.rpe&&ei===0){const input=document.createElement('select');input.name='rpe-'+idx;input.setAttribute('aria-label','RPE основного движения, подход '+(j+1));input.append(new Option('—',''));for(const n of [5,6,6.5,7,7.5,8,8.5,9,9.5,10])input.append(new Option(n===5?'≤5':String(n),String(n)));input.value=value?.rpe??'';td.append(input);}else td.textContent='—';row.append(td);table.querySelector('tbody').append(row);idx++;
      });box.append(table);
    }
    $('#log-form').elements.verified.checked=saved?.verified===true;
    $('#log-form').elements.status.value=saved?.status||'planned';$('#log-form').elements.feedback.value=saved?.feedback||'ok';$('#log-error').textContent='';logAdvice();$('#log-dialog').showModal();
  }
  function logAdvice(){const val=$('#log-form').elements.feedback.value;$('#log-advice').textContent=val==='pain'?'При боли прекратите упражнение; не пытайтесь компенсировать пропуск. Перед продолжением разберите причину со специалистом.':val==='hard'?'На следующем занятии уменьшите вес на 5–10% и при необходимости уберите последний рабочий подход. После сохранения можно пересмотреть будущие веса. Даты не меняются.':'RPE 8 ≈ 2 повтора в запасе, 9 ≈ 1. * — необязательный подход: только при условии, указанном в плане. Для пересмотра нужны 1–10 повторов с подтверждённой техникой, RPE 6–9 и подтверждение рабочей части. Лёгкие подходы ≤5 не оценивают максимум.';}
  function openReview(){
    const p=active();if(!p)return;
    const advice=G.review(p,state.logs,localToday());let message=advice.message;
    if(advice.change){
      const updated=G.applyReview(p,state.logs,localToday()),next=updated.sessions.find(s=>s.date>localToday()&&!G.hasLog(state.logs[p.id+'|'+s.id]));
      message+=' База: '+weight(p.meta.base===null?null:Number(p.meta.base.toFixed(1)))+' → '+weight(advice.base)+'.';
      if(next)message+=' Ближайшая тренировка '+fullDate(next.date)+': '+next.exercises[0].sets.filter(v=>v.kind!=='Разминка').map(v=>(v.weight===null?'подбор':weight(v.weight))+' × '+v.reps+' · отдых '+rest(v.rest)+(v.rpeTarget?' · RPE '+v.rpeTarget:'')).join('; ')+'.';
    }
    $('#review-message').textContent=message;$('#apply-review').hidden=!advice.change;$('#review-dialog').showModal();
  }
  function upgradeLibrary(){
    if(broken||!state.plans.some(p=>!['bench-natural-3','strength-natural-4'].includes(p.algorithm)))return;
    try{
      let next={...state,plans:state.plans.map(p=>G.upgrade(p,state.logs,localToday()))};next={...next,plans:G.coordinate(next.plans,next.logs,localToday(),next.current)};G.validateState(next);
      if(!window.MGymStorage.getItem(BACKUP))window.MGymStorage.setItem(BACKUP,JSON.stringify(state));
      if(persist(next))status('Расчёт исправлен · даты и записанные подходы сохранены');
    }catch(e){status('Не удалось обновить старый расчёт: '+e.message,true);}
  }
  function boot(data){
    original={...data,id:'original',name:'Путь к 150 кг · 2026'};
    try{lastRaw=window.MGymStorage.getItem(KEY);if(lastRaw)state=G.validateState(JSON.parse(lastRaw));}catch(e){broken=lastRaw!==null;status(broken?'Сохранённые данные повреждены. Оригинал доступен через «Мои данные» → резервная копия.':'Хранилище браузера недоступно. Используйте резервную копию.',true);}
    upgradeLibrary();if(!new URLSearchParams(location.search).has('discipline'))discipline=active()?.profile?.discipline||'bench';chooseDiscipline(discipline);
  }
  function chooseDiscipline(d){
    discipline=d;$('#discipline-select').value=d;
    const candidates=state.plans.filter(p=>(p.profile.discipline||'bench')===d);
    const chosen=candidates.find(p=>p.id===state.active)||candidates.find(p=>p.id===state.current?.[d])||candidates.at(-1);
    if(chosen){activate(chosen.id);return;}if(d==='bench'){activate('original');return;}
    $('#plan-select').replaceChildren(new Option('План ещё не создан',''));$('#plan-summary').textContent='Создайте персональную программу: '+G.disciplines[d];$('#rules').replaceChildren();$('#empty-plan').hidden=false;$('#weeks').parentElement.hidden=true;$('#detail').hidden=true;
  }
  function updateTechnique(){const d=form.elements.discipline.value;const options=form.elements.technique.options;options[0].textContent=d==='bench'?'С паузой 1–2 секунды':d==='squat'?'Глубина и фиксация соблюдены':'С пола, без отбива, полная фиксация';options[1].textContent=d==='bench'?'Без паузы / не уверен':'Техника не подтверждена';}
  $('#discipline-select').addEventListener('change',e=>chooseDiscipline(e.target.value));
  form.elements.discipline.addEventListener('change',updateTechnique);
  $('#new-plan').addEventListener('click',openGenerator);
  $('#plan-select').addEventListener('change',e=>{const next={...state,active:e.target.value,current:{...state.current,[discipline]:e.target.value}};if(e.target.value==='original')delete next.current[discipline];if(persist(next))activate(next.active);});
  document.querySelectorAll('[data-close]').forEach(b=>b.addEventListener('click',()=>b.closest('dialog').close()));
  form.elements.resultType.addEventListener('change',resultMode);
  form.addEventListener('input',()=>{draft=null;$('#preview').hidden=true;});
  form.addEventListener('submit',e=>{
    e.preventDefault();$('#generator-error').textContent='';
    try{
      if(state.plans.length>=20)throw Error('Сохранено 20 программ — предел текущей версии. Экспортируйте копию.');
      draft=G.generate(readProfile());$('#preview-summary').textContent=summary(draft);$('#preview-warnings').replaceChildren(...draft.meta.warnings.map(t=>{const li=document.createElement('li');li.textContent=t;return li;}));
      const first=draft.sessions[0];$('#preview-first').textContent='Первый день: '+fullDate(first.date)+' — '+first.exercises[0].sets.map(v=>(v.weight===null?'подбор':weight(v.weight))+' × '+v.reps+(v.optional?' (только если первый одиночный ≤RPE 6)':'')).join('; ')+'.';$('#preview').hidden=false;$('#preview').scrollIntoView({block:'nearest'});
    }catch(err){$('#generator-error').textContent=err.message;}
  });
  $('#save-plan').addEventListener('click',()=>{
    if(!draft)return;
    const next={...state,plans:[...state.plans,draft],active:draft.id,current:{...state.current,[draft.profile.discipline||'bench']:draft.id}};
    if(!persist(next)){$('#generator-error').textContent=$('#storage-status').textContent;return;}
    history.replaceState(null,'','#'+draft.sessions[0].id);activate(draft.id);draft=null;$('#generator-dialog').close();
  });
  $('#log-form').addEventListener('change',logAdvice);
  $('#log-form').addEventListener('submit',e=>{
    e.preventDefault();const f=new FormData(e.target),s=plan.sessions.find(s=>s.id===selected);let idx=0;
    const sets=s.exercises.flatMap((ex,ei)=>ex.sets.map(v=>{const i=idx++;const n=k=>f.get(k+'-'+i)==null||f.get(k+'-'+i)===''?null:Number(f.get(k+'-'+i));return {kg:n('kg'),reps:n('reps'),rpe:v.rpe&&ei===0?n('rpe'):null};}));
    if(sets.some(v=>(v.kg===null)!==(v.reps===null)||(v.rpe!==null&&v.reps===null))){$('#log-error').textContent='Для выполненного подхода укажите и вес, и повторения. RPE вводится только вместе с результатом.';return;}
    if(f.get('status')==='done'&&!sets.some(v=>v.reps>0)){$('#log-error').textContent='Запишите хотя бы один выполненный подход или оставьте статус «Ещё не завершена».';return;}
    const logs={...state.logs,[logKey()]:{sets,status:f.get('status'),feedback:f.get('feedback'),verified:f.get('verified')==='on'}};
    if(persist({...state,logs})){$('#log-dialog').close();renderCalendar();if(['bench-natural-3','strength-natural-4'].includes(plan.algorithm))openReview();}else $('#log-error').textContent=$('#storage-status').textContent;
  });
  $('#data-button').addEventListener('click',()=>{$('#data-dialog').showModal();});
  $('#export-data').addEventListener('click',()=>{
    const data=broken?lastRaw:JSON.stringify(pending||state,null,2);if(!window.MGymNative.exportBackup(data)){$('#data-message').textContent='Не удалось подготовить копию.';return;}$('#data-message').textContent=broken?'Выберите место для сохранения исходного файла.':'Копия содержит все программы и результаты, включая введённые данные спортсмена и изменения, которые не удалось сохранить в приложении. Храните её у себя.';
  });
  $('#import-data').addEventListener('change',async e=>{
    incoming=null;$('#import-preview').hidden=true;const file=e.target.files[0];if(!file)return;
    try{if(file.size>2000000)throw Error('Файл больше 2 МБ.');incoming=G.validateState(JSON.parse(await file.text()));$('#import-summary').textContent='В копии: '+incoming.plans.length+' программ, '+Object.keys(incoming.logs).length+' записей тренировок. Они будут добавлены. При совпадении программа получит отдельную копию; текущие результаты исходного плана имеют приоритет.';$('#import-preview').hidden=false;$('#data-message').textContent='Файл проверен.';}catch(err){$('#data-message').textContent='Не удалось открыть: '+err.message;}e.target.value='';
  });
  $('#confirm-import').addEventListener('click',()=>{
    if(!incoming)return;
    if(state.plans.length+incoming.plans.length>20){$('#data-message').textContent='После импорта будет больше 20 программ. Файл не добавлен.';return;}
    const map=new Map(),plans=[...state.plans],logs={...state.logs};
    for(const p of incoming.plans){const id=plans.some(v=>v.id===p.id)?'p-'+crypto.randomUUID():p.id;map.set(p.id,id);plans.push({...G.upgrade(p,incoming.logs,localToday()),id});}
    for(const [key,value] of Object.entries(incoming.logs)){const split=key.lastIndexOf('|'),id=key.slice(0,split),suffix=key.slice(split);if(id==='original'){if(!Object.hasOwn(logs,key))logs[key]=value;}else if(map.has(id))logs[map.get(id)+suffix]=value;}
    const next={version:1,plans,logs,active:map.get(incoming.active)||'original'};
    const wasBroken=broken;broken=false;
    if(persist(next)){incoming=null;$('#import-preview').hidden=true;$('#data-message').textContent='Копия восстановлена. Даты сохранены.';activate(next.active);}else {broken=wasBroken;$('#data-message').textContent=$('#storage-status').textContent;}
  });
  window.addEventListener('storage',e=>{if(e.key===KEY)status('Данные изменены в другой вкладке. Обновите страницу, чтобы продолжить.',true);});
  const reviewButton=document.createElement('button');reviewButton.type='button';reviewButton.textContent='Проверить нагрузку по результатам';reviewButton.addEventListener('click',openReview);$('.info-content').append(reviewButton);
  const verified=document.createElement('label');verified.className='check-label';verified.innerHTML='<input type="checkbox" name="verified">Пауза соблюдена, рабочая часть выполнена без боли';$('#log-advice').before(verified);
  $('#apply-review').addEventListener('click',()=>{
    try{const p=G.applyReview(active(),state.logs,localToday());if(persist({...state,plans:state.plans.map(v=>v.id===p.id?p:v)})){activate(p.id);$('#review-dialog').close();status('Поправка применена. '+p.meta.lastAdjustment);}else $('#review-message').textContent=$('#storage-status').textContent;}catch(e){$('#review-message').textContent=e.message;$('#apply-review').hidden=true;}
  });
  const oldCopy=document.createElement('button');oldCopy.type='button';oldCopy.textContent='Копия до обновления v3';oldCopy.addEventListener('click',()=>{
    try{const raw=window.MGymStorage.getItem(BACKUP);if(!raw){$('#data-message').textContent='Прежних программ на этом устройстве не было.';return;}if(!window.MGymNative.exportBackup(raw))throw Error('Не удалось подготовить копию.');}catch(e){$('#data-message').textContent=e.message;}
  });$('#data-dialog .actions').append(oldCopy);
  let scheduleDraft=null;
  const scheduleButton=document.createElement('button');scheduleButton.type='button';scheduleButton.textContent='Изменить цель и расписание';$('.info-content').append(scheduleButton);
  $('#schedule-weekdays').replaceChildren(...[...form.querySelectorAll('.weekday-options label')].map(e=>e.cloneNode(true)));
  scheduleButton.addEventListener('click',()=>{
    const p=active();if($('#plan-select').value===''||!p.profile){status('Изменение расписания доступно для персонального плана. Создайте его по своим данным.');return;}
    const f=$('#schedule-form');f.elements.target.value=p.profile.target;f.elements.goal.value=p.profile.goal;f.elements.recovery.value=p.profile.recovery;
    for(const e of f.querySelectorAll('[name=weekday]'))e.checked=p.profile.weekdays.includes(Number(e.value));
    scheduleDraft=null;$('#apply-schedule').hidden=true;$('#schedule-message').textContent='';$('#schedule-dialog').showModal();
  });
  $('#schedule-form').addEventListener('input',()=>{scheduleDraft=null;$('#apply-schedule').hidden=true;});
  $('#schedule-form').addEventListener('submit',e=>{
    e.preventDefault();try{const f=new FormData(e.target);scheduleDraft=G.replan(active(),state.logs,{target:f.get('target'),goal:Number(f.get('goal')),recovery:f.get('recovery'),weekdays:f.getAll('weekday').map(Number)},localToday());
      $('#schedule-message').textContent='Новая цель: '+fullDate(scheduleDraft.profile.target)+'. Будущих занятий: '+scheduleDraft.sessions.filter(s=>s.date>localToday()).length+'. '+scheduleDraft.meta.lastAdjustment;
      $('#apply-schedule').hidden=false;
    }catch(err){scheduleDraft=null;$('#apply-schedule').hidden=true;$('#schedule-message').textContent=err.message;}
  });
  $('#apply-schedule').addEventListener('click',()=>{if(!scheduleDraft)return;const p=scheduleDraft;if(persist({...state,plans:state.plans.map(x=>x.id===p.id?p:x)})){activate(p.id);$('#schedule-dialog').close();status(p.meta.lastAdjustment);scheduleDraft=null;}});
  function receiveImport(text){
    incoming=null;$('#import-preview').hidden=true;$('#data-dialog').showModal();
    try{incoming=G.validateState(JSON.parse(text));$('#import-summary').textContent='В копии '+incoming.plans.length+' программ и '+Object.keys(incoming.logs).length+' записей. Они будут добавлены; существующие программы сохранятся.';$('#import-preview').hidden=false;$('#data-message').textContent='Копия проверена. Подтвердите добавление.';}
    catch(e){$('#data-message').textContent='Не удалось открыть копию: '+e.message;}
  }
  return {boot,openLog,receiveImport,sessionStatus:id=>state.logs[logKey(id)]?.status};
})();
