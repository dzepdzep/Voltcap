const { chromium } = require('/opt/node-tools/node_modules/playwright');
const fs=require('fs');
(async()=>{
  const b=await chromium.launch(); const p=await b.newPage({viewport:{width:960,height:540}});
  await p.goto('file://'+__dirname+'/intro.html');
  const mode=process.argv[2]||'stills'; const short=process.argv[3]==='short';
  fs.mkdirSync(__dirname+'/frames',{recursive:true});
  const times = mode==='stills' ? [100,400,700,1000,1060,1150,1300,1500,1800,2100,2500] : [...Array(Math.ceil((short?1000:2750)/1000*30))].map((_,i)=>i*1000/30);
  let i=0;
  for(const t of times){const d=await p.evaluate(([t,s])=>window.render(t,s),[t,short]);
    fs.writeFileSync(`${__dirname}/frames/${mode==='stills'?'s_'+t:('f_'+String(i).padStart(4,'0'))}.png`,Buffer.from(d.split(',')[1],'base64')); i++;}
  await b.close();
})();
