using System.Collections.ObjectModel;
using System.IO;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using Microsoft.Win32;

namespace CardMaster.Desktop;

public partial class MainWindow : Window
{
    private readonly PcscService _pcsc = new();
    private readonly ObservableCollection<string> _readers = new();
    private readonly ObservableCollection<ScanLogEntry> _history = new();
    private CardInspection? _lastInspection;
    private string _lastReaderName = string.Empty;
    private DateTime? _lastScanTime;

    public MainWindow()
    {
        InitializeComponent();
        ReadersList.ItemsSource = _readers;
        HistoryGrid.ItemsSource = _history;
        Loaded += (_, _) => RefreshReaders();
    }

    private void RefreshReaders_Click(object sender, RoutedEventArgs e) => RefreshReaders();

    private void RefreshReaders()
    {
        try
        {
            var selected = ReadersList.SelectedItem as string;
            var readers = _pcsc.ListReaders();
            _readers.Clear();
            foreach (var reader in readers)
                _readers.Add(reader);

            if (selected is not null && _readers.Contains(selected))
                ReadersList.SelectedItem = selected;
            else if (_readers.Count > 0)
                ReadersList.SelectedIndex = 0;

            DeviceCountText.Text = $"{_readers.Count} قارئ";
            ReaderHint.Text = _readers.Count == 0
                ? "لم يعثر Windows على قارئ PC/SC. تحقق من توصيل القارئ وتعريفه وتشغيل خدمة Smart Card."
                : $"تم العثور على {_readers.Count} قارئ PC/SC. اختر قارئًا ثم ابدأ الفحص.";

            if (_readers.Count == 0)
                StatusText.Text = "لا توجد قارئات PC/SC ظاهرة حاليًا.";
            else
                StatusText.Text = "تم تحديث قائمة القارئات المتصلة.";

            if (_readers.Count == 0)
                ResetInspection("لا يوجد قارئ متصل.");
        }
        catch (Exception ex)
        {
            StatusText.Text = "تعذّر تعداد القارئات: " + ex.Message;
            ReaderHint.Text = "تحقق من خدمة Windows Smart Card وصلاحية الوصول إلى القارئ.";
        }
    }

    private void ReadersList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        var selected = ReadersList.SelectedItem as string;
        SelectedReaderText.Text = selected ?? "لا يوجد قارئ محدد";
        if (selected is null)
            ResetInspection("اختر قارئًا أولًا.");
        else
        {
            CardStateText.Text = "اضغط فحص البطاقة لعرض الحالة.";
            CardBadgeText.Text = "جاهز للفحص";
            AtrText.Text = "—";
            AtrLengthText.Text = "—";
            ProtocolText.Text = "—";
            ScanTimeText.Text = "—";
        }
    }

    private void ScanCard_Click(object sender, RoutedEventArgs e)
    {
        if (ReadersList.SelectedItem is not string reader)
        {
            StatusText.Text = "اختر قارئًا من القائمة أولًا.";
            return;
        }

        try
        {
            var result = _pcsc.InspectCard(reader);
            var now = DateTime.Now;
            _lastInspection = result;
            _lastReaderName = reader;
            _lastScanTime = now;

            StatusText.Text = result.Message;
            SelectedReaderText.Text = reader;
            CardStateText.Text = result.CardPresent ? "تم اكتشاف بطاقة في القارئ." : "لا توجد بطاقة قابلة للتعرّف حاليًا.";
            CardBadgeText.Text = result.CardPresent ? "تم اكتشاف بطاقة" : "لا توجد بطاقة";
            AtrText.Text = string.IsNullOrWhiteSpace(result.AtrHex) ? "غير متاح" : result.AtrHex;
            AtrLengthText.Text = result.AtrHex.Length > 0 ? $"{result.AtrHex.Length / 2} بايت" : "غير متاح";
            ProtocolText.Text = result.Protocol;
            ScanTimeText.Text = now.ToString("dd/MM/yyyy  HH:mm:ss");

            _history.Insert(0, new ScanLogEntry(
                now.ToString("HH:mm:ss  dd/MM/yyyy"),
                reader,
                result.CardPresent ? "موجودة" : "غير موجودة",
                result.Protocol,
                result.AtrHex.Length > 0 ? $"{result.AtrHex.Length / 2} بايت" : "—"
            ));
            while (_history.Count > 30)
                _history.RemoveAt(_history.Count - 1);
        }
        catch (Exception ex)
        {
            StatusText.Text = "تعذّر فحص البطاقة: " + ex.Message;
            CardStateText.Text = "لم تكتمل عملية الفحص.";
            CardBadgeText.Text = "تعذّر الفحص";
            AtrText.Text = "—";
            AtrLengthText.Text = "—";
            ProtocolText.Text = "—";
            ScanTimeText.Text = DateTime.Now.ToString("dd/MM/yyyy  HH:mm:ss");
            _lastInspection = null;
            _lastReaderName = reader;
            _lastScanTime = DateTime.Now;
        }
    }

    private void CopyReport_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            Clipboard.SetText(BuildReport());
            StatusText.Text = "تم نسخ تقرير التشخيص العام إلى الحافظة.";
        }
        catch (Exception ex)
        {
            StatusText.Text = "تعذّر نسخ التقرير: " + ex.Message;
        }
    }

    private void ExportReport_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var dialog = new SaveFileDialog
            {
                Title = "حفظ تقرير CardMaster",
                FileName = $"CardMaster-report-{DateTime.Now:yyyyMMdd-HHmm}.txt",
                Filter = "ملف نصي (*.txt)|*.txt",
                DefaultExt = ".txt"
            };
            if (dialog.ShowDialog(this) != true)
                return;

            File.WriteAllText(dialog.FileName, BuildReport(), new UTF8Encoding(encoderShouldEmitUTF8Identifier: true));
            StatusText.Text = "تم تصدير تقرير التشخيص بنجاح.";
        }
        catch (Exception ex)
        {
            StatusText.Text = "تعذّر تصدير التقرير: " + ex.Message;
        }
    }

    private string BuildReport()
    {
        var inspection = _lastInspection;
        var report = new StringBuilder();
        report.AppendLine("CardMaster Professional — تقرير تشخيص قارئ البطاقات");
        report.AppendLine($"وقت إنشاء التقرير: {DateTime.Now:dd/MM/yyyy HH:mm:ss}");
        report.AppendLine("اسم القارئ: " + (string.IsNullOrWhiteSpace(_lastReaderName) ? "غير محدد" : _lastReaderName));
        report.AppendLine("وقت آخر فحص: " + (_lastScanTime?.ToString("dd/MM/yyyy HH:mm:ss") ?? "غير متاح"));
        report.AppendLine("حالة البطاقة: " + (inspection?.CardPresent == true ? "تم اكتشاف بطاقة" : "غير متاح أو لا توجد بطاقة"));
        report.AppendLine("بروتوكول الاتصال: " + (inspection?.Protocol ?? "غير متاح"));
        report.AppendLine("ATR: " + (string.IsNullOrWhiteSpace(inspection?.AtrHex) ? "غير متاح" : inspection.AtrHex));
        var atrLengthText = !string.IsNullOrEmpty(inspection?.AtrHex)
            ? (inspection.AtrHex.Length / 2).ToString() + " بايت"
            : "غير متاح";
        report.AppendLine("طول ATR: " + atrLengthText);
        report.AppendLine();
        report.AppendLine("حدود هذا التقرير:");
        report.AppendLine("- يعرض معلومات PC/SC العامة التي يوفرها قارئ Windows فقط.");
        report.AppendLine("- لا يتضمن UID أو محتوى البطاقة أو بيانات دفع أو بيانات محمية.");
        report.AppendLine("- قارئ Smart Card التلامسي ليس بالضرورة قارئ NFC لاسلكيًا.");
        report.AppendLine("- اختلاف القارئ والبطاقة يغيّر المعلومات العامة المتاحة.");
        return report.ToString();
    }

    private void ClearHistory_Click(object sender, RoutedEventArgs e)
    {
        _history.Clear();
        StatusText.Text = "تم مسح سجل الفحوصات من هذه الجلسة.";
    }

    private void ResetInspection(string message)
    {
        _lastInspection = null;
        _lastReaderName = string.Empty;
        _lastScanTime = null;
        CardStateText.Text = message;
        CardBadgeText.Text = "بانتظار الفحص";
        AtrText.Text = "—";
        AtrLengthText.Text = "—";
        ProtocolText.Text = "—";
        ScanTimeText.Text = "—";
    }
}

internal sealed record ScanLogEntry(
    string Time,
    string Reader,
    string CardState,
    string Protocol,
    string AtrLength
);