using System.ComponentModel;
using System.Runtime.InteropServices;

namespace CardMaster.Desktop;

internal sealed class PcscService
{
    private const uint ScopeUser = 0;
    private const uint ScopeSystem = 2;
    private const uint ShareShared = 2;
    private const uint ProtocolT0 = 1;
    private const uint ProtocolT1 = 2;
    private const uint LeaveCard = 0;
    private const int Success = 0;
    private const int NoReadersAvailable = unchecked((int)0x8010002E);
    private const int NoSmartcard = unchecked((int)0x8010000C);
    private const int UnknownCard = unchecked((int)0x8010000D);

    public IReadOnlyList<string> ListReaders()
    {
        var result = SCardEstablishContext(ScopeSystem, IntPtr.Zero, IntPtr.Zero, out var context);
        if (result != Success)
            result = SCardEstablishContext(ScopeUser, IntPtr.Zero, IntPtr.Zero, out context);
        Check(result, "بدء خدمة PC/SC");

        try
        {
            uint length = 0;
            result = SCardListReaders(context, null, null, ref length);
            if (result == NoReadersAvailable || length == 0) return Array.Empty<string>();
            Check(result, "البحث عن القارئات");

            var buffer = new char[length];
            result = SCardListReaders(context, null, buffer, ref length);
            Check(result, "قراءة أسماء القارئات");
            return new string(buffer).Split('\0', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
        }
        finally
        {
            SCardReleaseContext(context);
        }
    }

    public CardInspection InspectCard(string readerName)
    {
        var result = SCardEstablishContext(ScopeSystem, IntPtr.Zero, IntPtr.Zero, out var context);
        if (result != Success)
            result = SCardEstablishContext(ScopeUser, IntPtr.Zero, IntPtr.Zero, out context);
        Check(result, "بدء خدمة PC/SC");

        try
        {
            result = SCardConnect(context, readerName, ShareShared, ProtocolT0 | ProtocolT1, out var card, out var protocol);
            if (result == NoSmartcard || result == UnknownCard)
                return new CardInspection(false, string.Empty, "غير متاح", "القارئ متصل، لكن لا توجد بطاقة قابلة للتعرّف حاليًا.");
            Check(result, "الاتصال بالبطاقة");

            try
            {
                var atr = new byte[64];
                uint atrLength = (uint)atr.Length;
                var readerNames = new char[512];
                uint readerNamesLength = (uint)readerNames.Length;
                uint state = 0;
                uint activeProtocol = 0;

                result = SCardStatus(card, readerNames, ref readerNamesLength, out state, out activeProtocol, atr, ref atrLength);
                Check(result, "قراءة حالة البطاقة");
                var atrHex = Convert.ToHexString(atr.Take((int)Math.Min(atrLength, (uint)atr.Length)).ToArray());
                var protocolName = activeProtocol switch
                {
                    ProtocolT0 => "T=0",
                    ProtocolT1 => "T=1",
                    _ => "غير معروف"
                };
                return new CardInspection(true, atrHex, protocolName, "تم اكتشاف البطاقة وقراءة معلومات ATR العامة فقط.");
            }
            finally
            {
                SCardDisconnect(card, LeaveCard);
            }
        }
        finally
        {
            SCardReleaseContext(context);
        }
    }

    private static void Check(int result, string operation)
    {
        if (result != Success)
            throw new InvalidOperationException($"{operation}: {new Win32Exception(result).Message} (0x{result:X8})");
    }

    [DllImport("winscard.dll", CharSet = CharSet.Unicode)]
    private static extern int SCardEstablishContext(uint scope, IntPtr reserved1, IntPtr reserved2, out IntPtr context);

    [DllImport("winscard.dll", CharSet = CharSet.Unicode)]
    private static extern int SCardListReaders(IntPtr context, string? groups, [Out] char[]? readers, ref uint length);

    [DllImport("winscard.dll")]
    private static extern int SCardReleaseContext(IntPtr context);

    [DllImport("winscard.dll", CharSet = CharSet.Unicode)]
    private static extern int SCardConnect(IntPtr context, string reader, uint shareMode, uint preferredProtocols, out IntPtr card, out uint activeProtocol);

    [DllImport("winscard.dll", CharSet = CharSet.Unicode)]
    private static extern int SCardStatus(IntPtr card, [Out] char[] readerName, ref uint readerNameLength, out uint state, out uint protocol, [Out] byte[] atr, ref uint atrLength);

    [DllImport("winscard.dll")]
    private static extern int SCardDisconnect(IntPtr card, uint disposition);
}

internal sealed record CardInspection(bool CardPresent, string AtrHex, string Protocol, string Message);
