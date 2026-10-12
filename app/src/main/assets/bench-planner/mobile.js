'use strict';
window.MGymStorage={
  getItem:key=>window.MGymNative.read(key),
  setItem:(key,value)=>{if(!window.MGymNative.write(key,String(value)))throw Error('Не удалось записать данные в память приложения. Сохраните резервную копию.');}
};
document.querySelector('#native-import').addEventListener('click',()=>window.MGymNative.importBackup());

// Keep the heading and save action visible while the long form scrolls.
for(const dialog of document.querySelectorAll('dialog')){
  const heading=dialog.querySelector('.dialog-heading'),body=document.createElement('div');
  body.className='mobile-dialog-body';
  for(const node of [...dialog.childNodes])if(node!==heading)body.append(node);
  dialog.append(body);
}
const save=document.querySelector('#log-form button[type=submit]');
save.setAttribute('form','log-form');
const footer=document.createElement('div');footer.className='mobile-dialog-footer';footer.append(save);
document.querySelector('#log-dialog').append(footer);

function sizeDialogs(){
  const viewport=window.visualViewport;
  const height=viewport?.height||window.innerHeight;
  if(height>0){
    document.documentElement.style.setProperty('--mg-dialog-height',Math.max(1,height-16)+'px');
    document.documentElement.style.setProperty('--mg-dialog-top',((viewport?.offsetTop||0)+8)+'px');
  }
  const focused=document.activeElement;
  if(focused?.matches('dialog[open] input,dialog[open] select'))requestAnimationFrame(()=>focused.scrollIntoView({block:'nearest'}));
}
sizeDialogs();
window.addEventListener('resize',sizeDialogs);
window.visualViewport?.addEventListener('resize',sizeDialogs);
window.visualViewport?.addEventListener('scroll',sizeDialogs);
