using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;
using Matnyaab.Word.Core;

namespace Matnyaab.Word.UI
{
    /// <summary>پوسته‌ها — همان رنگ‌های برنامهٔ متن‌یاب</summary>
    public class Palette
    {
        public string Id, Title; public bool Dark;
        public Color Accent, AccentHover, AccentSoft, AccentText, Canvas, Surface, Surface2, Text, Muted, Border, Hover, Selected, Success, Danger, Mark;

        private static Color C(string h) => ColorTranslator.FromHtml(h);

        public static readonly List<Palette> All = new List<Palette>
        {
            new Palette { Id = "blue", Title = "آبی متن‌یاب", Accent = C("#0F6CBD"), AccentHover = C("#115EA3"), AccentSoft = C("#E8F1FB"), AccentText = C("#0B4F8A"), Canvas = C("#EEF2F7"), Surface = C("#FFFFFF"), Surface2 = C("#F7F9FC"), Text = C("#1B1F24"), Muted = C("#5B6573"), Border = C("#DCE3EC"), Hover = C("#EEF4FB"), Selected = C("#D6E7F8"), Success = C("#107C41"), Danger = C("#C42B1C"), Mark = C("#FFF0A3") },
            new Palette { Id = "emerald", Title = "زمردی", Accent = C("#0E7C66"), AccentHover = C("#0B6655"), AccentSoft = C("#E5F4F0"), AccentText = C("#095646"), Canvas = C("#EDF3F1"), Surface = C("#FFFFFF"), Surface2 = C("#F6FAF8"), Text = C("#17201D"), Muted = C("#56645F"), Border = C("#D7E3DF"), Hover = C("#EAF5F1"), Selected = C("#CFEBE3"), Success = C("#107C41"), Danger = C("#C42B1C"), Mark = C("#FFF0A3") },
            new Palette { Id = "paper", Title = "کاغذی", Accent = C("#8A5A2B"), AccentHover = C("#744A22"), AccentSoft = C("#F3E8D8"), AccentText = C("#5E3C1A"), Canvas = C("#EFE6D6"), Surface = C("#FBF6EC"), Surface2 = C("#F6EEDF"), Text = C("#2B2118"), Muted = C("#6E5E4C"), Border = C("#E2D4BD"), Hover = C("#F3E9D7"), Selected = C("#EADAC0"), Success = C("#3F7A2E"), Danger = C("#B3261E"), Mark = C("#F7DE8A") },
            new Palette { Id = "violet", Title = "یاسی", Accent = C("#6B4FBB"), AccentHover = C("#5A41A3"), AccentSoft = C("#EFEAFB"), AccentText = C("#4A3591"), Canvas = C("#F0EEF6"), Surface = C("#FFFFFF"), Surface2 = C("#F8F7FC"), Text = C("#1E1B26"), Muted = C("#5F5A6D"), Border = C("#E0DCEB"), Hover = C("#F1EDFB"), Selected = C("#E0D7F6"), Success = C("#107C41"), Danger = C("#C42B1C"), Mark = C("#FFF0A3") },
            new Palette { Id = "night", Title = "شب", Dark = true, Accent = C("#2E7BD1"), AccentHover = C("#3A8AE0"), AccentSoft = C("#1F3347"), AccentText = C("#9CCBF6"), Canvas = C("#15181D"), Surface = C("#1E2229"), Surface2 = C("#232830"), Text = C("#E6E9EE"), Muted = C("#9AA4B2"), Border = C("#323945"), Hover = C("#2A313B"), Selected = C("#27405A"), Success = C("#3FB36B"), Danger = C("#E5534B"), Mark = C("#6B5A12") },
        };
    }

    /// <summary>جعبه‌ابزار طراحی پنل: رنگ، قلم و سازندهٔ کنترل‌ها</summary>
    public static class Kit
    {
        public static Palette P = Palette.All[0];
        public static event Action ThemeChanged;
        private static readonly PrivateFontCollection Pfc = new PrivateFontCollection();
        private static bool _init;
        private static readonly Dictionary<string, Font> Cache = new Dictionary<string, Font>();

        [DllImport("gdi32.dll", CharSet = CharSet.Unicode)]
        private static extern int AddFontResourceEx(string name, uint fl, IntPtr res);

        public static void Init()
        {
            if (_init) return;
            _init = true;
            try
            {
                var dir = Path.Combine(Paths.AddinDir, "Fonts");
                if (Directory.Exists(dir))
                    foreach (var f in Directory.GetFiles(dir, "*.ttf"))
                    {
                        try { Pfc.AddFontFile(f); AddFontResourceEx(f, 0x10, IntPtr.Zero); } catch (Exception e) { Log.Error("font " + f, e); }
                    }
            }
            catch (Exception e) { Log.Error("fonts", e); }
            ApplyTheme();
            AddinSettings.Changed += () => { var old = P.Id; ApplyTheme(); if (old != P.Id) ThemeChanged?.Invoke(); };
        }

        public static void ApplyTheme()
        {
            var id = AddinSettings.Current.Theme;
            if (id == "auto" || string.IsNullOrEmpty(id)) id = AddinSettings.MatnyaabSetting("THEME");
            P = Palette.All.FirstOrDefault(p => p.Id == id) ?? Palette.All[0];
        }

        public static void Notify() => ThemeChanged?.Invoke();

        private static FontFamily Family(string name)
        {
            var f = Pfc.Families.FirstOrDefault(x => string.Equals(x.Name, name, StringComparison.OrdinalIgnoreCase));
            if (f != null) return f;
            try { return new FontFamily(name); } catch { }
            return Pfc.Families.FirstOrDefault() ?? new FontFamily("Tahoma");
        }

        public static IEnumerable<string> FontChoices()
        {
            var installed = new InstalledFontCollection().Families.Select(f => f.Name);
            return new[] { "Vazirmatn", "Sahel", "IRNazanin", "B Nazanin", "Tahoma", "Segoe UI" }
                .Where(n => Pfc.Families.Any(f => f.Name == n) || installed.Contains(n)).Distinct();
        }

        public static bool FontInstalled(string name) =>
            string.IsNullOrEmpty(name) || new InstalledFontCollection().Families.Any(f => string.Equals(f.Name, name, StringComparison.OrdinalIgnoreCase));

        public static Font F(float size = 9.5f, bool bold = false)
        {
            var key = AddinSettings.Current.Font + "|" + size + "|" + bold;
            if (Cache.TryGetValue(key, out var f)) return f;
            f = new Font(Family(AddinSettings.Current.Font), size, bold ? FontStyle.Bold : FontStyle.Regular, GraphicsUnit.Point);
            Cache[key] = f;
            return f;
        }

        public static Font Symbol(float size) => new Font("Segoe UI Symbol", size, FontStyle.Regular, GraphicsUnit.Point);

        public static GraphicsPath Round(Rectangle r, int radius)
        {
            var p = new GraphicsPath();
            var d = radius * 2;
            if (d <= 0) { p.AddRectangle(r); return p; }
            p.AddArc(r.X, r.Y, d, d, 180, 90);
            p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            p.CloseFigure();
            return p;
        }

        // ---------------------------------------------------------------- سازنده‌ها

        public static Label Title(string t) => new ThemedLabel { Text = t, Role = "title" };
        public static Label Text(string t) => new ThemedLabel { Text = t, Role = "text" };
        public static Label Hint(string t) => new ThemedLabel { Text = t, Role = "hint" };

        public static TextBox Input(string placeholder = "", bool multiline = false, int lines = 4)
        {
            var tb = new ThemedTextBox { Placeholder = placeholder, Multiline = multiline };
            if (multiline) { tb.Height = (int)(F().GetHeight() * lines) + 12; tb.ScrollBars = ScrollBars.Vertical; tb.AcceptsReturn = true; }
            return tb;
        }

        public static ComboBox Combo(IEnumerable<string> items, int selected = 0, bool editable = false)
        {
            var c = new ComboBox { DropDownStyle = editable ? ComboBoxStyle.DropDown : ComboBoxStyle.DropDownList, FlatStyle = FlatStyle.Flat, RightToLeft = RightToLeft.Yes, Font = F(), Height = 30 };
            c.Items.AddRange(items.Cast<object>().ToArray());
            if (c.Items.Count > selected && selected >= 0) c.SelectedIndex = selected;
            return c;
        }

        public static CheckBox Check(string t, bool on = false, Action<bool> changed = null)
        {
            var c = new CheckBox { Text = t, Checked = on, AutoSize = false, Height = 26, RightToLeft = RightToLeft.Yes, Font = F(), FlatStyle = FlatStyle.Standard, TextAlign = ContentAlignment.MiddleLeft };
            if (changed != null) c.CheckedChanged += (s, e) => changed(c.Checked);
            return c;
        }

        public static RadioButton Radio(string t, bool on = false) =>
            new RadioButton { Text = t, Checked = on, AutoSize = true, RightToLeft = RightToLeft.Yes, Font = F(), Margin = new Padding(10, 3, 0, 3) };

        public static Btn Button(string t, Action click, BtnKind kind = BtnKind.Secondary, string tip = null)
        {
            var b = new Btn { Text = t, Kind = kind };
            if (click != null) b.Click += (s, e) => Safe(click);
            if (tip != null) Tips.SetToolTip(b, tip);
            return b;
        }

        public static readonly ToolTip Tips = new ToolTip { InitialDelay = 400, ReshowDelay = 200, AutoPopDelay = 9000 };

        public static Row Row(params Control[] items) { var r = new Row(); r.Controls.AddRange(items); return r; }

        /// <summary>اجرای ایمن یک کار UI با پیام دوستانه در صورت خطا</summary>
        public static void Safe(Action a)
        {
            try { a(); }
            catch (Exception e) { Log.Error("ui", e); Toast.Show("کار ناتمام ماند: " + e.Message, true); }
        }
    }

    public enum BtnKind { Primary, Secondary, Ghost, Danger, Chip }

    /// <summary>دکمهٔ گردگوشهٔ هماهنگ با پوسته</summary>
    public class Btn : Control
    {
        public BtnKind Kind = BtnKind.Secondary;
        public bool Selected;
        public bool Stretch;
        private bool _hover, _down;

        public Btn()
        {
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw | ControlStyles.UserPaint | ControlStyles.SupportsTransparentBackColor, true);
            Cursor = Cursors.Hand; Height = 34; Font = Kit.F(); RightToLeft = RightToLeft.Yes; Margin = new Padding(0, 0, 6, 6);
            TabStop = true;
        }

        public override Size GetPreferredSize(Size proposed)
        {
            var s = TextRenderer.MeasureText(Text ?? "", Kind == BtnKind.Chip ? Kit.F(9f) : Kit.F(9.5f, Kind == BtnKind.Primary));
            return new Size(s.Width + (Kind == BtnKind.Chip ? 22 : 30), Kind == BtnKind.Chip ? 28 : 34);
        }

        protected override void OnTextChanged(EventArgs e) { base.OnTextChanged(e); if (!Stretch) Size = GetPreferredSize(Size.Empty); Invalidate(); }
        protected override void OnMouseEnter(EventArgs e) { _hover = true; Invalidate(); base.OnMouseEnter(e); }
        protected override void OnMouseLeave(EventArgs e) { _hover = false; _down = false; Invalidate(); base.OnMouseLeave(e); }
        protected override void OnMouseDown(MouseEventArgs e) { _down = true; Invalidate(); base.OnMouseDown(e); }
        protected override void OnMouseUp(MouseEventArgs e) { _down = false; Invalidate(); base.OnMouseUp(e); }
        protected override void OnKeyDown(KeyEventArgs e) { if (e.KeyCode == Keys.Enter || e.KeyCode == Keys.Space) OnClick(EventArgs.Empty); base.OnKeyDown(e); }
        protected override void OnEnabledChanged(EventArgs e) { Invalidate(); base.OnEnabledChanged(e); }

        protected override void OnPaint(PaintEventArgs e)
        {
            var p = Kit.P; var g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.Clear(Parent?.BackColor ?? p.Surface);
            Color bg, fg, border;
            switch (Kind)
            {
                case BtnKind.Primary: bg = _hover ? p.AccentHover : p.Accent; fg = Color.White; border = bg; break;
                case BtnKind.Danger: bg = _hover ? p.Hover : p.Surface; fg = p.Danger; border = p.Border; break;
                case BtnKind.Ghost: bg = _hover ? p.Hover : (Parent?.BackColor ?? p.Surface); fg = p.AccentText; border = bg; break;
                case BtnKind.Chip: bg = Selected ? p.AccentSoft : (_hover ? p.Hover : p.Surface2); fg = Selected ? p.AccentText : p.Text; border = Selected ? p.Accent : p.Border; break;
                default: bg = _hover ? p.Hover : p.Surface; fg = p.Text; border = p.Border; break;
            }
            if (!Enabled) { fg = p.Muted; if (Kind == BtnKind.Primary) { bg = p.Border; border = bg; } }
            if (_down) bg = ControlPaint.Dark(bg, 0.02f);
            var r = new Rectangle(0, 0, Width - 1, Height - 1);
            using (var path = Kit.Round(r, Kind == BtnKind.Chip ? Height / 2 - 1 : 8))
            using (var b = new SolidBrush(bg)) using (var pen = new Pen(border))
            { g.FillPath(b, path); g.DrawPath(pen, path); }
            if (Focused && ShowFocusCues) using (var pen = new Pen(p.Accent, 1.5f) { DashStyle = DashStyle.Dot }) g.DrawPath(pen, Kit.Round(new Rectangle(2, 2, Width - 5, Height - 5), 6));
            var font = Kind == BtnKind.Chip ? Kit.F(9f, Selected) : Kit.F(9.5f, Kind == BtnKind.Primary);
            TextRenderer.DrawText(g, Text, font, r, fg, TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.RightToLeft | TextFormatFlags.EndEllipsis);
        }
    }

    public class ThemedLabel : Label
    {
        public string Role = "text";
        public ThemedLabel() { AutoSize = false; RightToLeft = RightToLeft.Yes; TextAlign = ContentAlignment.TopLeft; UseMnemonic = false; }

        public Font RoleFont => Role == "title" ? Kit.F(10.5f, true) : Role == "hint" ? Kit.F(8.5f) : Role == "big" ? Kit.F(13f, true) : Kit.F();

        public int HeightFor(int width)
        {
            Font = RoleFont;
            var s = TextRenderer.MeasureText(Text ?? " ", Font, new Size(Math.Max(10, width - 4), 0), TextFormatFlags.WordBreak | TextFormatFlags.RightToLeft);
            return s.Height + (Role == "title" ? 6 : 2);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Font = RoleFont;
            ForeColor = Role == "hint" ? Kit.P.Muted : Role == "danger" ? Kit.P.Danger : Role == "ok" ? Kit.P.Success : Kit.P.Text;
            BackColor = Parent?.BackColor ?? Kit.P.Surface;
            base.OnPaint(e);
        }
    }

    public class ThemedTextBox : TextBox
    {
        public string Placeholder = "";
        private const int EM_SETCUEBANNER = 0x1501;
        [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern IntPtr SendMessage(IntPtr h, int m, IntPtr w, string l);

        public ThemedTextBox() { BorderStyle = BorderStyle.FixedSingle; RightToLeft = RightToLeft.Yes; Font = Kit.F(10f); Height = 30; }

        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            if (!Multiline && !string.IsNullOrEmpty(Placeholder)) SendMessage(Handle, EM_SETCUEBANNER, (IntPtr)1, Placeholder);
            Restyle();
        }

        public void Restyle() { BackColor = Kit.P.Surface; ForeColor = Kit.P.Text; }
    }

    /// <summary>ردیف افقی خودشکن (راست‌به‌چپ)</summary>
    public class Row : FlowLayoutPanel
    {
        public Row() { AutoSize = true; AutoSizeMode = AutoSizeMode.GrowAndShrink; WrapContents = true; FlowDirection = FlowDirection.RightToLeft; Margin = Padding.Empty; Padding = Padding.Empty; RightToLeft = RightToLeft.No; }
        public int HeightFor(int width) { MaximumSize = new Size(width, 0); return GetPreferredSize(new Size(width, 0)).Height; }
    }

    /// <summary>چیدمان عمودی تمام‌عرض با محاسبهٔ ارتفاع متن‌های چندخطی</summary>
    public class Stack : Panel
    {
        public int Gap = 8;
        public Stack() { AutoSize = false; Padding = Padding.Empty; RightToLeft = RightToLeft.Yes; }

        public static int Measure(Control c, int w)
        {
            if (!c.Visible) return 0;
            switch (c)
            {
                case ThemedLabel l: return l.HeightFor(w);
                case Row r: return r.HeightFor(w);
                case Stack s: return s.Arrange(w);
                case Card k: return k.Arrange(w);
                case Btn b when b.Stretch: return b.Height;
                default: return c.Height;
            }
        }

        public int Arrange(int width)
        {
            var y = Padding.Top; var w = Math.Max(60, width - Padding.Horizontal);
            SuspendLayout();
            foreach (Control c in Controls.Cast<Control>().OrderBy(x => x.TabIndex))
            {
                if (!c.Visible) continue;
                var h = Measure(c, w);
                var cw = c is Btn b && !b.Stretch ? b.GetPreferredSize(Size.Empty).Width : w;
                var x = Padding.Left + (c is Btn && !((Btn)c).Stretch ? w - cw : 0);
                c.SetBounds(x, y, cw, h);
                y += h + Gap;
            }
            ResumeLayout(false);
            var total = Math.Max(0, y - Gap) + Padding.Bottom;
            Height = total;
            return total;
        }

        public T Add<T>(T c) where T : Control { c.TabIndex = Controls.Count; Controls.Add(c); return c; }
    }

    /// <summary>کارت گردگوشه با سرتیتر اختیاری</summary>
    public class Card : Panel
    {
        public readonly Stack Body = new Stack();
        public bool Soft;

        public Card(string title = null, string hint = null)
        {
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw | ControlStyles.UserPaint, true);
            Padding = new Padding(14, 12, 14, 12); RightToLeft = RightToLeft.Yes;
            Controls.Add(Body);
            if (title != null) Body.Add(Kit.Title(title));
            if (hint != null) Body.Add(Kit.Hint(hint));
        }

        public T Add<T>(T c) where T : Control => Body.Add(c);

        public int Arrange(int width)
        {
            var h = Body.Arrange(width - Padding.Horizontal);
            Body.Location = new Point(Padding.Left, Padding.Top);
            Body.Width = width - Padding.Horizontal;
            return h + Padding.Vertical;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            var p = Kit.P; var g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.Clear(Parent?.BackColor ?? p.Canvas);
            var bg = Soft ? p.Surface2 : p.Surface;
            Body.BackColor = bg;
            foreach (Control c in Body.Controls) if (!(c is TextBox) && !(c is ComboBox) && !(c is RichTextBox)) c.BackColor = bg;
            using (var path = Kit.Round(new Rectangle(0, 0, Width - 1, Height - 1), 12))
            using (var b = new SolidBrush(bg)) using (var pen = new Pen(p.Border)) { g.FillPath(b, path); g.DrawPath(pen, path); }
        }
    }

    /// <summary>صفحهٔ پیمایش‌پذیر یک زبانه</summary>
    public class Page : Panel
    {
        public readonly Stack Content = new Stack { Gap = 12 };

        public Page()
        {
            AutoScroll = true; RightToLeft = RightToLeft.Yes; Padding = new Padding(12);
            Controls.Add(Content);
            Resize += (s, e) => Relayout();
        }

        public T Add<T>(T c) where T : Control => Content.Add(c);

        public void Relayout()
        {
            if (Width <= 0) return;
            var w = ClientSize.Width - Padding.Horizontal - (VerticalScroll.Visible ? 0 : 0);
            Content.Location = new Point(Padding.Left, Padding.Top + AutoScrollPosition.Y);
            Content.Width = w;
            Content.Arrange(w);
            AutoScrollMinSize = new Size(0, Content.Height + Padding.Vertical);
            Restyle(this);
            Invalidate(true);
        }

        public static void Restyle(Control root)
        {
            root.BackColor = root is Card ? root.BackColor : Kit.P.Canvas;
            foreach (Control c in root.Controls)
            {
                switch (c)
                {
                    case ThemedTextBox t: t.Restyle(); break;
                    case RichTextBox rt: rt.BackColor = Kit.P.Surface; rt.ForeColor = Kit.P.Text; break;
                    case ComboBox cb: cb.BackColor = Kit.P.Surface; cb.ForeColor = Kit.P.Text; cb.Font = Kit.F(); break;
                    case CheckBox ck: ck.ForeColor = Kit.P.Text; ck.Font = Kit.F(); break;
                    case RadioButton rb: rb.ForeColor = Kit.P.Text; rb.Font = Kit.F(); break;
                }
                if (c is Stack || c is Row) c.BackColor = c.Parent?.BackColor ?? Kit.P.Canvas;
                if (c.HasChildren) Restyle(c);
            }
        }
    }

    /// <summary>اجرای کار در پس‌زمینه و بازگشت نتیجه به رشتهٔ رابط کاربری</summary>
    public static class Background
    {
        public static void Run<T>(Func<T> work, Action<T> done, Action<Exception> fail = null)
        {
            var ctx = SynchronizationContext.Current;
            if (!(ctx is WindowsFormsSynchronizationContext)) { try { ctx = new WindowsFormsSynchronizationContext(); } catch { ctx = null; } }
            Task.Run(() =>
            {
                try { var r = work(); Post(ctx, () => done(r)); }
                catch (Exception e) { Log.Error("bg", e); Post(ctx, () => (fail ?? (x => Toast.Show(x.Message, true)))(e)); }
            });
        }

        private static void Post(SynchronizationContext ctx, Action a)
        {
            if (ctx == null) { a(); return; }
            ctx.Post(_ => { try { a(); } catch (Exception e) { Log.Error("bg.post", e); } }, null);
        }
    }

    /// <summary>اعلان کوتاه و دوستانه در گوشهٔ صفحه</summary>
    public static class Toast
    {
        public static Action<string, bool> Sink;   // اگر پنل باز است، اعلان در خود پنل نشان داده می‌شود

        public static void Show(string msg, bool error = false)
        {
            try
            {
                if (Sink != null) { Sink(msg, error); return; }
                var f = new Form
                {
                    FormBorderStyle = FormBorderStyle.None, ShowInTaskbar = false, TopMost = true, StartPosition = FormStartPosition.Manual,
                    BackColor = error ? Kit.P.Danger : Kit.P.Accent, Size = new Size(360, 64), Opacity = 0.96, RightToLeft = RightToLeft.Yes,
                };
                var l = new Label { Dock = DockStyle.Fill, Text = msg, ForeColor = Color.White, Font = Kit.F(10f), TextAlign = ContentAlignment.MiddleCenter, Padding = new Padding(10) };
                f.Controls.Add(l);
                var wa = Screen.PrimaryScreen.WorkingArea;
                f.Location = new Point(wa.Left + 24, wa.Bottom - f.Height - 24);
                var t = new System.Windows.Forms.Timer { Interval = 4200 };
                t.Tick += (s, e) => { t.Stop(); f.Close(); };
                l.Click += (s, e) => f.Close();
                f.Show(); t.Start();
            }
            catch (Exception e) { Log.Error("toast", e); }
        }
    }
}
