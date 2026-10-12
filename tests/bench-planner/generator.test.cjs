const {test}=require('node:test');
const assert=require('node:assert/strict');
const G=require('../../app/src/main/assets/bench-planner/generator.js');
const profile={name:'Проверка',age:35,bodyweight:90,goal:150,start:'2026-10-12',target:'2026-12-05',experience:'trained',recovery:'normal',resultType:'set',resultWeight:132.5,reps:3,technique:'touch',resultDate:'2026-10-10',weekdays:[1,3,6],bar:20,step:2.5,equipment:'gym',pain:false};
test('Цель не завышает нагрузку; результат без паузы получает запас',()=>{
  const a=G.generate(profile),b=G.generate({...profile,goal:300}),c=G.generate({...profile,technique:'paused'});
  assert.deepEqual(a.sessions,b.sessions);assert.ok(a.meta.base<c.meta.base);assert.ok(a.meta.base<132.5*1.1);
});
test('Календарь и нагрузки не зависят от даты повторного открытия',()=>{
  const a=G.generate(profile),realDate=global.Date;
  global.Date=class extends realDate{constructor(...a){super(...(a.length?a:['2026-11-10T00:00:00Z']));}static now(){return 1794268800000;}};
  try{assert.deepEqual(G.generate(profile).sessions,a.sessions);}finally{global.Date=realDate;}
});
test('Неизвестный максимум не порождает выдуманные веса и максимальные попытки',()=>{
  const a=G.generate({...profile,resultType:'unknown'});assert.equal(a.meta.base,null);for(const s of a.sessions)for(const e of s.exercises)for(const v of e.sets){assert.equal(v.weight,null);assert.equal(v.rpe,false);}
});
test('Возраст и восстановление уменьшают объём, не силу по произвольной формуле',()=>{
  const a=G.generate(profile),b=G.generate({...profile,age:65});assert.equal(a.meta.base,b.meta.base);assert.ok(b.sessions[0].exercises[0].sets.length<a.sessions[0].exercises[0].sets.length);assert.ok(G.days(b.sessions.at(-2).date,b.sessions.at(-1).date)>5);
});
test('Границы и недопустимые вводы',()=>{
  for(const bad of [{age:17},{age:NaN},{bodyweight:0},{goal:Infinity},{target:'2026-10-20'},{target:'2026-02-30'},{resultDate:'2026-10-13'},{resultDate:'2025-10-10'},{weekdays:[0,1]},{weekdays:[1,1]},{weekdays:[1]},{pain:true},{recovery:'limited'},{reps:11}])assert.throws(()=>G.generate({...profile,...bad}));
  for(const target of ['2026-11-09','2027-03-29'])assert.ok(G.generate({...profile,target}).sessions.length);
});
test('Разные профили: даты, веса, шаг, подводка, RPE, упражнения',()=>{
  let count=0;
  for(const age of [18,40,60,85])for(const experience of ['novice','trained','advanced'])for(const step of [1,1.25,2.5,5])for(const resultType of ['max','set','unknown']){
    const p={...profile,age,experience,step,resultType,reps:resultType==='max'?1:3};const plan=G.generate(p);G.validatePlan(plan);count++;
    for(const [i,s] of plan.sessions.entries()){
      if(i)assert.ok(G.days(plan.sessions[i-1].date,s.date)>=2);
      if(i<plan.sessions.length-1)assert.ok(p.weekdays.includes(new Date(s.date+'T00:00:00Z').getUTCDay()));
      const warm=s.exercises[0].sets.filter(v=>v.kind==='Разминка');for(let j=1;j<warm.length;j++)assert.ok(warm[j].weight>warm[j-1].weight);
      for(const [ei,e] of s.exercises.entries())for(const v of e.sets){
        if(v.weight!==null){assert.ok(v.weight>=p.bar);assert.ok(Math.abs((v.weight-p.bar)/step-Math.round((v.weight-p.bar)/step))<1e-6);}
        if(v.rpe){assert.equal(ei,0);assert.equal(v.reps,1);assert.notEqual(experience,'novice');}
      }
      if(G.days(s.date,p.target)<=14&&i<plan.sessions.length-1){assert.equal(s.exercises.length,1);assert.equal(s.exercises[0].sets.filter(v=>!v.kind.toLowerCase().includes('разминка')).length,2);}
    }
  }assert.equal(count,144);
});
test('Резервная копия: полное восстановление и отклонение повреждений',()=>{
  const p=G.generate(profile),v={version:1,active:p.id,plans:[p],logs:{[p.id+'|'+p.sessions[0].id]:{status:'done',feedback:'ok',sets:[{kg:20,reps:10,rpe:null}]}}};assert.deepEqual(G.validateState(JSON.parse(JSON.stringify(v))),v);
  for(const change of [v=>v.version=2,v=>v.plans[0].sessions[0].date='2026-02-30',v=>v.plans[0].profile.age=10,v=>v.plans[0].meta=null,v=>v.active='missing',v=>v.logs.bad={},v=>v.plans.push(v.plans[0]),v=>v.plans[0].sessions[0].exercises[0].sets[0].weight=-1]){const bad=structuredClone(v);change(bad);assert.throws(()=>G.validateState(bad));}
});
