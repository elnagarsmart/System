const statusBox=document.querySelector("#status");
const resultBox=document.querySelector("#result");
const readBtn=document.querySelector("#readBtn");
const writeBtn=document.querySelector("#writeBtn");
const stopBtn=document.querySelector("#stopBtn");
let reader=null;
let controller=null;
function status(message){statusBox.textContent=message}
function show(message){resultBox.textContent=message}
function supported(){return "NDEFReader" in window}
function describeRecord(record){
  try{
    if(record.recordType==="text"){
      const decoder=new TextDecoder(record.encoding||"utf-8");
      return "نص: "+decoder.decode(record.data);
    }
    if(record.recordType==="url"){
      return "رابط: "+new TextDecoder().decode(record.data);
    }
    if(record.recordType==="mime"){
      return "نوع MIME: "+record.mediaType+" (تم تجاهل المحتوى الثنائي حفاظًا على الخصوصية)";
    }
    return "نوع السجل: "+record.recordType;
  }catch{return "تعذر عرض هذا السجل"}
}
readBtn.addEventListener("click",async()=>{
  if(!supported()){status("هذا المتصفح لا يدعم Web NFC. جرّب هاتف Android ومتصفح Chrome عبر HTTPS.");return}
  try{
    controller=new AbortController();reader=new NDEFReader();
    await reader.scan({signal:controller.signal});
    stopBtn.disabled=false;status("القارئ نشط. قرّب وسم NFC عاديًا من الهاتف.");
    reader.onreading=event=>{
      const lines=["تمت القراءة بنجاح","معرّف الوسم: "+(event.serialNumber||"غير متاح"),"السجلات:"];
      for(const record of event.message.records)lines.push("• "+describeRecord(record));
      show(lines.join("\n"));status("اكتملت القراءة.");
    };
    reader.onreadingerror=()=>status("تعذرت قراءة الوسم. تأكد أنه متوافق وقريب من الهاتف.");
  }catch(error){status("لم تبدأ القراءة: "+(error.message||error))}
});
writeBtn.addEventListener("click",async()=>{
  if(!supported()){status("الكتابة عبر المتصفح غير مدعومة هنا. استخدم جهازًا ومتصفحًا متوافقين.");return}
  const value=document.querySelector("#payload").value.trim();
  if(!value){status("اكتب نصًا عاديًا أو رابطًا أولًا.");return}
  if(!confirm("تأكد أن هذا وسم NFC عادي تملكه أو لديك تصريح بإدارته. هل تريد كتابة المحتوى عليه؟"))return;
  try{
    const writer=new NDEFReader();
    await writer.write({records:[{recordType:"text",data:value}]});
    show("تم إرسال طلب الكتابة بنجاح. افحص الوسم بإعادة قراءته للتأكد.");
    status("اكتملت عملية الكتابة.");
  }catch(error){status("تعذرت الكتابة: "+(error.message||error))}
});
stopBtn.addEventListener("click",()=>{if(controller)controller.abort();reader=null;controller=null;stopBtn.disabled=true;status("تم إيقاف القارئ.")});
document.querySelector("#clearBtn").addEventListener("click",()=>show("لم تتم قراءة أي وسم بعد."));
if("serviceWorker" in navigator && location.protocol.startsWith("http"))navigator.serviceWorker.register("./sw.js").catch(()=>{});
