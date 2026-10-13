const {test}=require('node:test'),assert=require('node:assert/strict'),G=require('../../app/src/main/assets/bench-planner/engine-v3.js');
const profile={name:'Проверка',age:35,bodyweight:90,goal:150,start:'2026-10-12',target:'2026-12-05',experience:'trained',recovery:'normal',resultType:'set',resultWeight:132.5,reps:3,technique:'touch',resultDate:'2026-10-10',weekdays:[1,4],bar:20,step:2.5,equipment:'gym',pain:false};
function log(p,s,weight=130,reps=4,rpe=8,feedback='ok'){
 const sets=s.exercises.flatMap((e,ei)=>e.sets.map(v=>({kg:ei?40:v.kind==='Разминка'?v.weight:weight,reps:ei?v.reps:v.kind==='Разминка'?v.reps:reps,rpe:!ei&&v.kind!=='Разминка'?rpe:null})));
 return {sets,status:'done',verified:true,feedback};
}
test('1–4 дня, 14–100 лет и разные массы: конечные веса, календарь, отсутствие двух тяжёлых дней подряд',()=>{
 let count=0;for(const age of [14,25,45,65,90])for(const bodyweight of [25,90,260])for(const weekdays of [[1],[1,4],[1,3,5],[0,1,3,5]])for(const experience of ['novice','trained']){
  const p=G.generate({...profile,age,bodyweight,weekdays,experience,supervised:true});G.validatePlan(p);count++;
  let hard=null;for(const s of p.sessions){if(s.phase!=='test')assert.ok(weekdays.includes(new Date(s.date+'T00:00Z').getUTCDay()));
   if(s.phase!=='test'&&s.exercises[0].sets.some(v=>v.weight>p.meta.base*.66)){if(hard)assert.ok(G.days(hard,s.date)>=2);hard=s.date;}
   for(const e of s.exercises)for(const v of e.sets){assert.ok(v.weight===null||Number.isFinite(v.weight));assert.ok(v.rest>=60);}
  }
 }assert.equal(count,120);
});
test('Дополнительные повторы повышают оценку; лёгкий/неполный/болезненный факт не повышает',()=>{
 assert.ok(G.estimateSet(115,5,8)>G.estimateSet(115,1,8));assert.equal(G.estimateSet(115,5,5),null);
 const p=G.generate(profile),s=p.sessions[0],key=p.id+'|'+s.id;
 for(const changes of [{status:'planned'},{verified:false},{feedback:'pain'}])assert.equal(G.review(p,{[key]:{...log(p,s),...changes}},s.date).change,false);
});
test('Два многоповторных занятия: ограниченная прибавка, идемпотентность и сохранение истории',()=>{
 const p=G.generate(profile),logs={};for(const s of p.sessions.slice(0,2))logs[p.id+'|'+s.id]=log(p,s,135,5,8);
 const d=p.sessions[1].date,a=G.review(p,logs,d);assert.ok(a.base>p.meta.base);assert.ok(a.base<=p.meta.base*1.025);
 const after=G.applyReview(p,logs,d);assert.equal(G.review(after,logs,d).change,false);assert.deepEqual(after.sessions.slice(0,2),p.sessions.slice(0,2));assert.deepEqual(after.sessions.map(s=>s.date),p.sessions.map(s=>s.date));G.validateState({version:1,active:p.id,plans:[after],logs});
 // Editing the same workout must recalculate against its original baseline, not compound the previous raise.
 logs[p.id+'|'+d].sets[4].reps=6;const edited=G.applyReview(after,logs,d);assert.ok(edited.meta.base<=p.meta.base*1.025);
});
test('Недовосстановление меняет ближайшие подходы, повторы, отдых и RPE; боль не назначает нагрузку',()=>{
 const p=G.generate(profile),s=p.sessions[0],logs={[p.id+'|'+s.id]:log(p,s,115,4,9,'hard')};const after=G.applyReview(p,logs,s.date);
 const before=p.sessions[1].exercises[0].sets.filter(v=>v.role==='work'),next=after.sessions[1].exercises[0].sets.filter(v=>v.role==='work');
 assert.ok(next.length<before.length);assert.ok(next[0].weight<before[0].weight);assert.ok(next[0].reps<before[0].reps);assert.ok(next[0].rest>before[0].rest);assert.equal(next[0].rpeTarget,6.5);
 logs[p.id+'|'+s.id].feedback='pain';assert.equal(G.review(p,logs,s.date).change,false);
});
test('Перенос декабря на февраль сохраняет журнал, ID и прошлое; новый цикл выдерживает следующий пересчёт',()=>{
 const p=G.generate(profile),s=p.sessions[0],logs={[p.id+'|'+s.id]:log(p,s)};
 const date='2026-11-10',next=G.replan(p,logs,{target:'2027-02-06',weekdays:[2],goal:155},date);
 assert.equal(next.id,p.id);assert.equal(next.sessions.at(-1).date,'2027-02-06');assert.deepEqual(next.sessions.filter(s=>s.date<=date),p.sessions.filter(s=>s.date<=date));
 assert.ok(next.sessions.filter(s=>s.date>date&&s.phase!=='test').every(s=>new Date(s.date+'T00:00Z').getUTCDay()===2));G.validatePlan(next);
 const recent=next.sessions.find(s=>s.date>date);logs[next.id+'|'+recent.id]=log(next,recent,100,5,8,'hard');const again=G.applyReview(next,logs,recent.date);assert.equal(again.sessions.at(-1).date,'2027-02-06');assert.equal(again.profile.blockStart,'2026-11-11');
 assert.throws(()=>G.replan(p,logs,{target:'2026-11-15'},date));
});
test('Один день в неделю и длинный срок используют повторные разгрузки, не бесконечную прогрессию',()=>{
 const p=G.generate({...profile,weekdays:[1],target:'2028-10-10'});G.validatePlan(p);assert.ok(p.sessions.filter(s=>s.phase==='deload').length>10);assert.equal(p.sessions[0].exercises.length,4);
 const b=G.generate({...profile,goal:300});assert.deepEqual(G.generate(profile).sessions,b.sessions);
});
test('Миграция сохраняет записанную v2 тренировку и учтённые поправки',()=>{
 const old=require('../../app/src/main/assets/bench-planner/engine-v2.js').generate(profile),s=old.sessions[0],logs={[old.id+'|'+s.id]:log(old,s)};old.meta.reviewed=[s.date];
 const p=G.upgrade(old,logs,'2026-10-12');assert.deepEqual(p.sessions[0],s);assert.equal(G.review(p,logs,s.date).change,false);G.validatePlan(p);
});
test('Подводка: последняя неделя без прибавок, несовершеннолетние без автоматического максимума',()=>{
 assert.throws(()=>G.generate({...profile,age:16}));const p=G.generate({...profile,age:16,supervised:true});assert.equal(p.sessions.at(-1).title,'Контроль техники');
 const a=G.generate(profile),logs={};for(const s of a.sessions.slice(-3,-1))logs[a.id+'|'+s.id]=log(a,s,150,5,8);
 assert.equal(G.review(a,logs,a.sessions.at(-2).date).change,false);
});
test('Две полностью лёгкие рабочие части допускают пробную прибавку без выдуманного 1ПМ; один подход — нет',()=>{
 const p=G.generate(profile),logs={};for(const s of p.sessions.slice(0,2))logs[p.id+'|'+s.id]=log(p,s,150,6,5);
 assert.ok(G.review(p,logs,p.sessions[1].date).base>p.meta.base);
 const key=p.id+'|'+p.sessions[1].date;logs[key].sets.at(6).rpe=null;assert.equal(G.review(p,logs,p.sessions[1].date).change,false);
});
test('Слабый спортсмен не получает неподъёмный гриф вместо лёгкого веса',()=>{
 const p=G.generate({...profile,resultType:'max',resultWeight:20,reps:1,bar:20,experience:'novice'});assert.ok(p.sessions[0].exercises[0].sets.every(v=>v.weight===null));assert.match(p.rules.join(' '),/легче|лёгкий гриф/);
 assert.equal(G.review({id:'original'},{},'2026-10-12').change,false);
});
test('75 кг → цель 100 кг за два месяца: нагрузка меняется по неделям, цель не подменяет силу',()=>{
 const input={...profile,goal:100,start:'2026-10-13',target:'2026-12-13',resultType:'max',resultWeight:75,reps:1,resultDate:'2026-10-12',weekdays:[2,5],experience:'novice'};
 const p=G.generate(input),sessions=p.sessions.filter(s=>!['deload','taper','test'].includes(s.phase));
 const main=sessions.filter(s=>s.phase!=='technique'),weights=[...new Set(main.map(s=>s.exercises[0].sets.find(v=>v.role==='work').weight))];
 assert.equal(p.meta.goalAssessment.status,'unsupported');assert.equal(p.meta.goalAssessment.requiredGain,33);
 assert.ok(weights.length>=3,JSON.stringify(weights));assert.ok(main.at(-1).exercises[0].sets.find(v=>v.role==='work').weight>main[0].exercises[0].sets.find(v=>v.role==='work').weight);
 assert.ok(sessions.some(s=>s.phase==='technique'));assert.ok(p.sessions.some(s=>s.phase==='deload'));assert.ok(p.sessions.some(s=>s.phase==='taper'));
 assert.ok(p.sessions.flatMap(s=>s.exercises[0].sets).every(v=>v.weight===null||v.weight<75));
 assert.deepEqual(p.sessions,G.generate({...input,goal:200}).sessions);
});
test('70 кг → 200 кг: цель помечена как необоснованная, контроль начинается от текущей силы',()=>{
 for(const experience of ['novice','trained','advanced'])for(const discipline of ['bench','squat','deadlift']){
  const p=G.generate({...profile,discipline,experience,goal:200,resultType:'max',resultWeight:70,reps:1});
  assert.equal(p.meta.goalAssessment.status,'unsupported');assert.equal(p.meta.goalAssessment.requiredGain,186);
  assert.ok(p.meta.goalAssessment.checkpoint.weight<70);assert.ok(p.sessions.flatMap(s=>s.exercises[0].sets).every(v=>v.weight===null||v.weight<70));
  assert.match(p.meta.warnings.join(' '),/необоснованно|нельзя обоснованно/);G.validatePlan(p);
 }
});
test('Старый максимум: возврат не держит 18 недель на одном весе',()=>{
 const input={...profile,goal:100,start:'2026-10-15',target:'2027-02-28',resultType:'max',resultWeight:75,reps:1,resultDate:'2026-07-01',weekdays:[0,4],experience:'trained'};
 const p=G.generate(input),main=p.sessions.filter(s=>['base','volume','strength'].includes(s.phase));
 const work=s=>s.exercises[0].sets.find(v=>v.role==='work');
 assert.equal(work(main[0]).weight,45);
 assert.ok(work(main.find(s=>s.week===18)).weight>=60);
 assert.ok(work(main.find(s=>s.week===18)).weight>work(main[0]).weight);
 assert.ok(p.sessions.some(s=>s.phase==='deload'));
 assert.ok(p.sessions.flatMap(s=>s.exercises[0].sets).every(v=>v.weight===null||v.weight<75));
 assert.ok(p.meta.goalAssessment.checkpoint.weight>47.5);
 const old=structuredClone(p),saved=structuredClone(old.sessions[0]);old.meta.templateVersion=2;
 for(const s of old.sessions)for(const v of s.exercises[0].sets)if(v.role==='work')v.weight=52.5;
 const logs={[old.id+'|'+saved.id]:{status:'done',feedback:'ok',verified:true,sets:[]}};
 const upgraded=G.upgrade(old,logs,'2026-10-20');
 assert.deepEqual(upgraded.sessions[0],old.sessions[0]);
 assert.ok(work(upgraded.sessions.find(s=>s.week===18)).weight>=60);
 assert.equal(upgraded.meta.templateVersion,G.TEMPLATE_VERSION);
});
test('Возраст, стаж, срок, шаг блинов и цель: допустимый календарь и ограниченная прогрессия',()=>{
 let checked=0;for(const age of [16,35,65,90])for(const experience of ['novice','trained','advanced'])for(const goal of [80,100,200])for(const step of [1,2.5,5])for(const weeks of [8,16]){
  const input={...profile,age,supervised:true,experience,goal,step,resultType:'max',resultWeight:75,reps:1,start:'2026-10-13',resultDate:'2026-10-12',target:G.iso(Date.parse('2026-10-13T00:00:00Z')+weeks*7*86400000),weekdays:[2,5]};
  const p=G.generate(input);G.validatePlan(p);checked++;
  assert.equal(p.meta.base,75);assert.ok(p.sessions.flatMap(s=>s.exercises[0].sets).every(v=>v.weight===null||v.weight<=75));
  if(experience==='novice'||age===16||age===90)assert.equal(p.sessions.at(-1).title,'Контроль техники');
  if(goal===200)assert.equal(p.meta.goalAssessment.status,'unsupported');
  if(experience==='novice'&&step<=2.5){const main=p.sessions.filter(s=>['base','volume'].includes(s.phase));assert.ok(new Set(main.map(s=>s.exercises[0].sets.find(v=>v.role==='work')?.weight)).size>=2);}
 }assert.equal(checked,216);
});
test('Обновление уже сохранённого плана меняет только будущие занятия',()=>{
 const input={...profile,goal:100,start:'2026-10-13',target:'2026-12-13',resultType:'max',resultWeight:75,reps:1,resultDate:'2026-10-12',weekdays:[2,5],experience:'novice'};
 const old=G.generate(input),first=structuredClone(old.sessions[0]);delete old.meta.templateVersion;old.meta.adjustments=[{date:first.date,signature:'old',message:'saved',mode:'normal',base:75,beforeBase:75}];
 for(const s of old.sessions)for(const v of s.exercises[0].sets)if(v.role==='work')v.weight=47.5;
 const logged=structuredClone(old.sessions[0]),logs={[old.id+'|'+first.id]:{status:'done',feedback:'ok',verified:true,sets:[]}};
 const updated=G.upgrade(old,logs,'2026-10-20');
 assert.equal(updated.id,old.id);assert.deepEqual(updated.sessions[0],logged);assert.equal(updated.meta.templateVersion,G.TEMPLATE_VERSION);
 assert.deepEqual(updated.meta.adjustments,old.meta.adjustments);assert.ok(new Set(updated.sessions.filter(s=>s.date>'2026-10-20'&&['base','volume'].includes(s.phase)).map(s=>s.exercises[0].sets.find(v=>v.role==='work')?.weight)).size>=2);
 assert.equal(G.upgrade(updated,logs,'2026-10-20'),updated);G.validatePlan(updated);
});
