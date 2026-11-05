using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;

namespace CardMaster.Desktop;

public partial class MainWindow : Window
{
    private readonly PcscService _pcsc = new();
    private readonly ObservableCollection<string> _readers = new();

    public MainWindow()
    {
        InitializeComponent();
        ReadersList.ItemsSource = _readers;
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
            foreach (var reader in readers) _readers.Add(reader);

            if (selected is not null && _readers.Contains(selected))
                ReadersList.SelectedItem = selected;
            else if (_readers.Count > 0)
                ReadersList.SelectedIndex = 0;

            ReaderHint.Text = _readers.Count == 0
                ? "لم يعثر Windows على قارئ PC/SC. تحقق من توصيل القارئ وتعريفه وتشغيل خدمة Smart Card."
                : $"تم العثور على {_readers.Count} قارئ. اختر قارئًا ثم افحص البطاقة.";
            StatusText.Text = _readers.Count == 0 ? "لا توجد قارئات PC/SC ظاهرة حاليًا." : "تم اكتشاف قارئات البطاقات.";
        }
        catch (Exception ex)
        {
            StatusText.Text = "تعذّر تعداد القارئات: " + ex.Message;
        }
    }

    private void ReadersList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        SelectedReaderText.Text = ReadersList.SelectedItem as string ?? "لا يوجد قارئ محدد";
        CardStateText.Text = "اضغط فحص البطاقة لعرض الحالة.";
        AtrText.Text = "—";
        ProtocolText.Text = "—";
    }

    private void ScanCard_Click(object sender, RoutedEventArgs e)
    {
        if (ReadersList.SelectedItem is not string reader)
        {
            StatusText.Text = "اختر قارئًا أولًا.";
            return;
        }

        try
        {
            var result = _pcsc.InspectCard(reader);
            StatusText.Text = result.Message;
            CardStateText.Text = result.CardPresent ? "تم اكتشاف بطاقة في القارئ." : "لا توجد بطاقة حاليًا.";
            AtrText.Text = result.AtrHex.Length == 0 ? "غير متاح" : result.AtrHex;
            ProtocolText.Text = result.Protocol;
        }
        catch (Exception ex)
        {
            StatusText.Text = "تعذّر فحص البطاقة: " + ex.Message;
            CardStateText.Text = "لم تكتمل عملية الفحص.";
            AtrText.Text = "—";
            ProtocolText.Text = "—";
        }
    }
}
