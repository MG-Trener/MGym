const {test}=require('node:test');
const assert=require('node:assert/strict');
const G=require('../../app/src/main/assets/bench-planner/engine-v3.js');
const p={name:'Жим',discipline:'bench',age:35,bodyweight:80,goal:100,start:'2026-10-15',target:'2027-02-28',experience:'novice',trainingContinuity:'regular',recovery:'normal',resultType:'max',resultWeight:75,reps:1,technique:'paused',resultDate:'2026-10-14',weekdays:[1,4],bar:20,step:2.5,equipment:'gym',pain:false};
const log={status:'done',feedback:'ok',sets:[{kg:20,reps:10,rpe:null}]};
test('Первый запуск и импорт прежней копии не возвращают исходный план',()=>{
  assert.equal(G.validateState({version:1,active:'original',plans:[],logs:{}}).active,'');
  const plan=G.generate(p),logs={'original|2026-10-12':log};
  const restored=G.validateState({version:1,active:'original',plans:[plan],logs});
  assert.equal(restored.active,plan.id);assert.deepEqual(restored.logs,logs);
  assert.doesNotThrow(()=>G.validateState({version:1,active:'',plans:[],logs}));
});
test('Удаление меняет только выбранный план, его журнал и ссылку текущей дисциплины',()=>{
  const a=G.generate(p),b=G.generate({...p,name:'Второй жим'}),c=G.generate({...p,name:'Присед',discipline:'squat'});
  const key=x=>x.id+'|'+x.sessions[0].id;
  const state={version:1,active:a.id,plans:[a,b,c],current:{bench:a.id,squat:c.id},logs:{[key(a)]:log,[key(b)]:log,[key(c)]:log,'original|2026-10-12':log}};
  const backup=structuredClone(state),next=G.removePlan(state,a.id);
  assert.deepEqual(state,backup);assert.deepEqual(next.plans,[b,c]);assert.equal(next.active,b.id);
  assert.deepEqual(next.current,{bench:b.id,squat:c.id});assert.deepEqual(next.logs,{[key(b)]:log,[key(c)]:log,'original|2026-10-12':log});
  assert.deepEqual(G.validateState(JSON.parse(JSON.stringify(backup))),backup);
  assert.throws(()=>G.removePlan(state,'missing'));assert.throws(()=>G.removePlan(state,'original'));
});
test('Удаление последнего плана оставляет пустой валидный планировщик; импорт восстанавливает копию',()=>{
  const plan=G.generate(p),state={version:1,active:plan.id,plans:[plan],current:{bench:plan.id},logs:{[plan.id+'|'+plan.sessions[0].id]:log}};
  const next=G.removePlan(state,plan.id);
  assert.deepEqual(next,{version:1,active:'',plans:[],current:{},logs:{}});
  assert.deepEqual(G.validateState(JSON.parse(JSON.stringify(state))),state);
});
test('Удаление неактивного плана сохраняет активную программу другой дисциплины',()=>{
  const a=G.generate(p),b=G.generate({...p,discipline:'deadlift'});
  const state={version:1,active:b.id,plans:[a,b],current:{bench:a.id,deadlift:b.id},logs:{}};
  const next=G.removePlan(state,a.id);assert.equal(next.active,b.id);assert.deepEqual(next.current,{deadlift:b.id});
});
