using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;

namespace Matnyaab.Word.Core
{
    /// <summary>یک سرویس هوش مصنوعی که کاربر خودش اضافه می‌کند (کلید با DPAPI رمز می‌شود)</summary>
    public class AiProvider
    {
        public string Id { get; set; } = Guid.NewGuid().ToString("N").Substring(0, 8);
        public string Title { get; set; } = "";
        /// <summary>openai (هر سرویس سازگار با OpenAI)، anthropic، gemini</summary>
        public string Kind { get; set; } = "openai";
        public string BaseUrl { get; set; } = "";
        public string Model { get; set; } = "";
        public string KeyProtected { get; set; } = "";

        [System.Web.Script.Serialization.ScriptIgnore]
        public string Key
        {
            get
            {
                if (string.IsNullOrEmpty(KeyProtected)) return "";
                try { return Encoding.UTF8.GetString(ProtectedData.Unprotect(Convert.FromBase64String(KeyProtected), Salt, DataProtectionScope.CurrentUser)); }
                catch { return ""; }
            }
            set
            {
                KeyProtected = string.IsNullOrEmpty(value) ? "" :
                    Convert.ToBase64String(ProtectedData.Protect(Encoding.UTF8.GetBytes(value), Salt, DataProtectionScope.CurrentUser));
            }
        }

        private static readonly byte[] Salt = Encoding.UTF8.GetBytes("matnyaab-word-ai");
    }

    /// <summary>منبعی که کاربر برای یک فایل تکمیل کرده (برای ارجاع APA)</summary>
    public class SourceInfo
    {
        public string Path { get; set; } = "";
        public string Authors { get; set; } = "";      // هر نویسنده در یک سطر: «نام خانوادگی، نام»
        public string Year { get; set; } = "";
        public string Title { get; set; } = "";
        public string Container { get; set; } = "";    // نشریه یا ناشر
        public string Volume { get; set; } = "";
        public string Issue { get; set; } = "";
        public string Pages { get; set; } = "";
        public string Url { get; set; } = "";
        public string Kind { get; set; } = "book";     // book | article | web | thesis
    }

    /// <summary>
    /// تنظیمات افزونه — همه «پرچم‌های قابلیت» در اختیار کاربر است.
    /// در پوشهٔ تنظیمات متن‌یاب ذخیره می‌شود تا هر دو برنامه به آن دسترسی داشته باشند.
    /// </summary>
    public class AddinSettings
    {
        // ---- نمایش و چیدمان
        public string PanePosition { get; set; } = "right";   // right | left | float
        public int PaneWidth { get; set; } = 410;
        public bool OpenPaneOnStart { get; set; } = false;
        public string Theme { get; set; } = "auto";           // auto = هماهنگ با متن‌یاب
        public string Font { get; set; } = "Vazirmatn";
        public string StartTab { get; set; } = "search";

        // ---- پرچم‌های قابلیت (گروه‌های نوار ابزار و زبانه‌های پنل)
        public bool ShowSearch { get; set; } = true;
        public bool ShowCite { get; set; } = true;
        public bool ShowFootnotes { get; set; } = true;
        public bool ShowStructure { get; set; } = true;
        public bool ShowTemplates { get; set; } = true;
        public bool ShowCleanup { get; set; } = true;
        public bool ShowAi { get; set; } = true;

        // ---- درج و استناد
        public bool InsertWithComment { get; set; } = true;
        public bool InsertCitation { get; set; } = true;
        public string CitationLang { get; set; } = "auto";    // auto | fa | en
        public int BlockQuoteWords { get; set; } = 40;
        public bool TrackChangesForAi { get; set; } = true;

        // ---- پیوند با متن‌یاب و به‌روزرسانی
        public bool UseMatnyaab { get; set; } = true;
        public bool AutoUpdate { get; set; } = true;
        public string LastUpdateCheck { get; set; } = "";
        public string SkippedVersion { get; set; } = "";

        // ---- هوش مصنوعی
        public string AiProviderId { get; set; } = "";
        public List<AiProvider> AiProviders { get; set; } = new List<AiProvider>();
        public List<string> AiPrompts { get; set; } = new List<string>();

        // ---- حافظه
        public List<string> Recent { get; set; } = new List<string>();
        public List<SourceInfo> Sources { get; set; } = new List<SourceInfo>();

        public SourceInfo SourceFor(string path) => Sources.FirstOrDefault(s => string.Equals(s.Path, path, StringComparison.OrdinalIgnoreCase));

        public void Remember(string q)
        {
            if (string.IsNullOrWhiteSpace(q)) return;
            Recent.Remove(q); Recent.Insert(0, q);
            if (Recent.Count > 15) Recent.RemoveRange(15, Recent.Count - 15);
        }

        // ---------------------------------------------------------------- ذخیره/بارگذاری

        private static AddinSettings _current;
        public static event Action Changed;

        public static AddinSettings Current => _current ?? (_current = Json.Load(Paths.SettingsFile, () => new AddinSettings()));

        public static void Save(bool notify = true)
        {
            try { Json.Save(Paths.SettingsFile, Current); }
            catch (Exception e) { Log.Error("Settings.Save", e); }
            if (notify) try { Changed?.Invoke(); } catch (Exception e) { Log.Error("Settings.Changed", e); }
        }

        /// <summary>خواندن یک مقدار از settings.ini متن‌یاب (Properties جاوا با \\uXXXX)</summary>
        public static string MatnyaabSetting(string key)
        {
            try
            {
                if (!File.Exists(Paths.MatnyaabIni)) return "";
                foreach (var raw in File.ReadAllLines(Paths.MatnyaabIni, Encoding.UTF8))
                {
                    var line = raw.Trim();
                    if (line.StartsWith("#") || !line.StartsWith(key)) continue;
                    var i = line.IndexOfAny(new[] { '=', ':' });
                    if (i < 0 || line.Substring(0, i).Trim() != key) continue;
                    return Unescape(line.Substring(i + 1).Trim());
                }
            }
            catch { }
            return "";
        }

        private static string Unescape(string s)
        {
            var sb = new StringBuilder();
            for (var i = 0; i < s.Length; i++)
            {
                if (s[i] == '\\' && i + 5 < s.Length + 0 && i + 1 < s.Length && s[i + 1] == 'u' && i + 5 < s.Length + 1)
                {
                    try { sb.Append((char)Convert.ToInt32(s.Substring(i + 2, 4), 16)); i += 5; continue; } catch { }
                }
                if (s[i] == '\\' && i + 1 < s.Length) { i++; sb.Append(s[i]); continue; }
                sb.Append(s[i]);
            }
            return sb.ToString();
        }
    }
}
