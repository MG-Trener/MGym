/* Расчётный шаблон жима. Коэффициенты — консервативные правила продукта, не прогноз результата. */
(function(root){
  'use strict';
  const DAY=86400000, VERSION=1;
  const iso=n=>new Date(n).toISOString().slice(0,10);
  function date(s){if(typeof s!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(s))throw Error('Укажите корректную дату.');const n=Date.parse(s+'T00:00:00Z');if(!Number.isFinite(n)||iso(n)!==s)throw Error('Некорректная дата.');return n;}
  const days=(a,b)=>(date(b)-date(a))/DAY;
  const round=(n,step,bar)=>Number((bar+Math.max(0,Math.floor((n-bar+1e-8)/step))*step).toFixed(2));
  function validate(p,extended=false){
    const errors=[];
    const range=(v,a,b)=>typeof v==='number'&&Number.isFinite(v)&&v>=a&&v<=b;
    if(!range(p.age,extended?14:18,extended?100:85)||!Number.isInteger(p.age))errors.push('Возраст вне диапазона: 14–100 лет для нового расчёта. До 14 лет нужен индивидуальный план.');
    if(extended&&(p.age<18||p.age>85)&&p.supervised!==true)errors.push('До 18 и после 85 лет подтвердите индивидуальное сопровождение тренером и допуск к нагрузке.');
    if(!range(p.bodyweight,extended?20:35,extended?400:250))errors.push('Проверьте массу тела (20–400 кг для нового расчёта).');
    if(!range(p.goal,10,400))errors.push('Цель в жиме: от 10 до 400 кг.');
    if(!['novice','trained','advanced'].includes(p.experience))errors.push('Укажите стаж.');
    if(!['normal','limited'].includes(p.recovery))errors.push('Укажите восстановление.');
    if(!['gym','dumbbells','none'].includes(p.equipment))errors.push('Укажите оборудование для дополнительных упражнений.');
    if(![1,1.25,2.5,5].includes(p.step)||!range(p.bar,5,25))errors.push('Проверьте вес грифа и минимальный шаг общего веса.');
    let duration;
    try{duration=days(p.start,p.target);if(duration<(extended?14:28)||duration>(extended?730:168))errors.push(extended?'Период: от 2 недель до 2 лет. Длинный срок разбивается на повторяющиеся блоки.':'Выберите период от 4 до 24 недель.');}catch(e){errors.push(e.message);}
    if(!Array.isArray(p.weekdays)||!(extended?[1,2,3,4]:[2,3]).includes(p.weekdays.length)||new Set(p.weekdays).size!==p.weekdays.length||p.weekdays.some(n=>!Number.isInteger(n)||n<0||n>6))errors.push(extended?'Выберите от 1 до 4 разных дней недели.':'Выберите 2 или 3 разных дня недели.');
    else if(!extended){const d=[...p.weekdays].sort((a,b)=>a-b);if(d.some((n,i)=>((d[(i+1)%d.length]-n+7)%7)<2))errors.push('Между жимами нужен хотя бы один свободный день.');}
    if(!extended&&p.recovery==='limited'&&p.weekdays?.length>2)errors.push('При ограниченном восстановлении выберите 2 дня жима.');
    if(extended&&p.resultRpe!=null&&(!range(p.resultRpe,6,9.5)||p.resultRpe*2%1!==0))errors.push('RPE исходного подхода: 6–9,5 или оставьте пустым.');
    if(!['max','set','unknown'].includes(p.resultType))errors.push('Выберите исходный результат.');
    if(p.resultType!=='unknown'){
      if(!range(p.resultWeight,p.bar,400))errors.push('Исходный вес должен быть не меньше грифа и не больше 400 кг.');
      if(!Number.isInteger(p.reps)||!range(p.reps,1,10)||(p.resultType==='max'&&p.reps!==1))errors.push('Для максимума укажите 1 повтор, для рабочего подхода — 1–10.');
      if(!['paused','touch'].includes(p.technique))errors.push('Укажите, была ли пауза.');
      try{const age=days(p.resultDate,p.start);if(age<0||age>180)errors.push('Нужен результат не старше 180 дней на дату начала, выполненный до начала программы.');}catch(e){errors.push(e.message);}
    }
    if(p.pain!==false)errors.push('При текущей боли или ограничении нагрузки автоматический силовой план не создаётся. Сначала согласуйте допустимую нагрузку со специалистом.');
    return errors;
  }
  const set=(weight,reps,rest,kind,rpe=false)=>({weight,reps,rest,kind,rpe});
  function warmups(load,p){
    if(load===null)return [set(null,8,60,'Лёгкая разминка')];
    const weights=[p.bar,...[.45,.65,.8].map(f=>round(load*f,p.step,p.bar))];
    return [...new Set(weights)].filter(w=>w<load).map((w,i)=>set(w,[10,5,3,1][i],i<2?60:120,'Разминка'));
  }
  function accessories(p,light,index){
    if(light||p.equipment==='none')return [];
    const names=p.equipment==='dumbbells'?['Тяга гантели в наклоне','Разведения для задней дельты']:index%2?['Тяга верхнего блока','Разведения для задней дельты']:['Тяга горизонтального блока','Разгибание рук на блоке'];
    return names.map((name,i)=>({name,load:'Подберите вес с запасом 3–4 повтора, без боли и отказа.',sets:Array.from({length:p.age>=60||p.recovery==='limited'?1:2},()=>set(null,i===0?10:12,i===0?90:75,'Вспомогательное'))}));
  }
  function generate(p){
    const errors=validate(p);if(errors.length)throw Error(errors.join('\n'));
    p=JSON.parse(JSON.stringify(p));
    const duration=days(p.start,p.target), unknown=p.resultType==='unknown', novice=p.experience==='novice';
    const estimate=unknown?null:p.resultWeight*(p.resultType==='max'?1:1+p.reps/30);
    const stale=!unknown&&days(p.resultDate,p.start)>60;
    const factor=(p.resultType==='max'?.95:.9)*(p.technique==='touch'?.95:1)*(stale?.95:1);
    const base=unknown?null:Number((estimate*factor).toFixed(2));
    const cautious=p.age>=60||p.recovery==='limited'||novice;
    const ageGroup=p.age<40?'18–39':p.age<60?'40–59':'60–85';
    const warnings=['Цель — ориентир, достижение не гарантируется. Веса не растут автоматически до желаемого рекорда.'];
    if(unknown)warnings.push('Исходного результата нет: показан календарь техники с подбором веса. Килограммы и максимальные попытки появятся только после ввода уверенного рабочего подхода.');
    else {
      if(p.resultType==='set')warnings.push('Оценка по подходу приблизительна; запас повторов не прибавляется к формуле. Начальные веса могут быть занижены.');
      if(p.technique==='touch')warnings.push('Результат без паузы не равен соревновательному. Применён запас нагрузки; точная разница индивидуальна.');
      if(stale)warnings.push('Результату больше 60 дней: расчётная база дополнительно уменьшена.');
      if(p.goal>estimate*1.1)warnings.push('Цель более чем на 10% выше исходной оценки. Это сигнал пересмотреть срок с тренером, а не прогноз достижимости.');
      if(base*.65<p.bar)warnings.push('Гриф ограничивает минимальный вес: используйте более лёгкий гриф, если текущий не оставляет нужного запаса повторов.');
    }
    if(novice)warnings.push('Стаж менее года: программа завершится контрольным подходом с запасом, без обязательного теста 1ПМ.');
    warnings.push(p.age>=60?'60+: меньший начальный объём и до 5 дней без жима перед контрольной датой. Это осторожная настройка, а не оценка ваших способностей.':p.age>=40?'40–59: возраст учитывается в группе наблюдения; объём снижается при ограниченном восстановлении, без произвольного штрафа к силе.':'18–39: стандартный начальный объём. Молодой возраст не означает готовность к отказным тренировкам.');
    const sessions=[];let index=0;const weekCounts={};
    for(let t=date(p.start);t<date(p.target);t+=DAY){
      if(!p.weekdays.includes(new Date(t).getUTCDay()))continue;
      const remaining=(date(p.target)-t)/DAY;if(remaining<=(p.age>=60?5:3))continue;
      const week=Math.floor((t-date(p.start))/DAY/7)+1;
      const slot=weekCounts[week]||0;weekCounts[week]=slot+1;
      const progress=(t-date(p.start))/DAY/duration, taper=remaining<=14;
      const deload=!taper&&week%4===0;
      const technical=slot===1;
      const calibration=unknown||week===1;
      const strength=progress>=.5&&!novice&&!unknown;
      let title,work=[];
      if(unknown){title='Подбор и техника';work=Array.from({length:2},()=>set(null,5,180,'Запас ≥4 повтора'));}
      else if(taper){title='Подводка · лёгкий жим';const load=round(base*(remaining<=7?.6:.7),p.step,p.bar);work=Array.from({length:2},()=>set(load,3,180,'Лёгкий жим'));}
      else if(deload){title='Разгрузочная неделя';const load=round(base*.6,p.step,p.bar);work=Array.from({length:2},()=>set(load,4,180,'Лёгкий жим'));}
      else if(strength&&!technical){
        title='Сила с паузой';
        work=[set(round(base*(progress>.75?.92:.88),p.step,p.bar),1,300,'Одиночный · ≤8',true),...Array.from({length:cautious?2:3},()=>set(round(base*.78,p.step,p.bar),3,240,'Рабочий жим'))];
      }else{
        title=calibration?'Калибровка нагрузки':technical?'Техника с паузой':'Базовый объём';
        const pct=calibration?.65:technical?.65:Math.min(.78,.68+progress*.15);
        work=Array.from({length:cautious||technical?2:3},()=>set(round(base*pct,p.step,p.bar),technical?4:5,180,'Рабочий жим'));
      }
      const load=work[0].weight;
      sessions.push({id:iso(t),date:iso(t),week,title,note:unknown?'Начните с лёгкого грифа, который уверенно контролируете. Прибавляйте минимальный шаг только с запасом ≥4 повтора. Запишите комфортный подход и уточните исходные данные.':'Сохраните 2–4 повтора в запасе. Если вес неожиданно тяжёлый — снизьте на 5–10%; при боли остановитесь. Пропуск не сдвигает календарь.',exercises:[{name:'Жим лёжа с паузой 1–2 с',load:'5–8 мин общей разминки. Пауза без отбива, страховка. RPE только у одиночного жима; лёгкий подход можно не оценивать.',sets:[...warmups(load,p),...work]},...accessories(p,taper||deload,index++)]});
    }
    const maxTest=!unknown&&!novice;
    const opener=unknown?null:round(base*(maxTest?.9:.7),p.step,p.bar);
    const finalSets=[...warmups(opener,p),set(opener,maxTest?1:5,maxTest?300:180,maxTest?'Уверенная первая':'Контроль с запасом',maxTest)];
    if(maxTest){finalSets.push(set(null,1,300,'2-я: по 1-й',true),set(null,1,300,'3-я: по 2-й',true));}
    sessions.push({id:p.target,date:p.target,week:Math.floor(duration/7)+1,title:maxTest?'Контроль / соревнования':'Контроль техники',note:maxTest?'Первый вес — ориентир. Следующие попытки выбирайте со страхующим или тренером после успешной предыдущей. При RPE ≥9 не прибавляйте. Целевой рекорд не обязателен.':'Без отказа и проверки максимума. Сравните качество паузы и комфортный рабочий вес с первой неделей.',exercises:[{name:'Жим лёжа с паузой 1–2 с',load:'Общая разминка, затем подводящие подходы. Отдых перед попыткой 3–5 минут.',sets:finalSets}]});
    return {id:'p-'+(root.crypto?.randomUUID?.()||Date.now()+'-'+Math.random().toString(36).slice(2)),version:VERSION,algorithm:'bench-natural-1',createdAt:new Date().toISOString(),name:(p.name||'Мой план').trim().slice(0,60),profile:p,meta:{base,estimate,ageGroup,relativeGoal:Number((p.goal/p.bodyweight).toFixed(2)),warnings},rules:[...warnings,'Календарь закреплён на момент создания. Повторный вход и пропуски не меняют даты.','RPE 8 ≈ ещё 2 повтора; 9 ≈ 1; 10 — запас отсутствует. При запасе 5+ точная оценка ненадёжна: оставьте поле пустым или выберите ≤5.','Фармакология, стимуляторы и бустеры в расчёте не используются. Важны обычное питание, сон и переносимость тренировок.','Тяжёлый жим выполняйте со страховкой. При боли прекратите упражнение.'],sessions};
  }
  // Импорт содержит только данные. Ограничения также защищают интерфейс от огромных/повреждённых файлов.
  function validatePlan(p){
    if(!p||typeof p.id!=='string'||p.id.length>100||typeof p.name!=='string'||p.name.length>100||!Array.isArray(p.sessions)||p.sessions.length<1||p.sessions.length>650||!Array.isArray(p.rules)||p.rules.length>30||p.rules.some(s=>typeof s!=='string'||s.length>1500))throw Error('Некорректный формат программы.');
    if(!/^p-[a-zA-Z0-9-]+$/.test(p.id)||!['bench-natural-1','bench-natural-2','bench-natural-3'].includes(p.algorithm)||p.version!==1||!p.profile||validate(p.profile,p.algorithm==='bench-natural-3').length||!p.meta||!(p.meta.base===null||Number.isFinite(p.meta.base)&&p.meta.base>0)||typeof p.meta.ageGroup!=='string'||!Number.isFinite(p.meta.relativeGoal))throw Error('Повреждены параметры расчёта.');
    const ids=new Set();let previous='';
    if(p.meta.reviewed!==undefined){if(!Array.isArray(p.meta.reviewed)||p.meta.reviewed.length>650)throw Error('Повреждена история пересмотра.');p.meta.reviewed.forEach(date);}
    if(p.algorithm==='bench-natural-3'){
      if(p.profile.blockStart){date(p.profile.blockStart);if(p.profile.blockStart<p.profile.start||p.profile.blockStart>=p.profile.target)throw Error('Повреждено начало блока.');}
      if(p.profile.baseVerifiedOn)date(p.profile.baseVerifiedOn);
      if(!Array.isArray(p.meta.adjustments)||p.meta.adjustments.length>650||!Array.isArray(p.meta.revisions||[])||(p.meta.revisions||[]).length>650)throw Error('Повреждена история изменений.');
      for(const a of p.meta.adjustments){date(a.date);if(typeof a.signature!=='string'||a.signature.length>30000||typeof a.message!=='string'||a.message.length>2000||!['normal','recovery'].includes(a.mode)||![a.base,a.beforeBase].every(n=>n===null||Number.isFinite(n)&&n>0&&n<=600))throw Error('Повреждена поправка.');}
    }
    for(const s of p.sessions){
      date(s.date);if(s.id!==s.date||ids.has(s.id)||s.date<=previous||!Number.isInteger(s.week)||s.week<1||s.week>210||typeof s.title!=='string'||s.title.length>100||typeof s.note!=='string'||s.note.length>2000||!Array.isArray(s.exercises)||s.exercises.length<1||s.exercises.length>4)throw Error('Повреждён календарь программы.');ids.add(s.id);previous=s.date;
      for(const ex of s.exercises){if(typeof ex.name!=='string'||ex.name.length>150||typeof ex.load!=='string'||ex.load.length>1500||!Array.isArray(ex.sets)||!ex.sets.length||ex.sets.length>20)throw Error('Повреждено упражнение.');for(const v of ex.sets){if(!(v.weight===null||Number.isFinite(v.weight)&&v.weight>=0&&v.weight<=500)||!Number.isInteger(v.reps)||v.reps<1||v.reps>100||!Number.isFinite(v.rest)||v.rest<0||v.rest>900||typeof v.kind!=='string'||v.kind.length>150||typeof v.rpe!=='boolean')throw Error('Повреждены подходы.');}}
    }return p;
  }
  function validateState(v){
    if(!v||v.version!==VERSION||!Array.isArray(v.plans)||v.plans.length>20||typeof v.active!=='string'||!v.logs||typeof v.logs!=='object'||Array.isArray(v.logs))throw Error('Неподдерживаемый файл. Нужна резервная копия MGym версии 1.');
    const ids=new Set(['original']);for(const p of v.plans){validatePlan(p);if(ids.has(p.id))throw Error('Повторяющийся идентификатор плана.');ids.add(p.id);}
    if(!ids.has(v.active))throw Error('Не найден активный план.');
    if(JSON.stringify(v).length>2000000)throw Error('Резервная копия слишком большая.');
    for(const [key,log] of Object.entries(v.logs)){
      const split=key.lastIndexOf('|'),pid=key.slice(0,split),sid=key.slice(split+1);date(sid);if(!ids.has(pid)||(pid!=='original'&&!v.plans.find(p=>p.id===pid).sessions.some(s=>s.id===sid)))throw Error('Запись ссылается на неизвестную тренировку.');
      if(key.length>160||!log||!['planned','done','skipped'].includes(log.status)||!['ok','hard','pain'].includes(log.feedback)||!Array.isArray(log.sets)||log.sets.length>80)throw Error('Повреждён журнал.');
      if(log.verified!==undefined&&typeof log.verified!=='boolean')throw Error('Повреждено подтверждение техники.');
      for(const s of log.sets){for(const [k,max] of [['kg',500],['reps',100],['rpe',10]])if(!(s[k]===null||typeof s[k]==='number'&&Number.isFinite(s[k])&&s[k]>=0&&s[k]<=max))throw Error('Некорректный результат в журнале.');if(s.reps!==null&&!Number.isInteger(s.reps))throw Error('Повторы должны быть целыми.');}
    }
    // Не переносим неизвестные корневые поля из файлов.
    return {version:VERSION,active:v.active,plans:v.plans,logs:v.logs};
  }
  const api={generate,validate,validatePlan,validateState,round,days,iso,VERSION};
  if(typeof module!=='undefined')module.exports=api;else root.BenchGenerator=api;
})(typeof globalThis!=='undefined'?globalThis:this);
