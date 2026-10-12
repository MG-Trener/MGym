/* Общий офлайн-движок. Коэффициенты — прозрачные правила продукта, не обещание результата. */
(function(root){
  'use strict';
  const G=typeof module!=='undefined'?require('./engine-v2.js'):root.BenchGenerator;
  const DAY=86400000, stamp=d=>Date.parse(d+'T00:00:00Z'), add=(d,n)=>G.iso(stamp(d)+n*DAY);
  const median=a=>{const b=[...a].sort((x,y)=>x-y);return b.length%2?b[(b.length-1)/2]:(b[b.length/2-1]+b[b.length/2])/2;};
  const clone=x=>JSON.parse(JSON.stringify(x));
  const hasLog=G.hasLog;
  // Приближённая кривая повторного максимума; RIR = 10 - RPE. Дробные RIR интерполируются.
  const ratios=[1,.955,.922,.892,.863,.837,.811,.786,.762,.739,.717,.697,.677,.658,.64];
  function estimateSet(kg,reps,rpe){
    if(!(Number.isFinite(kg)&&kg>0&&Number.isInteger(reps)&&reps>=1&&reps<=10&&Number.isFinite(rpe)&&rpe>=6&&rpe<=9.5))return null;
    const n=reps+10-rpe,index=n-1,lo=Math.floor(index),hi=Math.ceil(index);
    if(hi>=ratios.length)return null;
    return kg/(ratios[lo]+(ratios[hi]-ratios[lo])*(index-lo));
  }
  function validate(p){return G.validate(p,true);}
  function generate(profile,override=undefined){
    const errors=validate(profile);if(errors.length)throw Error(errors.join('\n'));
    const p=clone(profile), unknown=p.resultType==='unknown', supervised=p.age<18||p.age>85;
    const initial=unknown?null:p.resultType==='max'?p.resultWeight:estimateSet(p.resultWeight,p.reps,p.resultRpe)??(p.reps===1?p.resultWeight:p.resultWeight*(1+p.reps/30));
    const base=override===undefined?initial:override;
    if(base!==null&&(!Number.isFinite(base)||base<p.bar||base>600))throw Error('Некорректная оценка силы.');
    const cautious=p.recovery==='limited'||p.age>=60||supervised||p.experience==='novice';
    const stale=!unknown&&G.days(p.baseVerifiedOn||p.resultDate,p.blockStart||p.start)>60;
    const conservative=p.experience==='novice'||supervised||stale;
    const round=n=>G.round(n,p.step,p.bar), load=f=>base===null||base*f<p.bar?null:round(base*f);
    const make=(weight,reps,rest,kind,role='work',rpeTarget=null)=>({weight,reps,rest,kind,role,rpe:rpeTarget!==null,rpeTarget});
    const warm=top=>top===null?[make(null,8,60,'Разминка','warmup')]:[...new Set([p.bar,round(top*.45),round(top*.7),round(top*.87)])].filter(w=>w<top).map((w,i)=>make(w,[10,6,3,1][i],i<2?60:120,'Разминка','warmup'));
    const sessions=[],counts={};let lastHard=null;
    const blockStart=p.blockStart||p.start;
    for(let d=blockStart;d<p.target;d=add(d,1)){
      const remaining=G.days(d,p.target),week=Math.floor(G.days(p.start,d)/7)+1;
      if(!p.weekdays.includes(new Date(stamp(d)).getUTCDay())||remaining<=(cautious?4:2))continue;
      const slot=counts[week]||0;counts[week]=slot+1;
      const blockWeek=Math.floor(G.days(blockStart,d)/7)+1;
      const taper=remaining<=14,deload=!taper&&blockWeek%4===0;
      const adjacent=lastHard!==null&&G.days(lastHard,d)<2;
      const technical=adjacent||(p.weekdays.length>=3&&slot%2===1);
      const near=remaining<=56,cycle=(blockWeek-1)%4;
      let phase='base',title='База · жим с паузой',count=cautious?2:p.weekdays.length===1?4:3,reps=near?4:6,pct=near?.74:.68+cycle*.015,target=cautious?7:7.5,rest=180;
      let single=false;
      if(base===null){phase='calibration';title='Подбор нагрузки';pct=0;reps=5;count=2;target=6;}
      else if(taper){phase='taper';title='Подводка · техника';count=2;reps=2;pct=.6;target=null;
        if(!technical&&!conservative&&remaining>=5){single=true;pct=remaining>=8?.88:.85;title='Подводка · контроль';target=7.5;}
      }else if(deload){phase='deload';title='Разгрузка';count=2;reps=4;pct=.62;target=6;}
      else if(technical){phase='technique';title='Техника · лёгкий жим';count=2;reps=3;pct=.6;target=null;rest=120;}
      else if(conservative){title='База · техника и объём';reps=6;pct=stale?.62:.65;target=6.5;}
      else if(sessions.length===0){phase='calibration';title='Пауза · проверка нагрузки';single=true;pct=.8;target=7;reps=4;}
      else if(slot===0&&near){phase='strength';title='Силовой жим';single=true;pct=.83+cycle*.015;target=8;reps=cycle===2?3:4;rest=240;}
      else {phase='volume';title='Объём с паузой';pct=(near?.73:.68)+cycle*.015;reps=near?5:6;}
      if(adjacent&&base!==null){phase='technique';title='Техника · соседний день';single=false;pct=.55;count=2;reps=3;target=null;rest=120;}
      if(!technical)lastHard=d;
      const work=Array.from({length:count},()=>make(load(single?(taper?.7:.73):pct),reps,rest,'Рабочий','work',target));
      const main=single?[make(load(pct),1,300,'Контрольный','single',target),...work]:work;
      const exercises=[{name:'Жим лёжа с паузой 1–2 с',load:'Разминка 5–8 мин. RPE оценивайте после выполненного подхода. Рабочие веса уточняются по факту.',sets:[...warm(main[0].weight),...main]}];
      if(p.equipment!=='none'&&!taper){
        const pull=p.equipment==='dumbbells'?'Тяга гантели в наклоне':slot%2?'Тяга верхнего блока':'Тяга горизонтального блока';
        const tri=p.equipment==='dumbbells'?'Французское разгибание с гантелью':'Разгибание рук на блоке';
        const names=[pull,technical||deload?'Разведения для задней дельты':tri];
        if(p.weekdays.length===1&&!cautious&&!deload)names.push('Разведения для задней дельты');
        for(const name of names)exercises.push({name,load:'Подберите вес с запасом 3–4 повтора. Без боли и отказа.',sets:Array.from({length:cautious||deload?1:2},()=>make(null,name===pull?10:12,90,'Вспомогательное','accessory'))});
      }
      sessions.push({id:d,date:d,week,phase,title,note:technical?'Лёгкая техника: не превращайте соседние дни в две тяжёлые тренировки.':taper?'Объём снижен. Накопление усталости перед стартом не требуется.':'Соблюдайте целевой RPE. При недовосстановлении отметьте это в факте: следующая нагрузка будет пересмотрена.',exercises});
    }
    const test=!conservative&&base!==null,opener=load(test?.88:.65);
    sessions.push({id:p.target,date:p.target,week:Math.floor(G.days(p.start,p.target)/7)+1,phase:'test',title:test?'Соревнования · предварительные попытки':'Контроль техники',note:'Вес первой попытки — ориентир по текущим данным, не прогноз декабрьской/будущей силы. Перед стартом проверьте готовность. Цель не назначается автоматически.',exercises:[{name:'Жим лёжа с паузой 1–2 с',load:'Пауза и страховка. Вторая и третья попытки выбираются после предыдущей.',sets:[...warm(opener),make(opener,test?1:5,300,test?'1-я · предварительно':'Контроль с запасом',test?'attempt':'work',7),...(test?[make(null,1,300,'2-я · по факту','attempt',8.5),make(null,1,300,'3-я · по факту','attempt',9)]:[])]}]});
    const warnings=['Расчёт v3: цель не завышает текущую силу. Рост зависит от выполненных тренировок и восстановления.','Календарь фиксирован. Изменить дату и дни можно через «Изменить цель и расписание». Записи сохраняются.'];
    if(p.weekdays.length===1)warnings.push('Одно занятие в неделю: совмещены жим, спина и трицепс; пропуск заметно уменьшает тренировочный объём.');
    if(supervised)warnings.push('Индивидуальное сопровождение обязательно. Назначен контроль техники без автоматических максимальных попыток.');
    if(p.technique==='touch')warnings.push('Результат без паузы — неопределённая оценка. Уточняйте по подтверждённым подходам с паузой.');
    if(stale)warnings.push('Исходный результат старше 60 дней: назначен плавный возврат.');
    if(base===null)warnings.push('Исходный вес неизвестен. Подберите лёгкую нагрузку; запись с паузой и RPE 6–9 позволит предложить расчётную базу.');
    else if(base*.6<p.bar)warnings.push('Расчётная лёгкая нагрузка меньше грифа. Для строк «подбор» нужен более лёгкий гриф; текущий вес грифа не назначается насильно.');
    return {id:'p-'+(root.crypto?.randomUUID?.()||Date.now()+'-'+Math.random().toString(36).slice(2)),version:1,algorithm:'bench-natural-3',createdAt:new Date().toISOString(),name:(p.name||'Мой план').trim().slice(0,60),profile:p,sessions,meta:{base,estimate:initial,ageGroup:p.age<18?'14–17':p.age<40?'18–39':p.age<60?'40–59':p.age<=85?'60–85':'86–100',relativeGoal:Number((p.goal/p.bodyweight).toFixed(2)),warnings,reviewed:[],adjustments:[]},rules:[...warnings,'RPE 8 — примерно 2 повтора в запасе; RPE 6 — около 4. При ≤5 точная оценка силы не используется.','Для пересчёта подходят 1–10 фактических повторов с RPE 6–9, подтверждённой паузой и завершённой тренировкой.','Повышение опирается на два согласованных занятия. За одно применение база растёт не более 2,5%, не чаще раза в неделю.','В подводке объём не повышается. Последние 7 дней — без повышения базы.','Масса тела не задаёт рабочие килограммы: нагрузку определяют текущие результаты. Возраст влияет на исходный объём, далее учитывается фактическая переносимость.','Без фармакологии и бустеров. Максимальная переносимая нагрузка не означает работу до отказа.']};
  }
  function replaceFuture(plan,fresh,logs,asOf){
    const keep=plan.sessions.filter(s=>s.date<=asOf||hasLog(logs[plan.id+'|'+s.id]));
    const ids=new Set(keep.map(s=>s.id));
    return [...keep,...fresh.sessions.filter(s=>s.date>asOf&&!ids.has(s.id))].sort((a,b)=>a.date.localeCompare(b.date)).map(s=>({...s,week:Math.floor(G.days(plan.profile.start,s.date)/7)+1}));
  }
  function upgrade(plan,logs={},asOf=plan.profile.start){
    if(plan.algorithm==='bench-natural-3')return plan;
    const fresh=generate(plan.profile,plan.meta.base);
    return {...fresh,id:plan.id,createdAt:plan.createdAt,name:plan.name,sessions:replaceFuture(plan,fresh,logs,add(asOf,-1)),meta:{...fresh.meta,reviewed:plan.meta.reviewed||[],migratedFrom:plan.algorithm}};
  }
  const signature=log=>JSON.stringify([log.status,log.feedback,log.verified,log.sets]);
  function easyComplete(s,log){
    if(log?.status!=='done'||log.feedback!=='ok'||!log.verified||['technique','deload','taper','test'].includes(s.phase))return false;
    const work=s.exercises[0].sets.map((v,i)=>({v,a:log.sets[i]})).filter(x=>x.v.role==='work');
    return work.length>=2&&work.every(({v,a})=>v.weight!==null&&a?.kg>=v.weight&&a.reps>=v.reps&&a.rpe!==null&&a.rpe>=5&&a.rpe<=6);
  }
  function evidence(plan,s,log){
    if(!log||log.status!=='done'||log.feedback!=='ok'||!log.verified)return null;
    const values=log.sets.slice(0,s.exercises[0].sets.length).flatMap((v,i)=>{
      const prescribed=s.exercises[0].sets[i];
      const allowed=prescribed.kind!=='Разминка'&&prescribed.role!=='warmup'&&s.phase!=='technique';
      const n=allowed&&v.rpe<=9?estimateSet(v.kg,v.reps,v.rpe):null;return n===null?[]:[n];
    });
    return values.length?median(values):null;
  }
  function review(plan,logs,asOf){
    G.days(asOf,asOf);
    const no=message=>({base:null,change:false,message});
    if(!plan.profile||!plan.meta||plan.algorithm!=='bench-natural-3')return no('Адаптация доступна в персональном плане v3. Исходный план и его факты остаются в журнале.');
    const completed=plan.sessions.filter(s=>s.date<=asOf&&hasLog(logs[plan.id+'|'+s.id]));
    const s=completed.at(-1);if(!s)return no('Запишите тренировку, чтобы проверить нагрузку.');
    const log=logs[plan.id+'|'+s.id],history=plan.meta.adjustments||[],last=history.at(-1),sig=signature(log);
    if(last?.date===s.date&&last.signature===sig)return no('Эта запись уже учтена. '+last.message);
    if(!last&&(plan.meta.reviewed||[]).includes(s.date))return no('Эта тренировка уже учтена прежней версией. Следующий пересмотр — по новой тренировке.');
    if(log.feedback==='pain')return no('Отмечена боль: остановите болезненное упражнение и выясните причину. Автоматического назначения повышенной нагрузки нет.');
    if(log.status!=='done'&&log.status!=='skipped')return no('Факт сохранён. Для пересчёта завершите тренировку или отметьте пропуск.');
    if(G.days(s.date,asOf)>21)return no('Последняя запись старше 21 дня. Нужна свежая проверка; при изменении расписания будет назначен возврат в нагрузку.');
    const before=last?.date===s.date?last.beforeBase:plan.meta.base;
    const actual=log.sets.slice(0,s.exercises[0].sets.length);
    const missed=actual.some((v,i)=>{const t=s.exercises[0].sets[i];return t.kind!=='Разминка'&&!t.optional&&v.kg>0&&v.kg>=t.weight&&v.reps<t.reps;});
    const over=actual.some((v,i)=>v.rpe!==null&&v.rpe>Math.max(8,s.exercises[0].sets[i].rpeTarget??8)+.5);
    const hard=log.feedback==='hard'||missed||over;
    const recent=completed.filter(x=>G.days(x.date,s.date)<=28).map(x=>({date:x.date,value:evidence(plan,x,logs[plan.id+'|'+x.id])})).filter(x=>x.value!==null).slice(-3);
    const observation=recent.length?median(recent.map(x=>x.value)):null;
    let base=before,mode='normal',message='',change=false;
    const priorRaises=history.filter(a=>a.date!==s.date&&a.base>a.beforeBase).at(-1);
    const lastTwo=completed.filter(x=>G.days(x.date,s.date)<=21&&x.phase!=='technique').slice(-2);
    const mayRaise=G.days(s.date,plan.profile.target)>7&&(!priorRaises||G.days(priorRaises.date,s.date)>=7);
    if(hard||log.status==='skipped'){mode='recovery';change=true;message=hard?'Нагрузка оказалась чрезмерной: следующая тренировка легче, на один рабочий подход меньше, отдых длиннее.':'Пропуск: календарь сохранён; ближайшее занятие облегчено без догоняющего объёма.';}
    else if(!log.verified)return no('Факт сохранён. Подтвердите паузу и выполнение рабочей части без боли для оценки силы.');
    else if(before!==null&&mayRaise&&lastTwo.length===2&&lastTwo.every(x=>easyComplete(x,logs[plan.id+'|'+x.id]))){
      base=Math.floor(before*1.025*100)/100;change=true;message='Две рабочие части полностью выполнены с большим запасом: пробная небольшая прибавка. Это прогрессия нагрузки, не оценка максимума по лёгкому RPE.';
    }
    else if(observation!==null){
      const round=n=>G.round(n,plan.profile.step,plan.profile.bar);
      if(before===null){base=round(observation*.95);change=true;message='Предлагается начальная база по записанным подходам с паузой.';}
      else if(observation<before*.95){base=round(Math.max(observation,before*.95));change=base!==before;message='Оценка по факту ниже базы: ограниченное снижение нагрузки.';}
      else if(observation>before*1.02&&recent.length>=2&&recent.slice(-2).every(x=>x.value>before*1.02)&&mayRaise){
        base=Math.floor(Math.min(observation,before*1.025)*100)/100;change=base>before;message=change?'Два занятия подтверждают рост: предлагается ограниченное повышение базы. Рабочие килограммы округляются к доступным блинам.':'Сохраняем нагрузку.';
      }else message='Нагрузка сохранена. Для повышения нужны два согласованных занятия и не менее недели после предыдущей прибавки; последние 7 дней — без повышения.';
    }else message='Запись сохранена. Для оценки нужны 1–10 повторов с RPE 6–9 и подтверждённой паузой. Дополнительные повторы учитываются вместе с RPE.';
    if(!change)return no(message);
    if(observation!==null)message+=' Оценка силы ≈ '+observation.toFixed(1)+' кг, погрешность индивидуальна.';
    return {base,change,mode,date:s.date,signature:sig,beforeBase:before,message};
  }
  function applyReview(plan,logs,asOf){
    const a=review(plan,logs,asOf);if(!a.change)throw Error(a.message);
    const profile={...plan.profile,baseVerifiedOn:a.mode==='normal'?a.date:plan.profile.baseVerifiedOn};
    const fresh=generate(profile,a.base),sessions=replaceFuture(plan,fresh,logs,asOf);
    if(a.mode==='recovery'){
      const next=sessions.find(s=>s.date>asOf&&!hasLog(logs[plan.id+'|'+s.id]));
      if(next&&next.phase!=='test'){
        const ex=next.exercises[0],works=ex.sets.filter(v=>v.role==='work');
        ex.sets=ex.sets.filter(v=>v.role!=='single');
        if(works.length>2)ex.sets.splice(ex.sets.indexOf(works.at(-1)),1);
        for(const v of ex.sets){if(v.weight!==null)v.weight=G.round(v.weight*.95,plan.profile.step,plan.profile.bar);if(v.role==='work'){v.reps=Math.max(2,v.reps-1);v.rest=Math.max(240,v.rest);v.rpeTarget=6.5;v.rpe=true;}}
        next.title='Восстановление · облегчённый жим';next.note=a.message;next.exercises=next.exercises.slice(0,2);
      }
    }
    const adjustments=[...(plan.meta.adjustments||[]).filter(x=>x.date!==a.date),{...a,appliedOn:asOf}];
    return {...plan,algorithm:'bench-natural-3',profile,sessions,meta:{...fresh.meta,reviewed:[...new Set([...(plan.meta.reviewed||[]),a.date])],adjustments,lastAdjustment:a.message,revisions:plan.meta.revisions||[]}};
  }
  function replan(plan,logs,changes,asOf){
    G.days(asOf,asOf);
    if(asOf<plan.profile.start)throw Error('Дата изменения раньше начала программы.');
    const target=changes.target??plan.profile.target;
    if(G.days(asOf,target)<14)throw Error('До новой цели нужно минимум 14 дней. Для более короткой подводки нужен индивидуальный разбор.');
    if(G.days(plan.profile.start,target)>730)throw Error('Общая длина программы ограничена двумя годами; сохраните отдельный следующий цикл.');
    if(plan.sessions.some(s=>s.date>target&&hasLog(logs[plan.id+'|'+s.id])))throw Error('После новой даты есть записи. Выберите дату после них, чтобы сохранить историю.');
    const p={...plan.profile,...changes,start:plan.profile.start,blockStart:add(asOf,1)};
    const errors=validate(p);if(errors.length)throw Error(errors.join('\n'));
    const done=plan.sessions.filter(s=>s.date<=asOf&&logs[plan.id+'|'+s.id]?.status==='done');
    const last=done.at(-1),gap=last?G.days(last.date,asOf):G.days(plan.profile.start,asOf);
    if(last&&logs[plan.id+'|'+last.id]?.feedback==='pain')throw Error('В последней тренировке отмечена боль. Перед новым назначением нагрузки нужен индивидуальный разбор.');
    if(last&&logs[plan.id+'|'+last.id]?.verified)p.baseVerifiedOn=last.date;
    // Новые блоки начинаются от изменения, оценка силы остаётся фактической.
    const schedule={...p,resultDate:plan.profile.resultDate};
    if(gap>14)schedule.recovery='limited';
    // Для ровно 14 дней остаётся 13 дней от завтрашней даты: генерируем от сегодня, затем сохраняем сегодняшний день.
    const fresh=generate(schedule,plan.meta.base);
    const sessions=replaceFuture(plan,fresh,logs,asOf);
    if(gap>14||last&&logs[plan.id+'|'+last.id]?.feedback==='hard'){const next=sessions.find(s=>s.date>asOf&&!hasLog(logs[plan.id+'|'+s.id]));if(next){for(const v of next.exercises[0].sets)if(v.weight!==null)v.weight=G.round(v.weight*.9,p.step,p.bar);next.note='Возвращение после перерыва или недовосстановления: уменьшенная нагрузка. '+next.note;}}
    return {...plan,algorithm:'bench-natural-3',profile:p,sessions,meta:{...plan.meta,ageGroup:fresh.meta.ageGroup,warnings:fresh.meta.warnings,relativeGoal:fresh.meta.relativeGoal,revisions:[...(plan.meta.revisions||[]),{date:asOf,oldTarget:plan.profile.target,target,weekdays:p.weekdays}],lastAdjustment:'Цель и расписание изменены '+asOf+'. Выполненные и частично записанные дни сохранены.'}};
  }
  const api={...G,validate,generate,upgrade,review,applyReview,replan,estimateSet,evidence,hasLog};
  if(typeof module!=='undefined')module.exports=api;else root.BenchGenerator=api;
})(typeof globalThis!=='undefined'?globalThis:this);
