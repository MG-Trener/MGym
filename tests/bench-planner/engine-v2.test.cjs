const {test}=require('node:test');
const assert=require('node:assert/strict');
const G=require('../../app/src/main/assets/bench-planner/engine-v2.js');
const profile={name:'Регрессия',age:35,bodyweight:90,goal:150,start:'2026-10-12',target:'2026-12-05',experience:'trained',recovery:'normal',resultType:'set',resultWeight:132.5,reps:3,technique:'touch',resultDate:'2026-10-10',weekdays:[1,3,5],bar:20,step:2.5,equipment:'gym',pain:false};
const work=s=>s.exercises[0].sets.filter(x=>x.kind!=='Разминка');
test('132,5 × 3: нет двойного снижения; первая тренировка проверяет паузу',()=>{
  const p=G.generate(profile),w=work(p.sessions[0]);assert.equal(p.meta.base,145.75);
  assert.deepEqual(w.map(x=>[x.weight,x.reps]),[[115,1],[120,1],[105,4],[105,4],[105,4]]);
  assert.equal(w[1].optional,true);assert.match(p.sessions[0].note,/≤RPE 6/);
  assert.equal(G.generate({...profile,resultType:'set',resultWeight:120,reps:1}).meta.base,120);
});
test('Цель не подменяет возможности; нет выдуманного постоянного штрафа за паузу',()=>{
  const a=G.generate(profile),b=G.generate({...profile,goal:300}),c=G.generate({...profile,technique:'paused'});assert.deepEqual(a.sessions,b.sessions);assert.equal(a.meta.base,c.meta.base);assert.match(a.rules.join(' '),/Разница с паузой индивидуальна/);
});
test('Силовая ступень работает; подводка сохраняет специфическую нагрузку',()=>{
  const p=G.generate(profile),singles=p.sessions.filter(s=>s.phase==='strength').flatMap(work).filter(v=>v.role==='single');
  assert.ok(Math.max(...singles.map(s=>s.weight))>=130);assert.ok(singles.at(-1).weight>singles[0].weight);
  const taper=p.sessions.filter(s=>s.phase==='taper');assert.ok(taper.some(s=>work(s).some(v=>v.role==='single'&&v.weight>=130)));
  assert.ok(taper.some(s=>G.days(s.date,profile.target)<=7&&work(s).some(v=>v.role==='single')));
  assert.ok(taper.every(s=>work(s).filter(v=>v.role==='work').length<=2));
});
test('Два дня включают силовую и объёмную работу',()=>{
  const p=G.generate({...profile,weekdays:[1,4]});assert.ok(p.sessions.some(s=>s.phase==='volume'));assert.ok(!p.sessions.some(s=>s.phase==='technique'));
});
test('Календарь не зависит от дня открытия',()=>{
  const a=G.generate(profile),D=global.Date;global.Date=class extends D{constructor(...args){super(...(args.length?args:['2026-11-10T00:00:00Z']));}};
  try{assert.deepEqual(G.generate(profile).sessions,a.sessions);}finally{global.Date=D;}
});
test('Неизвестный результат, новичок и старые данные не получают обязательного максимума',()=>{
  const p=G.generate({...profile,resultType:'unknown'});for(const s of p.sessions)for(const ex of s.exercises)for(const v of ex.sets){assert.equal(v.weight,null);assert.equal(v.rpe,false);}
  for(const changes of [{experience:'novice'},{resultDate:'2026-06-01'}]){const p=G.generate({...profile,...changes});assert.equal(p.sessions.at(-1).title,'Контроль техники');assert.ok(p.sessions.every(s=>!work(s).some(v=>v.role==='single')));}
});
test('Возраст меняет объём, не уменьшает оценку силы',()=>{
  const a=G.generate(profile),b=G.generate({...profile,age:65});assert.equal(a.meta.base,b.meta.base);assert.ok(work(b.sessions[0]).length<work(a.sessions[0]).length);assert.ok(G.days(b.sessions.at(-2).date,b.sessions.at(-1).date)>5);
});
test('432 профиля: даты, округление, RPE, разминка',()=>{
  let n=0;for(const age of [18,40,60,85])for(const experience of ['novice','trained','advanced'])for(const step of [1,1.25,2.5,5])for(const resultType of ['max','set','unknown'])for(const target of ['2026-11-09','2026-12-05','2027-03-29']){
    const p={...profile,age,experience,step,resultType,target,reps:resultType==='max'?1:3},plan=G.generate(p);G.validatePlan(plan);n++;
    for(const [i,s] of plan.sessions.entries()){
      if(i)assert.ok(G.days(plan.sessions[i-1].date,s.date)>=2);
      if(i<plan.sessions.length-1)assert.ok(p.weekdays.includes(new Date(s.date+'T00:00:00Z').getUTCDay()));
      const warm=s.exercises[0].sets.filter(v=>v.kind==='Разминка');for(let j=1;j<warm.length;j++)if(warm[j].weight!==null)assert.ok(warm[j].weight>warm[j-1].weight);
      for(const [ei,ex] of s.exercises.entries())for(const v of ex.sets){if(v.weight!==null){assert.ok(v.weight>=p.bar);assert.ok(Math.abs((v.weight-p.bar)/step-Math.round((v.weight-p.bar)/step))<1e-6);}if(v.rpe){assert.equal(ei,0);assert.notEqual(experience,'novice');assert.ok(['single','work','attempt'].includes(v.role));}}
    }
  }assert.equal(n,432);
});
test('Миграция сохраняет даты, ID и индексы любых записанных подходов',()=>{
  const old=G.legacyGenerate(profile),before=structuredClone(old),logs={[old.id+'|'+old.sessions[1].id]:{status:'planned',feedback:'ok',sets:[{kg:20,reps:10,rpe:null}]}};
  const upgraded=G.upgrade(old,logs);assert.equal(upgraded.id,old.id);assert.equal(upgraded.createdAt,old.createdAt);assert.deepEqual(upgraded.sessions.map(s=>s.date),old.sessions.map(s=>s.date));assert.deepEqual(upgraded.sessions[1],old.sessions[1]);assert.deepEqual(old,before);assert.equal(work(upgraded.sessions[0])[0].weight,115);assert.equal(G.upgrade(upgraded,logs),upgraded);G.validatePlan(upgraded);
});
function logged(p,date,kg,rpe,feedback='ok'){
  const s=p.sessions.find(s=>s.date===date),i=s.exercises[0].sets.findIndex(v=>v.role==='single');assert.ok(i>=0);
  const sets=s.exercises.flatMap(e=>e.sets.map(()=>({kg:null,reps:null,rpe:null})));sets[i]={kg,reps:1,rpe};return {[p.id+'|'+date]:{sets,status:'done',feedback,verified:true}};
}
test('Пересмотр ограничивает рост и сохраняет факты; одна запись не применяется дважды',()=>{
  const p=G.generate(profile),logs=logged(p,'2026-10-19',135,7);const a=G.review(p,logs,'2026-10-19');assert.ok(a.base>p.meta.base);assert.ok(a.base-p.meta.base<=2.5);
  const next=G.applyReview(p,logs,'2026-10-19');assert.deepEqual(next.sessions.map(s=>s.date),p.sessions.map(s=>s.date));assert.deepEqual(next.sessions.filter(s=>s.date<='2026-10-19'),p.sessions.filter(s=>s.date<='2026-10-19'));assert.equal(G.review(next,logs,'2026-10-19').base,null);G.validatePlan(next);
});
test('Боль, RPE ≤5, непроверенная пауза, будущая/старая запись не повышают вес',()=>{
  const p=G.generate(profile);
  for(const [kg,rpe,feedback,verified,asOf] of [[135,7,'pain',true,'2026-10-19'],[120,5,'ok',true,'2026-10-19'],[135,7,'ok',false,'2026-10-19'],[135,7,'ok',true,'2026-10-18'],[135,7,'ok',true,'2026-11-20']]){const logs=logged(p,'2026-10-19',kg,rpe,feedback);Object.values(logs)[0].verified=verified;assert.equal(G.review(p,logs,asOf).base,null);}
});
test('Перегрузка снижает базу; при подводке рост заблокирован',()=>{
  const p=G.generate(profile);assert.ok(G.review(p,logged(p,'2026-10-19',120,9),'2026-10-19').base<p.meta.base);
  const s=p.sessions.find(s=>s.phase==='taper'&&work(s).some(v=>v.role==='single'));assert.equal(G.review(p,logged(p,s.date,140,7),s.date).base,null);
});
test('Копия v2 и повреждённая история',()=>{
  const p=G.generate(profile),v={version:1,active:p.id,plans:[p],logs:logged(p,p.sessions[0].date,115,7)};assert.deepEqual(G.validateState(JSON.parse(JSON.stringify(v))),v);v.plans[0].meta.reviewed=['invalid'];assert.throws(()=>G.validateState(v));
});
