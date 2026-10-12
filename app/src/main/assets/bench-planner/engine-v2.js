/* v2: процент от оценки силы применяется один раз; цель не является основанием нагрузки. */
(function(root){
  'use strict';
  const G=typeof module!=='undefined'?require('./generator.js'):root.BenchGenerator;
  const legacyGenerate=G.generate, DAY=86400000;
  const time=s=>Date.parse(s+'T00:00:00Z');
  const set=(weight,reps,rest,kind,rpe=false,extra={})=>({weight,reps,rest,kind,rpe,...extra});
  function generate(profile,override=null){
    const errors=G.validate(profile);if(errors.length)throw Error(errors.join('\n'));
    const p=JSON.parse(JSON.stringify(profile)), unknown=p.resultType==='unknown', novice=p.experience==='novice';
    const estimate=unknown?null:p.resultType==='max'||p.reps===1?p.resultWeight:p.resultWeight*(1+p.reps/30);
    // Давность меняет режим ввода в нагрузку, но не превращает оценку в фиктивный максимум с паузой.
    const stale=!unknown&&G.days(p.resultDate,p.start)>60;
    const base=override??estimate;
    if(base!==null&&(!Number.isFinite(base)||base<p.bar||base>600))throw Error('Некорректная база нагрузки.');
    const conservative=novice||stale;
    const cautious=p.age>=60||p.recovery==='limited'||conservative;
    const duration=G.days(p.start,p.target), sessions=[], counts={};
    const load=pct=>base===null?null:G.round(base*pct,p.step,p.bar);
    const work=(pct,reps,count,track=false)=>Array.from({length:count},(_,i)=>set(load(pct),reps,180+(reps<=3?60:0),'Рабочий',track&&i===count-1,{role:'work'}));
    const single=(pct,cap=8)=>set(load(pct),1,300,'Контроль ≤'+cap,true,{role:'single',rpeCap:cap});
    function warmups(top){
      if(top===null)return [set(null,8,60,'Разминка')];
      // Последний подводящий подход ближе к одиночному: нет скачка 85 -> 120 кг.
      const raw=[p.bar,top*.43,top*.69,top*.87];
      return [...new Set(raw.map(w=>G.round(w,p.step,p.bar)))].filter(w=>w<top).map((w,i)=>set(w,[10,6,3,1][i],i<2?60:150,'Разминка'));
    }
    function accessories(index,off){
      if(off||p.equipment==='none')return [];
      const names=p.equipment==='dumbbells'?['Тяга гантели в наклоне','Разведения для задней дельты']:index%2?['Тяга верхнего блока','Разведения для задней дельты']:['Тяга горизонтального блока','Разгибание рук на блоке'];
      return names.map((name,i)=>({name,load:'Запас 3–4 повтора, без отказа.',sets:Array.from({length:cautious?1:2},()=>set(null,i?12:10,i?75:90,'Вспомогательное'))}));
    }
    let taperHeavy=false,lastWeekSingle=false,index=0;
    for(let t=time(p.start);t<time(p.target);t+=DAY){
      const date=G.iso(t),remaining=G.days(date,p.target);
      if(!p.weekdays.includes(new Date(t).getUTCDay())||remaining<=(p.age>=60?5:3))continue;
      const elapsed=G.days(p.start,date),week=Math.floor(elapsed/7)+1,slot=counts[week]||0;counts[week]=slot+1;
      // При двух занятиях второе остаётся объёмным, а не превращается в ещё один лёгкий день.
      const technical=p.weekdays.length===3&&slot===1;
      const progress=Math.min(1,Math.max(0,(elapsed-7)/Math.max(1,duration-21)));
      const taper=remaining<=14,deload=!taper&&remaining>21&&week%4===0;
      let title,phase,sets,note='Запас ≥2 повтора. При RPE >8 снизьте рабочий вес на 5–10%; при боли остановитесь. Будущие веса условны до проверки по факту.';
      if(unknown){title='Подбор рабочего веса';phase='calibration';sets=[set(null,5,180,'Запас ≥4 повтора',false,{role:'work'}),set(null,5,180,'Запас ≥4 повтора',false,{role:'work'})];note='Выберите лёгкий контролируемый вес. Не проверяйте максимум. Запишите вес и повторы; для расчёта килограммов нужен исходный подход.';}
      else if(taper){
        phase='taper';
        if(!conservative&&remaining>=8&&!taperHeavy){title='Подводка · контроль';taperHeavy=true;sets=[single(.91),...work(.76,2,2)];}
        else if(!conservative&&remaining>=5&&remaining<=7&&!lastWeekSingle){title='Подводка · уверенный одиночный';lastWeekSingle=true;sets=[single(.86,7),...work(.68,2,1)];}
        else {title='Подводка · лёгкая техника';sets=work(.6,2,2);}
        note='Объём снижен. Контрольный вес — только при уверенной паузе и в пределах RPE; не проверяйте рекорд перед стартом.';
      }else if(deload){title='Разгрузка';phase='deload';sets=work(technical?.6:.68,3,2);}
      else if(conservative){title=technical?'Техника':'Возвращение / базовый объём';phase='base';sets=work((stale?.65:.60)+progress*.1,5,cautious?2:3);note='Плавный вход в нагрузку после перерыва или при небольшом стаже. Запас 3–4 повтора; без обязательного теста максимума.';}
      else if(index===0){
        title='Пауза · проверка нагрузки';phase='calibration';
        const first=single(.8,7),extraWeight=load(.825);
        const optional=extraWeight>first.weight?[set(extraWeight,1,300,'Если 1-й ≤6',true,{role:'single',optional:true,rpeCap:7})]:[];
        sets=[first,...optional,...work(.725,4,cautious?2:3)];
        note='Второй одиночный — только если первый ≤RPE 6, пауза чистая и боли нет. Иначе пропустите его. Рабочие подходы — с запасом ≥2.';
      }else if(technical){title='Техника с паузой';phase='technique';sets=work(.67,3,cautious?2:3);}
      else if(slot===0){
        title='Силовой жим';phase='strength';
        const pct=.84+progress*.075;
        sets=[single(pct),...work(.75+progress*.035,progress>.5?3:4,cautious?2:3,true)];
      }else {title='Объём с паузой';phase='volume';sets=work(.725+progress*.055,progress>.5?4:5,cautious?2:3,true);}
      const top=sets[0].weight;
      sessions.push({id:date,date,week,title,phase,note,exercises:[{name:'Жим лёжа с паузой 1–2 с',load:'Общая разминка 5–8 мин. Страховка. Веса — ориентир; трудность подхода важнее цифры.',sets:[...warmups(top),...sets]},...accessories(index++,taper||deload)]});
    }
    const test=!unknown&&!conservative,opener=load(test?.88:.65);
    sessions.push({id:p.target,date:p.target,week:Math.floor(duration/7)+1,title:test?'Контроль / соревнования':'Контроль техники',phase:'test',note:test?'Первая попытка — предварительный ориентир. Следующие веса выбирайте по успешной предыдущей и недавним контрольным подходам. Цель не обязательна.':'Контроль с запасом, без отказа и проверки максимума.',exercises:[{name:'Жим лёжа с паузой 1–2 с',load:'Пауза и страховка. Между попытками 4–5 минут. Не делайте большой скачок ради цели.',sets:[...warmups(opener),set(opener,test?1:5,300,test?'1-я · ориентир':'С запасом',test,{role:'attempt'}),...(test?[set(null,1,300,'2-я · по факту',true,{role:'attempt'}),set(null,1,300,'3-я · по факту',true,{role:'attempt'})]:[])]}]});
    const warnings=['Цель не повышает веса автоматически. Все прогнозные нагрузки проверяются по паузе, технике и RPE.'];
    if(unknown)warnings.push('Без исходного подхода килограммы неизвестны: пока это календарь подбора нагрузки.');
    else {
      warnings.push('База '+base.toFixed(1)+' кг — оценка, не подтверждённый максимум. Процент нагрузки применяется один раз.');
      if(p.technique==='touch')warnings.push('Исходный результат без паузы. Разница с паузой индивидуальна: первая тренировка проверяет переносимость, фиксированного штрафа к силе нет.');
      if(p.resultType==='set')warnings.push('Формула по подходу приблизительна; непредельный подход может занижать оценку. Уточняйте по выполненному жиму с паузой.');
      if(stale)warnings.push('Исходному результату больше 60 дней. Сначала восстановите рабочую нагрузку; пиковые попытки не назначены.');
      if(p.goal>base*1.1)warnings.push('Цель существенно выше исходной оценки. Рассмотрите больший срок; алгоритм не форсирует рост.');
    }
    warnings.push(p.age>=60?'60+: меньше рабочих подходов и больше отдыха перед контрольной датой. Это начальная настройка, корректируйте по переносимости.':'Возраст сам по себе не определяет силу. Объём зависит также от стажа и восстановления.');
    return {id:'p-'+(root.crypto?.randomUUID?.()||Date.now()+'-'+Math.random().toString(36).slice(2)),version:1,algorithm:'bench-natural-2',createdAt:new Date().toISOString(),name:(p.name||'Мой план').trim().slice(0,60),profile:p,meta:{base,estimate,ageGroup:p.age<40?'18–39':p.age<60?'40–59':'60–85',relativeGoal:Number((p.goal/p.bodyweight).toFixed(2)),warnings,reviewed:[]},rules:[...warnings,'После силового дня внесите факт и отметьте соблюдение паузы. Кнопка «Проверить нагрузку» предлагает ограниченную поправку будущих весов; даты и записанные тренировки сохраняются.','RPE 8 ≈ 2 повтора в запасе; 9 ≈ 1; 10 — без запаса. При RPE ≤5 оценка максимума слишком неточна, автоматического повышения по ней нет.','RPE нужен для контрольного одиночного и последнего рабочего подхода силового/объёмного жима. На разминке, технике и вспомогательных упражнениях его нет.','Не догоняйте пропуски двойным объёмом. При боли остановите упражнение. Натуральная подготовка не предполагает фармакологии или ускоренного восстановления.'],sessions};
  }
  function hasLog(log){return !!log&&(log.status!=='planned'||log.feedback!=='ok'||log.sets.some(s=>s.kg!==null||s.reps!==null||s.rpe!==null));}
  function upgrade(plan,logs={}){
    if(plan.algorithm!=='bench-natural-1')return plan;
    const fresh=generate(plan.profile),byDate=new Map(fresh.sessions.map(s=>[s.date,s]));
    // Календарь и индексы уже записанных подходов не меняются, включая частично заполненные дни.
    const sessions=plan.sessions.map(s=>hasLog(logs[plan.id+'|'+s.id])?s:{...(byDate.get(s.date)||s),id:s.id,date:s.date,week:s.week});
    return {...fresh,id:plan.id,createdAt:plan.createdAt,name:plan.name,sessions,meta:{...fresh.meta,migratedFrom:'bench-natural-1'}};
  }
  function review(plan,logs,asOf){
    G.days(asOf,asOf);
    const relevant=plan.sessions.filter(s=>s.date<=asOf&&hasLog(logs[plan.id+'|'+s.id]));
    const s=relevant.at(-1),log=s&&logs[plan.id+'|'+s.id];
    const none=message=>({message,base:null});
    if(plan.algorithm!=='bench-natural-2'||!s||plan.meta.base===null)return none('Нужен записанный контрольный жим и известная расчётная база.');
    if(log.feedback==='pain')return none('В журнале отмечена боль. Прекратите болезненное упражнение; автоматическая поправка не заменяет выяснение причины.');
    if(log.status==='skipped')return none('Пропуск не сдвигает даты. Не удваивайте объём; после длительного перерыва начните с проверки нагрузки.');
    if(G.days(s.date,asOf)>14)return none('Последней записи больше 14 дней. Нужен свежий контрольный подход.');
    if((plan.meta.reviewed||[]).includes(s.date))return none('Эта тренировка уже учтена. Для следующего пересмотра нужна новая запись.');
    const bench=s.exercises[0].sets,actual=log.sets.slice(0,bench.length),base=plan.meta.base;
    const failed=actual.some((v,i)=>bench[i].kind!=='Разминка'&&v.kg!==null&&v.kg>=bench[i].weight&&v.reps!==null&&v.reps<bench[i].reps);
    const hard=log.feedback==='hard'||failed||actual.some(v=>v.rpe>8);
    if(hard){const lower=G.round(base*.95,plan.profile.step,plan.profile.bar);return lower<base?{base:lower,date:s.date,message:'Есть недовосстановление, превышение RPE 8 или недобор повторов. Предлагается снизить базу примерно на 5%. Записанные дни и даты сохранятся.'}:none('Достигнут минимальный вес грифа. Нужен более лёгкий гриф или индивидуальный подбор упражнения.');}
    if(log.status!=='done'||log.verified!==true)return none('Для уточнения базы завершите тренировку и подтвердите: пауза соблюдена, рабочая часть выполнена без боли.');
    const singles=actual.filter((v,i)=>bench[i].role==='single'&&v.reps===1&&v.kg>0&&v.rpe>=6&&v.rpe<=8);
    if(!singles.length)return none('Нужен контрольный одиночный с паузой и RPE 6–8. При ≤5 оставьте веса прогнозными: такой подход плохо оценивает максимум.');
    // Таблица RPE — приближение. Не экстраполируем лёгкие подходы и не повышаем базу резко.
    const pct={6:.86,6.5:.875,7:.89,7.5:.905,8:.92};
    const estimates=singles.map(v=>v.kg/pct[v.rpe]).filter(Number.isFinite);
    if(!estimates.length)return none('Укажите RPE с шагом 0,5 от 6 до 8.');
    const observed=Math.min(...estimates),step=plan.profile.step;
    const lastReview=(plan.meta.reviewed||[]).at(-1);
    if(observed>base&&lastReview&&G.days(lastReview,s.date)<7)return none('Повышение базы — не чаще раза в 7 дней. Сейчас сохраните нагрузку.');
    if(observed>base&&G.days(s.date,plan.profile.target)<=14)return none('Подводка уже началась. База вверх не пересчитывается; ориентируйтесь на качество контрольных подходов.');
    const candidate=G.round(Math.max(base*.95,Math.min(observed,base+Math.min(step,base*.025))),step,plan.profile.bar);
    if(Math.abs(candidate-base)<Math.min(step,base*.01))return none('Факт согласуется с текущей базой. Изменение нагрузки не требуется.');
    return {base:candidate,date:s.date,message:'Оценка по паузе и RPE ≈ '+observed.toFixed(1)+' кг. Предлагаемая база '+candidate+' кг (была '+base.toFixed(1)+'). Это ограниченная поправка, не подтверждённый максимум.'};
  }
  function applyReview(plan,logs,asOf){
    const advice=review(plan,logs,asOf);if(advice.base===null)throw Error(advice.message);
    const fresh=generate(plan.profile,advice.base),byDate=new Map(fresh.sessions.map(s=>[s.date,s]));
    const sessions=plan.sessions.map(s=>s.date<asOf||s.date<=advice.date||hasLog(logs[plan.id+'|'+s.id])?s:{...(byDate.get(s.date)||s),week:s.week});
    return {...fresh,id:plan.id,createdAt:plan.createdAt,name:plan.name,sessions,meta:{...fresh.meta,reviewed:[...(plan.meta.reviewed||[]),advice.date],lastAdjustment:advice.message}};
  }
  const api={...G,generate,legacyGenerate,upgrade,review,applyReview,hasLog};
  if(typeof module!=='undefined')module.exports=api;else root.BenchGenerator=api;
})(typeof globalThis!=='undefined'?globalThis:this);
