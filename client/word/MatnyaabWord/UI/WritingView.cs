using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;
using Matnyaab.Word.Core;
using Matnyaab.Word.Ops;

namespace Matnyaab.Word.UI
{
    /// <summary>فهرست بازبینی: هر مورد پیش از اعمال، دیده و تأیید می‌شود</summary>
    public class HitList : Stack
    {
        public List<DocHit> Hits = new List<DocHit>();
        public bool WithLevels;
        private const int Max = 250;

        public HitList() { Gap = 4; }

        public void Fill(List<DocHit> hits)
        {
            Hits = hits;
            foreach (Control c in Controls.Cast<Control>().ToList()) c.Dispose();
            Controls.Clear();
            if (hits.Count == 0) { Add(Kit.Hint("موردی پیدا نشد 👌")); return; }
            var all = Kit.Check("همه (" + Fa.Num(hits.Count) + " مورد)", hits.All(h => h.Checked));
            Add(all);
            var boxes = new List<CheckBox>();
            foreach (var h in hits.Take(Max))
            {
                var label = h.Label + (string.IsNullOrEmpty(h.Detail) ? "" : "   —   " + h.Detail);
                var cb = Kit.Check(label.Length > 90 ? label.Substring(0, 90) + "…" : label, h.Checked, v => h.Checked = v);
                cb.Font = Kit.F(9f);
                boxes.Add(cb);
                if (WithLevels)
                {
                    var lvl = Kit.Combo(new[] { "عنوان ۱", "عنوان ۲", "عنوان ۳", "عنوان ۴" }, Math.Max(0, Math.Min(3, h.Level - 1)));
                    lvl.Width = 90;
                    lvl.SelectedIndexChanged += (s, e) => h.Level = lvl.SelectedIndex + 1;
                    cb.Width = 260;
                    var row = Kit.Row(cb, lvl);
                    Add(row);
                }
                else Add(cb);
            }
            if (hits.Count > Max) Add(Kit.Hint("و " + Fa.Num(hits.Count - Max) + " مورد دیگر (همه با «اعمال» انجام می‌شوند)"));
            all.CheckedChanged += (s, e) => { hits.ForEach(h => h.Checked = all.Checked); boxes.ForEach(b => b.Checked = all.Checked); };
        }
    }

    /// <summary>زبانهٔ ابزارهای نگارش: اصطلاح↔پانویس، سرفصل‌ها، یکدست‌سازی متن</summary>
    public class WritingView : Page
    {
        private readonly Card _terms, _heads, _clean;
        private readonly HitList _termList = new HitList(), _headList = new HitList { WithLevels = true }, _cleanList = new HitList();
        private string _termMode = "terms";
        private readonly Dictionary<string, CheckBox> _rules = new Dictionary<string, CheckBox>();

        public WritingView()
        {
            var s = AddinSettings.Current;
            Add(Kit.Row(
                Kit.Button("↶ واگرد", WordOps.UndoLast, BtnKind.Secondary, "برگرداندن آخرین عملیات (هر عملیات متن‌یاب یک گام است)"),
                Kit.Button("↷ ازنو", WordOps.RedoLast, BtnKind.Secondary)));

            _terms = new Card("اصطلاحات لاتین ↔ پانویس", "«تزکیه (Self-purification)» ← «تزکیه¹» و پانویس لاتین؛ و برعکس.");
            _terms.Add(Kit.Row(
                Kit.Button("یافتن اصطلاحات داخل پرانتز", () => Scan("terms"), BtnKind.Primary),
                Kit.Button("یافتن پانویس‌های لاتین", () => Scan("footnotes"))));
            _terms.Add(_termList);
            _terms.Add(Kit.Button("اعمال روی موارد تیک‌خورده", ApplyTerms, BtnKind.Primary));
            _terms.Visible = s.ShowFootnotes;
            Add(_terms);

            _heads = new Card("سرفصل‌ها و ساختار", "عنوان‌ها را از روی شماره‌گذاری، واژه‌های کلیدی و قالب تشخیص می‌دهد؛ سطح هر کدام را می‌توانید عوض کنید.");
            _heads.Add(Kit.Row(
                Kit.Button("تشخیص خودکار سرفصل‌ها", () => { _headList.Fill(WordOps.ScanHeadings()); Relayout(); }, BtnKind.Primary),
                Kit.Button("درج فهرست مطالب", WordOps.InsertToc),
                Kit.Button("به‌روزرسانی فهرست", WordOps.UpdateFields, BtnKind.Ghost)));
            _heads.Add(_headList);
            _heads.Add(Kit.Button("اعمال سبک عنوان‌ها", () => Done(WordOps.ApplyHeadings(_headList.Hits), "سرفصل"), BtnKind.Primary));
            _heads.Visible = s.ShowStructure;
            Add(_heads);

            _clean = new Card("یکدست‌سازی متن فارسی", "پیش از اعمال، همهٔ تغییرها را می‌بینید.");
            foreach (var r in Cleanup.Rules)
            {
                var cb = Kit.Check(r.Title + "  ·  " + r.Hint, r.Default);
                _rules[r.Id] = cb; _clean.Add(cb);
            }
            _clean.Add(Kit.Row(
                Kit.Button("پیش‌نمایش تغییرها", () => { _cleanList.Fill(WordOps.ScanCleanup(_rules.Where(x => x.Value.Checked).Select(x => x.Key))); Relayout(); }, BtnKind.Primary),
                Kit.Button("اعمال", () => Done(WordOps.ApplyCleanup(_cleanList.Hits), "اصلاح"), BtnKind.Secondary)));
            _clean.Add(_cleanList);
            _clean.Visible = s.ShowCleanup;
            Add(_clean);
        }

        /// <summary>نمونهٔ بازبینی برای تصاویر معرفی</summary>
        public void Demo()
        {
            _termList.Fill(new List<DocHit>
            {
                new DocHit { Label = "Self-purification", Detail = "…اخلاق اسلامی بر تزکیه" },
                new DocHit { Label = "Greater Jihad", Detail = "…پیکار درونی یا جهاد اکبر" },
                new DocHit { Label = "Virtue ethics", Detail = "…در سنت اخلاق فضیلت" },
            });
            _headList.Fill(new List<DocHit>
            {
                new DocHit { Label = "فصل اول: کلیات پژوهش", Detail = "با «فصل» آغاز می‌شود", Level = 1 },
                new DocHit { Label = "۱-۲- پیشینهٔ پژوهش", Detail = "شماره‌گذاری ۱-۲", Level = 2 },
                new DocHit { Label = "۱-۲-۱- پیشینهٔ داخلی", Detail = "شماره‌گذاری ۱-۲-۱", Level = 3 },
            });
            Relayout();
        }

        public void Focus(string section)
        {
            if (section == "terms" || section == "footnotes") Scan(section);
            else if (section == "headings") { _headList.Fill(WordOps.ScanHeadings()); Relayout(); }
            else if (section == "cleanup") { _cleanList.Fill(WordOps.ScanCleanup(_rules.Where(x => x.Value.Checked).Select(x => x.Key))); Relayout(); }
        }

        private void Scan(string mode)
        {
            if (!WordOps.Ready) { Toast.Show("یک سند ورد باز کنید."); return; }
            _termMode = mode;
            _termList.Fill(mode == "terms" ? WordOps.ScanLatinTerms() : WordOps.ScanLatinFootnotes());
            Relayout();
        }

        private void ApplyTerms()
        {
            var n = _termMode == "terms" ? WordOps.TermsToFootnotes(_termList.Hits) : WordOps.FootnotesToTerms(_termList.Hits);
            Done(n, _termMode == "terms" ? "پانویس" : "اصطلاح");
            _termList.Fill(new List<DocHit>()); Relayout();
        }

        private static void Done(int n, string what) =>
            Toast.Show(n == 0 ? "تغییری لازم نبود." : Fa.Num(n) + " " + what + " اعمال شد. با «↶ واگرد» یک‌جا برمی‌گردد.");
    }
}
