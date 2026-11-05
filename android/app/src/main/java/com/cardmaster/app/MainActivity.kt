package com.cardmaster.app

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    private var adapter: NfcAdapter? = null
    private var status by mutableStateOf("اضغط بدء التشخيص ثم قرّب الكارت من ظهر الهاتف.")
    private var technologies by mutableStateOf(listOf<String>())
    private var ndefSummary by mutableStateOf("لم يتم فحص أي كارت بعد.")
    private var scanning by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = NfcAdapter.getDefaultAdapter(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("CardMaster", style = MaterialTheme.typography.headlineMedium)
                        Text("تشخيص NFC على Android", style = MaterialTheme.typography.titleMedium)
                        Text("هذا الإصدار يعرض تقنيات الشريحة ومعلومات NDEF العامة فقط. لا يقرأ أو يغيّر بيانات الشحن أو الرصيد أو مفاتيح الحماية.")
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("الحالة", style = MaterialTheme.typography.titleMedium)
                                Text(status)
                                Text("تقنيات الشريحة", style = MaterialTheme.typography.titleMedium)
                                if (technologies.isEmpty()) Text("لا توجد نتيجة بعد")
                                technologies.forEach { Text("• $it") }
                                Text("معلومات NDEF", style = MaterialTheme.typography.titleMedium)
                                Text(ndefSummary)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { startScan() }, enabled = !scanning && adapter != null) {
                                Text("بدء التشخيص")
                            }
                            Button(onClick = { stopScan() }, enabled = scanning) {
                                Text("إيقاف")
                            }
                        }
                        if (adapter == null) Text("هذا الهاتف لا يعلن عن وجود NFC عبر Android.")
                        else if (adapter?.isEnabled != true) Text("فعّل NFC من إعدادات الهاتف.")
                        Spacer(Modifier.height(4.dp))
                        Text("تنبيه: ظهور IsoDep أو MIFARE لا يعني أن بيانات البطاقة متاحة للقراءة. البطاقات المحمية أو بطاقات العدادات قد تتطلب أنظمة مصرحًا بها.")
                    }
                }
            }
        }
    }

    private fun startScan() {
        val nfc = adapter ?: return
        if (!nfc.isEnabled) {
            status = "خاصية NFC غير مفعّلة."
            return
        }
        technologies = emptyList()
        ndefSummary = "قرّب الكارت من ظهر الهاتف."
        status = "في انتظار الكارت…"
        scanning = true
        nfc.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null
        )
    }

    private fun stopScan() {
        adapter?.disableReaderMode(this)
        scanning = false
        status = "تم إيقاف التشخيص."
    }

    override fun onTagDiscovered(tag: Tag) {
        val techs = tag.techList.map { it.substringAfterLast('.') }.distinct().sorted()
        val ndef = Ndef.get(tag)
        val summary = if (ndef == null) {
            "لم تُعلن الشريحة عن NDEF. هذا لا يعني أن الكارت تالف."
        } else {
            try {
                ndef.connect()
                val writable = if (ndef.isWritable) "نعم" else "لا"
                val maxSize = ndef.maxSize
                ndef.close()
                "NDEF متاح. السعة القصوى المعلنة: $maxSize بايت. قابلية الكتابة المعلنة: $writable. لم تتم قراءة محتوى الرسالة."
            } catch (_: Exception) {
                try { ndef.close() } catch (_: Exception) {}
                "تم اكتشاف واجهة NDEF لكن تعذّر الاستعلام عن خصائصها."
            }
        }
        runOnUiThread {
            technologies = techs
            ndefSummary = summary
            status = "تم اكتشاف شريحة NFC بنجاح."
            scanning = false
            adapter?.disableReaderMode(this)
        }
    }

    override fun onPause() {
        stopScan()
        super.onPause()
    }
}
