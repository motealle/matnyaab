using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Web.Script.Serialization;

namespace Matnyaab.Word.Core
{
    /// <summary>مسیرهای مشترک با برنامهٔ متن‌یاب</summary>
    public static class Paths
    {
        /// <summary>پوشهٔ تنظیمات متن‌یاب (همان APP_SETTINGS_DIR برنامه)</summary>
        public static string MatnyaabDir =>
            Environment.GetEnvironmentVariable("MATNYAAB_HOME") is string h && h.Length > 0 ? h :
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "MATNYAAB");

        public static string SettingsFile => Path.Combine(MatnyaabDir, "word-addin.json");
        public static string TemplatesFile => Path.Combine(MatnyaabDir, "word-templates.json");
        public static string BridgeFile => Path.Combine(MatnyaabDir, "bridge.json");
        public static string MatnyaabIni => Path.Combine(MatnyaabDir, "settings.ini");

        public static string LogFile => Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Matnyaab", "word-addin.log");

        /// <summary>پوشهٔ همین افزونه (برای قلم‌ها)</summary>
        public static string AddinDir => Path.GetDirectoryName(typeof(Paths).Assembly.Location) ?? "";

        public static void Ensure(string file)
        {
            var d = Path.GetDirectoryName(file);
            if (!string.IsNullOrEmpty(d)) Directory.CreateDirectory(d);
        }
    }

    public static class Log
    {
        private static readonly object Gate = new object();

        public static void Write(string msg)
        {
            try
            {
                lock (Gate)
                {
                    Paths.Ensure(Paths.LogFile);
                    var fi = new FileInfo(Paths.LogFile);
                    if (fi.Exists && fi.Length > 512 * 1024) fi.Delete();
                    File.AppendAllText(Paths.LogFile, DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss ") + msg + Environment.NewLine, Encoding.UTF8);
                }
            }
            catch { /* ثبت گزارش هرگز نباید ورد را از کار بیندازد */ }
        }

        public static void Error(string where, Exception e) => Write("ERROR " + where + ": " + e);
    }

    public static class Json
    {
        public static JavaScriptSerializer S() => new JavaScriptSerializer { MaxJsonLength = int.MaxValue, RecursionLimit = 64 };
        public static string Write(object o) => S().Serialize(o);
        public static T Read<T>(string s) => S().Deserialize<T>(s);
        public static Dictionary<string, object> Obj(string s) => S().Deserialize<Dictionary<string, object>>(s);

        public static T Load<T>(string file, Func<T> fallback)
        {
            try { if (File.Exists(file)) return Read<T>(File.ReadAllText(file, Encoding.UTF8)) ?? fallback(); }
            catch (Exception e) { Log.Error("Json.Load " + file, e); }
            return fallback();
        }

        /// <summary>ذخیرهٔ اتمی (فایل موقت و جایگزینی)</summary>
        public static void Save(string file, object o)
        {
            Paths.Ensure(file);
            var tmp = file + ".tmp";
            File.WriteAllText(tmp, Write(o), new UTF8Encoding(false));
            if (File.Exists(file)) File.Replace(tmp, file, null); else File.Move(tmp, file);
        }

        public static string Str(this Dictionary<string, object> d, string k, string def = "") =>
            d != null && d.TryGetValue(k, out var v) && v != null ? Convert.ToString(v, System.Globalization.CultureInfo.InvariantCulture) : def;

        public static int Int(this Dictionary<string, object> d, string k, int def = 0) =>
            d != null && d.TryGetValue(k, out var v) && v != null && int.TryParse(Convert.ToString(v, System.Globalization.CultureInfo.InvariantCulture), out var i) ? i : def;

        public static bool Bool(this Dictionary<string, object> d, string k, bool def = false) =>
            d != null && d.TryGetValue(k, out var v) && v is bool b ? b : def;

        public static List<Dictionary<string, object>> List(this Dictionary<string, object> d, string k)
        {
            var r = new List<Dictionary<string, object>>();
            if (d != null && d.TryGetValue(k, out var v) && v is System.Collections.IEnumerable e)
                foreach (var x in e) if (x is Dictionary<string, object> o) r.Add(o);
            return r;
        }
    }

    public static class Fa
    {
        private const string FaDigits = "۰۱۲۳۴۵۶۷۸۹";

        public static string Num(object n)
        {
            var s = Convert.ToString(n, System.Globalization.CultureInfo.InvariantCulture) ?? "";
            var sb = new StringBuilder(s.Length);
            foreach (var c in s) sb.Append(c >= '0' && c <= '9' ? FaDigits[c - '0'] : c);
            return sb.ToString();
        }

        public static string Latin(string s)
        {
            if (s == null) return "";
            var sb = new StringBuilder(s.Length);
            foreach (var c in s)
            {
                if (c >= '۰' && c <= '۹') sb.Append((char)('0' + (c - '۰')));
                else if (c >= '٠' && c <= '٩') sb.Append((char)('0' + (c - '٠')));
                else sb.Append(c);
            }
            return sb.ToString();
        }

        public static bool HasPersian(string s)
        {
            foreach (var c in s ?? "") if (c >= '؀' && c <= 'ۿ') return true;
            return false;
        }
    }
}
