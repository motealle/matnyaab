using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.RegularExpressions;
using Matnyaab.Word.Core;

namespace Matnyaab.Word.Ops
{
    /// <summary>یک بخش از سند رسمی: متن، نقش (عنوان، برچسب، متن، امضا…) و چینش.</summary>
    public class OfficeLine
    {
        public string Text = "", Role = "body"; // title | sub | label | body | sign | small | table
        public int Align = 3;                   // 0 چپ، 1 وسط، 2 راست، 3 دوطرفه (در RTL «راست» یعنی ابتدای سطر)
        public string[][] Rows;                 // برای Role = table
    }

    public static partial class WordOps
    {
        /// <summary>سند تازه با صفحه‌آرایی رسمی فارسی (A4، حاشیهٔ ۲٫۵، قلم نازنین/لوتوس) و بندهای داده‌شده.</summary>
        public static void NewOfficeDocument(IList<OfficeLine> lines, string font = "B Nazanin", double size = 13, bool letterhead = false)
        {
            if (App == null) throw new InvalidOperationException("ورد در دسترس نیست.");
            dynamic d = App.Documents.Add();
            using (new Undo("سند رسمی"))
            {
                try
                {
                    dynamic ps = d.PageSetup;
                    ps.PageWidth = Cm(21); ps.PageHeight = Cm(29.7);
                    ps.TopMargin = Cm(letterhead ? 4.5 : 2.5); ps.BottomMargin = Cm(2.5); ps.LeftMargin = Cm(2.5); ps.RightMargin = Cm(2.5);
                }
                catch (Exception e) { Log.Error("office.page", e); }
                try
                {
                    dynamic normal = d.Styles[-1]; // wdStyleNormal
                    normal.Font.Name = font; normal.Font.NameBi = font; normal.Font.SizeBi = (float)size; normal.Font.Size = (float)size;
                    normal.ParagraphFormat.ReadingOrder = 0; normal.ParagraphFormat.SpaceAfter = 4; normal.ParagraphFormat.LineSpacingRule = 5; normal.ParagraphFormat.LineSpacing = 12 * 1.25f;
                }
                catch (Exception e) { Log.Error("office.style", e); }

                dynamic r = d.Content;
                var first = true;
                foreach (var l in lines)
                {
                    if (!first) r.InsertParagraphAfter();
                    first = false;
                    dynamic p = d.Paragraphs[d.Paragraphs.Count].Range;
                    if (l.Role == "table" && l.Rows != null && l.Rows.Length > 0)
                    {
                        p.Text = "";
                        dynamic t = d.Tables.Add(p, l.Rows.Length, l.Rows[0].Length);
                        try { t.Borders.Enable = 1; t.Range.ParagraphFormat.ReadingOrder = 0; t.TableDirection = 0; } catch { }
                        for (var i = 0; i < l.Rows.Length; i++)
                            for (var j = 0; j < l.Rows[i].Length; j++)
                            {
                                t.Cell(i + 1, j + 1).Range.Text = l.Rows[i][j] ?? "";
                                if (i == 0) try { t.Cell(i + 1, j + 1).Range.Font.BoldBi = 1; t.Cell(i + 1, j + 1).Shading.BackgroundPatternColor = 15132390; } catch { }
                            }
                        r = d.Content;
                        continue;
                    }
                    p.InsertAfter(l.Text ?? "");
                    p = d.Paragraphs[d.Paragraphs.Count].Range;
                    try
                    {
                        p.ParagraphFormat.Alignment = l.Align; p.ParagraphFormat.ReadingOrder = 0;
                        p.Font.BoldBi = (l.Role == "title" || l.Role == "label" || l.Role == "sign") ? 1 : 0;
                        p.Font.Bold = p.Font.BoldBi;
                        var sz = l.Role == "title" ? size + 3 : l.Role == "sub" ? size + 1 : l.Role == "small" ? size - 2 : size;
                        p.Font.SizeBi = (float)sz; p.Font.Size = (float)sz;
                        if (l.Role == "body") p.ParagraphFormat.FirstLineIndent = Cm(0.6);
                        if (l.Role == "sign") p.ParagraphFormat.SpaceBefore = 18;
                        if (l.Role == "title") p.ParagraphFormat.SpaceAfter = 12;
                    }
                    catch (Exception e) { Log.Error("office.para", e); }
                    r = d.Content;
                }
            }
            try { d.Activate(); } catch { }
        }

        /// <summary>نقل‌قول‌های سند («…» یا "…") با دست‌کم شش واژه، برای راستی‌آزمایی.</summary>
        public static List<string> QuotedSegments()
        {
            var r = new List<string>();
            if (!Ready) return r;
            string all;
            try { all = (string)Doc.Content.Text ?? ""; } catch { return r; }
            foreach (Match m in Regex.Matches(all, "[«\"“]([^«»\"“”\\r]{25,1200})[»\"”]"))
            {
                var t = m.Groups[1].Value.Trim();
                if (t.Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries).Length >= 6 && !r.Contains(t)) r.Add(t);
            }
            return r;
        }

        /// <summary>جمله‌های سند (دست‌کم هشت واژه) برای پیش‌همانندجویی.</summary>
        public static List<string> Sentences(int max = 400)
        {
            var r = new List<string>();
            if (!Ready) return r;
            string all;
            try { all = (string)Doc.Content.Text ?? ""; } catch { return r; }
            foreach (var s in Regex.Split(all, "(?<=[.!؟?؛\\r])"))
            {
                var t = s.Trim();
                if (t.Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries).Length >= 8) r.Add(t);
                if (r.Count >= max) break;
            }
            return r;
        }

        /// <summary>انتخاب متن در سند (برای پرش به نقل‌قول بدون منبع)</summary>
        public static void Find(string text)
        {
            if (!Ready || string.IsNullOrEmpty(text)) return;
            try
            {
                dynamic f = Doc.Content; var key = text.Length > 200 ? text.Substring(0, 200) : text;
                f.Find.ClearFormatting();
                if ((bool)f.Find.Execute(key)) f.Select();
            }
            catch (Exception e) { Log.Error("find", e); }
        }
    }
}
