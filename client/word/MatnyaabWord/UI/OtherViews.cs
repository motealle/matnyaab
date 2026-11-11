using System;
using System.Collections.Generic;
using System.Drawing;
using System.Globalization;
using System.Linq;
using System.Windows.Forms;
using Matnyaab.Word.Core;
using Matnyaab.Word.Ops;

namespace Matnyaab.Word.UI
{
    // ==================================================================== قالب‌ها

    public class TemplatesView : Page
    {
        private string _cat = "thesis";
        private readonly Row _cats = new Row();
        private readonly Stack _list = new Stack { Gap = 10 };
        private readonly CheckBox _detect = Kit.Check("پیش از اعمال، سرفصل‌ها خودکار تشخیص داده شوند", true);

        public TemplatesView()
        {
            Add(Kit.Hint("قالب را یک‌جا روی سند جاری اعمال کنید: اندازهٔ صفحه، حاشیه‌ها، قلم‌ها، عنوان‌ها، پانویس و نقل‌قول. هر قالب را می‌توانید تکثیر و دلخواه کنید؛ همه با یک «واگرد» برمی‌گردد."));
            Add(_cats);
            Add(_detect);
            Add(Kit.Row(
                Kit.Button("+ قالب تازه", () => Edit(new DocTemplate { Name = "قالب من", Category = _cat == "custom" ? "custom" : _cat }), BtnKind.Primary),
                Kit.Button("↶ واگرد", WordOps.UndoLast, BtnKind.Ghost),
                Kit.Button("بازگردانی قالب‌های آماده", () => { TemplateStore.RestoreBuiltIns(); Fill(); }, BtnKind.Ghost)));
            Add(_list);
            Fill();
        }

        private void Fill()
        {
            _cats.Controls.Clear();
            foreach (var c in DocTemplate.Categories)
            {
                var b = Kit.Button(c.Value, () => { _cat = c.Key; Fill(); }, BtnKind.Chip);
                b.Selected = c.Key == _cat;
                _cats.Controls.Add(b);
            }
            foreach (Control c in _list.Controls.Cast<Control>().ToList()) c.Dispose();
            _list.Controls.Clear();
            var items = TemplateStore.All.Where(t => _cat == "custom" ? !t.BuiltIn : t.Category == _cat).ToList();
            if (items.Count == 0) _list.Add(Kit.Hint(_cat == "custom" ? "هنوز قالبی نساخته‌اید؛ «+ قالب تازه» یا «تکثیر» یک قالب آماده." : "قالبی در این دسته نیست."));
            foreach (var t in items) _list.Add(CardFor(t));
            Relayout();
        }

        private Card CardFor(DocTemplate t)
        {
            var card = new Card(t.Name, t.Publisher + (t.BuiltIn ? " · آماده" : " · ساختهٔ شما"));
            card.Add(Kit.Text(t.Summary()));
            if (!string.IsNullOrWhiteSpace(t.Note)) card.Add(Kit.Hint(t.Note));
            var missing = new[] { t.FontFa, t.HeadingFontFa, t.FontEn }.Distinct().Where(f => !Kit.FontInstalled(f)).ToList();
            if (missing.Count > 0) card.Add(new ThemedLabel { Role = "danger", Text = "قلم نصب نیست: " + string.Join("، ", missing) + " — ورد موقتاً قلم جایگزین نشان می‌دهد." });
            card.Add(Kit.Row(
                Kit.Button("اعمال روی سند", () => Apply(t), BtnKind.Primary),
                Kit.Button("تکثیر", () => { var c = TemplateStore.Duplicate(t); _cat = "custom"; Fill(); Edit(c); }),
                Kit.Button("ویرایش", () => Edit(t.BuiltIn ? TemplateStore.Duplicate(t) : t)),
                Kit.Button("حذف", () =>
                {
                    if (MessageBox.Show("قالب «" + t.Name + "» حذف شود؟" + (t.BuiltIn ? "\n(قالب آماده با «بازگردانی» برمی‌گردد)" : ""), "متن‌یاب", MessageBoxButtons.YesNo, MessageBoxIcon.Question, MessageBoxDefaultButton.Button2, MessageBoxOptions.RtlReading | MessageBoxOptions.RightAlign) == DialogResult.Yes)
                    { TemplateStore.Delete(t); Fill(); }
                }, BtnKind.Danger)));
            return card;
        }

        private void Apply(DocTemplate t)
        {
            if (!WordOps.Ready) { Toast.Show("یک سند ورد باز کنید."); return; }
            if (_detect.Checked)
            {
                var h = WordOps.ScanHeadings().Where(x => x.Checked).ToList();
                if (h.Count > 0) WordOps.ApplyHeadings(h);
            }
            WordOps.ApplyTemplate(t);
            Toast.Show("قالب «" + t.Name + "» اعمال شد ✓  (Ctrl+Z برای بازگشت)");
        }

        private void Edit(DocTemplate t)
        {
            if (TemplateEditor.Show(t)) { TemplateStore.Upsert(t); _cat = t.Category; Fill(); }
            else Fill();
        }
    }

    /// <summary>ویرایشگر قالب (فرم دوستانه با گروه‌بندی)</summary>
    public static class TemplateEditor
    {
        public static bool Show(DocTemplate t)
        {
            using (var f = new Form
            {
                Text = "ویرایش قالب", RightToLeft = RightToLeft.Yes, RightToLeftLayout = true, StartPosition = FormStartPosition.CenterScreen,
                ClientSize = new Size(480, 640), BackColor = Kit.P.Canvas, Font = Kit.F(), MinimizeBox = false,
            })
            {
                var page = new Page { Dock = DockStyle.Fill };
                var apply = new List<Action>();
                TextBox Txt(Card c, string label, string val) { c.Add(Kit.Hint(label)); var tb = Kit.Input(); tb.Text = val; c.Add(tb); return tb; }
                Action<double> NumField(Card c, string label, double val, Action<double> set)
                {
                    var tb = Txt(c, label, Fa.Num(val.ToString(CultureInfo.InvariantCulture)));
                    apply.Add(() => { if (double.TryParse(Fa.Latin(tb.Text).Replace('٫', '.').Replace('/', '.'), NumberStyles.Any, CultureInfo.InvariantCulture, out var d)) set(d); });
                    return set;
                }

                var g = new Card("شناسه");
                var name = Txt(g, "نام قالب", t.Name);
                var pub = Txt(g, "ناشر / دانشگاه", t.Publisher);
                g.Add(Kit.Hint("دسته"));
                var cats = DocTemplate.Categories.Keys.ToList();
                var cat = Kit.Combo(DocTemplate.Categories.Values, Math.Max(0, cats.IndexOf(t.Category))); g.Add(cat);
                var rtl = Kit.Check("سند راست‌به‌چپ (فارسی)", t.Rtl); g.Add(rtl);
                var note = Txt(g, "یادداشت", t.Note);
                page.Add(g);

                var pg = new Card("صفحه (سانتی‌متر)");
                pg.Add(Kit.Hint("اندازهٔ کاغذ"));
                var papers = DocTemplate.Papers.Keys.ToList();
                var paper = Kit.Combo(papers, Math.Max(0, papers.IndexOf(t.Paper))); pg.Add(paper);
                NumField(pg, "حاشیهٔ بالا", t.MarginTop, v => t.MarginTop = v);
                NumField(pg, "حاشیهٔ پایین", t.MarginBottom, v => t.MarginBottom = v);
                NumField(pg, "حاشیهٔ راست", t.MarginRight, v => t.MarginRight = v);
                NumField(pg, "حاشیهٔ چپ", t.MarginLeft, v => t.MarginLeft = v);
                NumField(pg, "حاشیهٔ صحافی", t.Gutter, v => t.Gutter = v);
                NumField(pg, "تعداد ستون", t.Columns, v => t.Columns = (int)Math.Round(v));
                pg.Add(Kit.Hint("شمارهٔ صفحه"));
                var pnIds = new[] { "bottom-center", "bottom-outside", "top-center", "none" };
                var pn = Kit.Combo(new[] { "پایین وسط", "پایین بیرونی", "بالا وسط", "بدون شماره" }, Math.Max(0, Array.IndexOf(pnIds, t.PageNumbers))); pg.Add(pn);
                page.Add(pg);

                var tx = new Card("متن");
                var fontFa = Txt(tx, "قلم فارسی", t.FontFa);
                var fontEn = Txt(tx, "قلم لاتین", t.FontEn);
                NumField(tx, "اندازهٔ قلم متن (pt)", t.BodySize, v => t.BodySize = v);
                NumField(tx, "اندازهٔ قلم لاتین (pt)", t.LatinSize, v => t.LatinSize = v);
                NumField(tx, "فاصلهٔ سطرها (ضریب)", t.LineSpacing, v => t.LineSpacing = v);
                NumField(tx, "تورفتگی سطر نخست (cm)", t.FirstIndent, v => t.FirstIndent = v);
                NumField(tx, "فاصله پس از بند (pt)", t.SpaceAfter, v => t.SpaceAfter = v);
                var just = Kit.Check("تراز دوطرفه", t.Justify); tx.Add(just);
                page.Add(tx);

                var hd = new Card("عنوان‌ها و عناصر");
                var hf = Txt(hd, "قلم عنوان‌ها", t.HeadingFontFa);
                NumField(hd, "عنوان ۱ (pt)", t.H1Size, v => t.H1Size = v);
                NumField(hd, "عنوان ۲ (pt)", t.H2Size, v => t.H2Size = v);
                NumField(hd, "عنوان ۳ (pt)", t.H3Size, v => t.H3Size = v);
                var h1c = Kit.Check("عنوان ۱ وسط‌چین", t.H1Center); hd.Add(h1c);
                var h1b = Kit.Check("هر عنوان ۱ از صفحهٔ تازه", t.H1PageBreak); hd.Add(h1b);
                NumField(hd, "عنوان سند (pt)", t.TitleSize, v => t.TitleSize = v);
                NumField(hd, "پانویس (pt)", t.FootnoteSize, v => t.FootnoteSize = v);
                NumField(hd, "زیرنویس شکل و جدول (pt)", t.CaptionSize, v => t.CaptionSize = v);
                NumField(hd, "نقل‌قول بلوکی (pt)", t.QuoteSize, v => t.QuoteSize = v);
                NumField(hd, "تورفتگی نقل‌قول (cm)", t.QuoteIndent, v => t.QuoteIndent = v);
                page.Add(hd);

                var ok = Kit.Button("ذخیرهٔ قالب", () => f.DialogResult = DialogResult.OK, BtnKind.Primary);
                page.Add(Kit.Row(ok, Kit.Button("انصراف", () => f.DialogResult = DialogResult.Cancel, BtnKind.Ghost)));
                f.Controls.Add(page);
                f.Shown += (s, e) => page.Relayout();
                if (f.ShowDialog() != DialogResult.OK) return false;
                t.Name = name.Text.Trim().Length > 0 ? name.Text.Trim() : "قالب من";
                t.Publisher = pub.Text.Trim(); t.Note = note.Text.Trim(); t.Rtl = rtl.Checked;
                t.Category = cats[Math.Max(0, cat.SelectedIndex)];
                t.Paper = papers[Math.Max(0, paper.SelectedIndex)];
                t.PageW = DocTemplate.Papers[t.Paper][0]; t.PageH = DocTemplate.Papers[t.Paper][1];
                t.PageNumbers = pnIds[Math.Max(0, pn.SelectedIndex)];
                t.FontFa = fontFa.Text.Trim(); t.FontEn = fontEn.Text.Trim(); t.HeadingFontFa = hf.Text.Trim();
                t.Justify = just.Checked; t.H1Center = h1c.Checked; t.H1PageBreak = h1b.Checked;
                apply.ForEach(a => a());
                t.BuiltIn = false;
                return true;
            }
        }
    }

    // ==================================================================== استناد

    public class CiteView : Page
    {
        private readonly Stack _list = new Stack { Gap = 6 };

        public CiteView()
        {
            var s = AddinSettings.Current;
            var how = new Card("درج با منبع", "در نتایج جست‌وجو «درج با منبع» را بزنید: نقل‌قول درون‌خطی (یا بلوکی برای متن‌های بلند)، ارجاع درون‌متنی APA و کامنتی با مشخصات کامل منبع و مسیر فایل افزوده می‌شود.");
            how.Add(Kit.Check("کامنت منبع کنار متن درج‌شده", s.InsertWithComment, v => { s.InsertWithComment = v; AddinSettings.Save(false); }));
            how.Add(Kit.Check("ارجاع درون‌متنی APA پس از نقل‌قول", s.InsertCitation, v => { s.InsertCitation = v; AddinSettings.Save(false); }));
            how.Add(Kit.Hint("زبان ارجاع"));
            var langs = new[] { "auto", "fa", "en" };
            var lang = Kit.Combo(new[] { "خودکار (بر پایهٔ متن)", "فارسی: (رحیمی، ۱۴۰۲، ص. ۵)", "English: (Smith, 2020, p. 5)" }, Math.Max(0, Array.IndexOf(langs, s.CitationLang)));
            lang.SelectedIndexChanged += (o, e) => { s.CitationLang = langs[lang.SelectedIndex]; AddinSettings.Save(false); };
            how.Add(lang);
            Add(how);

            var bib = new Card("منبع‌های این سند", "از روی درج‌های متن‌یاب جمع می‌شود؛ بخش «منابع» پایان سند را می‌سازد و به‌روز نگه می‌دارد.");
            bib.Add(Kit.Row(
                Kit.Button("ساخت / به‌روزرسانی فهرست منابع", () => { var n = WordOps.UpdateBibliography(); Toast.Show(n == 0 ? "هنوز منبعی ثبت نشده است." : "فهرست منابع با " + Fa.Num(n) + " منبع به‌روز شد ✓"); }, BtnKind.Primary),
                Kit.Button("تازه‌سازی", Fill, BtnKind.Ghost)));
            bib.Add(_list);
            Add(bib);

            var manual = new Card("ارجاع دستی", "برای منبعی که در متن‌یاب نیست: مشخصات را وارد کنید تا ارجاع درون‌متنی در جای مکان‌نما درج و منبع به فهرست افزوده شود.");
            manual.Add(Kit.Button("+ منبع و ارجاع تازه", () =>
            {
                if (!WordOps.Ready) { Toast.Show("یک سند ورد باز کنید."); return; }
                var r = SourceDialog.Edit("manual:" + Guid.NewGuid().ToString("N").Substring(0, 6), true);
                if (r == null) return;
                WordOps.App.Selection.TypeText(Apa.InText(r.Item1, r.Item2, AddinSettings.Current.CitationLang));
                WordOps.RegisterSource(r.Item1); Fill();
            }));
            Add(manual);
            Fill();
        }

        private void Fill()
        {
            foreach (Control c in _list.Controls.Cast<Control>().ToList()) c.Dispose();
            _list.Controls.Clear();
            var src = WordOps.Ready ? WordOps.DocSources() : new List<SourceInfo>();
            if (src.Count == 0) _list.Add(Kit.Hint("هنوز منبعی در این سند ثبت نشده است."));
            foreach (var s in src) _list.Add(Kit.Text("• " + Apa.Reference(s).Replace("*", "")));
            Relayout();
        }
    }

    // ==================================================================== دستیار هوشمند

    public class AiView : Page
    {
        private readonly PaneView _pane;
        private readonly ComboBox _provider = Kit.Combo(new string[0]);
        private readonly TextBox _instruction = Kit.Input("دستور دلخواه؛ مثلاً: این بند را برای چکیدهٔ مقاله فشرده کن", true, 2);
        private readonly TextBox _result = Kit.Input("", true, 8);
        private readonly Label _state = Kit.Hint("");
        private dynamic _range;

        public AiView(PaneView pane)
        {
            _pane = pane;
            var s = AddinSettings.Current;
            if (s.AiProviders.Count == 0)
            {
                var setup = new Card("دستیار هوشمند نگارش", "بندهای خود را با مدل دلخواه روان‌تر، علمی‌تر یا کوتاه‌تر کنید. کلید API را خودتان اضافه می‌کنید و فقط روی همین رایانه (رمزنگاری‌شده) می‌ماند؛ متن مستقیم از رایانهٔ شما به سرویس انتخابی می‌رود.");
                setup.Add(Kit.Button("افزودن سرویس و کلید API", () => _pane.Open("settings"), BtnKind.Primary));
                Add(setup);
                return;
            }
            var top = new Card("مدل");
            foreach (var p in s.AiProviders) _provider.Items.Add(p.Title + " · " + p.Model);
            _provider.SelectedIndex = Math.Max(0, s.AiProviders.FindIndex(p => p.Id == s.AiProviderId));
            _provider.SelectedIndexChanged += (o, e) => { s.AiProviderId = s.AiProviders[_provider.SelectedIndex].Id; AddinSettings.Save(false); };
            top.Add(_provider);
            Add(top);

            var ask = new Card("چه کنم؟", "بند جاری (یا متن انتخاب‌شده) با این دستور بازنویسی می‌شود؛ پیش از جایگزینی، نتیجه را می‌بینید و ویرایش می‌کنید.");
            var chips = new Row();
            foreach (var pr in Ai.Presets.Concat(s.AiPrompts))
                chips.Controls.Add(Kit.Button(pr, () => Run(pr), BtnKind.Chip));
            ask.Add(chips);
            ask.Add(_instruction);
            ask.Add(Kit.Row(
                Kit.Button("اجرا با این دستور", () => Run(_instruction.Text.Trim()), BtnKind.Primary),
                Kit.Button("ذخیرهٔ دستور", () => { var t = _instruction.Text.Trim(); if (t.Length > 0 && !s.AiPrompts.Contains(t)) { s.AiPrompts.Add(t); AddinSettings.Save(false); _pane.Rebuild(); _pane.Open("ai"); } }, BtnKind.Ghost)));
            Add(ask);

            var res = new Card("نتیجه");
            res.Add(_state);
            res.Add(_result);
            res.Add(Kit.Check("جایگزینی با «پیگیری تغییرات» (Track Changes)", s.TrackChangesForAi, v => { s.TrackChangesForAi = v; AddinSettings.Save(false); }));
            res.Add(Kit.Row(
                Kit.Button("جایگزین کن", () => { if (_range == null || _result.Text.Trim().Length == 0) return; WordOps.ReplaceBlock(_range, _result.Text.Trim(), s.TrackChangesForAi); Toast.Show("جایگزین شد ✓ (Ctrl+Z برای بازگشت)"); }, BtnKind.Primary),
                Kit.Button("درج زیر بند", () => { if (_range != null) WordOps.InsertBelow(_range, _result.Text.Trim()); }),
                Kit.Button("کپی", () => { if (_result.Text.Length > 0) Clipboard.SetText(_result.Text); }, BtnKind.Ghost)));
            Add(res);
        }

        private void Run(string instruction)
        {
            var s = AddinSettings.Current;
            if (string.IsNullOrWhiteSpace(instruction)) { Toast.Show("یک دستور بنویسید یا یکی از پیشنهادها را بزنید."); return; }
            if (!WordOps.Ready) { Toast.Show("یک سند ورد باز کنید."); return; }
            _range = WordOps.CurrentBlock(out var text);
            if (string.IsNullOrWhiteSpace(text)) { Toast.Show("مکان‌نما را در بندی که می‌خواهید بهتر شود بگذارید."); return; }
            var p = s.AiProviders.ElementAtOrDefault(Math.Max(0, _provider.SelectedIndex));
            _state.Text = "در حال نوشتن… (" + p?.Title + ")"; _result.Text = ""; Relayout();
            Background.Run(() => Ai.Complete(p, instruction, text), r => { _result.Text = r; _state.Text = "آماده است؛ اگر لازم است ویرایش کنید و سپس «جایگزین کن»."; Relayout(); },
                e => { _state.Text = e.Message; Relayout(); });
        }
    }

    // ==================================================================== تنظیمات

    public class SettingsView : Page
    {
        private readonly PaneView _pane;

        public SettingsView(PaneView pane)
        {
            _pane = pane;
            var s = AddinSettings.Current;
            void Save() { AddinSettings.Save(); }

            var look = new Card("نمایش");
            look.Add(Kit.Hint("جای پنل"));
            var pos = new[] { "right", "left", "float" };
            var posC = Kit.Combo(new[] { "چسبیده به راست", "چسبیده به چپ", "شناور" }, Math.Max(0, Array.IndexOf(pos, s.PanePosition)));
            posC.SelectedIndexChanged += (o, e) => { s.PanePosition = pos[posC.SelectedIndex]; Save(); Com.Connect.Instance?.ApplyDockAll(); };
            look.Add(posC);
            look.Add(Kit.Check("پنل با بازشدن ورد باز شود", s.OpenPaneOnStart, v => { s.OpenPaneOnStart = v; Save(); }));
            look.Add(Kit.Hint("پوسته"));
            var themes = new[] { "auto" }.Concat(Palette.All.Select(p => p.Id)).ToList();
            var th = Kit.Combo(new[] { "هماهنگ با برنامهٔ متن‌یاب" }.Concat(Palette.All.Select(p => p.Title)), Math.Max(0, themes.IndexOf(s.Theme)));
            th.SelectedIndexChanged += (o, e) => { s.Theme = themes[th.SelectedIndex]; Save(); };
            look.Add(th);
            look.Add(Kit.Hint("قلم پنل"));
            var fonts = Kit.FontChoices().ToList();
            var fc = Kit.Combo(fonts, Math.Max(0, fonts.IndexOf(s.Font)));
            fc.SelectedIndexChanged += (o, e) => { s.Font = fonts[fc.SelectedIndex]; Save(); Kit.Notify(); };
            look.Add(fc);
            Add(look);

            var feat = new Card("قابلیت‌ها", "هر بخشی را نمی‌خواهید خاموش کنید؛ از نوار ابزار و پنل پنهان می‌شود.");
            feat.Add(Kit.Check("جست‌وجو در متن‌یاب", s.ShowSearch, v => { s.ShowSearch = v; Save(); }));
            feat.Add(Kit.Check("استناد و فهرست منابع", s.ShowCite, v => { s.ShowCite = v; Save(); }));
            feat.Add(Kit.Check("اصطلاحات لاتین ↔ پانویس", s.ShowFootnotes, v => { s.ShowFootnotes = v; Save(); }));
            feat.Add(Kit.Check("سرفصل‌ها و فهرست مطالب", s.ShowStructure, v => { s.ShowStructure = v; Save(); }));
            feat.Add(Kit.Check("یکدست‌سازی متن فارسی", s.ShowCleanup, v => { s.ShowCleanup = v; Save(); }));
            feat.Add(Kit.Check("قالب‌های صفحه‌آرایی", s.ShowTemplates, v => { s.ShowTemplates = v; Save(); }));
            feat.Add(Kit.Check("دستیار هوشمند", s.ShowAi, v => { s.ShowAi = v; Save(); }));
            Add(feat);

            var ins = new Card("درج نقل‌قول");
            ins.Add(Kit.Hint("نقل‌قول بلوکی از چند واژه به بالا (APA: ۴۰)"));
            var bq = Kit.Input(); bq.Text = Fa.Num(s.BlockQuoteWords);
            bq.Leave += (o, e) => { if (int.TryParse(Fa.Latin(bq.Text), out var n) && n > 5) { s.BlockQuoteWords = n; AddinSettings.Save(false); } };
            ins.Add(bq);
            Add(ins);

            var link = new Card("پیوند با متن‌یاب", Bridge.Installed ? "متن‌یاب نصب است؛ تنظیمات پوسته، مجموعه‌ها و یادداشت‌ها مشترک‌اند." : "متن‌یاب نصب نیست؛ با نصب آن جست‌وجو در اسناد خودتان فعال می‌شود.");
            link.Add(Kit.Check("استفاده از متن‌یاب (و راه‌اندازی پنهان آن در صورت نیاز)", s.UseMatnyaab, v => { s.UseMatnyaab = v; Save(); }));
            link.Add(Kit.Row(
                Bridge.Installed ? Kit.Button("بازکردن متن‌یاب", () => Bridge.ShowApp(null)) : Kit.Button("دریافت متن‌یاب", () => System.Diagnostics.Process.Start(Updater.Site + "/#download"), BtnKind.Primary),
                Kit.Button("بررسی اتصال", _pane.CheckConnection, BtnKind.Ghost)));
            Add(link);

            Add(AiCard(s));

            var up = new Card("به‌روزرسانی", "نسخهٔ فعلی: " + Fa.Num(Updater.CurrentVersion));
            up.Add(Kit.Check("بررسی خودکار نسخهٔ تازه (روزی یک بار)", s.AutoUpdate, v => { s.AutoUpdate = v; Save(); }));
            var upState = Kit.Hint("");
            up.Add(upState);
            up.Add(Kit.Row(Kit.Button("بررسی نسخهٔ تازه", () =>
            {
                upState.Text = "در حال بررسی…"; Relayout();
                Background.Run(Updater.Check, r =>
                {
                    if (r == null) { upState.Text = "شما تازه‌ترین نسخه را دارید ✓"; Relayout(); return; }
                    upState.Text = "نسخهٔ " + Fa.Num(r.Version) + " آماده است. " + r.Notes; Relayout();
                    if (MessageBox.Show("نسخهٔ تازهٔ افزونه (" + r.Version + ") نصب شود؟\nپس از نصب، یک بار ورد را ببندید و باز کنید.", "متن‌یاب", MessageBoxButtons.YesNo, MessageBoxIcon.Information, MessageBoxDefaultButton.Button1, MessageBoxOptions.RtlReading | MessageBoxOptions.RightAlign) == DialogResult.Yes)
                        Background.Run(() => { Updater.Install(r); return 0; }, _ => Toast.Show("نصب‌کننده اجرا شد؛ پس از پایان، ورد را دوباره باز کنید."));
                });
            }), Kit.Button("گزارش خطاها", () => { try { System.Diagnostics.Process.Start("notepad.exe", Paths.LogFile); } catch { } }, BtnKind.Ghost)));
            Add(up);
        }

        private Card AiCard(AddinSettings s)
        {
            var card = new Card("سرویس‌های هوش مصنوعی", "هر سرویس سازگار را اضافه کنید: OpenAI، Claude، Gemini، OpenRouter یا هر درگاه سازگار با OpenAI (حتی مدل محلی). کلیدها با رمزنگاری ویندوز فقط برای حساب شما ذخیره می‌شوند.");
            foreach (var p in s.AiProviders.ToList())
                card.Add(Kit.Row(Kit.Button("حذف", () => { s.AiProviders.Remove(p); AddinSettings.Save(); _pane.Rebuild(); _pane.Open("settings"); }, BtnKind.Danger), new ThemedLabel { Text = "• " + p.Title + " · " + p.Model, Role = "text", Width = 250, Height = 30 }));
            var preset = Kit.Combo(Ai.Catalog.Select(c => c.Title));
            var url = Kit.Input("نشانی پایهٔ API");
            var model = Kit.Combo(new string[0], -1, true);
            var key = Kit.Input("کلید API"); key.UseSystemPasswordChar = true;
            var hint = Kit.Hint("");
            void Pick()
            {
                var c = Ai.Catalog.ElementAtOrDefault(preset.SelectedIndex);
                if (c == null) return;
                url.Text = c.BaseUrl; model.Items.Clear(); model.Items.AddRange(c.Models.Cast<object>().ToArray());
                model.Text = c.Models.FirstOrDefault() ?? ""; hint.Text = c.Hint; Relayout();
            }
            preset.SelectedIndexChanged += (o, e) => Pick();
            card.Add(Kit.Hint("سرویس")); card.Add(preset); card.Add(hint);
            card.Add(url); card.Add(Kit.Hint("مدل")); card.Add(model); card.Add(key);
            var test = Kit.Hint("");
            AiProvider Make()
            {
                var c = Ai.Catalog.ElementAtOrDefault(preset.SelectedIndex) ?? Ai.Catalog.Last();
                var p = new AiProvider { Title = c.Title, Kind = c.Kind, BaseUrl = url.Text.Trim(), Model = model.Text.Trim() };
                p.Key = key.Text.Trim();
                return p;
            }
            card.Add(Kit.Row(
                Kit.Button("افزودن", () =>
                {
                    var p = Make();
                    if (p.Model.Length == 0) { Toast.Show("نام مدل را بنویسید.", true); return; }
                    s.AiProviders.Add(p); s.AiProviderId = p.Id; AddinSettings.Save(); _pane.Rebuild(); _pane.Open("ai");
                }, BtnKind.Primary),
                Kit.Button("آزمایش اتصال", () =>
                {
                    test.Text = "در حال آزمایش…"; Relayout();
                    var p = Make();
                    Background.Run(() => Ai.Complete(p, "Reply with: OK", "سلام", 30000), r => { test.Text = "اتصال برقرار است ✓ " + (r.Length > 40 ? r.Substring(0, 40) : r); Relayout(); }, e => { test.Text = e.Message; Relayout(); });
                }, BtnKind.Ghost)));
            card.Add(test);
            preset.SelectedIndex = 0;
            return card;
        }
    }
}
