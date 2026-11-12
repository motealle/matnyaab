using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Linq;
using System.Windows.Forms;
using Matnyaab.Word.Core;

namespace Matnyaab.Word.UI
{
    /// <summary>
    /// پنل اصلی افزونه: سرتیتر با وضعیت اتصال، نوار زبانه‌ها و صفحهٔ هر زبانه.
    /// زبانه‌ها با پرچم‌های قابلیت در تنظیمات پنهان/نمایان می‌شوند.
    /// </summary>
    public class PaneView : UserControl
    {
        private class TabDef { public string Id, Title, Glyph; public Func<bool> Visible; public Func<Page> Make; public Page Page; }

        private readonly List<TabDef> _tabs;
        private readonly Panel _header = new Panel { Dock = DockStyle.Top, Height = 58 };
        private readonly TabStrip _strip = new TabStrip { Dock = DockStyle.Top, Height = 56 };
        private readonly Panel _host = new Panel { Dock = DockStyle.Fill };
        private readonly Label _banner = new Label { Dock = DockStyle.Top, Height = 0, TextAlign = ContentAlignment.MiddleCenter, Visible = false };
        private readonly Timer _bannerTimer = new Timer { Interval = 4500 };
        private string _status = "", _statusKind = "idle";
        private string _current;

        public SearchView Search { get; private set; }
        public WritingView Writing { get; private set; }
        public OfficeView Office { get; private set; }
        public void OpenOffice(string id) { Open("office"); Office?.Select(id); }

        public PaneView()
        {
            SetStyle(ControlStyles.OptimizedDoubleBuffer | ControlStyles.AllPaintingInWmPaint, true);
            RightToLeft = RightToLeft.Yes;
            Font = Kit.F();
            _tabs = new List<TabDef>
            {
                new TabDef { Id = "search", Title = "جست‌وجو", Glyph = "⌕", Visible = () => AddinSettings.Current.ShowSearch, Make = () => Search = new SearchView(this) },
                new TabDef { Id = "writing", Title = "نگارش", Glyph = "✎", Visible = () => AddinSettings.Current.ShowFootnotes || AddinSettings.Current.ShowStructure || AddinSettings.Current.ShowCleanup, Make = () => Writing = new WritingView() },
                new TabDef { Id = "templates", Title = "قالب", Glyph = "▤", Visible = () => AddinSettings.Current.ShowTemplates, Make = () => new TemplatesView() },
                new TabDef { Id = "cite", Title = "استناد", Glyph = "❝", Visible = () => AddinSettings.Current.ShowCite, Make = () => new CiteView() },
                new TabDef { Id = "research", Title = "پژوهش", Glyph = "🗂", Visible = () => AddinSettings.Current.ShowResearch, Make = () => new ResearchView() },
                new TabDef { Id = "office", Title = "اداری", Glyph = "✉", Visible = () => AddinSettings.Current.ShowOffice, Make = () => Office = new OfficeView() },
                new TabDef { Id = "ai", Title = "دستیار", Glyph = "✦", Visible = () => AddinSettings.Current.ShowAi, Make = () => new AiView(this) },
                new TabDef { Id = "settings", Title = "تنظیمات", Glyph = "⚙", Visible = () => true, Make = () => new SettingsView(this) },
            };
            _header.Paint += PaintHeader;
            _strip.Selected += id => Open(id);
            _banner.Click += (s, e) => HideBanner();
            _bannerTimer.Tick += (s, e) => HideBanner();
            Controls.Add(_host); Controls.Add(_banner); Controls.Add(_strip); Controls.Add(_header);
            Toast.Sink = ShowBanner;
            Kit.ThemeChanged += Rebuild;
            AddinSettings.Changed += () => { RefreshTabs(); };
            RefreshTabs();
            var start = AddinSettings.Current.StartTab;
            Open(_tabs.Any(t => t.Id == start && t.Visible()) ? start : _tabs.First(t => t.Visible()).Id);
            CheckConnection();
        }

        protected override void Dispose(bool disposing) { if (disposing && Toast.Sink == (Action<string, bool>)ShowBanner) Toast.Sink = null; base.Dispose(disposing); }

        private void RefreshTabs()
        {
            _strip.Items = _tabs.Where(t => t.Visible()).Select(t => (t.Id, t.Title, t.Glyph)).ToList();
            if (_current != null && !_tabs.First(t => t.Id == _current).Visible()) Open(_strip.Items.First().Item1);
            _strip.Invalidate();
        }

        public void Open(string id)
        {
            var t = _tabs.FirstOrDefault(x => x.Id == id) ?? _tabs[0];
            if (!t.Visible()) { t = _tabs.First(x => x.Visible()); }
            if (t.Page == null) { t.Page = t.Make(); t.Page.Dock = DockStyle.Fill; }
            _current = t.Id; _strip.Current = t.Id;
            _host.Controls.Clear(); _host.Controls.Add(t.Page);
            t.Page.Relayout();
            _strip.Invalidate();
        }

        public void OpenSearch(string query)
        {
            Open("search");
            if (!string.IsNullOrWhiteSpace(query)) Search?.Run(query);
        }

        public void OpenWriting(string section) { Open("writing"); Writing?.Focus(section); }

        /// <summary>بازسازی کامل پس از تغییر پوسته یا قلم</summary>
        public void Rebuild()
        {
            foreach (var t in _tabs) { if (t.Page != null) { t.Page.Dispose(); t.Page = null; } }
            Search = null; Writing = null;
            Font = Kit.F();
            _header.Invalidate(); _strip.Invalidate();
            Open(_current ?? "search");
        }

        // ---------------------------------------------------------------- وضعیت اتصال

        public void CheckConnection()
        {
            if (!Bridge.Installed) { SetStatus("بدون متن‌یاب — ابزارهای ورد فعال‌اند", "off"); return; }
            SetStatus("در حال اتصال به متن‌یاب…", "wait");
            Background.Run(() => Bridge.Call("ping", null, 1500), p =>
            {
                if (p != null && p.Bool("ok")) SetStatus("متصل به متن‌یاب " + Fa.Num(p.Str("version")), "ok");
                else SetStatus("متن‌یاب آماده است (با نخستین جست‌وجو باز می‌شود)", "idle");
            });
        }

        public void SetStatus(string text, string kind) { _status = text; _statusKind = kind; _header.Invalidate(); }

        private void PaintHeader(object sender, PaintEventArgs e)
        {
            var p = Kit.P; var g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.Clear(p.Surface);
            var w = _header.Width;
            // نشان: کاغذ گردگوشه با ذره‌بین
            var logo = new Rectangle(w - 46, 11, 34, 34);
            using (var path = Kit.Round(logo, 9)) using (var b = new LinearGradientBrush(logo, p.Accent, p.AccentHover, 45f)) g.FillPath(b, path);
            TextRenderer.DrawText(g, "م", Kit.F(13f, true), logo, Color.White, TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
            TextRenderer.DrawText(g, "متن‌یاب برای ورد", Kit.F(11f, true), new Rectangle(60, 8, w - 116, 24), p.Text, TextFormatFlags.Right | TextFormatFlags.RightToLeft);
            var dot = _statusKind == "ok" ? p.Success : _statusKind == "off" ? p.Muted : _statusKind == "err" ? p.Danger : p.Accent;
            using (var b = new SolidBrush(dot)) g.FillEllipse(b, w - 64, 38, 7, 7);
            TextRenderer.DrawText(g, _status, Kit.F(8.5f), new Rectangle(8, 32, w - 76, 20), p.Muted, TextFormatFlags.Right | TextFormatFlags.RightToLeft | TextFormatFlags.EndEllipsis);
            using (var pen = new Pen(p.Border)) g.DrawLine(pen, 0, _header.Height - 1, w, _header.Height - 1);
        }

        // ---------------------------------------------------------------- اعلان درون پنل

        private void ShowBanner(string msg, bool error)
        {
            _banner.Text = msg; _banner.Font = Kit.F(9.5f);
            _banner.BackColor = error ? Kit.P.Danger : Kit.P.Success; _banner.ForeColor = Color.White;
            _banner.Height = Math.Max(34, TextRenderer.MeasureText(msg, _banner.Font, new Size(Width - 20, 0), TextFormatFlags.WordBreak).Height + 14);
            _banner.Visible = true;
            _bannerTimer.Stop(); _bannerTimer.Start();
        }

        private void HideBanner() { _bannerTimer.Stop(); _banner.Visible = false; }
    }

    /// <summary>نوار زبانه‌ها با نماد و برچسب (راست‌به‌چپ)</summary>
    public class TabStrip : Control
    {
        public List<(string, string, string)> Items = new List<(string, string, string)>();
        public string Current;
        public event Action<string> Selected;
        private int _hover = -1;

        public TabStrip()
        {
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw | ControlStyles.UserPaint, true);
            Cursor = Cursors.Hand;
        }

        private Rectangle Cell(int i)
        {
            var n = Math.Max(1, Items.Count); var cw = (Width - 12) / n;
            return new Rectangle(Width - 6 - (i + 1) * cw, 6, cw, Height - 12);
        }

        protected override void OnMouseMove(MouseEventArgs e)
        {
            var h = -1; for (var i = 0; i < Items.Count; i++) if (Cell(i).Contains(e.Location)) { h = i; break; }
            if (h != _hover) { _hover = h; Invalidate(); }
        }

        protected override void OnMouseLeave(EventArgs e) { _hover = -1; Invalidate(); }

        protected override void OnMouseClick(MouseEventArgs e)
        {
            for (var i = 0; i < Items.Count; i++) if (Cell(i).Contains(e.Location)) { Selected?.Invoke(Items[i].Item1); return; }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            var p = Kit.P; var g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.Clear(p.Surface);
            using (var path = Kit.Round(new Rectangle(6, 6, Width - 13, Height - 13), 10)) using (var b = new SolidBrush(p.Surface2)) using (var pen = new Pen(p.Border)) { g.FillPath(b, path); g.DrawPath(pen, path); }
            for (var i = 0; i < Items.Count; i++)
            {
                var r = Rectangle.Inflate(Cell(i), -2, -2);
                var sel = Items[i].Item1 == Current;
                if (sel || i == _hover)
                    using (var path = Kit.Round(r, 8)) using (var b = new SolidBrush(sel ? p.Surface : p.Hover)) g.FillPath(b, path);
                if (sel) using (var b = new SolidBrush(p.Accent)) g.FillRectangle(b, r.X + r.Width / 2 - 10, r.Bottom - 3, 20, 3);
                var fg = sel ? p.AccentText : p.Muted;
                TextRenderer.DrawText(g, Items[i].Item3, Kit.Symbol(12f), new Rectangle(r.X, r.Y + 1, r.Width, 22), fg, TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
                TextRenderer.DrawText(g, Items[i].Item2, Kit.F(8.5f, sel), new Rectangle(r.X, r.Y + 21, r.Width, 18), fg, TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
            }
            using (var pen = new Pen(p.Border)) g.DrawLine(pen, 0, Height - 1, Width, Height - 1);
        }
    }
}
