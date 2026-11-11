using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;

namespace Matnyaab.Word.Core
{
    /// <summary>یک ویرایش پیشنهادی درون یک بند (موقعیت نسبت به آغاز بند)</summary>
    public class TextEdit
    {
        public int Start;
        public int Length;
        public string Replacement = "";
        public string Rule = "";
        public string Before = "";   // متن پیرامون برای پیش‌نمایش
        public string Original = "";
    }

    /// <summary>اصطلاح لاتین داخل پرانتز که می‌تواند پانویس شود</summary>
    public class LatinTerm
    {
        public int Start;            // آغاز «(» نسبت به بند (با فاصلهٔ پیشین اگر باشد)
        public int Length;
        public string Term = "";
        public string Context = "";  // چند واژهٔ پیش از اصطلاح
    }

    public static class LatinTerms
    {
        private static readonly Regex Paren = new Regex(@"(\s?)\(\s*([A-Za-z][^()؀-ۿ\r\n]{0,118}?)\s*\)", RegexOptions.Compiled);
        private static readonly Regex Year = new Regex(@"\b(1[5-9]|20)\d\d[a-z]?\b", RegexOptions.Compiled);
        private static readonly Regex Abbrev = new Regex(@"^(e\.?g|i\.?e|etc|cf|ibid|p|pp|vol|no|fig|see)\b\.?", RegexOptions.IgnoreCase | RegexOptions.Compiled);

        /// <summary>آیا متن یک اصطلاح لاتین است (نه ارجاع، نه شمارهٔ صفحه، نه فارسی)؟</summary>
        public static bool IsTerm(string t)
        {
            if (string.IsNullOrWhiteSpace(t)) return false;
            t = t.Trim();
            if (t.Length < 2 || t.Length > 120 || Fa.HasPersian(t)) return false;
            if (!t.Any(char.IsLetter) || t.Count(char.IsLetter) < 2) return false;
            if (Year.IsMatch(t) || t.Contains(" et al") || Abbrev.IsMatch(t)) return false;
            if (t.Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries).Length > 10) return false;
            if (t.Contains("http") || t.Contains("@")) return false;
            return true;
        }

        public static List<LatinTerm> Find(string paragraph)
        {
            var r = new List<LatinTerm>();
            if (string.IsNullOrEmpty(paragraph) || !Fa.HasPersian(paragraph)) return r;
            foreach (Match m in Paren.Matches(paragraph))
            {
                var term = m.Groups[2].Value.Trim();
                if (!IsTerm(term)) continue;
                // اصطلاح باید پس از یک واژهٔ فارسی بیاید (نه در آغاز بند یا پس از عدد)
                var before = paragraph.Substring(0, m.Index).TrimEnd();
                if (before.Length == 0 || !Fa.HasPersian(before.Substring(Math.Max(0, before.Length - 1)))) continue;
                var ctx = before.Length > 40 ? "…" + before.Substring(before.Length - 40) : before;
                r.Add(new LatinTerm { Start = m.Index, Length = m.Length, Term = term, Context = ctx });
            }
            return r;
        }
    }

    // ==================================================================== سرفصل‌ها

    public class ParaInfo
    {
        public int Index;
        public string Text = "";
        public float Size;          // 0 = نامشخص/مختلط
        public bool Bold;
        public bool Centered;
        public int ExistingLevel;   // 1..9 اگر از پیش سبک عنوان دارد، 0 = متن
    }

    public class HeadingGuess
    {
        public int Index;
        public string Text = "";
        public int Level;
        public string Reason = "";
        public bool Existing;
    }

    /// <summary>تشخیص خودکار سرفصل‌ها با ترکیب نشانه‌های محتوایی و قالبی</summary>
    public static class HeadingDetector
    {
        private static readonly Regex Numbered = new Regex(@"^\s*([0-9۰-۹]+(?:\s*[-./]\s*[0-9۰-۹]+){0,4})\s*[-.)]?\s+\S", RegexOptions.Compiled);
        private static readonly Regex Chapter = new Regex(@"^\s*(فصل|بخش|گفتار|مبحث|Chapter|Part|Section)\s+\S", RegexOptions.Compiled | RegexOptions.IgnoreCase);
        private static readonly string[] Keywords =
        {
            "مقدمه", "چکیده", "پیشگفتار", "درآمد", "کلیات", "کلیات پژوهش", "نتیجه‌گیری", "نتیجه گیری", "جمع‌بندی", "جمع بندی",
            "منابع", "فهرست منابع", "منابع و مآخذ", "کتابنامه", "پیوست‌ها", "پیوست", "پیشنهادها", "پیشنهادات", "سپاسگزاری", "تقدیر و تشکر",
            "پیشینهٔ پژوهش", "پیشینه پژوهش", "روش پژوهش", "یافته‌ها", "بحث", "Abstract", "Introduction", "Conclusion", "Conclusions",
            "References", "Bibliography", "Acknowledgments", "Acknowledgements", "Discussion", "Results", "Methods", "Method", "Appendix",
        };

        private static int Words(string t) => t.Split(new[] { ' ', '\t', '‌' }, StringSplitOptions.RemoveEmptyEntries).Length;

        private static string Clean(string t) => (t ?? "").Replace("\r", "").Replace("\a", "").Replace("\f", "").Trim();

        public static List<HeadingGuess> Detect(IList<ParaInfo> paras)
        {
            var result = new List<HeadingGuess>();
            var sizes = paras.Where(p => p.Size > 0 && Words(Clean(p.Text)) > 12).GroupBy(p => p.Size).OrderByDescending(g => g.Count()).FirstOrDefault();
            var body = sizes?.Key ?? 0f;

            foreach (var p in paras)
            {
                var t = Clean(p.Text);
                if (t.Length == 0) continue;
                if (p.ExistingLevel > 0) { result.Add(new HeadingGuess { Index = p.Index, Text = t, Level = p.ExistingLevel, Reason = "سبک عنوان دارد", Existing = true }); continue; }
                if (t.Length > 150 || Words(t) > 16) continue;
                var last = t[t.Length - 1];
                if (".؟?!،,؛;".IndexOf(last) >= 0) continue;

                var bare = t.TrimEnd(':', '：').Trim();
                int level = 0; string why = "";
                var ch = Chapter.Match(t);
                var num = Numbered.Match(t);
                if (ch.Success)
                {
                    var w = ch.Groups[1].Value.ToLowerInvariant();
                    level = w == "فصل" || w == "chapter" || w == "part" ? 1 : w == "بخش" || w == "section" ? 2 : 3;
                    why = "با «" + ch.Groups[1].Value + "» آغاز می‌شود";
                }
                else if (Keywords.Any(k => string.Equals(bare, k, StringComparison.OrdinalIgnoreCase)))
                {
                    level = 1; why = "عنوان رایج پژوهشی";
                }
                else if (num.Success && Words(t) <= 12)
                {
                    var groups = Regex.Split(num.Groups[1].Value, @"\s*[-./]\s*").Count(s => s.Length > 0);
                    level = Math.Min(4, Math.Max(1, groups));
                    why = "شماره‌گذاری " + Fa.Num(num.Groups[1].Value.Trim());
                    // فهرست‌های شماره‌دار کوتاه بدون قالب پررنگ ممکن است بند فهرست باشند
                    if (groups == 1 && !p.Bold && (body == 0 || p.Size <= body)) { level = 0; }
                }
                else if (p.Bold && Words(t) <= 12)
                {
                    if (body > 0 && p.Size >= body + 4) { level = 1; why = "پررنگ و درشت"; }
                    else if (body > 0 && p.Size >= body + 2) { level = 2; why = "پررنگ و کمی درشت"; }
                    else if (p.Centered) { level = 1; why = "پررنگ و وسط‌چین"; }
                    else { level = 3; why = "بند کوتاه پررنگ"; }
                }
                else if (body > 0 && p.Size >= body + 3 && Words(t) <= 10)
                {
                    level = p.Size >= body + 5 ? 1 : 2; why = "درشت‌تر از متن";
                }
                if (level > 0) result.Add(new HeadingGuess { Index = p.Index, Text = t, Level = level, Reason = why });
            }
            return result;
        }
    }

    // ==================================================================== پاک‌سازی و یکدست‌سازی

    public class CleanupRule
    {
        public string Id = "";
        public string Title = "";
        public string Hint = "";
        public bool Default = true;
        public Func<string, IEnumerable<TextEdit>> Run;
    }

    public static class Cleanup
    {
        private const string FaL = "ء-غف-يپچژکگیۀ";

        private static IEnumerable<TextEdit> Re(string text, string rule, Regex re, Func<Match, string> repl, int group = 0)
        {
            foreach (Match m in re.Matches(text))
            {
                var g = m.Groups[group];
                var rep = repl(m);
                if (rep == g.Value) continue;
                yield return new TextEdit { Start = g.Index, Length = g.Length, Replacement = rep, Rule = rule, Original = g.Value, Before = Around(text, g.Index, g.Length) };
            }
        }

        private static string Around(string t, int i, int len)
        {
            var a = Math.Max(0, i - 18); var b = Math.Min(t.Length, i + len + 18);
            return t.Substring(a, b - a).Replace("\r", " ").Replace("\n", " ");
        }

        public static readonly List<CleanupRule> Rules = new List<CleanupRule>
        {
            new CleanupRule { Id = "arabic", Title = "ی و ک عربی ← فارسی", Hint = "ي ← ی ، ك ← ک ، ى ← ی",
                Run = t => Re(t, "arabic", new Regex("[يكى]"), m => m.Value == "ك" ? "ک" : "ی") },
            new CleanupRule { Id = "zwnj-mi", Title = "نیم‌فاصلهٔ «می / نمی»", Hint = "می شود ← می‌شود",
                Run = t => Re(t, "zwnj-mi", new Regex("(?<![" + FaL + "‌])(?:ن?می)( )(?=[" + FaL + "]{2,})"), m => "‌", 1) },
            new CleanupRule { Id = "zwnj-suffix", Title = "نیم‌فاصلهٔ پسوندها", Hint = "کتاب ها ← کتاب‌ها ، بزرگ تر ← بزرگ‌تر",
                Run = t => Re(t, "zwnj-suffix", new Regex("(?<=[" + FaL + "]{2})( )(?=(?:ها|های|هایی|هایم|هایش|هایمان|تر|ترین|تری)(?![" + FaL + "‌]))"), m => "‌", 1) },
            new CleanupRule { Id = "spaces", Title = "فاصله‌های اضافه", Hint = "چند فاصلهٔ پشت‌سرهم ← یک فاصله",
                Run = t => Re(t, "spaces", new Regex("(?<=\\S)[  ]{2,}(?=\\S)"), m => " ") },
            new CleanupRule { Id = "punct-space", Title = "فاصلهٔ پیش از علائم", Hint = "واژه ، ← واژه،",
                Run = t => Re(t, "punct-space", new Regex("(?<=[" + FaL + "])( +)(?=[،؛؟:.!])"), m => "", 1) },
            new CleanupRule { Id = "punct-fa", Title = "علائم لاتین ← فارسی", Hint = "در متن فارسی: , ← ، ؛ ? ← ؟ ؛ ; ← ؛",
                Run = t => Re(t, "punct-fa", new Regex("(?<=[" + FaL + "] ?)([,;?])(?= ?[" + FaL + "]|\\s*$)"), m => m.Groups[1].Value == "," ? "،" : m.Groups[1].Value == ";" ? "؛" : "؟", 1) },
            new CleanupRule { Id = "digits-fa", Title = "اعداد لاتین درون متن فارسی", Hint = "۱۲۳ به جای 123 (ارجاع‌های لاتین دست نمی‌خورد)", Default = false,
                Run = t => Re(t, "digits-fa", new Regex(@"(?<![A-Za-z0-9.])\d+(?:[./]\d+)*(?![A-Za-z0-9])"), m => PersianContext(t, m.Index, m.Length) ? Fa.Num(m.Value) : m.Value) },
            new CleanupRule { Id = "quotes", Title = "گیومهٔ فارسی", Hint = "\"متن\" ← «متن»", Default = false,
                Run = t => Re(t, "quotes", new Regex("\"([^\"\\r\\n]*[" + FaL + "][^\"\\r\\n]*)\""), m => "«" + m.Groups[1].Value + "»") },
        };

        /// <summary>عدد در بافت فارسی است: نزدیک‌ترین حرف دو طرف لاتین نیست و حرف فارسی در همسایگی هست</summary>
        private static bool PersianContext(string t, int i, int len)
        {
            char Near(int from, int step)
            {
                for (int k = from, n = 0; k >= 0 && k < t.Length && n < 14; k += step, n++) if (char.IsLetter(t[k])) return t[k];
                return '\0';
            }
            var a = Near(i - 1, -1); var b = Near(i + len, 1);
            bool Lat(char c) => c != '\0' && c < 0x0250;
            bool Per(char c) => c >= '\u0600' && c <= '\u06FF';
            return !Lat(a) && !Lat(b) && (Per(a) || Per(b));
        }

        /// <summary>همهٔ ویرایش‌های قواعد انتخاب‌شده، بدون هم‌پوشانی، مرتب از انتها به ابتدا</summary>
        public static List<TextEdit> Plan(string text, IEnumerable<string> ruleIds)
        {
            var ids = new HashSet<string>(ruleIds);
            // قواعد دیگر روی نسخهٔ یکسان‌شدهٔ ی/ک اجرا می‌شوند (طول و جایگاه نویسه‌ها یکی است)
            var probe = text.Replace('ي', 'ی').Replace('ى', 'ی').Replace('ك', 'ک');
            var all = Rules.Where(r => ids.Contains(r.Id)).SelectMany(r => r.Run(r.Id == "arabic" ? text : probe)).OrderBy(e => e.Start).ToList();
            foreach (var e in all) e.Original = text.Substring(e.Start, e.Length);
            var outp = new List<TextEdit>();
            var end = -1;
            foreach (var e in all) { if (e.Start >= end) { outp.Add(e); end = e.Start + Math.Max(1, e.Length); } }
            outp.Reverse();
            return outp;
        }

        public static string Apply(string text, IEnumerable<TextEdit> descending)
        {
            var sb = new StringBuilder(text);
            foreach (var e in descending) { sb.Remove(e.Start, e.Length); sb.Insert(e.Start, e.Replacement); }
            return sb.ToString();
        }
    }

    // ==================================================================== ارجاع APA

    public static class Apa
    {
        public static bool IsPersian(SourceInfo s) => Fa.HasPersian((s.Authors ?? "") + s.Title);

        private static List<string[]> Authors(SourceInfo s) =>
            (s.Authors ?? "").Split(new[] { '\n', ';', '؛' }, StringSplitOptions.RemoveEmptyEntries)
                .Select(a => a.Trim()).Where(a => a.Length > 0)
                .Select(a => a.Split(new[] { ',', '،' }, 2).Select(x => x.Trim()).ToArray()).ToList();

        private static string Initials(string first) =>
            string.Join(" ", (first ?? "").Split(new[] { ' ', '-' }, StringSplitOptions.RemoveEmptyEntries).Select(n => char.ToUpperInvariant(n[0]) + "."));

        /// <summary>ارجاع درون‌متنی: (رحیمی، ۱۴۰۲، ص. ۱۲) یا (Smith &amp; Lee, 2020, p. 12)</summary>
        public static string InText(SourceInfo s, string page = "", string lang = "auto")
        {
            var fa = lang == "fa" || (lang == "auto" && IsPersian(s));
            var a = Authors(s);
            string who;
            if (a.Count == 0) who = Shorten(s.Title, fa);
            else if (a.Count == 1) who = a[0][0];
            else if (a.Count == 2) who = a[0][0] + (fa ? " و " : " & ") + a[1][0];
            else who = a[0][0] + (fa ? " و همکاران" : " et al.");
            var year = string.IsNullOrWhiteSpace(s.Year) ? (fa ? "بی‌تا" : "n.d.") : s.Year.Trim();
            var sep = fa ? "، " : ", ";
            var pg = string.IsNullOrWhiteSpace(page) ? "" : sep + (fa ? "ص. " + Fa.Num(page.Trim()) : (page.Contains("-") ? "pp. " : "p. ") + page.Trim());
            return "(" + who + sep + (fa ? Fa.Num(year) : year) + pg + ")";
        }

        private static string Shorten(string t, bool fa)
        {
            var w = (t ?? "").Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries);
            var s = string.Join(" ", w.Take(4));
            return w.Length > 4 ? s + "…" : s.Length > 0 ? s : (fa ? "بی‌نام" : "Anon.");
        }

        /// <summary>فهرست منابع APA ۷؛ بخش‌های ایتالیک با علامت * مشخص می‌شوند تا در ورد قالب‌دهی شوند</summary>
        public static string Reference(SourceInfo s, string lang = "auto")
        {
            var fa = lang == "fa" || (lang == "auto" && IsPersian(s));
            var a = Authors(s);
            string names;
            if (a.Count == 0) names = "";
            else
            {
                var parts = a.Select(x => fa ? (x.Length > 1 ? x[0] + "، " + x[1] : x[0]) : (x.Length > 1 ? x[0] + ", " + Initials(x[1]) : x[0])).ToList();
                names = parts.Count == 1 ? parts[0] : string.Join(fa ? "؛ " : ", ", parts.Take(parts.Count - 1)) + (fa ? " و " : ", & ") + parts.Last();
            }
            var year = string.IsNullOrWhiteSpace(s.Year) ? (fa ? "بی‌تا" : "n.d.") : (fa ? Fa.Num(s.Year.Trim()) : s.Year.Trim());
            var dot = ". ";
            var sb = new StringBuilder();
            if (names.Length > 0) sb.Append(names).Append(names.EndsWith(".") ? " " : dot);
            sb.Append("(").Append(year).Append(")").Append(dot);
            var title = (s.Title ?? "").Trim();
            switch (s.Kind)
            {
                case "article":
                    sb.Append(title).Append(dot);
                    if (!string.IsNullOrWhiteSpace(s.Container))
                    {
                        sb.Append("*").Append(s.Container.Trim());
                        if (!string.IsNullOrWhiteSpace(s.Volume)) sb.Append(fa ? "، " : ", ").Append(fa ? Fa.Num(s.Volume) : s.Volume);
                        sb.Append("*");
                        if (!string.IsNullOrWhiteSpace(s.Issue)) sb.Append("(").Append(fa ? Fa.Num(s.Issue) : s.Issue).Append(")");
                        if (!string.IsNullOrWhiteSpace(s.Pages)) sb.Append(fa ? "، " : ", ").Append(fa ? Fa.Num(s.Pages) : s.Pages);
                        sb.Append(".");
                    }
                    break;
                case "web":
                    sb.Append("*").Append(title).Append("*").Append(".");
                    if (!string.IsNullOrWhiteSpace(s.Container)) sb.Append(" ").Append(s.Container.Trim()).Append(".");
                    break;
                case "thesis":
                    sb.Append("*").Append(title).Append("*").Append(fa ? " [پایان‌نامه]. " : " [Thesis]. ");
                    if (!string.IsNullOrWhiteSpace(s.Container)) sb.Append(s.Container.Trim()).Append(".");
                    break;
                default:
                    sb.Append("*").Append(title).Append("*").Append(".");
                    if (!string.IsNullOrWhiteSpace(s.Container)) sb.Append(" ").Append(s.Container.Trim()).Append(".");
                    break;
            }
            if (!string.IsNullOrWhiteSpace(s.Url)) sb.Append(" ").Append(s.Url.Trim());
            return sb.ToString().Replace("..", ".").Trim();
        }

        /// <summary>منبع پیش‌فرض از روی نام فایل (کاربر سپس کاملش می‌کند)</summary>
        public static SourceInfo Guess(string path)
        {
            var name = System.IO.Path.GetFileNameWithoutExtension(path ?? "") ?? "";
            var s = new SourceInfo { Path = path ?? "", Title = name.Replace('_', ' ').Trim() };
            var m = Regex.Match(name, @"(?<!\d)(1[34]\d\d|19\d\d|20\d\d)(?!\d)");
            if (m.Success) s.Year = m.Value;
            return s;
        }
    }
}
