const statusBox=document.querySelector("#status");
const resultBox=document.querySelector("#result");
const readBtn=document.querySelector("#readBtn");
const writeBtn=document.querySelector("#writeBtn");
const stopBtn=document.querySelector("#stopBtn");
const STORAGE_KEY="cardmaster.records.v1";
let reader=null;
let controller=null;

function status(message){statusBox.textContent=message}
function show(message){resultBox.textContent=message}
function supported(){return "NDEFReader" in window}

function describeRecord(record){
  try{
    if(record.recordType==="text"){
      return "نص: "+new TextDecoder(record.encoding||"utf-8").decode(record.data);
    }
    if(record.recordType==="url"){
      return "رابط: "+new TextDecoder().decode(record.data);
    }
    if(record.recordType==="mime"){
      return "سجل MIME: "+record.mediaType+" (لم يتم عرض محتوى ثنائي)";
    }
    return "نوع السجل: "+record.recordType;
  }catch{return "تعذر عرض هذا السجل"}
}

readBtn.addEventListener("click",async()=>{
  if(!window.isSecureContext){status("يلزم فتح الموقع عبر HTTPS حتى يسمح المتصفح باستخدام NFC.");return}
  if(!supported()){status("المتصفح لا يدعم Web NFC. افتح الموقع من Chrome على Android.");return}
  try{
    controller=new AbortController();
    reader=new NDEFReader();
    await reader.scan({signal:controller.signal});
    stopBtn.disabled=false;
    status("القارئ نشط. قرّب وسم NFC عاديًا ومتوافقًا مع NDEF من الهاتف.");
    reader.onreading=event=>{
      const lines=["تمت القراءة بنجاح","معرّف الوسم: لم يتم عرضه حفاظًا على الخصوصية","السجلات:"];
      for(const record of event.message.records)lines.push("• "+describeRecord(record));
      show(lines.join("\n"));
      status("اكتملت القراءة. لم يتم حفظ محتوى الوسم تلقائيًا.");
    };
    reader.onreadingerror=()=>status("تعذرت قراءة الوسم. تأكد أنه متوافق مع NDEF وقريب من الهاتف.");
  }catch(error){status("لم تبدأ القراءة: "+(error.message||error))}
});

writeBtn.addEventListener("click",async()=>{
  if(!window.isSecureContext){status("يلزم فتح الموقع عبر HTTPS حتى يسمح المتصفح باستخدام NFC.");return}
  if(!supported()){status("الكتابة عبر المتصفح غير مدعومة هنا. استخدم Chrome على Android.");return}
  const value=document.querySelector("#payload").value.trim();
  if(!value){status("اكتب نصًا عاديًا أو رابطًا أولًا.");return}
  if(!confirm("تأكد أن هذا وسم NDEF عادي تملكه أو لديك تصريح بإدارته. ستُكتب بيانات جديدة على الوسم. هل تريد المتابعة؟"))return;
  try{
    const writer=new NDEFReader();
    await writer.write({records:[{recordType:"text",data:value}]});
    show("تم إرسال طلب الكتابة بنجاح. أعد قراءة الوسم للتأكد من النتيجة.");
    status("اكتملت عملية الكتابة.");
  }catch(error){status("تعذرت الكتابة: "+(error.message||error))}
});

stopBtn.addEventListener("click",()=>{
  if(controller)controller.abort();
  reader=null;controller=null;stopBtn.disabled=true;status("تم إيقاف القارئ.");
});
document.querySelector("#clearBtn").addEventListener("click",()=>show("لم تتم قراءة أي وسم بعد."));

function loadRecords(){
  try{
    const data=JSON.parse(localStorage.getItem(STORAGE_KEY)||"[]");
    return Array.isArray(data)?data.filter(x=>x&&typeof x.name==="string"&&typeof x.type==="string"):[];
  }catch{return []}
}
let records=loadRecords();
function saveRecords(){
  try{localStorage.setItem(STORAGE_KEY,JSON.stringify(records));renderRecords()}
  catch{status("مساحة التخزين ممتلئة أو غير متاحة. صدّر نسخة ثم احذف بعض السجلات.");}
}
function renderRecords(){
  const list=document.querySelector("#recordList");
  document.querySelector("#recordCount").textContent=String(records.length);
  list.replaceChildren();
  if(records.length===0){
    const empty=document.createElement("p");empty.className="empty";empty.textContent="لا توجد سجلات محفوظة بعد.";list.append(empty);return;
  }
  records.forEach(item=>{
    const card=document.createElement("article");card.className="record";
    const details=document.createElement("div");
    const heading=document.createElement("h4");heading.textContent=item.name;
    const category=document.createElement("p");category.className="tag";category.textContent=item.type;
    details.append(heading,category);
    if(item.notes){const note=document.createElement("p");note.textContent=item.notes;details.append(note);}
    const del=document.createElement("button");del.className="delete";del.type="button";del.textContent="حذف";
    del.addEventListener("click",()=>{
      if(!confirm("حذف هذا السجل من هذا الجهاز؟"))return;
      records=records.filter(x=>x.id!==item.id);saveRecords();
    });
    card.append(details,del);list.append(card);
  });
}
document.querySelector("#recordForm").addEventListener("submit",event=>{
  event.preventDefault();
  const name=document.querySelector("#recordName").value.trim();
  const type=document.querySelector("#recordType").value;
  const notes=document.querySelector("#recordNotes").value.trim();
  if(!name){status("اكتب اسمًا للسجل أولًا.");return}
  if(records.length>=200){status("وصلت للحد المحلي البالغ 200 سجل. صدّر نسخة وامسح سجلات قديمة.");return}
  const id=(crypto.randomUUID?crypto.randomUUID():String(Date.now())+"-"+Math.random().toString(16).slice(2));
  records.unshift({id,name,type,notes,createdAt:new Date().toISOString()});
  saveRecords();event.target.reset();status("تم حفظ السجل محليًا على هذا الجهاز.");
});
document.querySelector("#exportBtn").addEventListener("click",()=>{
  const blob=new Blob([JSON.stringify({app:"CardMaster",version:1,records},null,2)],{type:"application/json"});
  const url=URL.createObjectURL(blob);const link=document.createElement("a");
  link.href=url;link.download="cardmaster-backup.json";link.click();URL.revokeObjectURL(url);
  status("تم تجهيز نسخة احتياطية للتنزيل.");
});
const importFile=document.querySelector("#importFile");
document.querySelector("#importBtn").addEventListener("click",()=>importFile.click());
importFile.addEventListener("change",async()=>{
  const file=importFile.files&&importFile.files[0];if(!file)return;
  try{
    if(file.size>1024*1024)throw new Error("حجم الملف أكبر من 1 ميجابايت.");
    const parsed=JSON.parse(await file.text());
    const incoming=Array.isArray(parsed)?parsed:parsed.records;
    if(!Array.isArray(incoming))throw new Error("ملف النسخة الاحتياطية غير صحيح.");
    const cleaned=incoming.slice(0,200).filter(x=>x&&typeof x.name==="string"&&typeof x.type==="string")
      .map(x=>({id:String(x.id||Date.now()+"-"+Math.random()),name:x.name.slice(0,60),type:x.type.slice(0,40),notes:typeof x.notes==="string"?x.notes.slice(0,300):"",createdAt:x.createdAt||new Date().toISOString()}));
    if(!confirm("سيتم استبدال السجلات الحالية بالسجلات الموجودة في الملف. هل تريد المتابعة؟"))return;
    records=cleaned;saveRecords();status("تم استيراد النسخة الاحتياطية.");
  }catch(error){status("تعذر استيراد الملف: "+error.message)}
  finally{importFile.value=""}
});
renderRecords();
if("serviceWorker" in navigator && location.protocol.startsWith("http")){
  navigator.serviceWorker.register("./sw.js").catch(()=>{});
}
