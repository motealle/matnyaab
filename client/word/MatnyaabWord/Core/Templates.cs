using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;

namespace Matnyaab.Word.Core
{
    /// <summary>قالب صفحه‌آرایی (اندازه‌ها به سانتی‌متر و پوینت)</summary>
    public class DocTemplate
    {
        public string Id { get; set; } = Guid.NewGuid().ToString("N").Substring(0, 10);
        public string Name { get; set; } = "";
        public string Category { get; set; } = "custom";     // thesis | article | report | custom
        public string Publisher { get; set; } = "";
        public string Note { get; set; } = "";
        public bool BuiltIn { get; set; }
        public bool Rtl { get; set; } = true;

        // صفحه
        public string Paper { get; set; } = "A4";
        public double PageW { get; set; } = 21.0;
        public double PageH { get; set; } = 29.7;
        public double MarginTop { get; set; } = 3;
        public double MarginBottom { get; set; } = 2.5;
        public double MarginRight { get; set; } = 3;
        public double MarginLeft { get; set; } = 2.5;
        public double Gutter { get; set; } = 0;
        public int Columns { get; set; } = 1;
        public string PageNumbers { get; set; } = "bottom-center";   // bottom-center | bottom-outside | top-center | none

        // متن
        public string FontFa { get; set; } = "B Nazanin";
        public string FontEn { get; set; } = "Times New Roman";
        public double BodySize { get; set; } = 13;
        public double LatinSize { get; set; } = 11;
        public double LineSpacing { get; set; } = 1.15;
        public double FirstIndent { get; set; } = 0.5;
        public double SpaceAfter { get; set; } = 6;
        public bool Justify { get; set; } = true;

        // عنوان‌ها
        public string HeadingFontFa { get; set; } = "B Titr";
        public double H1Size { get; set; } = 16;
        public double H2Size { get; set; } = 14;
        public double H3Size { get; set; } = 13;
        public bool H1Center { get; set; }
        public bool H1PageBreak { get; set; }
        public double HeadingSpaceBefore { get; set; } = 12;

        // عناصر دیگر
        public double TitleSize { get; set; } = 20;
        public double AbstractSize { get; set; } = 11;
        public double FootnoteSize { get; set; } = 10;
        public double CaptionSize { get; set; } = 10;
        public double QuoteSize { get; set; } = 12;
        public double QuoteIndent { get; set; } = 1;

        public DocTemplate Clone(string newName = null)
        {
            var c = Json.Read<DocTemplate>(Json.Write(this));
            c.Id = Guid.NewGuid().ToString("N").Substring(0, 10);
            c.BuiltIn = false;
            c.Category = Category;
            c.Name = newName ?? (Name + " (رونوشت)");
            return c;
        }

        public string Summary() =>
            Paper + " · حاشیه " + Fa.Num(MarginTop) + "/" + Fa.Num(MarginRight) + "/" + Fa.Num(MarginBottom) + "/" + Fa.Num(MarginLeft) +
            " · " + (Rtl ? FontFa : FontEn) + " " + Fa.Num(BodySize) + (Columns > 1 ? " · " + Fa.Num(Columns) + " ستونه" : "");

        public static readonly Dictionary<string, double[]> Papers = new Dictionary<string, double[]>
        {
            ["A4"] = new[] { 21.0, 29.7 }, ["A5"] = new[] { 14.8, 21.0 }, ["B5"] = new[] { 17.6, 25.0 },
            ["وزیری"] = new[] { 16.5, 23.5 }, ["رقعی"] = new[] { 14.5, 21.5 }, ["Letter"] = new[] { 21.59, 27.94 },
        };

        public static readonly Dictionary<string, string> Categories = new Dictionary<string, string>
        {
            ["thesis"] = "پایان‌نامه و رساله", ["article"] = "مقاله و نشریه", ["report"] = "گزارش", ["custom"] = "قالب‌های من",
        };
    }

    /// <summary>قالب‌های آماده (توکار) + قالب‌های کاربر با امکان تکثیر، ویرایش و حذف</summary>
    public static class TemplateStore
    {
        private static List<DocTemplate> _builtIn;

        public static List<DocTemplate> BuiltIn
        {
            get
            {
                if (_builtIn != null) return _builtIn;
                try
                {
                    using (var s = typeof(TemplateStore).Assembly.GetManifestResourceStream("Matnyaab.Word.templates.json"))
                    using (var r = new StreamReader(s))
                        _builtIn = Json.Read<List<DocTemplate>>(r.ReadToEnd());
                    _builtIn.ForEach(t => t.BuiltIn = true);
                }
                catch (Exception e) { Log.Error("templates", e); _builtIn = new List<DocTemplate>(); }
                return _builtIn;
            }
        }

        public static List<DocTemplate> User { get; private set; } = Json.Load(Paths.TemplatesFile, () => new List<DocTemplate>());

        /// <summary>قالب‌های توکاری که کاربر پنهان کرده است</summary>
        public static List<string> Hidden => _hidden ?? (_hidden = Json.Load(Paths.TemplatesFile + ".hidden", () => new List<string>()));
        private static List<string> _hidden;

        public static IEnumerable<DocTemplate> All => BuiltIn.Where(t => !Hidden.Contains(t.Id)).Concat(User);

        public static DocTemplate Get(string id) => All.FirstOrDefault(t => t.Id == id);

        public static void Save()
        {
            try { Json.Save(Paths.TemplatesFile, User); Json.Save(Paths.TemplatesFile + ".hidden", Hidden); }
            catch (Exception e) { Log.Error("templates.save", e); }
        }

        public static DocTemplate Duplicate(DocTemplate t)
        {
            var c = t.Clone();
            User.Add(c); Save();
            return c;
        }

        public static void Upsert(DocTemplate t)
        {
            if (t.BuiltIn) t = t.Clone(t.Name);
            var i = User.FindIndex(x => x.Id == t.Id);
            if (i >= 0) User[i] = t; else User.Add(t);
            Save();
        }

        public static void Delete(DocTemplate t)
        {
            if (t.BuiltIn) { if (!Hidden.Contains(t.Id)) Hidden.Add(t.Id); }
            else User.RemoveAll(x => x.Id == t.Id);
            Save();
        }

        public static void RestoreBuiltIns() { Hidden.Clear(); Save(); }
    }
}
