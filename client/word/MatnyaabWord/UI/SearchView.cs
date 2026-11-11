using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;
using Matnyaab.Word.Core;
using Matnyaab.Word.Ops;

namespace Matnyaab.Word.UI
{
    /// <summary>زبانهٔ جست‌وجو در کتابخانهٔ متن‌یاب و درج نتیجه با منبع</summary>
    public class SearchView : Page
    {
        private readonly PaneView _pane;
        private string _mode = "phrase";   // phrase | phrases | bool
        private readonly Btn _mPhrase, _mPhrases, _mBool;
        private readonly TextBox _input = Kit.Input("واژه یا عبارت؛ مثلاً: تهذیب نفس");
        private readonly TextBox _inputMulti = Kit.Input("", true, 4);
        private readonly CheckBox _strict, _punct, _syn, _sem;
        private readonly ComboBox _match = Kit.Combo(new[] { "واژهٔ کامل", "بخشی از واژه (مجاهد ← مجاهدت)", "هم‌خانواده (علم ← عالم، تعلیم، معلم)" });
        private readonly ComboBox _spacing = Kit.Combo(new[] { "یکسان‌سازی فاصله و نیم‌فاصله", "نادیده‌گرفتن فاصله‌های اضافه", "فاصله‌گذاری دقیق" });
        private readonly Card _opts = new Card { Soft = true };
        private readonly Label _summary = Kit.Hint("");
        private static readonly string[] SortIds = { "relevance", "count", "density", "newest", "oldest", "name" };
        private readonly ComboBox _sort = Kit.Combo(new[] { "مرتبط‌ترین", "بیشترین تکرار", "بیشترین تراکم", "جدیدترین فایل", "قدیمی‌ترین فایل", "نام فایل" });
        private readonly Stack _results = new Stack { Gap = 10 };
        private readonly Card _offline = new Card("متن‌یاب روی این رایانه نصب نیست",
            "برای جست‌وجو در هزاران سند خودتان، متن‌یاب را نصب کنید. ابزارهای نگارش، قالب‌ها و استناد بدون آن هم کار می‌کنند.");
        private List<string> _phrases = new List<string>();

        public SearchView(PaneView pane)
        {
            _pane = pane;
            _mPhrase = Chip("واژه / عبارت", "phrase"); _mPhrases = Chip("چند عبارت", "phrases"); _mBool = Chip("منطقی", "bool");
            Add(Kit.Row(_mPhrase, _mPhrases, _mBool));
            _input.KeyDown += (s, e) => { if (e.KeyCode == Keys.Enter) { e.SuppressKeyPress = true; Run(null); } };
            _inputMulti.KeyDown += (s, e) => { if (e.KeyCode == Keys.Enter && e.Control) { e.SuppressKeyPress = true; Run(null); } };
            Add(_input); Add(_inputMulti);

            _strict = Kit.Check("جست‌وجوی دقیق (همان شکل، ترتیب و فاصله)", false, v => SyncOpts());
            _syn = Kit.Check("مترادف‌ها هم جست‌وجو شوند (خودسازی ← تزکیه نفس…)");
            _sem = Kit.Check("جست‌وجوی مفهومی (اگر در متن‌یاب فعال است)", true);
            _punct = Kit.Check("بی‌اعتنا به علائم نگارشی میان واژه‌ها", true);
            _opts.Add(Kit.Hint("شیوهٔ تطبیق"));
            _opts.Add(_strict); _opts.Add(_match); _opts.Add(_spacing); _opts.Add(_punct); _opts.Add(_syn); _opts.Add(_sem);
            Add(_opts);

            var go = Kit.Button("جست‌وجو در متن‌یاب", () => Run(null), BtnKind.Primary);
            go.Stretch = true;
            Add(go);
            Add(Kit.Row(
                Kit.Button("بازکردن در برنامهٔ متن‌یاب", () => Bridge.ShowApp(CurrentQuery()), BtnKind.Ghost),
                Kit.Button("جست‌وجوی متن انتخاب‌شده", () => Run(WordOps.SelectionText()), BtnKind.Ghost)));
            Add(_summary);
            _sort.SelectedIndexChanged += (s, e) => { if (_results.Controls.Count > 0) Run(null); };
            Add(_sort);

            _offline.Add(Kit.Row(Kit.Button("دریافت متن‌یاب", () => System.Diagnostics.Process.Start(Updater.Site + "/#download"), BtnKind.Primary)));
            _offline.Visible = !Bridge.Installed;
            Add(_offline);
            Add(_results);

            var link = new Card("پیوند دوطرفه با متن‌یاب", "آنچه در ورد می‌نویسید هم جست‌وجوپذیر شود.");
            link.Add(Kit.Row(
                Kit.Button("ذخیرهٔ انتخاب در یادداشت‌ها", SaveNote, BtnKind.Secondary, "متن انتخاب‌شده در «یادداشت‌های پژوهشی» متن‌یاب ذخیره و فوراً فهرست می‌شود."),
                Kit.Button("افزودن پوشهٔ این سند", AddFolder, BtnKind.Secondary, "پوشهٔ سند جاری به مجموعه‌های متن‌یاب افزوده می‌شود.")));
            link.Visible = Bridge.Installed;
            Add(link);
            SetMode("phrase");
        }

        private Btn Chip(string t, string mode) { var b = Kit.Button(t, () => SetMode(mode), BtnKind.Chip); return b; }

        private void SetMode(string m)
        {
            _mode = m;
            _mPhrase.Selected = m == "phrase"; _mPhrases.Selected = m == "phrases"; _mBool.Selected = m == "bool";
            foreach (var b in new[] { _mPhrase, _mPhrases, _mBool }) b.Invalidate();
            _input.Visible = m != "phrases"; _inputMulti.Visible = m == "phrases";
            ((ThemedTextBox)_input).Placeholder = m == "bool" ? "(جهاد OR مبارزه) AND نفس NOT نظامی · جهاد NEAR/5 نفس" : "واژه یا عبارت؛ مثلاً: تهذیب نفس";
            _input.RecreateHandleSafe();
            Relayout();
        }

        private void SyncOpts() { _match.Enabled = _spacing.Enabled = _punct.Enabled = _syn.Enabled = !_strict.Checked; }

        private string CurrentQuery() => _mode == "phrases" ? _inputMulti.Text.Trim() : _input.Text.Trim();

        public void Run(string query)
        {
            if (query != null)
            {
                if (query.Contains("\n")) { SetMode("phrases"); _inputMulti.Text = query.Replace("\r", "").Replace("\n", "\r\n"); }
                else { if (_mode == "phrases") SetMode("phrase"); _input.Text = query; }
            }
            var q = CurrentQuery();
            if (q.Length == 0) { Toast.Show("واژه یا عبارتی بنویسید 🙂"); return; }
            if (!Bridge.Installed) { _offline.Visible = true; Relayout(); return; }
            AddinSettings.Current.Remember(q); AddinSettings.Save(false);
            var body = new Dictionary<string, object>
            {
                ["strict"] = _strict.Checked, ["match"] = _match.SelectedIndex == 1 ? "partial" : _match.SelectedIndex == 2 ? "family" : "whole", ["synonyms"] = _syn.Checked, ["semantic"] = _sem.Checked,
                ["spacing"] = _spacing.SelectedIndex == 1 ? "collapse" : _spacing.SelectedIndex == 2 ? "exact" : "unify",
                ["ignorePunct"] = _punct.Checked, ["sort"] = SortIds[Math.Max(0, _sort.SelectedIndex)], ["limit"] = 60, ["snippets"] = 5,
            };
            if (_mode == "bool") body["expression"] = q;
            else if (_mode == "phrases") { body["phrases"] = q.Split('\n').Select(x => x.Trim()).Where(x => x.Length > 0).ToArray(); body["combine"] = "any"; }
            else body["phrases"] = new[] { q };

            _summary.Text = "در حال جست‌وجو…"; Relayout();
            _pane.SetStatus("در حال جست‌وجو در متن‌یاب…", "wait");
            Background.Run(() =>
            {
                var ok = Bridge.Ensure();
                if (ok == null) return null;
                return Bridge.Call("search", body, 60000);
            }, res =>
            {
                if (res == null) { _summary.Text = "اتصال به متن‌یاب برقرار نشد. " + Bridge.LastError; _pane.SetStatus("اتصال برقرار نشد", "err"); Relayout(); return; }
                if (!res.Bool("ok")) { _summary.Text = res.Str("error"); _pane.SetStatus("متصل به متن‌یاب", "ok"); Relayout(); return; }
                _pane.SetStatus("متصل به متن‌یاب", "ok");
                Show(res);
            });
        }

        /// <summary>برای تصاویر معرفی (بدون متن‌یاب)</summary>
        public void DemoShow(string query, Dictionary<string, object> res)
        {
            SetMode(query.Contains("\n") ? "phrases" : "phrase");
            if (query.Contains("\n")) _inputMulti.Text = query.Replace("\r", "").Replace("\n", "\r\n"); else _input.Text = query;
            _offline.Visible = false;
            Show(res);
        }

        private void Show(Dictionary<string, object> res)
        {
            _results.Controls.Clear();
            _phrases = (res.TryGetValue("phrases", out var ph) && ph is System.Collections.IEnumerable e ? e.Cast<object>().Select(Convert.ToString).ToList() : new List<string>());
            var list = res.List("results");
            _summary.Text = list.Count == 0 ? "چیزی پیدا نشد؛ «بخشی از واژه» یا حالت «چند عبارت» را بیازمایید." :
                Fa.Num(res.Int("totalFiles")) + " فایل · " + Fa.Num(res.Int("totalMatches")) + " مورد" + (res.Int("totalFiles") > list.Count ? " (" + Fa.Num(list.Count) + " فایل نخست)" : "");
            foreach (var r in list) _results.Add(new ResultCard(r, _phrases));
            Relayout();
        }

        private void SaveNote()
        {
            var t = WordOps.SelectionText();
            if (t.Length == 0) { Toast.Show("نخست بخشی از متن را انتخاب کنید."); return; }
            Background.Run(() => { Bridge.Ensure(); return Bridge.Call("note", new { text = t, title = t.Length > 50 ? t.Substring(0, 50) : t, source = WordOps.DocTitle() }); },
                r => Toast.Show(r != null && r.Bool("ok") ? "در «یادداشت‌های پژوهشی» متن‌یاب ذخیره شد و جست‌وجوپذیر است ✓" : "ذخیره نشد: " + Bridge.LastError, r == null || !r.Bool("ok")));
        }

        private void AddFolder()
        {
            var dir = WordOps.DocFolder();
            if (dir == null) { Toast.Show("نخست سند را ذخیره کنید تا پوشه‌اش معلوم شود."); return; }
            Background.Run(() => { Bridge.Ensure(); return Bridge.Call("add-folder", new { path = dir }); }, r =>
            {
                if (r == null || !r.Bool("ok")) Toast.Show("افزوده نشد: " + (r?.Str("error") ?? Bridge.LastError), true);
                else Toast.Show(r.Bool("already") ? "این پوشه از پیش در متن‌یاب هست." : r.Bool("interactive") ? "پنجرهٔ افزودن پوشه در متن‌یاب باز شد." : "فهرست‌گیری پوشه آغاز شد؛ چند لحظهٔ دیگر جست‌وجوپذیر است.");
            });
        }
    }

    internal static class TextBoxExt
    {
        public static void RecreateHandleSafe(this TextBox t) { try { if (t.IsHandleCreated) { var s = t.Text; t.Multiline = !t.Multiline; t.Multiline = !t.Multiline; t.Text = s; } } catch { } }
    }

    /// <summary>کارت یک فایل در نتایج، با تکه‌های متن و کنش‌های درج</summary>
    public class ResultCard : Card
    {
        private readonly Dictionary<string, object> _r;
        private readonly List<Dictionary<string, object>> _snips;
        private bool _expanded;

        public ResultCard(Dictionary<string, object> r, List<string> phrases)
        {
            _r = r; _snips = r.List("snippets");
            var name = r.Str("name");
            Add(new ThemedLabel { Text = System.IO.Path.GetFileNameWithoutExtension(name), Role = "title" });
            var ext = System.IO.Path.GetExtension(name).TrimStart('.').ToUpperInvariant();
            var conceptual = r.Int("matches") == 0 && r.TryGetValue("semantic", out var sv) && Convert.ToDouble(sv) >= 0;
            var meta = (conceptual ? "مفهومی · " : "") + (ext.Length > 0 ? ext + " · " : "") + r.Str("collection") + " · " + Fa.Num(r.Int("matches")) + " مورد" + (r.Int("relevance", -1) >= 0 ? " · ارتباط " + Fa.Num(r.Int("relevance")) + "٪" : "");
            if (phrases.Count > 1 && r.TryGetValue("perPhrase", out var pp) && pp is System.Collections.IEnumerable e)
            {
                var counts = e.Cast<object>().Select(x => Convert.ToInt32(x)).ToList();
                meta += " · " + Fa.Num(counts.Count(c => c > 0)) + " از " + Fa.Num(counts.Count) + " عبارت";
                var row = new Row();
                for (var i = 0; i < counts.Count && i < phrases.Count; i++)
                    if (counts[i] > 0) row.Controls.Add(new Btn { Kind = BtnKind.Chip, Text = phrases[i] + " " + Fa.Num(counts[i]), Selected = true, Enabled = true, Cursor = Cursors.Default });
                Add(Kit.Hint(meta)); Add(row);
            }
            else Add(Kit.Hint(meta));
            Build();
        }

        private void Build()
        {
            foreach (var c in Body.Controls.OfType<Control>().Where(c => c.Tag as string == "snip").ToList()) { Body.Controls.Remove(c); c.Dispose(); }
            var show = _expanded ? _snips : _snips.Take(1).ToList();
            foreach (var s in show)
            {
                var box = new SnippetBox(s) { Tag = "snip" };
                Add(box);
                var row = Kit.Row(
                    Kit.Button("درج با منبع", () => Insert(s, true), BtnKind.Primary, "نقل‌قول + ارجاع APA + کامنت منبع"),
                    Kit.Button("درج", () => Insert(s, false), BtnKind.Secondary, "فقط متن، بدون نشانهٔ منبع"),
                    Kit.Button("کپی", () => Clipboard.SetText(Text(s)), BtnKind.Ghost),
                    Kit.Button("بند کامل", () => InsertParagraph(s), BtnKind.Ghost, "کل بندِ دربرگیرنده را با منبع درج می‌کند"));
                row.Tag = "snip";
                Add(row);
            }
            var foot = Kit.Row();
            foot.Tag = "snip";
            if (_snips.Count > 1)
                foot.Controls.Add(Kit.Button(_expanded ? "بستن" : "+ " + Fa.Num(_snips.Count - 1) + " مورد دیگر", () => { _expanded = !_expanded; Build(); (FindPage(this))?.Relayout(); }, BtnKind.Ghost));
            foot.Controls.Add(Kit.Button("بازکردن فایل", () => { try { System.Diagnostics.Process.Start(_r.Str("path")); } catch { Toast.Show("فایل پیدا نشد؛ شاید جابه‌جا شده است.", true); } }, BtnKind.Ghost));
            foot.Controls.Add(Kit.Button("منبع", () => SourceDialog.Edit(_r.Str("path")), BtnKind.Ghost, "مشخصات منبع این فایل برای ارجاع APA"));
            Add(foot);
        }

        private static Page FindPage(Control c) { while (c != null && !(c is Page)) c = c.Parent; return c as Page; }

        private static string Text(Dictionary<string, object> s) => (s.Str("before") + s.Str("match") + s.Str("after")).Trim().Replace("\r", " ").Replace("\n", " ");

        private void Insert(Dictionary<string, object> s, bool withSource)
        {
            if (!WordOps.Ready) { Toast.Show("یک سند ورد باز کنید."); return; }
            var text = Text(s);
            if (!withSource) { WordOps.App.Selection.TypeText(text); return; }
            var src = SourceDialog.Ensure(_r.Str("path"));
            if (src == null) return;
            var set = AddinSettings.Current;
            WordOps.InsertExcerpt(text, src.Item1, src.Item2, set.InsertWithComment, set.InsertCitation, _r.Str("path"));
            Toast.Show("درج شد؛ منبع در کامنت و فهرست منابع ثبت شد ✓");
        }

        private void InsertParagraph(Dictionary<string, object> s)
        {
            Background.Run(() => Bridge.Call("excerpt", new { path = _r.Str("path"), start = s.Int("start"), end = s.Int("end") }), r =>
            {
                if (r == null || !r.Bool("ok")) { Toast.Show("بند کامل در دسترس نیست.", true); return; }
                var src = SourceDialog.Ensure(_r.Str("path"));
                if (src == null) return;
                var set = AddinSettings.Current;
                WordOps.InsertExcerpt(r.Str("text"), src.Item1, src.Item2, set.InsertWithComment, set.InsertCitation, _r.Str("path"));
            });
        }
    }

    /// <summary>نمایش تکهٔ متن با واژهٔ یافت‌شدهٔ برجسته</summary>
    public class SnippetBox : RichTextBox
    {
        public SnippetBox(Dictionary<string, object> s)
        {
            ReadOnly = true; BorderStyle = BorderStyle.None; ScrollBars = RichTextBoxScrollBars.None; RightToLeft = RightToLeft.Yes;
            BackColor = Kit.P.Surface2; ForeColor = Kit.P.Text; Font = Kit.F(10f); Cursor = Cursors.IBeam; DetectUrls = false;
            var before = "…" + s.Str("before").TrimStart().Replace("\n", " ");
            var match = s.Str("match");
            var after = s.Str("after").TrimEnd().Replace("\n", " ") + "…";
            Text = before + match + after;
            Select(before.Length, match.Length);
            SelectionBackColor = Kit.P.Mark; SelectionFont = Kit.F(10f, true);
            Select(0, 0);
            Height = 96;
        }

        protected override void OnContentsResized(ContentsResizedEventArgs e) { Height = Math.Min(220, e.NewRectangle.Height + 8); base.OnContentsResized(e); }
    }

    /// <summary>فرم کوچک مشخصات منبع (برای APA)؛ پاسخ کاربر برای همان فایل به خاطر سپرده می‌شود</summary>
    public static class SourceDialog
    {
        public static Tuple<SourceInfo, string> Ensure(string path) => Edit(path, askPage: true);

        public static Tuple<SourceInfo, string> Edit(string path, bool askPage = false)
        {
            var set = AddinSettings.Current;
            var src = set.SourceFor(path) ?? Apa.Guess(path);
            using (var f = new Form
            {
                Text = "مشخصات منبع", RightToLeft = RightToLeft.Yes, RightToLeftLayout = true, StartPosition = FormStartPosition.CenterScreen,
                FormBorderStyle = FormBorderStyle.FixedDialog, MaximizeBox = false, MinimizeBox = false, ClientSize = new Size(440, 520), BackColor = Kit.P.Canvas, Font = Kit.F(),
            })
            {
                var page = new Page { Dock = DockStyle.Fill };
                var card = new Card("منبع برای ارجاع APA", "یک بار تکمیل کنید؛ دفعه‌های بعد برای همین فایل خودکار است.");
                var kind = Kit.Combo(new[] { "کتاب", "مقالهٔ نشریه", "پایان‌نامه", "وب‌گاه" }, new[] { "book", "article", "thesis", "web" }.ToList().IndexOf(src.Kind) is int k && k >= 0 ? k : 0);
                var authors = Kit.Input("", true, 2); authors.Text = src.Authors;
                var year = Kit.Input("۱۴۰۲ یا 2021"); year.Text = src.Year;
                var title = Kit.Input("عنوان"); title.Text = src.Title;
                var cont = Kit.Input("ناشر یا نام نشریه"); cont.Text = src.Container;
                var vol = Kit.Input("دوره/جلد"); vol.Text = src.Volume;
                var iss = Kit.Input("شماره"); iss.Text = src.Issue;
                var pages = Kit.Input("صفحه‌ها (مقاله)"); pages.Text = src.Pages;
                var page2 = Kit.Input("صفحهٔ همین نقل‌قول (اختیاری)");
                card.Add(Kit.Hint("نوع منبع")); card.Add(kind);
                card.Add(Kit.Hint("نویسندگان — هر نفر در یک سطر: «نام خانوادگی، نام»")); card.Add(authors);
                card.Add(year); card.Add(title); card.Add(cont);
                card.Add(Kit.Row(vol, iss)); vol.Width = iss.Width = 120;
                card.Add(pages);
                if (askPage) card.Add(page2);
                var preview = Kit.Hint("");
                card.Add(preview);
                void Upd()
                {
                    src.Kind = new[] { "book", "article", "thesis", "web" }[Math.Max(0, kind.SelectedIndex)];
                    src.Authors = authors.Text.Trim(); src.Year = Fa.Latin(year.Text.Trim()); src.Title = title.Text.Trim(); src.Container = cont.Text.Trim();
                    src.Volume = vol.Text.Trim(); src.Issue = iss.Text.Trim(); src.Pages = pages.Text.Trim();
                    preview.Text = Apa.Reference(src).Replace("*", "") + "\n" + Apa.InText(src, Fa.Latin(page2.Text.Trim()));
                    page.Relayout();
                }
                foreach (var c in new Control[] { authors, year, title, cont, vol, iss, pages, page2 }) c.TextChanged += (s, e) => Upd();
                kind.SelectedIndexChanged += (s, e) => Upd();
                var ok = Kit.Button("ثبت و ادامه", () => f.DialogResult = DialogResult.OK, BtnKind.Primary);
                var cancel = Kit.Button("انصراف", () => f.DialogResult = DialogResult.Cancel, BtnKind.Ghost);
                page.Add(card); page.Add(Kit.Row(ok, cancel));
                f.Controls.Add(page);
                f.Shown += (s, e) => Upd();
                if (f.ShowDialog() != DialogResult.OK) return null;
                Upd();
                set.Sources.RemoveAll(x => string.Equals(x.Path, path, StringComparison.OrdinalIgnoreCase));
                src.Path = path; set.Sources.Add(src); AddinSettings.Save(false);
                return Tuple.Create(src, Fa.Latin(page2.Text.Trim()));
            }
        }
    }
}
