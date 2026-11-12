using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Windows.Forms;
using Matnyaab.Word.Core;
using Matnyaab.Word.Ops;

namespace Matnyaab.Word.UI
{
    // ==================================================================== پژوهش: فیش و اعتبارسنجی

    /// <summary>
    /// فیش از متن انتخاب‌شده (مشترک با «میز پژوهش» برنامه)، درج فیش با منبع،
    /// «این متن از کجاست؟» و راستی‌آزمایی نقل‌قول‌ها / پیش‌همانندجویی سند در برابر کتابخانهٔ کاربر.
    /// </summary>
    public class ResearchView : Page
    {
        private readonly ComboBox _project = Kit.Combo(new string[0], 0, true);
        private readonly ComboBox _topic = Kit.Combo(new string[0], 0, true);
        private readonly TextBox _filter = Kit.Input("جست‌وجو در فیش‌ها");
        private readonly Stack _fishes = new Stack { Gap = 6 };
        private readonly Stack _checks = new Stack { Gap = 6 };
        private readonly Label _checkState = Kit.Hint("");
        private List<Dictionary<string, object>> _all = new List<Dictionary<string, object>>();
        private List<Dictionary<string, object>> _projects = new List<Dictionary<string, object>>();

        public ResearchView()
        {
            var add = new Card("فیش از متن انتخاب‌شده", "متن را در سند انتخاب کنید و فیش بزنید؛ در «میز پژوهش» متن‌یاب هم دیده می‌شود و برای ساخت کتاب به کار می‌رود.");
            add.Add(Kit.Hint("پروژه")); add.Add(_project);
            add.Add(Kit.Hint("سرفصل")); add.Add(_topic);
            add.Add(Kit.Row(Kit.Button("＋ فیش از انتخاب", AddFish, BtnKind.Primary), Kit.Button("تازه‌سازی", Load, BtnKind.Ghost)));
            _project.SelectedIndexChanged += (o, e) => { FillTopics(); Fill(); };
            Add(add);

            var list = new Card("فیش‌ها", "روی «درج» بزنید تا متن فیش با ارجاع و کامنت منبع در جای مکان‌نما بنشیند.");
            _filter.TextChanged += (o, e) => Fill();
            list.Add(_filter); list.Add(_fishes);
            Add(list);

            var verify = new Card("اعتبارسنجی با کتابخانهٔ شما", "منبع یک نقل‌قول را پیدا کنید، یا همهٔ نقل‌قول‌های «…» سند را با منابع مقایسه کنید تا نقل‌قول‌های بی‌منبع یا تغییرکرده را ببینید.");
            verify.Add(Kit.Row(
                Kit.Button("این متن از کجاست؟", FindSource, BtnKind.Primary),
                Kit.Button("بررسی نقل‌قول‌ها", () => Check(true)),
                Kit.Button("پیش‌همانندجویی", () => Check(false), BtnKind.Ghost)));
            verify.Add(_checkState); verify.Add(_checks);
            Add(verify);
            Load();
        }

        private static Dictionary<string, object> Bridge0()
        {
            var p = Bridge.Ensure();
            if (p == null) Toast.Show("متن‌یاب در دسترس نیست: " + Bridge.LastError, true);
            return p;
        }

        private void Load()
        {
            Background.Run(() => { if (Bridge.Ensure(8000) == null) return null; return Bridge.Call("fish/list", new { }); }, r =>
            {
                if (r == null) { _fishes.Controls.Clear(); _fishes.Add(Kit.Hint("برای فیش‌ها، متن‌یاب ۳ را نصب کنید یا پیوند را در تنظیمات روشن کنید.")); Relayout(); return; }
                _projects = r.List("projects"); _all = r.List("fishes");
                var cur = _project.Text;
                _project.Items.Clear();
                foreach (var p in _projects) _project.Items.Add(p.Str("name"));
                if (_project.Items.Count == 0) _project.Items.Add("پژوهش‌های من");
                _project.Text = string.IsNullOrEmpty(cur) ? (string)_project.Items[_project.Items.Count - 1] : cur;
                FillTopics(); Fill();
            });
        }

        private void FillTopics()
        {
            var cur = _topic.Text;
            _topic.Items.Clear();
            var p = _projects.FirstOrDefault(x => x.Str("name") == _project.Text);
            if (p != null && p.TryGetValue("topics", out var t) && t is System.Collections.IEnumerable e)
                foreach (var x in e) _topic.Items.Add(Convert.ToString(x));
            _topic.Text = cur;
        }

        private void Fill()
        {
            foreach (Control c in _fishes.Controls.Cast<Control>().ToList()) c.Dispose();
            _fishes.Controls.Clear();
            var q = _filter.Text.Trim();
            var list = _all.Where(f => f.Str("project") == _project.Text && (q.Length == 0 || (f.Str("title") + " " + f.Str("text") + " " + f.Str("topic")).Contains(q))).Reverse().Take(60).ToList();
            if (list.Count == 0) _fishes.Add(Kit.Hint("فیشی در این پروژه نیست."));
            foreach (var f in list)
            {
                var c = new Card(f.Str("title"), (f.Str("topic").Length > 0 ? "§ " + f.Str("topic") + " · " : "") + f.Str("sourceName")) { Soft = true };
                var text = f.Str("text");
                c.Add(Kit.Text(text.Length > 260 ? text.Substring(0, 260) + "…" : text));
                c.Add(Kit.Row(Kit.Button("درج با منبع", () => Insert(f), BtnKind.Primary), Kit.Button("کپی", () => Clipboard.SetText(text), BtnKind.Ghost)));
                _fishes.Add(c);
            }
            Relayout();
        }

        private void Insert(Dictionary<string, object> f)
        {
            if (!WordOps.Ready) { Toast.Show("یک سند ورد باز کنید."); return; }
            var s = AddinSettings.Current;
            var src = new SourceInfo { Path = f.Str("sourcePath"), Title = f.Str("sourceName") };
            try { WordOps.InsertExcerpt(f.Str("text"), src, "", s.InsertWithComment, s.InsertCitation, "fish"); Toast.Show("فیش درج شد ✓"); }
            catch (Exception e) { Toast.Show(e.Message, true); }
        }

        private void AddFish()
        {
            if (!WordOps.Ready) { Toast.Show("یک سند ورد باز کنید."); return; }
            var t = WordOps.SelectionText();
            if (t.Length < 3) { Toast.Show("ابتدا متنی را در سند انتخاب کنید."); return; }
            var body = new Dictionary<string, object>
            {
                ["text"] = t, ["project"] = _project.Text.Trim(), ["topic"] = _topic.Text.Trim(),
                ["sourceName"] = WordOps.DocTitle(), ["sourcePath"] = WordOps.DocFolder(),
            };
            Background.Run(() => Bridge0() == null ? null : Bridge.Call("fish/add", body), r =>
            {
                if (r == null || !r.Bool("ok")) { Toast.Show("فیش ذخیره نشد: " + (r?.Str("error") ?? Bridge.LastError), true); return; }
                Toast.Show("فیش ذخیره شد ✓"); Load();
            });
        }

        private void FindSource()
        {
            if (!WordOps.Ready) { Toast.Show("یک سند ورد باز کنید."); return; }
            var t = WordOps.SelectionText();
            if (t.Split(' ').Length < 4) { Toast.Show("نقل‌قول (دست‌کم چهار واژه) را انتخاب کنید."); return; }
            _checkState.Text = "در حال جست‌وجوی منبع…"; Relayout();
            Background.Run(() => Bridge0() == null ? null : Bridge.Call("find-source", new { text = t }, 60000), r =>
            {
                ClearChecks();
                var m = r?.List("matches") ?? new List<Dictionary<string, object>>();
                _checkState.Text = m.Count == 0 ? "منبعی برای این متن در کتابخانهٔ متن‌یاب پیدا نشد." : "منبع‌های محتمل:";
                foreach (var x in m) _checks.Add(MatchCard(x, null));
                Relayout();
            });
        }

        private void Check(bool quotes)
        {
            if (!WordOps.Ready) { Toast.Show("یک سند ورد باز کنید."); return; }
            var items = quotes ? WordOps.QuotedSegments() : WordOps.Sentences();
            if (items.Count == 0) { Toast.Show(quotes ? "نقل‌قولی میان «» در سند پیدا نشد." : "جملهٔ کافی در سند نیست."); return; }
            _checkState.Text = "در حال بررسی " + Fa.Num(items.Count) + " مورد…"; Relayout();
            Background.Run(() => Bridge0() == null ? null : Bridge.Call("check", new { items }, 300000), r =>
            {
                ClearChecks();
                if (r == null) { _checkState.Text = Bridge.LastError; Relayout(); return; }
                var res = r.TryGetValue("results", out var v) && v is System.Collections.IEnumerable e ? e.Cast<object>().ToList() : new List<object>();
                int found = 0;
                for (var i = 0; i < items.Count && i < res.Count; i++)
                {
                    var m = res[i] as Dictionary<string, object>;
                    if (m != null) found++;
                    if (quotes && m != null && Similarity(m) >= 0.9) continue;          // نقل‌قول درست؛ نیازی به نمایش نیست
                    if (!quotes && m == null) continue;                                    // جملهٔ مستقل
                    _checks.Add(MatchCard(m, items[i]));
                }
                _checkState.Text = quotes
                    ? Fa.Num(items.Count) + " نقل‌قول بررسی شد: " + Fa.Num(found) + " منبع پیدا شد. موارد زیر بی‌منبع یا با تغییرند."
                    : Fa.Num(found * 100 / Math.Max(1, items.Count)) + "٪ جمله‌ها (" + Fa.Num(found) + " از " + Fa.Num(items.Count) + ") با منابع کتابخانه همانندند؛ ارجاعشان را بررسی کنید.";
                Relayout();
            });
        }

        private static double Similarity(Dictionary<string, object> m) { try { return Convert.ToDouble(m["similarity"], CultureInfo.InvariantCulture); } catch { return 0; } }

        private void ClearChecks() { foreach (Control c in _checks.Controls.Cast<Control>().ToList()) c.Dispose(); _checks.Controls.Clear(); }

        private Control MatchCard(Dictionary<string, object> m, string mine)
        {
            var title = m == null ? "بدون منبع در کتابخانه" : m.Str("name") + " — " + Fa.Num((int)(Similarity(m) * 100)) + "٪";
            var c = new Card(title) { Soft = true };
            if (mine != null) c.Add(Kit.Text("متن شما: " + (mine.Length > 200 ? mine.Substring(0, 200) + "…" : mine)));
            if (m != null) { var ex = m.Str("excerpt"); c.Add(Kit.Hint("منبع: " + (ex.Length > 300 ? ex.Substring(0, 300) + "…" : ex))); }
            var row = new Row();
            if (mine != null) row.Controls.Add(Kit.Button("نشان بده", () => WordOps.Find(mine), BtnKind.Ghost));
            if (m != null)
            {
                row.Controls.Add(Kit.Button("افزودن ارجاع", () =>
                {
                    if (mine != null) WordOps.Find(mine);
                    var s = AddinSettings.Current;
                    var src = new SourceInfo { Path = m.Str("path"), Title = m.Str("name") };
                    try { WordOps.App.Selection.Collapse(0); WordOps.App.Selection.TypeText(" " + Apa.InText(src, "", s.CitationLang)); WordOps.RegisterSource(src); Toast.Show("ارجاع افزوده شد ✓"); }
                    catch (Exception e) { Toast.Show(e.Message, true); }
                }, BtnKind.Primary));
                row.Controls.Add(Kit.Button("بازکردن منبع", () => { try { System.Diagnostics.Process.Start(m.Str("path")); } catch { } }, BtnKind.Ghost));
            }
            c.Add(row);
            return c;
        }
    }

    // ==================================================================== اداری: نامه و قالب‌های رسمی

    /// <summary>نامهٔ اداری و قالب‌های رسمی رایج، در سندی تازه با صفحه‌آرایی استاندارد؛ پیش‌نویس متن با دستیار (اختیاری).</summary>
    public class OfficeView : Page
    {
        private class Field { public string Key, Label; public bool Multi; public string Default = ""; public TextBox Box; }
        private class Format { public string Id, Title, Hint; public List<Field> Fields; public Func<Dictionary<string, string>, List<OfficeLine>> Build; }

        private readonly ComboBox _kind;
        private readonly Stack _form = new Stack { Gap = 6 };
        private readonly ComboBox _font = Kit.Combo(new[] { "B Nazanin", "B Lotus", "B Mitra", "IRNazanin", "Vazirmatn" }, 0, true);
        private readonly CheckBox _letterhead = Kit.Check("جای سربرگ چاپی (حاشیهٔ بالای ۴٫۵ سانتی‌متر)");
        private readonly List<Format> _formats;
        private Format _cur;

        private static string Today()
        {
            var pc = new PersianCalendar(); var n = DateTime.Now;
            return Fa.Num(pc.GetYear(n) + "/" + pc.GetMonth(n).ToString("00") + "/" + pc.GetDayOfMonth(n).ToString("00"));
        }

        private static OfficeLine L(string t, string role = "body", int align = 3) => new OfficeLine { Text = t, Role = role, Align = align };
        private static IEnumerable<OfficeLine> Paras(string t, string role = "body") =>
            (t ?? "").Replace("\r\n", "\n").Split('\n').Where(x => x.Trim().Length > 0).Select(x => L(x.Trim(), role));

        public OfficeView()
        {
            _formats = new List<Format>
            {
                new Format { Id = "letter", Title = "نامهٔ اداری", Hint = "ساختار استاندارد نامهٔ اداری: شماره، تاریخ، پیوست، گیرنده، موضوع، احترام، متن، امضا و رونوشت.",
                    Fields = new List<Field> {
                        new Field { Key = "org", Label = "فرستنده (سازمان/واحد)" }, new Field { Key = "no", Label = "شماره" }, new Field { Key = "date", Label = "تاریخ", Default = Today() },
                        new Field { Key = "attach", Label = "پیوست", Default = "ندارد" }, new Field { Key = "to", Label = "گیرنده", Default = "جناب آقای / سرکار خانم …\nریاست محترم …" },
                        new Field { Key = "subject", Label = "موضوع" }, new Field { Key = "body", Label = "متن نامه", Multi = true },
                        new Field { Key = "sign", Label = "نام و سمت امضاکننده", Default = "نام و نام خانوادگی\nسمت" }, new Field { Key = "cc", Label = "رونوشت (هر سطر یک گیرنده)", Multi = true } },
                    Build = v => {
                        var l = new List<OfficeLine> { L("بسمه تعالی", "sub", 1) };
                        if (v["org"].Length > 0) l.Add(L(v["org"], "label", 2));
                        l.Add(L("شماره: " + v["no"], "small", 0)); l.Add(L("تاریخ: " + v["date"], "small", 0)); l.Add(L("پیوست: " + v["attach"], "small", 0));
                        l.AddRange(Paras(v["to"], "label").Select(x => { x.Align = 2; return x; }));
                        l.Add(L("موضوع: " + v["subject"], "label", 2));
                        l.Add(L("با سلام و احترام،", "body", 2));
                        l.AddRange(Paras(v["body"]));
                        l.AddRange(Paras(v["sign"], "sign").Select(x => { x.Align = 0; return x; }));
                        var cc = Paras(v["cc"], "small").ToList();
                        if (cc.Count > 0) { l.Add(L("رونوشت:", "label", 2)); l.AddRange(cc.Select(x => { x.Text = "- " + x.Text + " جهت استحضار."; x.Align = 2; return x; })); }
                        return l; } },
                new Format { Id = "minutes", Title = "صورتجلسه", Hint = "عنوان، تاریخ و مکان، حاضران، دستور جلسه و جدول مصوبات با مسئول و مهلت.",
                    Fields = new List<Field> {
                        new Field { Key = "title", Label = "عنوان جلسه" }, new Field { Key = "date", Label = "تاریخ و ساعت", Default = Today() }, new Field { Key = "place", Label = "مکان" },
                        new Field { Key = "people", Label = "حاضران (هر سطر یک نفر)", Multi = true }, new Field { Key = "agenda", Label = "دستور جلسه (هر سطر یک بند)", Multi = true },
                        new Field { Key = "acts", Label = "مصوبات — هر سطر: مصوبه | مسئول | مهلت", Multi = true } },
                    Build = v => {
                        var l = new List<OfficeLine> { L("صورتجلسهٔ " + v["title"], "title", 1), L("تاریخ: " + v["date"] + "    مکان: " + v["place"], "small", 1), L("حاضران", "label", 2) };
                        l.AddRange(Paras(v["people"]).Select(x => { x.Text = "• " + x.Text; x.Role = "small"; x.Align = 2; return x; }));
                        l.Add(L("دستور جلسه", "label", 2));
                        var i = 0; l.AddRange(Paras(v["agenda"]).Select(x => { x.Text = Fa.Num(++i) + ". " + x.Text; x.Align = 2; return x; }));
                        l.Add(L("مصوبات", "label", 2));
                        var rows = new List<string[]> { new[] { "ردیف", "مصوبه", "مسئول پیگیری", "مهلت" } };
                        var j = 0; foreach (var a in Paras(v["acts"])) { var p = a.Text.Split('|').Select(x => x.Trim()).ToList(); while (p.Count < 3) p.Add(""); rows.Add(new[] { Fa.Num(++j), p[0], p[1], p[2] }); }
                        l.Add(new OfficeLine { Role = "table", Rows = rows.ToArray() });
                        l.Add(L("امضای حاضران:", "sign", 2));
                        return l; } },
                new Format { Id = "report", Title = "گزارش کار", Hint = "گزارش رسمی با مقدمه، شرح اقدامات، نتایج، مشکلات و پیشنهادها.",
                    Fields = new List<Field> {
                        new Field { Key = "title", Label = "عنوان گزارش" }, new Field { Key = "to", Label = "گیرنده" }, new Field { Key = "date", Label = "تاریخ", Default = Today() },
                        new Field { Key = "intro", Label = "مقدمه", Multi = true }, new Field { Key = "acts", Label = "اقدامات (هر سطر یک مورد)", Multi = true },
                        new Field { Key = "results", Label = "نتایج", Multi = true }, new Field { Key = "issues", Label = "مشکلات و پیشنهادها", Multi = true }, new Field { Key = "sign", Label = "تهیه‌کننده", Default = "نام و سمت" } },
                    Build = v => {
                        var l = new List<OfficeLine> { L(v["title"], "title", 1), L("به: " + v["to"] + "    تاریخ: " + v["date"], "small", 1) };
                        void Sec(string h, string k, bool list = false) { if (v[k].Trim().Length == 0) return; l.Add(L(h, "label", 2)); var n = 0; l.AddRange(Paras(v[k]).Select(x => { if (list) x.Text = Fa.Num(++n) + ". " + x.Text; return x; })); }
                        Sec("۱. مقدمه", "intro"); Sec("۲. شرح اقدامات", "acts", true); Sec("۳. نتایج", "results"); Sec("۴. مشکلات و پیشنهادها", "issues");
                        l.Add(L(v["sign"], "sign", 0));
                        return l; } },
                new Format { Id = "request", Title = "درخواست", Hint = "درخواست رسمی (مرخصی، تمدید، مجوز، تأییدیه…) با مشخصات متقاضی.",
                    Fields = new List<Field> {
                        new Field { Key = "to", Label = "گیرنده", Default = "ریاست محترم …" }, new Field { Key = "subject", Label = "موضوع" }, new Field { Key = "name", Label = "نام و مشخصات متقاضی" },
                        new Field { Key = "body", Label = "شرح درخواست", Multi = true }, new Field { Key = "date", Label = "تاریخ", Default = Today() } },
                    Build = v => new List<OfficeLine> { L("بسمه تعالی", "sub", 1), L(v["to"], "label", 2), L("موضوع: " + v["subject"], "label", 2), L("با سلام و احترام،", "body", 2),
                        L("اینجانب " + v["name"] + " به استحضار می‌رساند:", "body") }.Concat(Paras(v["body"])).Concat(new[] { L("خواهشمند است دستور فرمایید اقدام لازم انجام شود.", "body"), L(v["name"], "sign", 0), L("تاریخ: " + v["date"], "small", 0) }).ToList() },
                new Format { Id = "circular", Title = "بخشنامه", Hint = "بخشنامه با شماره، گیرندگان، موضوع، بندهای شماره‌دار و تاریخ اجرا.",
                    Fields = new List<Field> {
                        new Field { Key = "org", Label = "صادرکننده" }, new Field { Key = "no", Label = "شماره" }, new Field { Key = "date", Label = "تاریخ", Default = Today() },
                        new Field { Key = "to", Label = "گیرندگان", Default = "کلیهٔ واحدهای تابعه" }, new Field { Key = "subject", Label = "موضوع" },
                        new Field { Key = "items", Label = "بندها (هر سطر یک بند)", Multi = true }, new Field { Key = "start", Label = "تاریخ اجرا" }, new Field { Key = "sign", Label = "امضاکننده", Default = "نام و سمت" } },
                    Build = v => {
                        var l = new List<OfficeLine> { L("بخشنامه", "title", 1), L(v["org"], "label", 1), L("شماره: " + v["no"] + "    تاریخ: " + v["date"], "small", 1),
                            L("به: " + v["to"], "label", 2), L("موضوع: " + v["subject"], "label", 2), L("با سلام و احترام، به پیوست موارد زیر جهت اجرا ابلاغ می‌گردد:", "body") };
                        var n = 0; l.AddRange(Paras(v["items"]).Select(x => { x.Text = Fa.Num(++n) + ". " + x.Text; return x; }));
                        if (v["start"].Length > 0) l.Add(L("این بخشنامه از تاریخ " + v["start"] + " لازم‌الاجراست.", "body"));
                        l.Add(L(v["sign"], "sign", 0));
                        return l; } },
            };

            var head = new Card("سند رسمی تازه", "قالب را انتخاب و فرم را پر کنید؛ سندی تازه با صفحه‌آرایی رسمی (A4، حاشیهٔ استاندارد، قلم فارسی) ساخته می‌شود و می‌توانید ویرایشش کنید.");
            _kind = Kit.Combo(_formats.Select(f => f.Title));
            _kind.SelectedIndexChanged += (o, e) => Show(_formats[_kind.SelectedIndex]);
            head.Add(_kind);
            Add(head);
            var form = new Card(null); form.Add(_form); Add(form);
            var opts = new Card("صفحه‌آرایی");
            opts.Add(Kit.Hint("قلم")); opts.Add(_font); opts.Add(_letterhead);
            opts.Add(Kit.Row(Kit.Button("ساخت سند", Build, BtnKind.Primary), Kit.Button("پیش‌نویس متن با دستیار", Draft), Kit.Button("پاک کردن فرم", () => Show(_cur), BtnKind.Ghost)));
            Add(opts);
            Show(_formats[0]);
        }

        public void Select(string id) { var i = _formats.FindIndex(f => f.Id == id); if (i >= 0) _kind.SelectedIndex = i; }

        private void Show(Format f)
        {
            _cur = f;
            foreach (Control c in _form.Controls.Cast<Control>().ToList()) c.Dispose();
            _form.Controls.Clear();
            _form.Add(Kit.Hint(f.Hint));
            foreach (var x in f.Fields)
            {
                _form.Add(Kit.Hint(x.Label));
                x.Box = Kit.Input("", x.Multi || x.Default.Contains("\n"), x.Multi ? 5 : 2);
                x.Box.Text = x.Default.Replace("\n", "\r\n");
                _form.Add(x.Box);
            }
            Relayout();
        }

        private Dictionary<string, string> Values() => _cur.Fields.ToDictionary(x => x.Key, x => (x.Box?.Text ?? "").Trim());

        private void Build()
        {
            try { WordOps.NewOfficeDocument(_cur.Build(Values()), _font.Text.Trim().Length > 0 ? _font.Text.Trim() : "B Nazanin", 13, _letterhead.Checked); Toast.Show(_cur.Title + " ساخته شد ✓"); }
            catch (Exception e) { Toast.Show(e.Message, true); }
        }

        /// <summary>متن اصلی را از روی چند کلمهٔ کاربر با لحن رسمی اداری می‌نویسد (با سرویس هوش مصنوعیِ خود کاربر).</summary>
        private void Draft()
        {
            var s = AddinSettings.Current;
            var p = s.AiProviders.FirstOrDefault(x => x.Id == s.AiProviderId) ?? s.AiProviders.FirstOrDefault();
            if (p == null) { Toast.Show("برای پیش‌نویس، در «تنظیمات» یک سرویس هوش مصنوعی بیفزایید."); return; }
            var v = Values();
            var target = _cur.Fields.FirstOrDefault(x => x.Multi && (x.Key == "body" || x.Key == "intro" || x.Key == "items")) ?? _cur.Fields.First(x => x.Multi);
            var brief = string.Join("\n", _cur.Fields.Where(x => x.Box.Text.Trim().Length > 0).Select(x => x.Label + ": " + x.Box.Text.Trim()));
            var instruction = "Write the «" + target.Label + "» section of a Persian official document of type «" + _cur.Title + "» in formal Iranian administrative style (نثر اداری رسمی، مؤدبانه و موجز). " +
                              "Use the given notes; do not invent numbers or names. One item per line if it is a list. Output Persian text only.";
            target.Box.Text = "در حال نوشتن…";
            Background.Run(() => Ai.Complete(p, instruction, brief), r => target.Box.Text = r.Trim().Replace("\n", "\r\n"), e => { target.Box.Text = ""; Toast.Show(e.Message, true); });
        }
    }
}
