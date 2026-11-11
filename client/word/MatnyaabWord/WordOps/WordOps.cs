using System;
using System.Collections.Generic;
using System.Linq;
using Matnyaab.Word.Core;

namespace Matnyaab.Word.Ops
{
    /// <summary>یک مورد پیداشده در سند (موقعیت مطلق در متن اصلی سند)</summary>
    public class DocHit
    {
        public int Start, End;
        public string Expect = "";      // متن مورد انتظار برای اطمینان پیش از ویرایش
        public string Label = "", Detail = "", Replacement = "";
        public int Level;               // برای سرفصل‌ها
        public int FootnoteIndex;       // برای پانویس ← متن
        public bool Checked = true;
    }

    /// <summary>
    /// همهٔ کار با مدل شیء ورد (با پیوند دیرهنگام: بدون وابستگی به نسخهٔ آفیس).
    /// هر عملیات تغییردهنده در یک «واگرد سفارشی» پیچیده می‌شود تا با یک Ctrl+Z برگردد.
    /// </summary>
    public static class WordOps
    {
        public static dynamic App;   // در Connect مقداردهی می‌شود

        public static bool Ready => App != null && SafeCount() > 0;

        private static int SafeCount() { try { return (int)App.Documents.Count; } catch { return 0; } }

        public static dynamic Doc => Ready ? App.ActiveDocument : null;

        private static float Cm(double cm) => (float)(cm * 28.3464567);

        public static int Version
        {
            get { try { return (int)double.Parse(((string)App.Version).Split('.')[0]); } catch { return 0; } }
        }

        private sealed class Undo : IDisposable
        {
            private readonly dynamic _rec;
            private readonly bool _screen;

            public Undo(string name)
            {
                try { _rec = App.UndoRecord; _rec.StartCustomRecord("متن‌یاب: " + name); } catch { _rec = null; }
                try { _screen = App.ScreenUpdating; App.ScreenUpdating = false; } catch { }
            }

            public void Dispose()
            {
                try { if (_rec != null) _rec.EndCustomRecord(); } catch { }
                try { App.ScreenUpdating = true; App.ScreenRefresh(); } catch { }
            }
        }

        public static void UndoLast() { try { Doc?.Undo(1); } catch (Exception e) { Log.Error("undo", e); } }
        public static void RedoLast() { try { Doc?.Redo(1); } catch (Exception e) { Log.Error("redo", e); } }

        // ================================================================ انتخاب و درج

        public static string SelectionText()
        {
            try { return ((string)App.Selection.Range.Text ?? "").Replace("\r", " ").Replace("\a", " ").Trim(); } catch { return ""; }
        }

        /// <summary>متن بند فعلی یا متن انتخاب‌شده (برای هوش مصنوعی)</summary>
        public static dynamic CurrentBlock(out string text)
        {
            text = "";
            if (!Ready) return null;
            dynamic r = App.Selection.Range;
            if ((int)r.Start == (int)r.End) r = r.Paragraphs[1].Range;
            var t = (string)r.Text ?? "";
            if (t.EndsWith("\r")) r = Doc.Range(r.Start, (int)r.End - 1);
            text = ((string)r.Text ?? "").Trim();
            return r;
        }

        public static void ReplaceBlock(dynamic r, string text, bool track)
        {
            dynamic d = Doc;
            bool old = false;
            using (new Undo("بازنویسی بند"))
            {
                try { old = d.TrackRevisions; if (track) d.TrackRevisions = true; } catch { }
                r.Text = text;
                try { d.TrackRevisions = old; } catch { }
            }
        }

        public static void InsertBelow(dynamic r, string text)
        {
            using (new Undo("درج پیشنهاد"))
            {
                dynamic p = r.Paragraphs[r.Paragraphs.Count].Range;
                p.InsertParagraphAfter();
                dynamic n = Doc.Range((int)p.End, (int)p.End);
                n.InsertAfter(text);
            }
        }

        /// <summary>درج برگرفته از متن‌یاب: نقل‌قول درون‌خطی یا بلوکی + ارجاع + کامنت منبع</summary>
        public static void InsertExcerpt(string text, SourceInfo src, string page, bool comment, bool cite, string origin)
        {
            var s = AddinSettings.Current;
            dynamic d = Doc;
            if (d == null) throw new InvalidOperationException("یک سند ورد باز کنید.");
            text = (text ?? "").Trim();
            var words = text.Split(new[] { ' ', '\n' }, StringSplitOptions.RemoveEmptyEntries).Length;
            var fa = s.CitationLang == "fa" || (s.CitationLang == "auto" && Fa.HasPersian(text));
            var citeText = cite ? " " + Apa.InText(src, page, s.CitationLang == "auto" ? (fa ? "fa" : "en") : s.CitationLang) : "";
            using (new Undo("درج از متن‌یاب"))
            {
                dynamic sel = App.Selection;
                dynamic r = sel.Range;
                dynamic inserted;
                if (words > s.BlockQuoteWords)
                {
                    // نقل‌قول بلوکی (APA: بیش از ۴۰ واژه) در بند جداگانه
                    if ((int)r.Start != (int)r.Paragraphs[1].Range.Start) { r.InsertParagraphAfter(); r = d.Range((int)r.End, (int)r.End); }
                    var start = (int)r.Start;
                    r.InsertAfter(text + citeText);
                    inserted = d.Range(start, start + (text + citeText).Length);
                    inserted.InsertParagraphAfter();
                    try { inserted.Style = d.Styles[-181]; } // wdStyleQuote
                    catch { inserted.ParagraphFormat.LeftIndent = Cm(1); inserted.ParagraphFormat.RightIndent = Cm(1); }
                }
                else
                {
                    var q = fa ? "«" + text + "»" : "“" + text + "”";
                    var start = (int)r.Start;
                    r.Text = q + citeText;
                    inserted = d.Range(start, start + (q + citeText).Length);
                }
                if (comment)
                {
                    var note = "منبع: " + Apa.Reference(src, fa ? "fa" : "en").Replace("*", "") +
                               (string.IsNullOrEmpty(origin) ? "" : "\nفایل: " + origin) +
                               "\nبرگرفته با متن‌یاب — " + DateTime.Now.ToString("yyyy/MM/dd HH:mm");
                    try { dynamic c = d.Comments.Add(inserted, note); try { c.Author = "متن‌یاب"; c.Initial = "م"; } catch { } }
                    catch (Exception e) { Log.Error("comment", e); }
                }
                if (cite) RegisterSource(src);
                sel.SetRange((int)inserted.End, (int)inserted.End);
            }
        }

        // ================================================================ فهرست منابع

        private const string RefVar = "MatnyaabRefs";
        private const string RefMark = "MatnyaabReferences";

        public static List<SourceInfo> DocSources()
        {
            try { var v = (string)Doc.Variables[RefVar].Value; return Json.Read<List<SourceInfo>>(v) ?? new List<SourceInfo>(); }
            catch { return new List<SourceInfo>(); }
        }

        public static void RegisterSource(SourceInfo s)
        {
            var list = DocSources();
            list.RemoveAll(x => string.Equals(x.Path, s.Path, StringComparison.OrdinalIgnoreCase) && x.Path.Length > 0 || Apa.Reference(x) == Apa.Reference(s));
            list.Add(s);
            try { Doc.Variables[RefVar].Delete(); } catch { }
            Doc.Variables.Add(RefVar, Json.Write(list));
        }

        /// <summary>ساخت یا به‌روزرسانی بخش «منابع» در پایان سند (مرتب الفبایی، تورفتگی آویزان، ایتالیک APA)</summary>
        public static int UpdateBibliography()
        {
            dynamic d = Doc;
            var list = DocSources().OrderBy(x => Apa.IsPersian(x) ? 0 : 1).ThenBy(x => Apa.Reference(x).TrimStart('*'), StringComparer.CurrentCulture).ToList();
            if (list.Count == 0) return 0;
            using (new Undo("فهرست منابع"))
            {
                dynamic r;
                if ((bool)d.Bookmarks.Exists(RefMark)) { r = d.Bookmarks[RefMark].Range; r.Text = ""; }
                else
                {
                    dynamic end = d.Content; end.Collapse(0);
                    end.InsertParagraphAfter();
                    dynamic h = d.Range((int)d.Content.End - 1, (int)d.Content.End - 1);
                    h.InsertAfter(Fa.HasPersian(Apa.Reference(list[0])) ? "منابع" : "References");
                    try { h.Style = d.Styles[-2]; } catch { }
                    h.InsertParagraphAfter();
                    r = d.Range((int)d.Content.End - 1, (int)d.Content.End - 1);
                }
                var start = (int)r.Start;
                var plain = list.Select(x => Apa.Reference(x)).ToList();
                r.InsertAfter(string.Join("\r", plain.Select(p => p.Replace("*", ""))));
                dynamic all = d.Range(start, start + plain.Sum(p => p.Replace("*", "").Length) + plain.Count - 1);
                try { all.Style = d.Styles[-1]; } catch { }
                try { all.Paragraphs.TabHangingIndent(2); all.ParagraphFormat.FirstLineIndent = -Cm(1); } catch { }
                // ایتالیک بخش‌های میان *
                var pos = start;
                foreach (var p in plain)
                {
                    var off = 0; var open = -1;
                    for (var i = 0; i < p.Length; i++)
                    {
                        if (p[i] != '*') { off++; continue; }
                        if (open < 0) open = pos + off;
                        else { try { d.Range(open, pos + off).Font.Italic = true; } catch { } open = -1; }
                    }
                    pos += p.Replace("*", "").Length + 1;
                }
                try { d.Bookmarks.Add(RefMark, all); } catch { }
            }
            return list.Count;
        }

        // ================================================================ قالب‌ها

        private static void SetFont(dynamic f, DocTemplate t, double size, bool? bold, string faFont = null)
        {
            f.Name = t.FontEn;
            try { f.NameBi = faFont ?? t.FontFa; f.SizeBi = (float)size; } catch { }
            f.Size = (float)(t.Rtl ? Math.Max(8, size - (t.BodySize - t.LatinSize)) : size);
            if (bold.HasValue) { f.Bold = bold.Value ? -1 : 0; try { f.BoldBi = bold.Value ? -1 : 0; } catch { } }
        }

        private static void Para(dynamic pf, DocTemplate t, bool body)
        {
            try { pf.ReadingOrder = t.Rtl ? 0 : 1; } catch { }
            pf.LineSpacingRule = 5; // wdLineSpaceMultiple
            pf.LineSpacing = (float)(12 * t.LineSpacing);
            pf.SpaceAfter = (float)(body ? t.SpaceAfter : 3);
            if (body) { pf.FirstLineIndent = Cm(t.FirstIndent); pf.Alignment = t.Justify ? 3 : (t.Rtl ? 2 : 0); }
        }

        private static dynamic Style(dynamic d, int builtin) { try { return d.Styles[builtin]; } catch { return null; } }

        public static void ApplyTemplate(DocTemplate t)
        {
            dynamic d = Doc;
            if (d == null) throw new InvalidOperationException("یک سند ورد باز کنید.");
            using (new Undo("قالب «" + t.Name + "»"))
            {
                dynamic ps = d.PageSetup;
                ps.PageWidth = Cm(t.PageW); ps.PageHeight = Cm(t.PageH);
                ps.TopMargin = Cm(t.MarginTop); ps.BottomMargin = Cm(t.MarginBottom);
                ps.RightMargin = Cm(t.MarginRight); ps.LeftMargin = Cm(t.MarginLeft);
                try { ps.Gutter = Cm(t.Gutter); ps.GutterPos = t.Rtl ? 2 : 0; } catch { }
                try { ps.TextColumns.SetCount(Math.Max(1, t.Columns)); } catch { }
                try { ps.SectionDirection = t.Rtl ? 0 : 1; } catch { }

                var normal = Style(d, -1);
                if (normal != null) { SetFont(normal.Font, t, t.BodySize, false); Para(normal.ParagraphFormat, t, true); }

                var hs = new[] { t.H1Size, t.H2Size, t.H3Size };
                for (var i = 0; i < 3; i++)
                {
                    var h = Style(d, -2 - i);
                    if (h == null) continue;
                    SetFont(h.Font, t, hs[i], true, t.HeadingFontFa);
                    try { h.Font.Color = 0; h.Font.ColorIndex = 1; } catch { }
                    dynamic pf = h.ParagraphFormat;
                    try { pf.ReadingOrder = t.Rtl ? 0 : 1; } catch { }
                    pf.SpaceBefore = (float)t.HeadingSpaceBefore; pf.SpaceAfter = 6; pf.FirstLineIndent = 0;
                    pf.KeepWithNext = -1;
                    pf.Alignment = i == 0 && t.H1Center ? 1 : (t.Rtl ? 2 : 0);
                    if (i == 0) pf.PageBreakBefore = t.H1PageBreak ? -1 : 0;
                }

                var title = Style(d, -63);
                if (title != null) { SetFont(title.Font, t, t.TitleSize, true, t.HeadingFontFa); try { title.ParagraphFormat.Alignment = 1; title.ParagraphFormat.ReadingOrder = t.Rtl ? 0 : 1; } catch { } }
                var fn = Style(d, -30);
                if (fn != null) { SetFont(fn.Font, t, t.FootnoteSize, null); try { fn.ParagraphFormat.LineSpacingRule = 0; fn.ParagraphFormat.SpaceAfter = 0; fn.ParagraphFormat.FirstLineIndent = 0; } catch { } }
                var cap = Style(d, -35);
                if (cap != null) { SetFont(cap.Font, t, t.CaptionSize, true); try { cap.Font.Color = 0; cap.ParagraphFormat.Alignment = 1; } catch { } }
                var quote = Style(d, -181);
                if (quote != null)
                {
                    SetFont(quote.Font, t, t.QuoteSize, null);
                    try { quote.Font.Italic = 0; quote.Font.Color = 0; quote.ParagraphFormat.LeftIndent = Cm(t.QuoteIndent); quote.ParagraphFormat.RightIndent = Cm(t.QuoteIndent); quote.ParagraphFormat.FirstLineIndent = 0; } catch { }
                }

                ApplyPageNumbers(d, t);
            }
        }

        private static void ApplyPageNumbers(dynamic d, DocTemplate t)
        {
            if (t.PageNumbers == "none") return;
            try
            {
                foreach (dynamic sec in d.Sections)
                {
                    dynamic hf = t.PageNumbers.StartsWith("top") ? sec.Headers[1] : sec.Footers[1];
                    if ((int)hf.PageNumbers.Count > 0) continue;
                    var align = t.PageNumbers.EndsWith("outside") ? 4 : 1; // wdAlignPageNumberOutside / Center
                    hf.PageNumbers.Add(align, true);
                }
            }
            catch (Exception e) { Log.Error("pagenumbers", e); }
        }

        public static void InsertToc()
        {
            dynamic d = Doc;
            using (new Undo("فهرست مطالب"))
            {
                dynamic r = App.Selection.Range;
                d.TablesOfContents.Add(Range: r, UseHeadingStyles: true, UpperHeadingLevel: 1, LowerHeadingLevel: 3);
            }
        }

        public static void UpdateFields()
        {
            try { foreach (dynamic toc in Doc.TablesOfContents) toc.Update(); } catch { }
        }

        // ================================================================ پیمایش بندها

        private static IEnumerable<(dynamic para, int start, int end, string text)> Paragraphs(int max = 20000)
        {
            dynamic d = Doc;
            if (d == null) yield break;
            var n = 0;
            foreach (dynamic p in d.Paragraphs)
            {
                if (n++ > max) yield break;
                dynamic r = p.Range;
                yield return (p, (int)r.Start, (int)r.End, (string)r.Text ?? "");
            }
        }

        private static bool Verify(DocHit h)
        {
            try { return (string)Doc.Range(h.Start, h.End).Text == h.Expect; } catch { return false; }
        }

        // ---------------------------------------------------------------- سرفصل‌ها

        public static List<DocHit> ScanHeadings()
        {
            var infos = new List<ParaInfo>(); var spans = new List<(int s, int e)>();
            foreach (var (p, s, e, text) in Paragraphs())
            {
                int lvl = 0; float size = 0; bool bold = false, center = false;
                try { lvl = (int)p.OutlineLevel; if (lvl >= 10) lvl = 0; } catch { }
                try { var fs = (float)p.Range.Font.Size; size = fs > 200 ? 0 : fs; } catch { }
                try { var fb = (float)p.Range.Font.SizeBi; if (fb > 0 && fb < 200 && Fa.HasPersian(text)) size = fb; } catch { }
                try { var b = (int)p.Range.Font.Bold; var bb = (int)p.Range.Font.BoldBi; bold = b == -1 || bb == -1; } catch { }
                try { center = (int)p.Alignment == 1; } catch { }
                infos.Add(new ParaInfo { Index = infos.Count, Text = text, Size = size, Bold = bold, Centered = center, ExistingLevel = lvl });
                spans.Add((s, e));
            }
            return HeadingDetector.Detect(infos).Select(g => new DocHit
            {
                Start = spans[g.Index].s, End = spans[g.Index].e, Label = g.Text, Detail = g.Reason, Level = g.Level, Checked = !g.Existing,
                Expect = infos[g.Index].Text,
            }).ToList();
        }

        public static int ApplyHeadings(IEnumerable<DocHit> hits)
        {
            var n = 0;
            using (new Undo("سرفصل‌ها"))
                foreach (var h in hits.Where(x => x.Checked))
                {
                    if (!Verify(h)) continue;
                    try { Doc.Range(h.Start, h.End).Paragraphs[1].Style = Doc.Styles[-1 - Math.Max(1, Math.Min(9, h.Level))]; n++; }
                    catch (Exception e) { Log.Error("heading", e); }
                }
            return n;
        }

        // ---------------------------------------------------------------- اصطلاحات ↔ پانویس

        public static List<DocHit> ScanLatinTerms()
        {
            var r = new List<DocHit>();
            foreach (var (_, s, _, text) in Paragraphs())
                foreach (var t in LatinTerms.Find(text))
                    r.Add(new DocHit { Start = s + t.Start, End = s + t.Start + t.Length, Expect = text.Substring(t.Start, t.Length), Label = t.Term, Detail = t.Context });
            return r;
        }

        public static int TermsToFootnotes(IEnumerable<DocHit> hits)
        {
            var n = 0;
            dynamic d = Doc;
            using (new Undo("اصطلاحات ← پانویس"))
                foreach (var h in hits.Where(x => x.Checked).OrderByDescending(x => x.Start))
                {
                    if (!Verify(h)) continue;
                    try
                    {
                        d.Range(h.Start, h.End).Delete();
                        dynamic at = d.Range(h.Start, h.Start);
                        dynamic fn = d.Footnotes.Add(Range: at, Text: h.Label);
                        try { fn.Range.ParagraphFormat.ReadingOrder = 1; fn.Range.ParagraphFormat.Alignment = 0; } catch { }
                        n++;
                    }
                    catch (Exception e) { Log.Error("term->fn", e); }
                }
            return n;
        }

        public static List<DocHit> ScanLatinFootnotes()
        {
            var r = new List<DocHit>();
            dynamic d = Doc;
            if (d == null) return r;
            var count = (int)d.Footnotes.Count;
            for (var i = 1; i <= count; i++)
            {
                dynamic fn = d.Footnotes[i];
                var t = ((string)fn.Range.Text ?? "").Trim();
                if (!LatinTerms.IsTerm(t)) continue;
                dynamic rf = fn.Reference;
                var ctx = "";
                try { var a = Math.Max(0, (int)rf.Start - 30); ctx = "…" + ((string)d.Range(a, (int)rf.Start).Text ?? ""); } catch { }
                r.Add(new DocHit { FootnoteIndex = i, Start = rf.Start, End = rf.End, Label = t, Detail = ctx });
            }
            return r;
        }

        public static int FootnotesToTerms(IEnumerable<DocHit> hits)
        {
            var n = 0;
            dynamic d = Doc;
            using (new Undo("پانویس ← درون متن"))
                foreach (var h in hits.Where(x => x.Checked).OrderByDescending(x => x.FootnoteIndex))
                {
                    try
                    {
                        dynamic fn = d.Footnotes[h.FootnoteIndex];
                        if (((string)fn.Range.Text ?? "").Trim() != h.Label) continue;
                        var at = (int)fn.Reference.Start;
                        fn.Delete();
                        d.Range(at, at).InsertAfter(" (" + h.Label + ")");
                        n++;
                    }
                    catch (Exception e) { Log.Error("fn->term", e); }
                }
            return n;
        }

        // ---------------------------------------------------------------- پاک‌سازی

        public static List<DocHit> ScanCleanup(IEnumerable<string> rules)
        {
            var ids = rules.ToList();
            var r = new List<DocHit>();
            foreach (var (_, s, _, text) in Paragraphs())
                foreach (var e in Cleanup.Plan(text, ids))
                    r.Add(new DocHit { Start = s + e.Start, End = s + e.Start + e.Length, Expect = e.Original, Replacement = e.Replacement, Label = Show(e.Original) + " ← " + Show(e.Replacement), Detail = e.Before.Trim() });
            return r;
        }

        private static string Show(string s) => s == "\u200C" ? "[نیم‌فاصله]" : s == " " ? "[فاصله]" : s.Length == 0 ? "[حذف]" : s.Trim().Length == 0 ? "[فاصله‌ها]" : "«" + s + "»";

        public static int ApplyCleanup(IEnumerable<DocHit> hits)
        {
            var n = 0;
            dynamic d = Doc;
            using (new Undo("یکدست‌سازی متن"))
                foreach (var h in hits.Where(x => x.Checked).OrderByDescending(x => x.Start))
                {
                    if (h.End > h.Start && !Verify(h)) continue;
                    try
                    {
                        dynamic rr = d.Range(h.Start, h.End);
                        if (h.End > h.Start) rr.Text = h.Replacement; else rr.InsertAfter(h.Replacement);
                        n++;
                    }
                    catch (Exception e) { Log.Error("cleanup", e); }
                }
            return n;
        }

        public static string DocFolder()
        {
            try { var p = (string)Doc.Path; return string.IsNullOrEmpty(p) ? null : p; } catch { return null; }
        }

        public static string DocTitle()
        {
            try { return (string)Doc.Name; } catch { return ""; }
        }
    }
}
