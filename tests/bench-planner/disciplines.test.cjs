const {test}=require('node:test'),assert=require('node:assert/strict'),G=require('../../app/src/main/assets/bench-planner/engine-v3.js');
const profile={name:'План',age:35,bodyweight:90,goal:180,start:'2026-10-12',target:'2026-12-05',experience:'trained',recovery:'normal',resultType:'max',resultWeight:160,reps:1,technique:'paused',resultDate:'2026-10-10',weekdays:[1,4],bar:20,step:2.5,equipment:'gym',pain:false};
test('Три независимых движения: названия, подсобная работа, ограничения объёма и копия',()=>{
 for(const discipline of ['bench','squat','deadlift'])for(const weekdays of [[1],[1,4],[1,3,5],[0,1,3,5]])for(const equipment of ['gym','dumbbells','none']){
  const p=G.generate({...profile,discipline,weekdays,equipment});G.validatePlan(p);
  assert.equal(p.profile.discipline,discipline);assert.ok(p.sessions.every(s=>s.exercises[0].name===({bench:'Жим лёжа с паузой 1–2 с',squat:'Приседания',deadlift:'Становая тяга'})[discipline]));
  if(discipline==='deadlift')for(const s of p.sessions)assert.ok(s.exercises[0].sets.filter(v=>v.role==='work').length<=2);
  if(discipline!=='bench')assert.ok(!p.sessions.flatMap(s=>s.exercises).some(e=>/Трицепс|Разгибание рук|Французское/.test(e.name)));
  const state={version:1,active:p.id,plans:[p],logs:{},current:{[discipline]:p.id}};assert.deepEqual(G.validateState(JSON.parse(JSON.stringify(state))),state);
 }
 assert.ok(G.validate({...profile,discipline:'unknown'}).length);
});
test('Присед и тяга рядом согласуются без накопительного снижения; разнесённые дни сохраняются',()=>{
 const squat=G.generate({...profile,discipline:'squat',weekdays:[1]}),dead=G.generate({...profile,discipline:'deadlift',weekdays:[2]});
 const once=G.coordinate([squat,dead],{},profile.start),twice=G.coordinate(once,{},profile.start);assert.deepEqual(once,twice);
 assert.equal(once[1].sessions[0].phase,'technique');assert.equal(once[1].sessions[0].exercises.length,1);
 assert.ok(once[1].sessions[0].exercises[0].sets.every(v=>v.weight<=dead.meta.base*.55));
 const spaced=G.generate({...profile,discipline:'deadlift',weekdays:[4]});assert.equal(G.coordinate([squat,spaced],{},profile.start)[1].sessions[0].phase,spaced.sessions[0].phase);
});
test('Записанный факт не меняется; новая тяга учитывает вчерашний присед',()=>{
 const squat=G.generate({...profile,discipline:'squat',weekdays:[1]}),dead=G.generate({...profile,discipline:'deadlift',weekdays:[2]});
 const s=squat.sessions[0],logs={[squat.id+'|'+s.id]:{status:'done',sets:[{kg:100,reps:5,rpe:8}],verified:true,feedback:'ok'}};
 const result=G.coordinate([squat,dead],logs,'2026-10-13');assert.deepEqual(result[0].sessions[0],s);assert.equal(result[1].sessions[0].phase,'technique');
});
test('Пересмотр и перенос цели сохраняют движение и его прошлые факты',()=>{
 for(const discipline of ['squat','deadlift']){
  const p=G.generate({...profile,discipline}),s=p.sessions[0];const sets=s.exercises.flatMap((e,i)=>e.sets.map(v=>({kg:i?20:v.weight,reps:v.reps,rpe:i||!v.rpe?null:9.5})));
  const logs={[p.id+'|'+s.id]:{status:'done',sets,verified:true,feedback:'hard'}};
  const after=G.applyReview(p,logs,s.date);assert.equal(after.profile.discipline,discipline);assert.deepEqual(after.sessions[0],s);
  const moved=G.replan(after,logs,{target:'2027-02-06',weekdays:[2]},'2026-10-20');G.validatePlan(moved);assert.equal(moved.profile.discipline,discipline);assert.deepEqual(moved.sessions[0],s);
 }
});
test('Выбранный старый план участвует в согласовании, архивная альтернатива не мешает',()=>{
 const squat=G.generate({...profile,discipline:'squat',weekdays:[1]}),alternative=G.generate({...profile,discipline:'squat',weekdays:[4]}),dead=G.generate({...profile,discipline:'deadlift',weekdays:[2]});
 const automatic=G.coordinate([squat,alternative,dead],{},profile.start);assert.notEqual(automatic[2].sessions[0].phase,'technique');
 const selected=G.coordinate([squat,alternative,dead],{},profile.start,{squat:squat.id});assert.equal(selected[2].sessions[0].phase,'technique');
});
