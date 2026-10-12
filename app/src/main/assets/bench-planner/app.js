const months=['янв','фев','мар','апр','май','июн','июл','авг','сен','окт','ноя','дек'];
const fullDate=s=>new Intl.DateTimeFormat('ru-RU',{day:'numeric',month:'long',year:'numeric',timeZone:'UTC'}).format(new Date(s+'T12:00:00Z'));
const weight=n=>n==null?'по самочувствию':String(n).replace('.',',')+' кг';
const rest=n=>n>=60?Math.floor(n/60)+':'+String(n%60).padStart(2,'0'):n+' с';
const accessoryImages={
  'Тяга горизонтального блока':'cable-row.webp',
  'Разгибание рук на блоке':'triceps-pushdown.webp',
  'Тяга гантели в наклоне':'dumbbell-row.webp',
  'Разведения для задней дельты':'rear-delt-fly.webp',
  'Тяга верхнего блока':'lat-pulldown.webp',
  'Французское разгибание с гантелью':'overhead-triceps-extension.webp'
};
let plan,selected;

function selectSession(id){
  selected=id;history.replaceState(null,'','#'+id);renderCalendar();renderDetail();
}
function renderCalendar(){
  const session=plan.sessions.find(s=>s.id===selected);
  const weeks=document.querySelector('#weeks');weeks.replaceChildren();
  for(const n of [...new Set(plan.sessions.map(s=>s.week))]){
    const b=document.createElement('button');b.type='button';b.className='week'+(n===session.week?' active':'');b.textContent='Неделя '+n;b.setAttribute('aria-pressed',n===session.week);
    b.addEventListener('click',()=>selectSession(plan.sessions.find(s=>s.week===n).id));weeks.append(b);
  }
  const days=document.querySelector('#sessions');days.replaceChildren();
  for(const s of plan.sessions.filter(s=>s.week===session.week)){
    const d=new Date(s.date+'T12:00:00Z');
    const b=document.createElement('button');b.type='button';b.className='session'+(s.id===selected?' active':'');b.setAttribute('aria-current',s.id===selected?'date':'false');
    b.innerHTML='<span class="session-date">'+d.getUTCDate()+' '+months[d.getUTCMonth()]+'</span><span class="session-title"></span>';
    const status=window.Planner?.sessionStatus(s.id);
    b.querySelector('.session-title').textContent=(status==='done'?'✓ ':status==='skipped'?'— ':'')+s.title;
    b.addEventListener('click',()=>selectSession(s.id));days.append(b);
  }
}
function renderMainTable(ex){
  const section=document.createElement('section');section.className='main-exercise';
  section.innerHTML='<div class="exercise-title"><h3></h3><span class="set-count"></span></div><p class="exercise-hint"></p><table class="set-table"><thead><tr><th scope="col">№</th><th scope="col">Вес</th><th scope="col">Повт.</th><th scope="col">Отдых</th><th scope="col">Подход</th></tr></thead><tbody></tbody></table>';
  section.querySelector('h3').textContent=ex.name;
  section.querySelector('.set-count').textContent=ex.sets.length+' подходов';
  section.querySelector('.exercise-hint').textContent=ex.load;
  const tbody=section.querySelector('tbody');
  ex.sets.forEach((set,i)=>{
    const tr=document.createElement('tr');if(set.rpe)tr.className='heavy';
    const kind=set.kind+(set.rpeTarget?' · RPE '+set.rpeTarget:set.rpe?' · RPE':'');
    [i+1,set.weight===null?'подбор':weight(set.weight),set.reps,rest(set.rest),kind].forEach((value,j)=>{
      const td=document.createElement('td');td.textContent=value;if(j===4)td.className='kind';tr.append(td);
    });
    tbody.append(tr);
  });
  return section;
}
function renderAccessory(ex){
  const section=document.createElement('section');section.className='accessory';const sets=ex.sets;
  section.innerHTML='<img class="accessory-image" alt="" width="48" height="48" loading="lazy" decoding="async"><div class="accessory-body"><div class="accessory-title"><h4></h4><span class="accessory-prescription"></span></div><p class="accessory-meta"></p></div>';
  const illustration=accessoryImages[ex.name];
  if(illustration)section.querySelector('.accessory-image').src='assets/'+illustration;
  else section.querySelector('.accessory-image').remove();
  section.querySelector('h4').textContent=ex.name;
  const same=sets.every(s=>s.reps===sets[0].reps&&s.rest===sets[0].rest&&s.weight===sets[0].weight);
  section.querySelector('.accessory-prescription').textContent=same?sets.length+' × '+sets[0].reps:sets.map(s=>s.reps).join(' / ')+' повт.';
  const accessoryWeight=same?weight(sets[0].weight):sets.map(s=>weight(s.weight)).join(' / ');
  const guidance=ex.load.replace(/^Умеренный вес,\s*/,'');
  section.querySelector('.accessory-meta').textContent=(accessoryWeight==='по самочувствию'?'вес свободный':accessoryWeight)+' · отдых '+(same?rest(sets[0].rest):sets.map(s=>rest(s.rest)).join(' / '))+' · '+guidance;
  return section;
}
function renderDetail(){
  const s=plan.sessions.find(v=>v.id===selected),box=document.querySelector('#detail');box.replaceChildren();
  const top=document.createElement('div');top.className='detail-top';top.innerHTML='<div><span class="detail-date"></span><h2></h2></div><span class="day-count"></span>';
  top.querySelector('.detail-date').textContent=fullDate(s.date);
  top.querySelector('h2').textContent=s.title;
  const dayCount=top.querySelector('.day-count');dayCount.textContent='День '+(plan.sessions.indexOf(s)+1)+' / '+plan.sessions.length;
  const logButton=document.createElement('button');logButton.type='button';logButton.className='log-button';logButton.textContent='Записать факт';logButton.addEventListener('click',()=>window.Planner.openLog());dayCount.append(logButton);
  box.append(top);
  const content=document.createElement('div');content.className='detail-content';content.append(renderMainTable(s.exercises[0]));
  const aside=document.createElement('aside');aside.className='accessories';
  if(s.exercises.length>1){const title=document.createElement('h3');title.textContent='После жима';aside.append(title);s.exercises.slice(1).forEach(ex=>aside.append(renderAccessory(ex)))}
  const note=document.createElement('p');note.className='day-note';note.textContent=(plan.algorithm==='bench-natural-2'&&!s.phase?'Этот день уже записан: исходное назначение сохранено. ':'')+s.note;aside.append(note);
  content.append(aside);box.append(content);
}
fetch('plan.json').then(r=>{if(!r.ok)throw Error('План недоступен');return r.json()}).then(data=>{
  window.Planner.boot(data);
}).catch(e=>{document.querySelector('#detail').textContent='Не удалось загрузить план: '+e.message});
