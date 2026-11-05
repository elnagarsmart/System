package com.cardmaster.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Night = Color(0xFF080A14)
private val Panel = Color(0xFF111629)
private val PanelRaised = Color(0xFF171D35)
private val Line = Color(0xFF293052)
private val Purple = Color(0xFF8757F5)
private val Violet = Color(0xFFB18BFF)
private val Cyan = Color(0xFF43C7F4)
private val TextMain = Color(0xFFF5F3FF)
private val TextMuted = Color(0xFFAAA9C2)
private val Green = Color(0xFF69D9A7)
private val Amber = Color(0xFFFFC66D)

private enum class AppTab { Home, History, Settings }

private data class ScanEntry(
    val time: String,
    val technologies: List<String>,
    val ndefAvailable: Boolean,
    val ndefSummary: String
)

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    private var adapter: NfcAdapter? = null
    private var nfcEnabledState by mutableStateOf(false)
    private var status by mutableStateOf("ابدأ الفحص ثم قرّب البطاقة من ظهر الهاتف.")
    private var technologies by mutableStateOf(emptyList<String>())
    private var ndefSummary by mutableStateOf("لم يتم فحص أي بطاقة بعد.")
    private var scanning by mutableStateOf(false)
    private var history by mutableStateOf(emptyList<ScanEntry>())
    private var selectedTab by mutableStateOf(AppTab.Home)
    private var vibrationEnabled by mutableStateOf(true)
    private var lastScanTime by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = NfcAdapter.getDefaultAdapter(this)
        nfcEnabledState = adapter?.isEnabled == true
        history = loadHistory()
        vibrationEnabled = getSharedPreferences("cardmaster", MODE_PRIVATE)
            .getBoolean("vibration", true)

        window.statusBarColor = android.graphics.Color.rgb(8, 10, 20)
        window.navigationBarColor = android.graphics.Color.rgb(8, 10, 20)

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme {
                    CardMasterScreen(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        nfcAvailable = adapter != null,
                        nfcEnabled = nfcEnabledState,
                        status = status,
                        scanning = scanning,
                        technologies = technologies,
                        ndefSummary = ndefSummary,
                        lastScanTime = lastScanTime,
                        history = history,
                        vibrationEnabled = vibrationEnabled,
                        deviceName = (Build.MANUFACTURER + " " + Build.MODEL).trim(),
                        androidVersion = Build.VERSION.RELEASE ?: "غير معروف",
                        onStartScan = { startScan() },
                        onStopScan = { stopScan() },
                        onToggleVibration = { enabled ->
                            vibrationEnabled = enabled
                            getSharedPreferences("cardmaster", MODE_PRIVATE)
                                .edit().putBoolean("vibration", enabled).apply()
                        },
                        onClearHistory = { clearHistory() },
                        onCopyReport = { copyReport() },
                        onShareReport = { shareReport() }
                    )
                }
            }
        }
    }

    private fun startScan() {
        val nfc = adapter
        if (nfc == null) {
            status = "الهاتف لا يوفّر NFC عبر نظام Android."
            return
        }
        if (!nfc.isEnabled) {
            status = "خاصية NFC مغلقة. فعّلها من إعدادات الهاتف ثم أعد المحاولة."
            return
        }
        technologies = emptyList()
        ndefSummary = "قرّب البطاقة من ظهر الهاتف وانتظر لحظة."
        lastScanTime = ""
        status = "المستشعر جاهز — في انتظار البطاقة…"
        scanning = true
        try {
            nfc.enableReaderMode(
                this,
                this,
                NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                null
            )
        } catch (_: Exception) {
            scanning = false
            status = "تعذّر تشغيل قارئ NFC. أغلق أي تطبيق آخر يستخدم المستشعر ثم حاول مجددًا."
        }
    }

    private fun stopScan() {
        if (scanning) {
            try { adapter?.disableReaderMode(this) } catch (_: Exception) {}
            scanning = false
            status = "تم إيقاف الفحص. يمكنك بدء فحص جديد في أي وقت."
        }
    }

    override fun onTagDiscovered(tag: Tag) {
        val techs = tag.techList
            .map { it.substringAfterLast('.') }
            .distinct()
            .sorted()

        var ndefAvailable = false
        val ndef = Ndef.get(tag)
        val summary = if (ndef == null) {
            "لم تعلن البطاقة عن دعم NDEF. هذا لا يعني أنها تالفة؛ قد تستخدم تقنية أو نظامًا آخر."
        } else {
            try {
                ndef.connect()
                ndefAvailable = true
                val writable = if (ndef.isWritable) "متاحة" else "غير متاحة"
                val maxSize = ndef.maxSize
                "تم التعرف على NDEF. السعة المعلنة: $maxSize بايت. الكتابة: $writable. لم تتم قراءة محتوى الرسالة."
            } catch (_: Exception) {
                "تم اكتشاف واجهة NDEF، لكن تعذّر الاستعلام عن خصائصها من هذه البطاقة."
            } finally {
                try { ndef.close() } catch (_: Exception) {}
            }
        }

        val time = SimpleDateFormat("dd/MM/yyyy  HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = ScanEntry(time, techs, ndefAvailable, summary)

        runOnUiThread {
            technologies = techs
            ndefSummary = summary
            lastScanTime = time
            status = "تم اكتشاف بطاقة NFC بنجاح."
            scanning = false
            history = (listOf(entry) + history).take(20)
            saveHistory()
            try { adapter?.disableReaderMode(this) } catch (_: Exception) {}
            if (vibrationEnabled) vibrateOnce()
        }
    }

    private fun vibrateOnce() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(75)
                }
            }
        } catch (_: Exception) {}
    }

    private fun loadHistory(): List<ScanEntry> {
        return try {
            val array = JSONArray(getSharedPreferences("cardmaster", MODE_PRIVATE).getString("history", "[]"))
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val techArray = item.optJSONArray("technologies") ?: JSONArray()
                    val techs = buildList {
                        for (j in 0 until techArray.length()) add(techArray.optString(j))
                    }
                    add(
                        ScanEntry(
                            time = item.optString("time"),
                            technologies = techs,
                            ndefAvailable = item.optBoolean("ndefAvailable"),
                            ndefSummary = item.optString("ndefSummary")
                        )
                    )
                }
            }.take(20)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveHistory() {
        val array = JSONArray()
        history.take(20).forEach { entry ->
            val techArray = JSONArray()
            entry.technologies.forEach { techArray.put(it) }
            array.put(
                JSONObject()
                    .put("time", entry.time)
                    .put("technologies", techArray)
                    .put("ndefAvailable", entry.ndefAvailable)
                    .put("ndefSummary", entry.ndefSummary)
            )
        }
        getSharedPreferences("cardmaster", MODE_PRIVATE)
            .edit().putString("history", array.toString()).apply()
    }

    private fun clearHistory() {
        history = emptyList()
        saveHistory()
        Toast.makeText(this, "تم مسح سجل الفحوصات من هذا الهاتف.", Toast.LENGTH_SHORT).show()
    }

    private fun reportText(): String {
        val techText = if (technologies.isEmpty()) "لا توجد تقنيات مسجلة" else technologies.joinToString(", ")
        return "CardMaster — تقرير تشخيص NFC\n" +
            "الحالة: $status\n" +
            "التقنيات: $techText\n" +
            "معلومات NDEF: $ndefSummary\n" +
            "وقت الفحص: " + lastScanTime.ifBlank { "غير متاح" } + "\n" +
            "ملاحظة: لا يتضمن التقرير رقم البطاقة أو محتواها أو أي بيانات سرية."
    }

    private fun copyReport() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CardMaster report", reportText()))
        Toast.makeText(this, "تم نسخ تقرير التشخيص.", Toast.LENGTH_SHORT).show()
    }

    private fun shareReport() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "CardMaster NFC report")
            putExtra(Intent.EXTRA_TEXT, reportText())
        }
        startActivity(Intent.createChooser(intent, "مشاركة تقرير CardMaster"))
    }

    override fun onResume() {
        super.onResume()
        adapter = NfcAdapter.getDefaultAdapter(this)
        nfcEnabledState = adapter?.isEnabled == true
        if (!scanning && adapter != null && !nfcEnabledState) {
            status = "خاصية NFC مغلقة. فعّلها من إعدادات الهاتف."
        } else if (!scanning && nfcEnabledState && status.startsWith("خاصية NFC مغلقة")) {
            status = "NFC مفعّل وجاهز للفحص."
        }
    }

    override fun onPause() {
        stopScan()
        super.onPause()
    }
}

@Composable
private fun CardMasterScreen(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    nfcAvailable: Boolean,
    nfcEnabled: Boolean,
    status: String,
    scanning: Boolean,
    technologies: List<String>,
    ndefSummary: String,
    lastScanTime: String,
    history: List<ScanEntry>,
    vibrationEnabled: Boolean,
    deviceName: String,
    androidVersion: String,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onToggleVibration: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit
) {
    Scaffold(
        containerColor = Night,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0D1020), tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = selectedTab == AppTab.Home,
                    onClick = { onSelectTab(AppTab.Home) },
                    icon = { Text("⌂", fontSize = 23.sp, fontWeight = FontWeight.Bold) },
                    label = { Text("الرئيسية") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.History,
                    onClick = { onSelectTab(AppTab.History) },
                    icon = { Text("◷", fontSize = 22.sp) },
                    label = { Text("السجل") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.Settings,
                    onClick = { onSelectTab(AppTab.Settings) },
                    icon = { Text("⚙", fontSize = 21.sp) },
                    label = { Text("الإعدادات") },
                    colors = navColors()
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {
            when (selectedTab) {
                AppTab.Home -> HomeContent(
                    nfcAvailable, nfcEnabled, status, scanning, technologies, ndefSummary,
                    lastScanTime, history.size, deviceName, androidVersion,
                    onStartScan, onStopScan,
                    onOpenHistory = { onSelectTab(AppTab.History) },
                    onOpenSettings = { onSelectTab(AppTab.Settings) },
                    onCopyReport = onCopyReport
                )
                AppTab.History -> HistoryContent(history, onClearHistory)
                AppTab.Settings -> SettingsContent(
                    nfcAvailable, nfcEnabled, vibrationEnabled, deviceName, androidVersion,
                    onToggleVibration, onClearHistory, onCopyReport, onShareReport
                )
            }
            Text(
                "CardMaster  •  إصدار 1.1  •  مشروع مستقل",
                color = TextMuted.copy(alpha = 0.65f),
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Violet,
    selectedTextColor = Violet,
    indicatorColor = Purple.copy(alpha = 0.18f),
    unselectedIconColor = TextMuted,
    unselectedTextColor = TextMuted
)

@Composable
private fun HomeContent(
    nfcAvailable: Boolean,
    nfcEnabled: Boolean,
    status: String,
    scanning: Boolean,
    technologies: List<String>,
    ndefSummary: String,
    lastScanTime: String,
    historyCount: Int,
    deviceName: String,
    androidVersion: String,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onCopyReport: () -> Unit
) {
    var showTechGuide by androidx.compose.runtime.remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandMark(52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("CardMaster", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("قارئ وتشخيص البطاقات الذكية", color = TextMuted, fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(PanelRaised)
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center
        ) {
            Text("⚙", color = Violet, fontSize = 22.sp)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusPill(if (nfcEnabled) "NFC جاهز" else if (nfcAvailable) "NFC مغلق" else "لا يوجد NFC", nfcEnabled)
        Text("فحص آمن — دون حفظ بيانات البطاقة", color = TextMuted, fontSize = 11.sp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF21154A), Color(0xFF111B3C), Color(0xFF0B2237))))
            .border(1.dp, Purple.copy(alpha = 0.34f), RoundedCornerShape(28.dp))
            .padding(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("مركز الفحص الذكي", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("تشخيص NFC", color = TextMain, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text("NFC", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            CardIllustration(Modifier.fillMaxWidth().height(148.dp))
            Text("اكتشف التقنية ونوع الاتصال", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "اعرف ما الذي يعلنه الكارت قبل استخدام أي تطبيق آخر.",
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = if (scanning) onStopScan else onStartScan,
                enabled = scanning || (nfcAvailable && nfcEnabled),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Purple,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF39334E),
                    disabledContentColor = TextMuted
                )
            ) {
                Text(if (scanning) "إيقاف الفحص" else "بدء التشخيص", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(if (scanning) "  ■" else "  ◉", fontSize = 18.sp)
            }
            if (scanning) {
                Spacer(Modifier.height(10.dp))
                Text("قرّب البطاقة من ظهر الهاتف الآن…", color = Cyan, fontSize = 12.sp)
            }
        }
    }

    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle("حالة الفحص", "متابعة مباشرة لاتصال NFC")
            }
            StatusPill(if (scanning) "جارٍ الفحص" else if (technologies.isNotEmpty()) "اكتمل" else "جاهز", scanning || technologies.isNotEmpty())
        }
        Spacer(Modifier.height(12.dp))
        Text(status, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (technologies.isNotEmpty()) {
            Spacer(Modifier.height(13.dp))
            Text("التقنيات المكتشفة", color = TextMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                technologies.take(3).forEach { TechPill(it) }
                if (technologies.size > 3) TechPill("+" + (technologies.size - 3))
            }
            Spacer(Modifier.height(10.dp))
            Divider(color = Line)
            Spacer(Modifier.height(10.dp))
            Text("معلومات NDEF", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(ndefSummary, color = TextMuted, fontSize = 12.sp)
            if (lastScanTime.isNotBlank()) Text("آخر فحص: " + lastScanTime, color = TextMuted, fontSize = 10.sp)
            OutlinedButton(onClick = onCopyReport, shape = RoundedCornerShape(12.dp)) {
                Text("نسخ التقرير", color = Violet, fontSize = 12.sp)
            }
        } else {
            Spacer(Modifier.height(5.dp))
            Text(
                if (!nfcAvailable) "هذا الجهاز لا يعلن عن وجود مستشعر NFC."
                else if (!nfcEnabled) "فعّل NFC من إعدادات الهاتف حتى يبدأ الفحص."
                else "لم يتم اكتشاف بطاقة بعد. اضغط بدء التشخيص وقرّب بطاقة متوافقة.",
                color = TextMuted,
                fontSize = 12.sp
            )
        }
    }

    SectionTitle("أدوات CardMaster", "كل ما تحتاجه لمتابعة الفحص")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ToolTile("سجل الفحوصات", historyCount.toString() + " نتيجة محفوظة محليًا", "◷", Modifier.weight(1f), onOpenHistory)
        ToolTile("أنواع التقنيات", "اعرف معنى NFC-A وIsoDep", "▤", Modifier.weight(1f)) {
            showTechGuide = !showTechGuide
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ToolTile("معلومات الهاتف", deviceName.take(23), "▣", Modifier.weight(1f), onOpenSettings)
        ToolTile("الخصوصية", "بياناتك لا تغادر الهاتف", "◇", Modifier.weight(1f), onOpenSettings)
    }
    if (showTechGuide) {
        PanelCard {
            SectionTitle("دليل تقنيات NFC", "تقنيات قد يعرضها Android")
            Text(
                "NfcA وNfcB وNfcF وNfcV: أنواع اتصال لاسلكي. IsoDep: طبقة اتصال قد تستخدمها بطاقات متعددة. Ndef: تنسيق شائع لتخزين الرسائل على الوسوم.",
                color = TextMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "ظهور اسم تقنية لا يعني أن بيانات البطاقة متاحة للقراءة؛ قد تكون البيانات محمية أو تتطلب تطبيقًا رسميًا.",
                color = Amber,
                fontSize = 12.sp
            )
        }
    }

    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("◆", color = Cyan, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("خصوصيتك أولويتنا", color = TextMain, fontWeight = FontWeight.Bold)
                Text("لا يتم حفظ رقم البطاقة أو محتواها. السجل يحتوي على وقت الفحص والتقنيات العامة فقط.", color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun HistoryContent(history: List<ScanEntry>, onClearHistory: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("سجل الفحوصات", color = TextMain, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Text("آخر 20 فحصًا — محفوظة على هذا الهاتف فقط", color = TextMuted, fontSize = 12.sp)
        }
        Text(history.size.toString() + "/20", color = Violet, fontWeight = FontWeight.Bold)
    }
    if (history.isEmpty()) {
        PanelCard {
            Text("لا توجد فحوصات محفوظة بعد", color = TextMain, fontWeight = FontWeight.Bold)
            Text("ابدأ التشخيص من الصفحة الرئيسية وستظهر النتائج هنا تلقائيًا.", color = TextMuted, fontSize = 12.sp)
        }
    } else {
        OutlinedButton(onClick = onClearHistory, shape = RoundedCornerShape(12.dp)) {
            Text("مسح السجل", color = Color(0xFFFF9AA9))
        }
        history.forEachIndexed { index, entry ->
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Purple.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) { Text("N", color = Violet, fontWeight = FontWeight.ExtraBold) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("فحص NFC رقم " + (history.size - index), color = TextMain, fontWeight = FontWeight.Bold)
                        Text(entry.time, color = TextMuted, fontSize = 10.sp)
                    }
                    StatusPill(if (entry.ndefAvailable) "NDEF" else "عام", entry.ndefAvailable)
                }
                Spacer(Modifier.height(11.dp))
                Text(
                    if (entry.technologies.isEmpty()) "لم يتم تسجيل أسماء تقنيات." else entry.technologies.joinToString("  •  "),
                    color = Cyan,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(7.dp))
                Text(entry.ndefSummary, color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SettingsContent(
    nfcAvailable: Boolean,
    nfcEnabled: Boolean,
    vibrationEnabled: Boolean,
    deviceName: String,
    androidVersion: String,
    onToggleVibration: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit
) {
    Text("الإعدادات", color = TextMain, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
    Text("إدارة التطبيق وخصائص جهازك", color = TextMuted, fontSize = 12.sp)

    PanelCard {
        SectionTitle("حالة الجهاز", "معلومات أساسية عن الهاتف")
        InfoRow("الجهاز", deviceName)
        Divider(color = Line)
        InfoRow("إصدار Android", androidVersion)
        Divider(color = Line)
        InfoRow("مستشعر NFC", if (!nfcAvailable) "غير موجود" else if (nfcEnabled) "متاح ومفعّل" else "موجود لكنه مغلق")
    }

    PanelCard {
        SectionTitle("تفضيلات التطبيق", "إعدادات تحفظ على هذا الهاتف")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("اهتزاز عند نجاح الفحص", color = TextMain, fontWeight = FontWeight.Bold)
                Text("اهتزاز خفيف عند اكتشاف بطاقة", color = TextMuted, fontSize = 11.sp)
            }
            Switch(
                checked = vibrationEnabled,
                onCheckedChange = onToggleVibration,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Purple)
            )
        }
    }

    PanelCard {
        SectionTitle("التقارير والبيانات", "أدوات سريعة")
        OutlinedButton(onClick = onCopyReport, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
            Text("نسخ تقرير التشخيص", color = Violet)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onShareReport, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
            Text("مشاركة تقرير التشخيص", color = Violet)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onClearHistory, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
            Text("حذف سجل الفحوصات", color = Color(0xFFFF9AA9))
        }
    }

    PanelCard {
        Text("حدود القراءة", color = Amber, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(
            "CardMaster يعرض أنواع الاتصال وخصائص NDEF العامة فقط. لا يقرأ أو يعدّل بيانات الدفع أو الرصيد أو بيانات العدادات أو مفاتيح الحماية، ولا يتجاوز أنظمة الحماية.",
            color = TextMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(21.dp))
            .background(Brush.linearGradient(listOf(PanelRaised, Panel)))
            .border(1.dp, Line, RoundedCornerShape(21.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        content = content
    )
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun StatusPill(label: String, good: Boolean) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (good) Green.copy(alpha = 0.13f) else Amber.copy(alpha = 0.12f))
            .border(1.dp, if (good) Green.copy(alpha = 0.35f) else Amber.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(label, color = if (good) Green else Amber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TechPill(label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Purple.copy(alpha = 0.16f))
            .border(1.dp, Purple.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = Violet, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ToolTile(
    title: String,
    description: String,
    symbol: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Purple.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = Violet, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Text(title, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(description, color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Purple, Color(0xFF4230A6), Color(0xFF124B78))),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.toPx() * 0.28f)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.1f),
            topLeft = androidx.compose.ui.geometry.Offset(size.toPx() * 0.16f, size.toPx() * 0.20f),
            size = androidx.compose.ui.geometry.Size(size.toPx() * 0.54f, size.toPx() * 0.52f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.toPx() * 0.09f)
        )
        drawArc(
            color = Color.White,
            startAngle = -48f,
            sweepAngle = 96f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(size.toPx() * 0.45f, size.toPx() * 0.28f),
            size = androidx.compose.ui.geometry.Size(size.toPx() * 0.38f, size.toPx() * 0.42f),
            style = Stroke(width = size.toPx() * 0.055f, cap = StrokeCap.Round)
        )
        drawArc(
            color = Cyan,
            startAngle = -48f,
            sweepAngle = 96f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(size.toPx() * 0.53f, size.toPx() * 0.36f),
            size = androidx.compose.ui.geometry.Size(size.toPx() * 0.25f, size.toPx() * 0.26f),
            style = Stroke(width = size.toPx() * 0.045f, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun CardIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(
            brush = Brush.radialGradient(listOf(Purple.copy(alpha = 0.26f), Color.Transparent)),
            radius = h * 0.72f,
            center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.52f)
        )
        drawRoundRect(
            color = Color(0xFF151A39),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.27f, h * 0.08f),
            size = androidx.compose.ui.geometry.Size(w * 0.34f, h * 0.82f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.12f),
            style = Stroke(width = 4.dp.toPx())
        )
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF3346B7), Purple, Color(0xFF142D69))),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.44f, h * 0.43f),
            size = androidx.compose.ui.geometry.Size(w * 0.38f, h * 0.39f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.07f)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.16f),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.48f, h * 0.50f),
            size = androidx.compose.ui.geometry.Size(w * 0.15f, h * 0.04f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
        )
        drawCircle(color = Amber, radius = h * 0.055f, center = androidx.compose.ui.geometry.Offset(w * 0.71f, h * 0.70f))
        drawArc(
            color = Cyan,
            startAngle = -48f,
            sweepAngle = 96f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.37f, h * 0.28f),
            size = androidx.compose.ui.geometry.Size(w * 0.23f, h * 0.28f),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = Cyan.copy(alpha = 0.55f),
            startAngle = -48f,
            sweepAngle = 96f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.30f, h * 0.20f),
            size = androidx.compose.ui.geometry.Size(w * 0.38f, h * 0.44f),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
