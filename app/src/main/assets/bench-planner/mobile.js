'use strict';
window.MGymStorage={
  getItem:key=>window.MGymNative.read(key),
  setItem:(key,value)=>{if(!window.MGymNative.write(key,String(value)))throw Error('Не удалось записать данные в память приложения. Сохраните резервную копию.');}
};
document.querySelector('#native-import').addEventListener('click',()=>window.MGymNative.importBackup());
