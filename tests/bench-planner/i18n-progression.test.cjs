const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const assets=path.resolve(__dirname,'../../app/src/main/assets/bench-planner'),G=require(path.join(assets,'engine-v3.js'));
const profile={name:'QA',age:35,bodyweight:80,goal:100,start:'2026-10-15',target:'2027-02-28',experience:'novice',trainingContinuity:'regular',recovery:'normal',resultType:'max',resultWeight:75,reps:1,technique:'touch',resultDate:'2026-07-01',weekdays:[1,3,5],bar:20,step:2.5,equipment:'gym',pain:false};
function translate(tag){
 const c={window:{MGymNative:{language:()=>tag}},document:{documentElement:{},addEventListener(){}},MutationObserver:class{observe(){}disconnect(){}},Intl,Date,queueMicrotask};vm.createContext(c);
 for(const file of ['i18n-catalog.js','i18n-progress.js','i18n.js'])vm.runInContext(fs.readFileSync(path.join(assets,file),'utf8'),c);
 return c.window.MGymI18n.text;
}
test('Новая прогрессия, динамические предупреждения и сводка переводятся на все восемь языков',()=>{
 const texts=new Set(['Тренировки последние 4 недели','Тренируюсь регулярно','Возвращаюсь после перерыва','Пока не могу оценить','Копия до обновления 0.6.0','Исходная оценка: 75 kg; Цель: 100 kg. Плановый ориентир цикла: 87.5 kg. Контроль при готовности: 75 kg × 3. Рабочие подходы: 47.5 kg × 6 → 75 kg × 3']);
 for(const discipline of ['bench','squat','deadlift']){
  const p=G.generate({...profile,discipline});for(const t of p.meta.warnings)texts.add(t);
  for(const s of p.sessions){texts.add(s.title);texts.add(s.note);for(const ex of s.exercises)texts.add(ex.load);}
 }
 const missing=new Set();for(const tag of ['en','de','fr','es','pt-BR','zh-Hans']){
  const t=translate(tag);for(const source of texts)if(/[А-Яа-яЁё]/.test(t(source)))missing.add(source);
 }assert.deepEqual([...missing],[]);
 const kk=translate('kk');assert.notEqual(kk('Тренировки последние 4 недели'),'Тренировки последние 4 недели');assert.match(kk('Рабочие подходы: 47.5 kg × 6 → 75 kg × 3'),/47\.5.*75/);
});
test('Удаление, подтверждение и восстановление переводятся без изменения названия плана',()=>{
 const texts=['Удалить план','Удалить план?','Копия до последнего удаления','План удалён. Копия до удаления доступна в «Мои данные».','Удалённых планов на этом устройстве ещё нет.','Удалятся выбранная программа и её записи в планировщике. Выполненные тренировки в дневнике MGym и остальные планы сохранятся. Перед удалением автоматически сохраняется копия всех планов: «Мои данные» → «Копия до последнего удаления».'];
 for(const tag of ['en','de','fr','es','pt-BR','zh-Hans'])for(const source of texts)assert.doesNotMatch(translate(tag)(source),/[А-Яа-яЁё]/);
 assert.equal(translate('en')('User QA 150'),'User QA 150');
});
