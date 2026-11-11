using System;
using System.Collections.Generic;
using System.IO;
using System.Runtime.InteropServices;
using System.Windows.Forms;
using Matnyaab.Word.Core;
using Matnyaab.Word.Ops;
using Matnyaab.Word.UI;

[assembly: ComVisible(false)]
[assembly: Guid("6E0F7C51-2B83-4C1D-9B55-1F3A6D2C9E40")]

namespace Matnyaab.Word.Com
{
    // ---------------------------------------------------------------- رابط‌های COM آفیس (بدون نیاز به PIA)

    [ComImport, Guid("B65AD801-ABAF-11D0-BB8B-00A0C90F2744"), TypeLibType((short)0x1040)]
    public interface IDTExtensibility2
    {
        [DispId(1)] void OnConnection([In, MarshalAs(UnmanagedType.IDispatch)] object application, [In] int connectMode, [In, MarshalAs(UnmanagedType.IDispatch)] object addInInst, [In, MarshalAs(UnmanagedType.SafeArray, SafeArraySubType = VarEnum.VT_VARIANT)] ref Array custom);
        [DispId(2)] void OnDisconnection([In] int removeMode, [In, MarshalAs(UnmanagedType.SafeArray, SafeArraySubType = VarEnum.VT_VARIANT)] ref Array custom);
        [DispId(3)] void OnAddInsUpdate([In, MarshalAs(UnmanagedType.SafeArray, SafeArraySubType = VarEnum.VT_VARIANT)] ref Array custom);
        [DispId(4)] void OnStartupComplete([In, MarshalAs(UnmanagedType.SafeArray, SafeArraySubType = VarEnum.VT_VARIANT)] ref Array custom);
        [DispId(5)] void OnBeginShutdown([In, MarshalAs(UnmanagedType.SafeArray, SafeArraySubType = VarEnum.VT_VARIANT)] ref Array custom);
    }

    [ComImport, Guid("000C0396-0000-0000-C000-000000000046"), TypeLibType((short)0x1040)]
    public interface IRibbonExtensibility
    {
        [DispId(1)] [return: MarshalAs(UnmanagedType.BStr)] string GetCustomUI([In, MarshalAs(UnmanagedType.BStr)] string ribbonId);
    }

    [ComImport, Guid("000C033E-0000-0000-C000-000000000046"), TypeLibType((short)0x1040)]
    public interface ICustomTaskPaneConsumer
    {
        [DispId(1)] void CTPFactoryAvailable([In, MarshalAs(UnmanagedType.IDispatch)] object factory);
    }

    /// <summary>
    /// نقطهٔ ورود افزونهٔ «متن‌یاب برای ورد» (COM Add-in اشتراکی، ثبت‌شده برای کاربر جاری).
    /// همهٔ فراخوانی‌های آفیس در try/catch هستند تا هیچ خطایی ورد را کند یا غیرفعال نکند.
    /// </summary>
    [ComVisible(true), Guid("3B1E5F7A-8C2D-4E6B-A9F0-5D7C1B2E4A61"), ProgId("Matnyaab.Word.Connect"), ClassInterface(ClassInterfaceType.AutoDispatch)]
    public class Connect : IDTExtensibility2, IRibbonExtensibility, ICustomTaskPaneConsumer
    {
        public const string PaneProgId = "Matnyaab.Word.Pane";
        public static Connect Instance { get; private set; }

        private dynamic _app;
        private dynamic _ctpFactory;
        private dynamic _ribbon;
        private readonly Dictionary<string, dynamic> _panesByKey = new Dictionary<string, dynamic>();

        // ---------------------------------------------------------------- چرخهٔ عمر

        public void OnConnection(object application, int connectMode, object addInInst, ref Array custom)
        {
            try
            {
                Instance = this;
                _app = application;
                WordOps.App = application;
                Kit.Init();
                Log.Write("connected; Word " + WordOps.Version + "; addin " + Updater.CurrentVersion);
            }
            catch (Exception e) { Log.Error("OnConnection", e); }
        }

        public void OnDisconnection(int removeMode, ref Array custom)
        {
            try { foreach (var p in _panesByKey.Values) { try { p.Delete(); } catch { } } _panesByKey.Clear(); }
            catch (Exception e) { Log.Error("OnDisconnection", e); }
            WordOps.App = null; _app = null; Instance = null;
        }

        public void OnAddInsUpdate(ref Array custom) { }

        public void OnStartupComplete(ref Array custom)
        {
            try
            {
                if (AddinSettings.Current.OpenPaneOnStart) ShowPane(null);
                Background.Run(() => { Updater.RefreshAiCatalog(); return 0; }, _ => { });
                var s = AddinSettings.Current;
                var today = DateTime.Now.ToString("yyyy-MM-dd");
                if (s.AutoUpdate && s.LastUpdateCheck != today)
                    Background.Run(Updater.Check, r =>
                    {
                        s.LastUpdateCheck = today; AddinSettings.Save(false);
                        if (r != null && r.Version != s.SkippedVersion)
                            Toast.Show("نسخهٔ تازهٔ «متن‌یاب برای ورد» (" + Fa.Num(r.Version) + ") آماده است؛ از «تنظیمات ← به‌روزرسانی» نصبش کنید 🌱");
                    }, e => { });
            }
            catch (Exception e) { Log.Error("OnStartupComplete", e); }
        }

        public void OnBeginShutdown(ref Array custom) { }

        public void CTPFactoryAvailable(object factory) { _ctpFactory = factory; }

        // ---------------------------------------------------------------- نوار ابزار

        public string GetCustomUI(string ribbonId)
        {
            try
            {
                using (var s = typeof(Connect).Assembly.GetManifestResourceStream("Matnyaab.Word.Ribbon.xml"))
                using (var r = new StreamReader(s)) return r.ReadToEnd();
            }
            catch (Exception e) { Log.Error("GetCustomUI", e); return null; }
        }

        public void OnRibbonLoad(object ribbon) { _ribbon = ribbon; AddinSettings.Changed += () => { try { _ribbon?.Invalidate(); } catch { } }; }

        private static string Id(object control) { try { return (string)((dynamic)control).Id; } catch { return ""; } }

        public bool GetVisible(object control)
        {
            var s = AddinSettings.Current;
            switch (Id(control))
            {
                case "grpCite": return s.ShowCite;
                case "grpFootnotes": return s.ShowFootnotes;
                case "grpStructure": return s.ShowStructure || s.ShowCleanup;
                case "grpTemplates": return s.ShowTemplates;
                case "grpAi": return s.ShowAi;
                case "btnSearchSel": return s.ShowSearch;
                case "btnCleanup": return s.ShowCleanup;
                case "btnHeadings": case "btnToc": return s.ShowStructure;
                default: return true;
            }
        }

        public void OnAction(object control)
        {
            var id = Id(control);
            try
            {
                switch (id)
                {
                    case "btnPane": TogglePane(); break;
                    case "btnSearchSel": ShowPane(p => p.OpenSearch(WordOps.SelectionText())); break;
                    case "btnOpenApp": Bridge.ShowApp(WordOps.SelectionText()); break;
                    case "btnCiteInsert": ShowPane(p => p.OpenTab("cite")); break;
                    case "btnBib": RunBibliography(); break;
                    case "btnTermsToFn": ShowPane(p => p.OpenWriting("terms")); break;
                    case "btnFnToTerms": ShowPane(p => p.OpenWriting("footnotes")); break;
                    case "btnHeadings": ShowPane(p => p.OpenWriting("headings")); break;
                    case "btnCleanup": ShowPane(p => p.OpenWriting("cleanup")); break;
                    case "btnToc": WordOps.InsertToc(); break;
                    case "btnTemplates": ShowPane(p => p.OpenTab("templates")); break;
                    case "btnAi": ShowPane(p => p.OpenTab("ai")); break;
                    case "btnSettings": ShowPane(p => p.OpenTab("settings")); break;
                    case "btnHelp": System.Diagnostics.Process.Start(Updater.Site + "/#word"); break;
                }
            }
            catch (Exception e)
            {
                Log.Error("OnAction " + id, e);
                Toast.Show("کار ناتمام ماند: " + e.Message);
            }
        }

        private void RunBibliography()
        {
            var n = WordOps.UpdateBibliography();
            Toast.Show(n == 0 ? "هنوز منبعی از متن‌یاب در این سند درج نکرده‌اید." : "فهرست منابع با " + Fa.Num(n) + " منبع به‌روز شد.");
        }

        // ---------------------------------------------------------------- پنل کناری

        private string WindowKey()
        {
            try { dynamic w = _app.ActiveWindow; return ((int)w.Hwnd).ToString(); }
            catch { try { return (string)_app.ActiveDocument.FullName; } catch { return "default"; } }
        }

        private dynamic PaneForWindow(bool create)
        {
            if (_ctpFactory == null) return null;
            var key = WindowKey();
            if (_panesByKey.TryGetValue(key, out var ctp))
            {
                try { var _ = ctp.Visible; return ctp; } catch { _panesByKey.Remove(key); }
            }
            if (!create) return null;
            dynamic win = null;
            try { win = _app.ActiveWindow; } catch { }
            ctp = win != null ? _ctpFactory.CreateCTP(PaneProgId, "متن‌یاب", win) : _ctpFactory.CreateCTP(PaneProgId, "متن‌یاب");
            ApplyDock(ctp);
            _panesByKey[key] = ctp;
            return ctp;
        }

        public void ApplyDock(dynamic ctp)
        {
            var s = AddinSettings.Current;
            try
            {
                ctp.DockPosition = s.PanePosition == "left" ? 0 : s.PanePosition == "float" ? 4 : 2;
                if (s.PanePosition != "float") ctp.Width = Math.Max(320, Math.Min(720, s.PaneWidth));
                else { ctp.Width = 440; ctp.Height = 720; }
            }
            catch (Exception e) { Log.Error("dock", e); }
        }

        public void ApplyDockAll() { foreach (var p in _panesByKey.Values) ApplyDock(p); }

        private void TogglePane()
        {
            var ctp = PaneForWindow(true);
            if (ctp == null) { Toast.Show("پنل در این نسخهٔ ورد در دسترس نیست."); return; }
            ctp.Visible = !(bool)ctp.Visible;
        }

        public void ShowPane(Action<PaneControl> then)
        {
            var ctp = PaneForWindow(true);
            if (ctp == null) return;
            ctp.Visible = true;
            if (then != null && ctp.ContentControl is PaneControl pc) then(pc);
        }

        public void HidePane() { try { var p = PaneForWindow(false); if (p != null) p.Visible = false; } catch { } }
    }

    /// <summary>کنترل ActiveX میزبان پنل (آفیس با ProgId آن را می‌سازد)</summary>
    [ComVisible(true), Guid("9D2A4C6E-1F3B-4A57-8E20-6B4D3C5F7A82"), ProgId(Connect.PaneProgId), ClassInterface(ClassInterfaceType.AutoDispatch)]
    public class PaneControl : UserControl
    {
        private readonly PaneView _view;

        public PaneControl()
        {
            try { Kit.Init(); } catch { }
            RightToLeft = RightToLeft.Yes;
            _view = new PaneView { Dock = DockStyle.Fill };
            Controls.Add(_view);
        }

        public void OpenTab(string id) => _view.Open(id);
        public void OpenSearch(string query) => _view.OpenSearch(query);
        public void OpenWriting(string section) => _view.OpenWriting(section);
    }
}
