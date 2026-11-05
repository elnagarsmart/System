# CardMaster for Windows

واجهة Windows مستقلة لمشروع CardMaster، تستهدف Windows 10/11 على أجهزة x64.

## الوظائف الحالية
- تعداد قارئات البطاقات التي يعرضها Windows عبر PC/SC.
- الاتصال المشترك بالقارئ المحدد للتحقق من وجود بطاقة.
- عرض ATR وبروتوكول الاتصال T=0 أو T=1 عند إتاحتهما.
- واجهة عربية RTL وسجل تشخيص مباشر دون حفظ رقم البطاقة أو بياناتها.

## حدود التوافق
- قارئ Smart Card المدمج في بعض أجهزة HP هو قارئ بطاقات تلامسية، وليس بالضرورة قارئ NFC.
- لاكتشاف بطاقات NFC لاسلكيًا من الكمبيوتر، يلزم قارئ USB NFC متوافق مع PC/SC وتعريفه.
- هذا الإصدار لا يرسل APDU ولا يقرأ قطاعات محمية ولا يتعامل مع بيانات الدفع أو الرصيد أو مفاتيح البطاقات.
- ظهور قارئ في Windows لا يضمن توافقه مع كل أنواع البطاقات.

## البناء
يتطلب .NET 8 SDK على Windows:

```powershell
dotnet publish .\CardMaster.Desktop\CardMaster.Desktop.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true
```

الناتج في:
`CardMaster.Desktop\bin\Release\net8.0-windows\win-x64\publish\CardMaster.exe`
