const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),catalog={};
for(const file of ['catalog.tsv','progression.tsv','plans.tsv'])for(const [i,line] of fs.readFileSync(path.join(root,'localization',file),'utf8').split(/\r?\n/).entries()){
 if(!line || line.startsWith('#'))continue;
 const [key,...values]=line.split('|');
 if(values.length!==7||values.some(v=>!v))throw Error(`${file}:${i+1}: expected seven translations`);
 if(catalog[key])throw Error(`Duplicate key: ${key}`);
 const tokens=s=>[...s.matchAll(/\{\d+}/g)].map(m=>m[0]).sort().join(',');
 if(values.some(v=>tokens(v)!==tokens(key)))throw Error(`Placeholder mismatch: ${key}`);
 catalog[key]=values;
}
const assets=path.join(root,'app/src/main/assets');
fs.mkdirSync(path.join(assets,'i18n'),{recursive:true});
fs.writeFileSync(path.join(assets,'i18n/catalog.json'),JSON.stringify(catalog,null,2)+'\n');
fs.writeFileSync(path.join(assets,'bench-planner/i18n-catalog.js'),'window.MGymCatalog='+JSON.stringify(catalog)+';\n');
console.log(`${Object.keys(catalog).length} keys, 8 languages, placeholders verified`);
