# CardMaster Professional for Windows

تطبيق Windows مستقل يستهدف Windows 10/11 x64 باستخدام WPF وWindows PC/SC.

## الوظائف
- اكتشاف قارئات البطاقات التي يعرضها Windows عبر PC/SC.
- اختيار القارئ وتحديث القائمة.
- فحص وجود البطاقة وعرض ATR وبروتوكول T=0 أو T=1 وطول ATR ووقت الفحص.
- سجل لآخر 30 فحصًا داخل الجلسة.
- نسخ تقرير التشخيص العام أو تصديره كملف TXT بترميز UTF-8.
- واجهة عربية RTL بتصميم CardMaster الداكن البنفسجي.

## التوافق
- قارئ Smart Card المدمج في بعض أجهزة HP قد يكون قارئًا تلامسيًا ولا يدعم NFC اللاسلكي.
- لوسوم NFC اللاسلكية، يحتاج الكمبيوتر قارئ USB NFC متوافقًا مع PC/SC وتعريفًا سليمًا.
- لا يقرأ الإصدار الحالي سجلات NDEF عبر PC/SC ولا يرسل أوامر إلى البطاقة؛ يعرض فقط بيانات الاتصال العامة التي توفرها واجهة Windows.
- لا يقرأ بيانات الدفع أو الرصيد أو بيانات الهوية أو الدخول أو المفاتيح ولا يتجاوز حماية البطاقة.

## البناء محليًا
يتطلب .NET 8 SDK على Windows:

```powershell
dotnet publish .\CardMaster.Desktop\CardMaster.Desktop.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true
```

الناتج في:
`CardMaster.Desktop\bin\Release\net8.0-windows\win-x64\publish\CardMaster.exe`

## الخصوصية
سجل الفحوصات موجود في ذاكرة التطبيق خلال الجلسة فقط. تصدير التقرير إجراء يدوي، ولا يحتوي على UID أو محتوى البطاقة.
