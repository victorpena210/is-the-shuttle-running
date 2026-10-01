/**
 * Reproducible, silent product film. All interface scenes are illustrations
 * based on the supplied application's templates; all people/numbers are samples.
 * Requires Node 20+, @napi-rs/canvas, ffmpeg. No network or application access.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import { once } from 'node:events';

const require = createRequire(import.meta.url);
let canvasModule;
try { canvasModule = require('@napi-rs/canvas'); }
catch { canvasModule = require(path.join(process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES || '', '@napi-rs/canvas')); }
const { createCanvas, loadImage } = canvasModule;
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const OUT = path.resolve(process.env.VIDEO_OUTPUT || path.join(ROOT, 'src/main/resources/static/video'));
const PREVIEW = process.env.VIDEO_PREVIEW || path.join(ROOT, 'target/video-preview');
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(PREVIEW, { recursive: true });

const W = 1920, H = 1080, FPS = 30, DURATION = 80;
const C = { bg: '#f7f8f2', ink: '#17201c', green: '#c8f36b', muted: '#586359', line: '#dce1d6', pale: '#e8eddf', white: '#ffffff', darkLine: '#374438', amber: '#ffd580' };
const canvas = createCanvas(W, H), ctx = canvas.getContext('2d');
const logo = await loadImage(path.join(ROOT, 'src/main/resources/static/images/shuttle-mark.svg'));
const font = '"Nimbus Sans", "Arial", sans-serif';
const mono = '"Nimbus Mono PS", monospace';
const clamp = (v, min=0, max=1) => Math.min(max, Math.max(min,v));
const ease = v => 1-Math.pow(1-clamp(v),3);
const lerp = (a,b,t) => a+(b-a)*t;

function rect(x,y,w,h,fill,r=0,stroke=null,lw=1) {
  ctx.beginPath(); ctx.roundRect(x,y,w,h,r);
  if(fill){ctx.fillStyle=fill;ctx.fill();}
  if(stroke){ctx.strokeStyle=stroke;ctx.lineWidth=lw;ctx.stroke();}
}
function text(s,x,y,size=28,color=C.ink,weight=400,align='left',family=font) {
  ctx.font=`${weight} ${size}px ${family}`;ctx.fillStyle=color;ctx.textAlign=align;ctx.textBaseline='top';ctx.fillText(s,x,y);
}
function lines(ss,x,y,size=28,color=C.ink,weight=400,leading=1.25){ss.forEach((s,i)=>text(s,x,y+i*size*leading,size,color,weight));}
function line(x1,y1,x2,y2,color=C.line,lw=2){ctx.beginPath();ctx.moveTo(x1,y1);ctx.lineTo(x2,y2);ctx.strokeStyle=color;ctx.lineWidth=lw;ctx.stroke();}
function dot(x,y,r,color){ctx.beginPath();ctx.arc(x,y,r,0,2*Math.PI);ctx.fillStyle=color;ctx.fill();}
function arrow(x,y,color=C.ink,size=23){line(x-size,y+size,x+size,y-size,color,3);line(x-size,y-size,x+size,y-size,color,3);line(x+size,y-size,x+size,y+size,color,3);}
function check(x,y,size=12,color=C.ink){ctx.lineCap='round';line(x-size,y,x-2,y+size*.7,color,3);line(x-2,y+size*.7,x+size,y-size*.7,color,3);ctx.lineCap='butt';}
function pill(label,x,y,w,fill=C.pale,color=C.ink){rect(x,y,w,38,fill,19);text(label,x+w/2,y+10,20,color,700,'center');}
function label(s,x,y,color=C.muted){text(s,x,y,20,color,600,'left',mono);}
function btn(s,x,y,w,fill=C.green,color=C.ink){rect(x,y,w,58,fill,8);text(s,x+w/2,y+18,25,color,700,'center');}
function inAt(t,start,fn){const a=ease((t-start)/.65);ctx.save();ctx.globalAlpha*=a;ctx.translate(0,22*(1-a));fn();ctx.restore();}
function focus(x,y,w,h,t){const a=clamp(t/.4)*clamp((2.7-t)/.4);if(a<=0)return;ctx.save();ctx.globalAlpha=a;rect(x-7,y-7,w+14,h+14,null,19,'#739c3f',3);ctx.restore();}
function pointer(x,y,t,click=false){ctx.save();ctx.translate(x,y);ctx.beginPath();ctx.moveTo(0,0);ctx.lineTo(5,29);ctx.lineTo(12,22);ctx.lineTo(19,33);ctx.lineTo(25,29);ctx.lineTo(18,18);ctx.lineTo(28,15);ctx.closePath();ctx.fillStyle=C.ink;ctx.fill();ctx.strokeStyle=C.bg;ctx.lineWidth=2;ctx.stroke();ctx.restore();if(click){ctx.save();ctx.globalAlpha=1-clamp(t);ctx.beginPath();ctx.arc(x+7,y+12,10+26*clamp(t),0,Math.PI*2);ctx.strokeStyle='#749e3d';ctx.lineWidth=4;ctx.stroke();ctx.restore();}}
function shell(t,chapter,dark=false){
  const bg=dark?C.ink:C.bg,ink=dark?C.bg:C.ink;
  rect(0,0,W,H,bg);ctx.drawImage(logo,80,47,59,59);
  lines(['Is the shuttle','running?'],156,47,27,ink,700,1.04);
  label('PRODUCT WALKTHROUGH',1330,59,dark?'#b9c6b8':C.muted);
  text('80 SEC',1838,60,20,dark?C.green:C.muted,600,'right',mono);
  line(80,135,1840,135,dark?C.darkLine:C.line);
  line(80,1004,1840,1004,dark?C.darkLine:C.line);
  text('istheshuttlerunning.com',80,1025,24,dark?C.green:C.ink,700);
  text(chapter,1840,1028,19,dark?'#b9c6b8':C.muted,500,'right',mono);
  rect(80,1071,1760,3,dark?C.darkLine:C.line);
  rect(80,1071,1760*clamp(t/DURATION),3,dark?C.green:'#759e3b');
}
function caption(s,dark=false){text(s,960,939,28,dark?'#d6e0d1':C.muted,400,'center');}
function heading(kicker,title,description){label(kicker,100,177);text(title,100,218,57,C.ink,700);if(description)text(description,100,282,27,C.muted);}
function uiFrame(tab,y=339){
  rect(100,y,1720,550,C.white,20,C.line,2);
  rect(100,y,1720,73,C.ink,20);rect(100,y+35,1720,38,C.ink);
  ctx.drawImage(logo,123,y+14,44,44);text('Is the shuttle running?',183,y+23,25,C.bg,700);
  const tabs=[['Schedules',593,137],['Demand',765,118],['Coverage',914,128],['Records',1074,118]];
  for(const [name,x,w]of tabs){if(tab===name)rect(x-13,y+17,w+18,40,C.green,7);text(name,x,y+27,20,tab===name?C.ink:'#cbd3c7',tab===name?700:400);}
  text('SAMPLE ORGANIZATION',1785,y+29,18,'#bcc7b7',500,'right',mono);
}
function miniBrand(x,y){ctx.drawImage(logo,x,y,44,44);lines(['Is the shuttle','running?'],x+57,y,21,C.ink,700,1.06);}

function intro(u,t,poster=false){
  shell(t,'ONE SHUTTLE DAY. THREE PERSPECTIVES.');
  inAt(poster?5:u,.1,()=>{
    label('A SIMPLE QUESTION DESERVES A CLEAR ANSWER.',100,230);
    text('Is the shuttle',96,299,100,C.ink,700);
    rect(101,496,463,27,C.green);text('running?',96,405,112,C.ink,700);
    lines(['One shared answer.', 'A coordinated team behind it.'],100,575,37,C.muted,400,1.4);
    [['Managers',100,171],['Drivers',289,139],['Riders',446,130]].forEach(([s,x,w])=>pill(s,x,741,w));
  });
  inAt(poster?5:u,.45,()=>{
    rect(1057,213,753,650,C.ink,26);
    label('THE RIDER’S VIEW',1106,261,'#b9c6b8');text('EXAMPLE',1758,261,19,'#b9c6b8',400,'right',mono);
    line(1106,306,1760,306,C.darkLine);
    pill('Service running',1106,342,232,'#2b3c24',C.green);
    dot(1739,360,9+Math.sin(u*2)*2,C.green);
    lines(['Yes.', 'We’re moving.'],1100,418,82,C.green,700,1.05);
    text('Clear status. One shared link.',1106,644,30,'#c3ccc1');
    line(1110,761,1746,761,'#668b42',3);dot(1110,761,8,C.green);dot(1746,761,8,C.green);
    ctx.drawImage(logo,poster?1410:lerp(1200,1570,ease(u/7)),728,65,65);
    text('Example organization · Service status',1433,817,20,'#b9c6b8',400,'center');
  });
  caption('Plan the service. Record the drive. Keep riders informed.');
}
function schedules(u,t){
  shell(t,'01 / MANAGERS');heading('FOR THE PERSON PLANNING THE DAY','Start with the schedule.','Import a Dayforce CSV and keep a history of your uploads.');uiFrame('Schedules');
  inAt(u,.1,()=>{
    label('WORKFORCE SCHEDULE',139,446);text('Upload weekly schedule',139,485,35,C.ink,700);
    text('Use the exported Dayforce CSV.',139,535,26,C.muted);
    rect(140,595,1030,108,C.bg,12,C.line,2);
    rect(163,616,60,66,C.pale,9);label('CSV',174,640,C.ink);
    text('employee-schedule.csv',247,616,29,C.ink,700);text('Selected file · example data',247,657,22,C.muted);
    const p=ease((u-2.2)/2.2);rect(143,705,1024,4,C.pale);rect(143,705,1024*p,4,'#789d45');
    btn(u<4.4?'Upload schedule':'Schedule imported',140,747,308,u<4.4?C.green:C.pale);
    if(u>4.4){check(478,776,11,'#4d702a');text('Import saved to history',505,764,23,C.muted);}
    if(u>1.5&&u<3.1)pointer(380,776,u-2.2,u>2.2);
  });
  inAt(u,.45,()=>{
    rect(1230,442,547,402,C.pale,15);label('THE NEXT STEP',1266,481);
    lines(['From employee shifts', 'to transportation demand.'],1266,535,32,C.ink,700,1.25);
    line(1266,643,1740,643,'#cbd5c2');
    ['Morning arrivals','Midday turnover','Evening pickups'].forEach((s,i)=>{dot(1277,694+i*52,5,'#678741');text(s,1300,681+i*52,26,C.muted);});
  });
  caption('The schedule becomes the starting point for a coordinated shuttle day.');
}
function demand(u,t){
  shell(t,'01 / MANAGERS');heading('SEE THE SHAPE OF THE DAY','Know when the shuttle is needed.','See arrivals, turnover, and pickups in practical service windows.');uiFrame('Demand');
  const data=[{x:140,k:'MORNING ARRIVALS',times:['8:30 AM','9:00 AM','9:30 AM'],n:[28,64,42]},
    {x:695,k:'MIDDAY TURNOVER',times:['12:00 PM','12:30 PM','1:00 PM'],n:[22,45,30]},
    {x:1250,k:'EVENING PICKUPS',times:['5:00 PM','5:30 PM','6:00 PM'],n:[46,82,58]}];
  data.forEach((d,i)=>inAt(u,.15+i*.18,()=>{
    rect(d.x,449,528,358,i===2?C.ink:C.bg,13,i===2?null:C.line);const fg=i===2?C.bg:C.ink;
    label(d.k,d.x+27,480,i===2?'#b9c6b8':C.muted);
    text(i===2?'Busiest at 5:30 PM':i===0?'The day begins.':'The shift changes.',d.x+27,527,30,fg,700);
    d.n.forEach((n,j)=>{const yy=590+j*65;text(d.times[j],d.x+27,yy+5,23,i===2?'#c9d3c3':C.muted);rect(d.x+172,yy+3,266,28,i===2?'#364534':C.pale,5);rect(d.x+172,yy+3,266*n/82*ease((u-.65-j*.15-i*.16)/1.1),28,i===2?C.green:'#97ba63',5);text(String(n),d.x+491,yy+4,23,fg,700,'right');});
  }));
  text('Example counts represent schedule-derived demand, not confirmed rides.',140,841,23,C.muted);
  caption('Use the busiest windows to guide coverage decisions.');
}
function coverage(u,t){
  shell(t,'01 / MANAGERS');heading('A PLAN YOUR TEAM CAN WORK WITH','Put coverage in place.','Review driver availability, fair assignments, and manager adjustments.');uiFrame('Coverage');
  const cards=[['MORNING','8:30 AM – 11:30 AM','Maya Rivera','Auto assigned'],['MIDDAY','12:00 PM – 3:00 PM','Jordan Lee','Auto assigned'],['EVENING','5:00 PM – 8:00 PM',u<5?'Alex Johnson':'Sam Taylor',u<5?'Auto assigned':'Manager override']];
  cards.forEach((d,i)=>inAt(u,.15+i*.23,()=>{
    const x=140+i*554;rect(x,449,528,303,C.bg,13,C.line);label(d[0],x+26,481);text(d[1],x+26,531,29,C.ink,700);line(x+26,588,x+502,588);
    text(d[2],x+26,618,32,C.ink,700);pill(d[3],x+26,678,i===2&&u>=5?223:187,i===2&&u>=5?C.amber:C.pale);
    if(i===2)focus(x,449,528,303,u-4.7);
  }));
  const note=u<5?'Coverage is planned across three service periods.':'Override reason: driver availability changed. The adjustment stays in the record.';
  rect(140,782,1636,64,u<5?C.pale:'#faf0d5',9);text(note,169,803,25,C.ink,500);
  caption('Automatic assignments support the plan. Managers stay in control.');
}
function drivers(u,t){
  shell(t,'02 / DRIVERS',true);
  inAt(u,.1,()=>{
    label('AT THE SHUTTLE',100,220,'#aabd9b');
    lines(['Tap.', 'Confirm.', 'Drive.'],93,283,111,C.bg,700,1.02);
    text('Use the NFC tag or scan the QR code.',100,655,30,'#c0cdb8');
    [['Start',100,119],['Pause',237,132],['Resume',387,157],['End',562,109]].forEach(([s,x,w],i)=>pill(s,x,735,w,u>4&&i===0?C.green:'#2d3c30',u>4&&i===0?C.ink:'#d1dcc9'));
    lines(['Actual driving activity is recorded', 'separately from the planned assignment.'],100,819,27,'#b9c6b8',400,1.3);
  });
  inAt(u,.45,()=>{
    const x=1136,y=189,w=581,h=718;
    rect(x-13,y-13,w+26,h+26,'#09110d',44,'#435042',2);rect(x,y,w,h,C.bg,33);
    rect(x+w/2-60,y+14,120,8,'#cbd3c4',4);miniBrand(x+34,y+49);
    text('DEMO DRIVER CHECKPOINT',x+34,y+117,18,C.muted,500,'left',mono);
    text(u<4.6?'Confirm start driving':'Driving recorded',x+34,y+160,38,C.ink,700);
    text('Signed in as Maya Rivera',x+34,y+218,26,C.muted);
    [['Checkpoint method','NFC tap'],['Scheduled driver','Maya Rivera'],['Scheduled time','8:30 AM – 11:30 AM']].forEach(([l,v],i)=>{label(l.toUpperCase(),x+34,y+284+i*88);text(v,x+34,y+313+i*88,29,C.ink,600);});
    btn(u<4.6?'Confirm start driving':'Service is running',x+34,y+565,w-68,u<4.6?C.green:C.pale);
    if(u>3.6&&u<5)pointer(x+378,y+591,u-4.2,u>4.2);
    if(u>4.6)dot(x+51,y+661,6,'#648d39');
    text(u<4.6?'Confirm at the shuttle.':'Actual start saved · 8:30 AM',x+34+(u>4.6?35:0),y+648,23,C.muted);
  });
  caption('Start, pause, resume, and end service at the shuttle.',true);
}
function riders(u,t){
  shell(t,'03 / RIDERS');const paused=u>=4.2&&u<7.1;
  inAt(u,.1,()=>{
    label('FOR THE PERSON WAITING FOR A RIDE',100,239);
    lines(['One link.', 'A clear answer.'],95,308,91,C.ink,700,1.06);
    lines(['Open your organization’s shuttle link', 'or scan its QR code.'],100,554,32,C.muted,400,1.4);
    pill('No rider account needed',100,682,341);
    lines(['Service status follows driver activity', 'and operational updates.'],100,767,28,C.muted,400,1.38);
  });
  inAt(u,.4,()=>{
    const x=1102,y=198,w=675,h=687;rect(x,y,w,h,C.ink,25);
    label('EXAMPLE ORGANIZATION',x+42,y+43,'#b9c6b8');text('PUBLIC RIDER PAGE',x+42,y+82,19,'#a6b79b',500,'left',mono);
    line(x+42,y+128,x+w-42,y+128,C.darkLine);
    const c=paused?C.amber:C.green;
    pill(paused?'Temporarily paused':'Service running',x+42,y+165,paused?279:226,paused?'#423724':'#2b3c24',c);
    dot(x+w-54,y+184,9+Math.sin(u*3)*2,c);
    lines(paused?['We’ll be', 'right back.']:['The shuttle', 'is running.'],x+39,y+241,66,c,700,1.05);
    text(paused?'Service is temporarily paused.':'Driver: Maya Rivera',x+42,y+416,29,'#d3dccd');
    line(x+42,y+478,x+w-42,y+478,C.darkLine);
    if(paused){lines(['Check this page for an update', 'before heading to the stop.'],x+42,y+519,29,'#c3cfbb',400,1.4);}
    else {text(u>9?'Thanks for confirming your ride.':'Did you ride the shuttle?',x+42,y+516,27,'#d3dccd',600);btn(u>9?'Ride confirmed':'Confirm my ride',x+42,y+573,w-84,u>9?C.pale:C.green);}
    if(u>8.3&&u<9.5)pointer(x+420,y+599,u-8.85,u>8.85);
  });
  caption(paused?'Temporary pauses are visible, so riders know service has changed.':'Riders can check the status and confirm a ride while service is active.');
}
function records(u,t){
  shell(t,'BACK TO THE MANAGER');heading('KEEP THE PLAN AND THE RECORD CONNECTED','Know what actually happened.','Compare scheduled coverage with actual driving activity.');uiFrame('Records');
  const metrics=[['SCHEDULED','9h 00m'],['ACTUAL DRIVING','8h 45m'],['RIDE CONFIRMATIONS','84']];
  metrics.forEach(([l,v],i)=>inAt(u,.1+i*.15,()=>{const x=140+i*554;rect(x,445,528,127,i===1?C.ink:C.pale,13);label(l,x+24,468,i===1?'#b9c6b8':C.muted);text(v,x+24,504,43,i===1?C.green:C.ink,700);}));
  label('DRIVER',165,609);label('SCHEDULED',794,609);label('ACTUAL',1180,609);label('REVIEW',1610,609);
  [['Maya Rivera','3h 00m','2h 45m','15m paused'],['Jordan Lee','3h 00m','3h 00m','Recorded'],['Sam Taylor','3h 00m','3h 00m','Override']].forEach((r,i)=>inAt(u,.55+i*.18,()=>{const y=648+i*67;line(140,y-9,1778,y-9);text(r[0],165,y+9,27,C.ink,700);text(r[1],794,y+9,26,C.muted);text(r[2],1180,y+9,26,C.ink,600);pill(r[3],1590,y+1,160,i===0?C.amber:C.pale);}));
  caption('Planned coverage, actual sessions, and ride confirmations stay distinct.');
}
function outro(u,t){
  shell(t,'PLAN. OPERATE. VERIFY.',true);
  inAt(u,.1,()=>{
    label('ONE OPERATION. EVERYONE IN THE LOOP.',100,222,'#b9c6b8');
    lines(['Less guesswork at the stop.', 'More clarity behind the scenes.'],94,294,70,C.bg,700,1.23);
    const a=[['01 / MANAGERS','Plan the day.','Schedules and driver coverage.'],['02 / DRIVERS','Record the drive.','Checkpoints and actual activity.'],['03 / RIDERS','Know the status.','One shared shuttle link.']];
    a.forEach((d,i)=>{const x=100+i*584;rect(x,540,552,193,'#233127',14,C.darkLine);label(d[0],x+29,570,C.green);text(d[1],x+29,617,34,C.bg,700);text(d[2],x+29,678,23,'#bac8b3');});
    rect(100,801,1719,86,C.green,11);text('istheshuttlerunning.com',131,827,36,C.ink,700);text('Request a demo',1667,831,29,C.ink,700,'right');arrow(1755,844,C.ink,13);
  });
  caption('A simple question deserves a clear answer.',true);
}

const scenes=[
  {start:0,end:8,fn:intro,label:'Opening'},
  {start:8,end:18,fn:schedules,label:'Schedule import'},
  {start:18,end:27,fn:demand,label:'Transportation demand'},
  {start:27,end:37,fn:coverage,label:'Driver coverage'},
  {start:37,end:48,fn:drivers,label:'Driver checkpoints'},
  {start:48,end:60,fn:riders,label:'Rider status'},
  {start:60,end:70,fn:records,label:'Driving records'},
  {start:70,end:80,fn:outro,label:'Closing'}
];
function render(t){
  const s=scenes.find(s=>t>=s.start&&t<s.end)||scenes.at(-1);ctx.globalAlpha=1;ctx.setTransform(1,0,0,1,0,0);s.fn(t-s.start,t);
  if(s.start>0&&t-s.start<.35){const p=scenes[scenes.indexOf(s)-1];const snap=createCanvas(W,H);snap.getContext('2d').drawImage(canvas,0,0);p.fn(p.end-p.start-.01,s.start-.01);ctx.globalAlpha=ease((t-s.start)/.35);ctx.drawImage(snap,0,0);ctx.globalAlpha=1;}
  // Always distinguish the staged examples from a recording of production data.
  text('Illustrated walkthrough · Sample data',960,1029,18,t>=37&&t<48||t>=70?'#b9c6b8':C.muted,400,'center');
}

for(const t of [4,14,23,30,34,41,44,51,54,58,65,75]){
  render(t);fs.writeFileSync(path.join(PREVIEW,`frame-${String(t).padStart(2,'0')}.png`),canvas.toBuffer('image/png'));
}
intro(5,4,true);text('Illustrated walkthrough · Sample data',960,1029,18,C.muted,400,'center');
const posterBytes=canvas.toBuffer('image/png');
fs.writeFileSync(path.join(PREVIEW,'istheshuttlerunning-demo-poster.png'),posterBytes);
fs.writeFileSync(path.join(ROOT,'src/main/resources/static/images/istheshuttlerunning-demo-poster.png'),posterBytes);
if(process.argv.includes('--preview')){console.log('Preview frames ready:',PREVIEW);process.exit(0);}

const output=path.join(OUT,'istheshuttlerunning-manager-driver-rider-demo.mp4');
const ffmpeg=spawn('ffmpeg',['-hide_banner','-loglevel','warning','-y','-f','rawvideo','-pix_fmt','rgba','-s',`${W}x${H}`,'-r',String(FPS),'-i','pipe:0','-an','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart','-metadata','title=Is the Shuttle Running? — One shuttle day. Three perspectives.','-metadata','comment=Illustrated product walkthrough. Sample data. Silent, with on-screen explanations.',output],{stdio:['pipe','inherit','inherit']});
const done=once(ffmpeg,'close');
ffmpeg.stdin.on('error',err=>{if(err.code!=='EPIPE')throw err;});
for(let f=0;f<FPS*DURATION;f++){
  render(f/FPS);
  const bytes=ctx.getImageData(0,0,W,H).data;
  if(!ffmpeg.stdin.write(bytes))await once(ffmpeg.stdin,'drain');
  if(f%(FPS*5)===0)console.log(`Rendered ${f/FPS}s / ${DURATION}s`);
}
ffmpeg.stdin.end();
const [code]=await done;if(code!==0)throw new Error(`ffmpeg exited ${code}`);
console.log('Video ready:',output);
