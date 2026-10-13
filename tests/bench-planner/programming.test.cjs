const {test}=require('node:test'),assert=require('node:assert/strict'),G=require('../../app/src/main/assets/bench-planner/engine-v3.js');
const profile={name:'Системная проверка',age:35,bodyweight:80,goal:100,start:'2026-10-15',target:'2027-02-28',experience:'novice',trainingContinuity:'regular',recovery:'normal',resultType:'max',resultWeight:75,reps:1,technique:'touch',resultDate:'2026-07-01',weekdays:[1,4],bar:20,step:2.5,equipment:'gym',pain:false};
const work=s=>s.exercises[0].sets.filter(v=>v.role==='work');
function done(s,rpe=6.5,feedback='ok',multiplier=1){
 return {status:'done',verified:true,feedback,sets:s.exercises.flatMap((e,ei)=>e.sets.map(v=>({kg:ei?20:v.weight===null?20:v.weight*multiplier,reps:v.reps,rpe:!ei&&v.rpe?rpe:null})))};
}
test('20 недель: план развивает нагрузку и контроль у всех уровней, а не повторяет проценты старого максимума',()=>{
 for(const experience of ['novice','trained','advanced'])for(const trainingContinuity of ['regular','returning','unknown'])for(const age of [25,65]){
  const p=G.generate({...profile,experience,trainingContinuity,age}),t=p.meta.progression.trajectory;
  assert.ok(t.last.weight>t.first.weight+p.profile.step,JSON.stringify({experience,trainingContinuity,age,t}));
  assert.ok(p.meta.goalAssessment.checkpoint.weight>t.first.weight);
  assert.ok(p.meta.progression.plannedResult>p.meta.base);
  assert.ok(p.sessions.some(s=>s.phase==='strength'));assert.ok(p.sessions.some(s=>s.phase==='deload'));assert.ok(p.sessions.some(s=>s.phase==='taper'));
  assert.ok(p.sessions.some(s=>s.conditional&&s.reference>p.meta.base));
 }
});
test('Цель участвует в ограниченной траектории: 50 → 90 и 70 → 200 получают промежуточный цикл; 140 → 150 получает целевой цикл',()=>{
 assert.equal(G.generate({...profile,goal:70}).meta.goalAssessment.milestone,75);
 for(const [resultWeight,goal,experience] of [[50,90,'novice'],[70,200,'trained'],[140,150,'trained'],[140,150,'advanced']]){
  const p=G.generate({...profile,resultWeight,goal,experience}),g=p.meta.goalAssessment;
  if(goal/resultWeight>1.5){assert.equal(g.status,'unsupported');assert.ok(g.milestone<goal);}
  else {assert.notEqual(g.status,'unsupported');assert.ok(g.milestone>=147.5&&g.milestone<=150);}
  const larger=G.generate({...profile,resultWeight,goal:400,experience});
  assert.deepEqual(p.sessions[0].exercises,larger.sessions[0].exercises);
  assert.ok(larger.meta.progression.plannedGain<=larger.meta.progression.upperGain);
 }
});
test('1944 профиля: разные сроки, исходная сила, возраст, регулярность, шаг и восстановление — допустимые дозировки',()=>{
 let count=0;for(const age of [16,35,65,90])for(const experience of ['novice','trained','advanced'])for(const trainingContinuity of ['regular','returning','unknown'])for(const recovery of ['normal','limited'])for(const weeks of [8,20,32])for(const step of [1,2.5,5])for(const resultWeight of [50,75,140]){
  const p=G.generate({...profile,age,supervised:true,experience,trainingContinuity,recovery,step,resultWeight,target:G.iso(Date.parse(profile.start+'T00:00Z')+weeks*7*86400000)});G.validatePlan(p);count++;
  assert.equal(p.meta.base,resultWeight);
  for(const s of p.sessions){
   assert.ok(s.reference<=p.meta.base*(1+p.meta.progression.plannedGain)+1e-7);
   for(const v of work(s))if(v.weight!==null&&v.rpeTarget!==null)assert.ok(G.estimateSet(v.weight,v.reps,v.rpeTarget)<=s.reference+1e-7);
  }
  if(age<18||age>85)assert.equal(p.sessions.at(-1).exercises[0].sets.filter(v=>v.role==='attempt').length,0);
  if(weeks===20&&step<=2.5)assert.ok(p.meta.progression.trajectory.last.weight>p.meta.progression.trajectory.first.weight);
 }assert.equal(count,1944);
});
test('Масса тела не выдумывает силу; возраст меняет дозировку, а не отнимает килограммы от результата',()=>{
 const a=G.generate({...profile,technique:'paused',resultDate:'2026-10-14',experience:'trained',bodyweight:50}),b=G.generate({...profile,technique:'paused',resultDate:'2026-10-14',experience:'trained',bodyweight:120});
 assert.deepEqual(a.sessions,b.sessions);assert.notEqual(a.meta.relativeGoal,b.meta.relativeGoal);
 const old=G.generate({...a.profile,age:65});assert.equal(old.meta.base,a.meta.base);assert.ok(work(old.sessions[0]).length<work(a.sessions[0]).length);
});
test('Давний рекорд и фактический перерыв — разные причины; соседние дни не считаются двумя силовыми днями',()=>{
 const regular=G.generate(profile),returning=G.generate({...profile,trainingContinuity:'returning'});
 assert.ok(returning.meta.progression.entryWeeks>regular.meta.progression.entryWeeks);
 assert.ok(regular.meta.progression.plannedResult>=returning.meta.progression.plannedResult);
 const adjacent=G.generate({...profile,weekdays:[1,2]}),one=G.generate({...profile,weekdays:[1]});
 assert.equal(adjacent.meta.progression.nominalGain,one.meta.progression.nominalGain);
 let hard=null;for(const s of adjacent.sessions)if(!['technique','deload','taper','test'].includes(s.phase)){if(hard)assert.ok(G.days(hard,s.date)>=2);hard=s.date;}
});
test('Пересмотр опирается на новый факт, без повторного начисления прошедших плановых прибавок',()=>{
 const p=G.generate({...profile,technique:'paused',resultDate:'2026-10-14',experience:'trained'}),logs={};
 const recent=p.sessions.filter(s=>s.date>='2026-12-01'&&s.date<'2026-12-20'&&['base','volume','strength'].includes(s.phase)).slice(-2);
 for(const s of recent)logs[p.id+'|'+s.id]=done(s,6.5,'ok',1.1);
 const d=recent.at(-1).date,a=G.review(p,logs,d);assert.ok(a.change&&a.base>p.meta.base);
 const next=G.applyReview(p,logs,d);assert.equal(next.profile.progressionAnchor,d);
 assert.equal(next.sessions.find(s=>s.date>d).reference>=next.meta.base,true);
 assert.ok(next.meta.progression.plannedResult<=next.meta.base*(1+next.meta.progression.upperGain)+p.profile.step);
 assert.equal(G.review(next,logs,d).change,false);
 assert.deepEqual(next.sessions.filter(s=>s.date<=d),p.sessions.filter(s=>s.date<=d));
});
test('Недобор и высокий RPE приостанавливают будущие прибавки; две полные переносимые рабочие части возобновляют',()=>{
 const p=G.generate(profile),s=p.sessions[0],logs={[p.id+'|'+s.id]:done(s,8,'hard')};
 let next=G.applyReview(p,logs,s.date);assert.equal(next.profile.progressionPaused,true);assert.equal(next.meta.progression.plannedGain,0);
 const upcoming=next.sessions.filter(v=>v.date>s.date&&v.phase==='base').slice(0,2);
 assert.equal(upcoming.length,2);
 for(const t of upcoming)logs[p.id+'|'+t.id]=done(t,6.5);
 const resumed=G.applyReview(next,logs,upcoming.at(-1).date);assert.equal(resumed.profile.progressionPaused,false);assert.ok(resumed.meta.progression.plannedGain>0);
});
test('Подсобная работа имеет прогрессию повторов; неизвестная исходная сила не даёт выдуманный прогноз',()=>{
 const p=G.generate(profile),reps=new Set(p.sessions.filter(s=>s.exercises[1]).map(s=>s.exercises[1].sets[0].reps));assert.ok(reps.size>=3);
 const unknown=G.generate({...profile,resultType:'unknown'});assert.equal(unknown.meta.progression.plannedResult,null);assert.equal(unknown.meta.goalAssessment.milestone,null);assert.ok(unknown.sessions.every(s=>work(s).every(v=>v.weight===null)));
});
test('Слишком тяжёлый гриф после факта не назначается повторно как минимальный безопасный вес',()=>{
 const p=G.generate({...profile,resultWeight:30,goal:40}),s=p.sessions[0],logs={[p.id+'|'+s.id]:done(s,9,'hard')};
 const after=G.applyReview(p,logs,s.date),next=after.sessions.find(v=>v.date>s.date);
 assert.ok(work(next).every(v=>v.weight===null));assert.ok(after.profile.progressionWorkWeight<p.profile.bar);
});
test('Крупный шаг блинов и короткий срок не создают прогноз прироста без нагрузочных недель',()=>{
 const p=G.generate({...profile,resultWeight:25,goal:30,step:5,target:'2026-10-29'});
 assert.equal(p.meta.goalAssessment.milestone,25);assert.equal(p.meta.goalAssessment.status,'unsupported');
 assert.ok(p.sessions.every(s=>s.reference<=25));
});
