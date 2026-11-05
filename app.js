const $=id=>document.getElementById(id);
const statusBox=$("status"),resultBox=$("result");
const STORAGE_KEY="cardmaster.records.v2",SETTINGS_KEY="cardmaster.settings.v1";
let reader=null,controller=null,lastRead="";
function status(message){statusBox.textContent=message}
function show(message){resultBox.textContent=message}
function nfcSupported(){return "NDEFReader" in window}
function setDiagnostic(id,text,kind){const el=$(id);el.textContent=text;el.className=kind||""}
function currentSettings(){return {vibrate:$("vibrateSetting").checked,confirmWrite:$("confirmWriteSetting").checked,glassIntensity:Number($("glassSetting").value||72)}}
function applyCardGlass(raw){
 const value=Math.max(0,Math.min(100,Number.isFinite(Number(raw))?Number(raw):72)),t=value/100,root=document.documentElement;
 root.style.setProperty("--glass-alpha",(0.94-t*.42).toFixed(3));
 root.style.setProperty("--glass-blur",(2+t*23).toFixed(1)+"px");
 root.style.setProperty("--glass-saturation",(108+t*65).toFixed(0)+"%");
 root.style.setProperty("--glass-sheen",(0.02+t*.18).toFixed(3));
 root.dataset.glass=value===0?"off":"on";
 const label=value<25?"زجاج خفيف جدًا":value<55?"زجاج هادئ":value<82?"زجاج متوازن":"زجاج عميق";
 $("glassSettingValue").textContent=value+"% · "+label;
}
function saveSettings(){try{const settings=currentSettings();localStorage.setItem(SETTINGS_KEY,JSON.stringify(settings));applyCardGlass(settings.glassIntensity)}catch{}}
function loadSettings(){try{const s=JSON.parse(localStorage.getItem(SETTINGS_KEY)||"{}");$("vibrateSetting").checked=!!s.vibrate;$("confirmWriteSetting").checked=s.confirmWrite!==false;$("glassSetting").value=String(Math.max(0,Math.min(100,Number.isFinite(Number(s.glassIntensity))?Number(s.glassIntensity):72)));}catch{$("glassSetting").value="72"}applyCardGlass($("glassSetting").value);}
async function diagnose(){
  const secure=window.isSecureContext===true;
  const android=/Android/i.test(navigator.userAgent);
  const chromeAndroid=android&&/Chrome\//i.test(navigator.userAgent)&&!/EdgA|OPR|SamsungBrowser/i.test(navigator.userAgent);
  setDiagnostic("browserStatus",chromeAndroid?"Chrome على Android":android?"متصفح Android آخر":/Windows/i.test(navigator.userAgent)?"Windows / متصفح كمبيوتر":"متصفح غير معروف",chromeAndroid?"ok":"warn");
  setDiagnostic("secureStatus",secure?"اتصال آمن HTTPS":"الاتصال غير آمن",""+(secure?"ok":"bad"));
  setDiagnostic("apiStatus",nfcSupported()?"Web NFC متاح":"Web NFC غير متاح",nfcSupported()?"ok":"bad");
  let appLabel="الموقع يعمل";
  if("serviceWorker" in navigator){try{const regs=await navigator.serviceWorker.getRegistrations();appLabel=regs.length?"التثبيت/العمل دون اتصال مفعّل":"الموقع يعمل؛ التخزين المؤقت غير جاهز"}catch{}}
  setDiagnostic("appStatus",appLabel,"ok");
  let message;
  if(!secure)message="سبب التعطّل: المتصفح لا يسمح بقراءة NFC إلا من HTTPS. افتح رابط الموقع المنشور.";
  else if(!nfcSupported())message=android?"هذا المتصفح لا يوفّر Web NFC. افتح الرابط في Chrome الرسمي على Android، وتأكد من تفعيل NFC من إعدادات الهاتف.":"أنت على كمبيوتر أو متصفح لا يدعم Web NFC. صفحة الويب لا تستطيع قراءة قارئ الكروت في Windows مباشرة.";
  else message="المتصفح يوفّر Web NFC. فعّل NFC من إعدادات الهاتف، واسمح للموقع باستخدامه، ثم قرّب وسم NDEF عاديًا من ظهر الهاتف.";
  $("diagnosis").textContent=message;
  return {secure,android,chromeAndroid,api:nfcSupported()};
}
function showFriendlyError(error){
  const name=error&&error.name||"";
  if(name==="NotAllowedError")return "تم رفض الإذن. اسمح للموقع باستخدام NFC من نافذة المتصفح وإعدادات الموقع.";
  if(name==="NotSupportedError")return "الهاتف أو الوسم لا يدعم طريقة NDEF المطلوبة، أو أن NFC غير متاح.";
  if(name==="NotReadableError")return "تعذرت قراءة وحدة NFC. تأكد أن NFC مفعّل، وأغلق أي تطبيق آخر يستخدمه.";
  if(name==="SecurityError")return "يلزم HTTPS مع فتح الموقع في تبويب ظاهر، وليس داخل إطار مضمّن.";
  if(name==="InvalidStateError")return "القارئ بدأ بالفعل. أوقف القراءة الحالية ثم حاول مرة أخرى.";
  return (error&&error.message)||"حدث خطأ غير معروف أثناء الاتصال بوحدة NFC.";
}
function decodeRecord(record){
  if(!record.data)return "سجل فارغ ("+record.recordType+")";
  const bytes=new Uint8Array(record.data.buffer,record.data.byteOffset,record.data.byteLength);
  const type=record.recordType||"unknown";
  if(type==="mime"&&record.mediaType&&!(/^(text\/|application\/(json|xml))/i.test(record.mediaType)))return "محتوى ثنائي ("+record.mediaType+"): "+Array.from(bytes.slice(0,32)).map(b=>b.toString(16).padStart(2,"0")).join(" ") +(bytes.length>32?" …":"");
  try{return type+" : "+new TextDecoder(record.encoding||"utf-8",{fatal:false}).decode(bytes).slice(0,1000)}
  catch{return "سجل غير نصي ("+type+"), الحجم "+bytes.length+" بايت"}
}
function safeVibrate(){if($("vibrateSetting").checked&&navigator.vibrate)navigator.vibrate(80)}
function saveRecord(name,type,notes){
  let rows=loadRecords();
  if(rows.length>=200){status("وصلت إلى الحد الأقصى 200 سجل. صدّر نسخة احتياطية ثم احذف سجلات قديمة.");return false}
  rows.unshift({id:crypto.randomUUID?crypto.randomUUID():String(Date.now()),name:name.slice(0,60),type:type.slice(0,40),notes:notes.slice(0,300),createdAt:new Date().toISOString()});
  records=rows;persistRecords();return true;
}
$("diagnoseBtn").addEventListener("click",()=>diagnose());
$("readBtn").addEventListener("click",async()=>{
  const diag=await diagnose();
  if(!diag.secure||!diag.api){status($("diagnosis").textContent);return}
  try{
    controller=new AbortController();reader=new NDEFReader();
    await reader.scan({signal:controller.signal});
    $("stopBtn").disabled=false;status("تم بدء القراءة. أبقِ الصفحة مفتوحة وقرّب الوسم من ظهر الهاتف.");
    reader.onreading=event=>{
      const lines=["تمت قراءة وسم NDEF بنجاح","عدد السجلات: "+event.message.records.length,"—"];
      for(const record of event.message.records)lines.push(decodeRecord(record));
      lastRead=lines.join("\n");show(lastRead);$("saveReadBtn").disabled=false;status("اكتملت القراءة. لم نحفظ محتوى الوسم تلقائيًا.");safeVibrate();
    };
    reader.onreadingerror=()=>status("اقترب الوسم لكن تعذرت قراءة رسالته. قد يكون غير مهيأ بصيغة NDEF أو محميًا أو غير متوافق.");
  }catch(error){status("تعذرت بداية القراءة: "+showFriendlyError(error))}
});
$("writeBtn").addEventListener("click",async()=>{
  const diag=await diagnose();
  if(!diag.secure||!diag.api){status($("diagnosis").textContent);return}
  const value=$("payload").value.trim();if(!value){status("اكتب نصًا أو رابطًا عاديًا أولًا.");return}
  if($("confirmWriteSetting").checked&&!confirm("اكتب فقط على وسم NDEF عادي تملكه أو لديك تصريح بإدارته. هل تريد المتابعة؟"))return;
  try{await new NDEFReader().write({records:[{recordType:"text",data:value}]});status("تمت الكتابة. أعد قراءة الوسم للتأكد.");show("تمت كتابة نص NDEF على الوسم.");safeVibrate()}
  catch(error){status("تعذرت الكتابة: "+showFriendlyError(error))}
});
$("stopBtn").addEventListener("click",()=>{if(controller)controller.abort();reader=null;controller=null;$("stopBtn").disabled=true;status("تم إيقاف القارئ.")});
$("clearBtn").addEventListener("click",()=>{lastRead="";show("لم تتم قراءة أي وسم بعد.");$("saveReadBtn").disabled=true});
$("saveReadBtn").addEventListener("click",()=>{
  if(!lastRead)return;
  if(saveRecord("قراءة NFC "+new Date().toLocaleString("ar-EG"),"قراءة NDEF",lastRead))status("تم حفظ نتيجة القراءة في السجل المحلي.");
});
function loadRecords(){try{const data=JSON.parse(localStorage.getItem(STORAGE_KEY)||"[]");return Array.isArray(data)?data.filter(x=>x&&typeof x.name==="string"&&typeof x.type==="string"):[]}catch{return []}}
let records=loadRecords();
function persistRecords(){try{localStorage.setItem(STORAGE_KEY,JSON.stringify(records));renderRecords()}catch{status("تعذّر الحفظ في المتصفح. صدّر نسخة احتياطية أو وفر مساحة.")}}
function renderRecords(){
  $("recordCount").textContent=String(records.length);const list=$("recordList");list.replaceChildren();
  if(!records.length){const p=document.createElement("p");p.className="empty";p.textContent="لا توجد سجلات محفوظة بعد.";list.append(p);return}
  records.forEach(item=>{
    const card=document.createElement("article");card.className="record";const details=document.createElement("div");
    const h=document.createElement("h4");h.textContent=item.name;
    const t=document.createElement("p");t.className="tag";t.textContent=item.type;details.append(h,t);
    if(item.notes){const n=document.createElement("p");n.textContent=item.notes;details.append(n)}
    const del=document.createElement("button");del.type="button";del.className="delete";del.textContent="حذف";
    del.addEventListener("click",()=>{if(confirm("حذف هذا السجل؟")){records=records.filter(x=>x.id!==item.id);persistRecords()}});
    card.append(details,del);list.append(card);
  });
}
$("recordForm").addEventListener("submit",event=>{
  event.preventDefault();const name=$("recordName").value.trim();if(!name){status("اكتب اسم السجل.");return}
  if(saveRecord(name,$("recordType").value,$("recordNotes").value.trim())){event.target.reset();status("تم حفظ السجل على هذا الجهاز.")}
});
$("exportBtn").addEventListener("click",()=>{
  const blob=new Blob([JSON.stringify({app:"CardMaster",version:2,records},null,2)],{type:"application/json"});
  const url=URL.createObjectURL(blob),link=document.createElement("a");link.href=url;link.download="cardmaster-backup.json";link.click();URL.revokeObjectURL(url);status("تم تجهيز النسخة الاحتياطية.");
});
const importFile=$("importFile");$("importBtn").addEventListener("click",()=>importFile.click());
importFile.addEventListener("change",async()=>{
  const file=importFile.files&&importFile.files[0];if(!file)return;
  try{
    if(file.size>1024*1024)throw new Error("حجم الملف أكبر من 1 ميجابايت.");
    const parsed=JSON.parse(await file.text()),incoming=Array.isArray(parsed)?parsed:parsed.records;
    if(!Array.isArray(incoming))throw new Error("تنسيق ملف النسخة غير صحيح.");
    const cleaned=incoming.slice(0,200).filter(x=>x&&typeof x.name==="string"&&typeof x.type==="string")
      .map(x=>({id:String(x.id||Date.now()+"-"+Math.random()),name:x.name.slice(0,60),type:x.type.slice(0,40),notes:typeof x.notes==="string"?x.notes.slice(0,300):"",createdAt:x.createdAt||new Date().toISOString()}));
    if(confirm("سيتم استبدال السجلات الحالية بسجلات الملف. متابعة؟")){records=cleaned;persistRecords();status("تم استيراد النسخة الاحتياطية.")}
  }catch(error){status("تعذر الاستيراد: "+error.message)}finally{importFile.value=""}
});
$("clearRecordsBtn").addEventListener("click",()=>{
  if(!confirm("تحذير: سيتم حذف كل السجلات والإعدادات المحلية من هذا المتصفح. هل أنت متأكد؟"))return;
  records=[];persistRecords();status("تم حذف السجلات المحلية.");
});
$("vibrateSetting").addEventListener("change",saveSettings);
$("confirmWriteSetting").addEventListener("change",saveSettings);
$("glassSetting").addEventListener("input",saveSettings);
loadSettings();renderRecords();diagnose();
if("serviceWorker" in navigator&&location.protocol.startsWith("http"))navigator.serviceWorker.register("./sw.js").catch(()=>{});
