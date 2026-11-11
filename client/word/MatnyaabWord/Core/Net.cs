using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Text;
using System.Threading;
using Microsoft.Win32;

namespace Matnyaab.Word.Core
{
    public static class Http
    {
        static Http()
        {
            try { ServicePointManager.SecurityProtocol |= SecurityProtocolType.Tls12 | (SecurityProtocolType)12288; }
            catch { ServicePointManager.SecurityProtocol |= SecurityProtocolType.Tls12; }
        }

        public static string Send(string method, string url, string body, IDictionary<string, string> headers, int timeoutMs, out int status)
        {
            var req = (HttpWebRequest)WebRequest.Create(url);
            req.Method = method;
            req.Timeout = timeoutMs; req.ReadWriteTimeout = timeoutMs;
            req.UserAgent = "MatnyaabWord/" + Updater.CurrentVersion;
            if (url.StartsWith("http://127.0.0.1")) req.Proxy = null;
            if (headers != null) foreach (var h in headers) req.Headers[h.Key] = h.Value;
            if (body != null)
            {
                var b = Encoding.UTF8.GetBytes(body);
                req.ContentType = "application/json; charset=utf-8";
                req.ContentLength = b.Length;
                using (var s = req.GetRequestStream()) s.Write(b, 0, b.Length);
            }
            try
            {
                using (var res = (HttpWebResponse)req.GetResponse())
                using (var r = new StreamReader(res.GetResponseStream(), Encoding.UTF8))
                { status = (int)res.StatusCode; return r.ReadToEnd(); }
            }
            catch (WebException we) when (we.Response is HttpWebResponse hr)
            {
                status = (int)hr.StatusCode;
                using (var r = new StreamReader(hr.GetResponseStream(), Encoding.UTF8)) return r.ReadToEnd();
            }
        }

        public static void Download(string url, string file)
        {
            using (var wc = new WebClient()) { wc.Headers["User-Agent"] = "MatnyaabWord/" + Updater.CurrentVersion; wc.DownloadFile(url, file); }
        }
    }

    /// <summary>
    /// اتصال به برنامهٔ متن‌یاب از راه پل محلی (127.0.0.1 + توکن).
    /// اگر متن‌یاب باز نباشد ولی نصب باشد، نسخهٔ پنهان آن («--bridge») بی‌صدا راه‌اندازی می‌شود.
    /// </summary>
    public static class Bridge
    {
        public static string LastError = "";

        public static string InstalledExe()
        {
            try
            {
                using (var k = Registry.CurrentUser.OpenSubKey(@"Software\Matnyaab"))
                {
                    var p = k?.GetValue("InstallPath") as string;
                    if (!string.IsNullOrEmpty(p) && File.Exists(Path.Combine(p, "Matnyaab.exe"))) return Path.Combine(p, "Matnyaab.exe");
                }
            }
            catch { }
            var guess = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "Matnyaab", "Matnyaab.exe");
            return File.Exists(guess) ? guess : null;
        }

        public static bool Installed => InstalledExe() != null;

        private static bool ReadEndpoint(out int port, out string token)
        {
            port = 0; token = "";
            try
            {
                if (!File.Exists(Paths.BridgeFile)) return false;
                var o = Json.Obj(File.ReadAllText(Paths.BridgeFile, Encoding.UTF8));
                port = o.Int("port"); token = o.Str("token");
                return port > 0 && token.Length > 0;
            }
            catch { return false; }
        }

        public static Dictionary<string, object> Call(string path, object body, int timeoutMs = 15000)
        {
            if (!ReadEndpoint(out var port, out var token)) return null;
            try
            {
                var txt = Http.Send("POST", "http://127.0.0.1:" + port + "/v1/" + path, Json.Write(body ?? new { }),
                    new Dictionary<string, string> { ["X-Matnyaab-Token"] = token }, timeoutMs, out var st);
                var o = Json.Obj(txt);
                if (st != 200) LastError = o.Str("error", "خطای " + st);
                return o;
            }
            catch (Exception e) { LastError = e.Message; return null; }
        }

        /// <summary>اطمینان از در دسترس بودن پل؛ در صورت نیاز متن‌یاب را پنهانی راه می‌اندازد</summary>
        public static Dictionary<string, object> Ensure(int waitMs = 20000)
        {
            var p = Call("ping", null, 2500);
            if (p != null && p.Bool("ok")) return p;
            var exe = InstalledExe();
            if (exe == null || !AddinSettings.Current.UseMatnyaab) return null;
            try
            {
                Process.Start(new ProcessStartInfo(exe, "--bridge") { UseShellExecute = false, CreateNoWindow = true, WorkingDirectory = Path.GetDirectoryName(exe) });
            }
            catch (Exception e) { Log.Error("bridge.start", e); LastError = e.Message; return null; }
            var sw = Stopwatch.StartNew();
            while (sw.ElapsedMilliseconds < waitMs)
            {
                Thread.Sleep(400);
                p = Call("ping", null, 1500);
                if (p != null && p.Bool("ok")) return p;
            }
            LastError = "متن‌یاب پاسخ نداد.";
            return null;
        }

        /// <summary>بازکردن پنجرهٔ متن‌یاب (و اجرای جست‌وجو)</summary>
        public static void ShowApp(string query)
        {
            var p = Call("ping", null, 1500);
            if (p != null && p.Str("mode") == "gui") { Call("show", new { query }); return; }
            if (p != null) Call("shutdown", null, 1500);
            var exe = InstalledExe();
            if (exe == null) return;
            var args = string.IsNullOrWhiteSpace(query) ? "" : "--search \"" + query.Replace("\"", "'") + "\"";
            try { Process.Start(new ProcessStartInfo(exe, args) { UseShellExecute = true, WorkingDirectory = Path.GetDirectoryName(exe) }); }
            catch (Exception e) { Log.Error("bridge.show", e); }
        }
    }

    // ==================================================================== هوش مصنوعی

    /// <summary>کاتالوگ پیشنهادی سرویس‌ها (از پنل مدیریت سایت؛ نسخهٔ پیش‌فرض داخلی)</summary>
    public class AiPreset
    {
        public string Id = "", Title = "", Kind = "openai", BaseUrl = "", Hint = "";
        public List<string> Models = new List<string>();
    }

    public static class Ai
    {
        public static List<AiPreset> Catalog = new List<AiPreset>
        {
            new AiPreset { Id = "openai", Title = "OpenAI", Kind = "openai", BaseUrl = "https://api.openai.com/v1", Models = { "gpt-4o-mini", "gpt-4o" } },
            new AiPreset { Id = "anthropic", Title = "Anthropic Claude", Kind = "anthropic", BaseUrl = "https://api.anthropic.com/v1", Models = { "claude-sonnet-4-5", "claude-haiku-4-5" } },
            new AiPreset { Id = "gemini", Title = "Google Gemini", Kind = "gemini", BaseUrl = "https://generativelanguage.googleapis.com/v1beta", Models = { "gemini-2.0-flash", "gemini-1.5-pro" } },
            new AiPreset { Id = "openrouter", Title = "OpenRouter", Kind = "openai", BaseUrl = "https://openrouter.ai/api/v1", Models = { "openai/gpt-4o-mini" } },
            new AiPreset { Id = "custom", Title = "سرویس سازگار با OpenAI (دلخواه)", Kind = "openai", BaseUrl = "", Hint = "نشانی پایهٔ API را وارد کنید؛ مثلاً درگاه‌های داخلی یا Ollama محلی (http://localhost:11434/v1)" },
        };

        public static readonly string[] Presets =
        {
            "روان و خوش‌خوان کن، بدون تغییر معنا",
            "لحن را علمی و دانشگاهی کن",
            "کوتاه‌تر و فشرده‌تر کن",
            "فقط غلط‌های املایی، نگارشی و نیم‌فاصله‌ها را اصلاح کن",
            "به انگلیسی آکادمیک ترجمه کن",
            "به فارسی روان ترجمه کن",
        };

        public const string SystemPrompt =
            "You are an expert academic editor for Persian (Farsi) and English research writing. " +
            "Follow the user's instruction on the given paragraph. Preserve meaning, citations in parentheses, numbers and footnote markers. " +
            "Use correct Persian orthography with zero-width non-joiners (نیم‌فاصله). Return ONLY the revised paragraph text, no explanations.";

        public static string Complete(AiProvider p, string instruction, string text, int timeoutMs = 90000)
        {
            if (p == null) throw new InvalidOperationException("هنوز سرویسی انتخاب نشده است.");
            var key = p.Key;
            var user = "Instruction: " + instruction + "\n\nParagraph:\n" + text;
            string url, body; var h = new Dictionary<string, string>();
            switch (p.Kind)
            {
                case "anthropic":
                    url = Trim(p.BaseUrl, "https://api.anthropic.com/v1") + "/messages";
                    h["x-api-key"] = key; h["anthropic-version"] = "2023-06-01";
                    body = Json.Write(new Dictionary<string, object>
                    {
                        ["model"] = p.Model, ["max_tokens"] = 2048, ["system"] = SystemPrompt,
                        ["messages"] = new[] { new Dictionary<string, object> { ["role"] = "user", ["content"] = user } },
                    });
                    break;
                case "gemini":
                    url = Trim(p.BaseUrl, "https://generativelanguage.googleapis.com/v1beta") + "/models/" + Uri.EscapeDataString(p.Model) + ":generateContent";
                    h["x-goog-api-key"] = key;
                    body = Json.Write(new Dictionary<string, object>
                    {
                        ["systemInstruction"] = new Dictionary<string, object> { ["parts"] = new[] { new Dictionary<string, object> { ["text"] = SystemPrompt } } },
                        ["contents"] = new[] { new Dictionary<string, object> { ["role"] = "user", ["parts"] = new[] { new Dictionary<string, object> { ["text"] = user } } } },
                    });
                    break;
                default:
                    url = Trim(p.BaseUrl, "https://api.openai.com/v1") + "/chat/completions";
                    if (!string.IsNullOrEmpty(key)) h["Authorization"] = "Bearer " + key;
                    body = Json.Write(new Dictionary<string, object>
                    {
                        ["model"] = p.Model, ["temperature"] = 0.3,
                        ["messages"] = new[]
                        {
                            new Dictionary<string, object> { ["role"] = "system", ["content"] = SystemPrompt },
                            new Dictionary<string, object> { ["role"] = "user", ["content"] = user },
                        },
                    });
                    break;
            }
            var txt = Http.Send("POST", url, body, h, timeoutMs, out var st);
            Dictionary<string, object> o;
            try { o = Json.Obj(txt); } catch { throw new InvalidOperationException("پاسخ نامفهوم از سرویس (کد " + st + ")"); }
            if (st >= 400)
            {
                var err = o.TryGetValue("error", out var e) ? (e is Dictionary<string, object> ed ? ed.Str("message") : Convert.ToString(e)) : txt;
                throw new InvalidOperationException(st == 401 || st == 403 ? "کلید API پذیرفته نشد؛ آن را در تنظیمات بررسی کنید." : "خطای سرویس (" + st + "): " + err);
            }
            string outText;
            switch (p.Kind)
            {
                case "anthropic": outText = string.Concat(o.List("content").Select(c => c.Str("text"))); break;
                case "gemini":
                    outText = string.Concat(o.List("candidates").Take(1).SelectMany(c => (c.TryGetValue("content", out var cc) && cc is Dictionary<string, object> cd ? cd.List("parts") : new List<Dictionary<string, object>>()).Select(x => x.Str("text"))));
                    break;
                default:
                    outText = o.List("choices").Select(c => c.TryGetValue("message", out var m) && m is Dictionary<string, object> md ? md.Str("content") : "").FirstOrDefault() ?? "";
                    break;
            }
            return (outText ?? "").Trim();
        }

        private static string Trim(string url, string def) => (string.IsNullOrWhiteSpace(url) ? def : url.Trim()).TrimEnd('/');
    }

    // ==================================================================== پیکربندی سایت و به‌روزرسانی

    public static class Updater
    {
        public const string Site = "https://www.matnyaab.ir";
        public static string CurrentVersion => typeof(Updater).Assembly.GetName().Version.ToString(3);

        public class Release { public string Version = "", Url = "", Sha256 = "", Notes = ""; }

        /// <summary>بررسی نسخهٔ تازه (releases.json سایت)؛ null یعنی نسخهٔ فعلی تازه‌ترین است</summary>
        public static Release Check()
        {
            var txt = Http.Send("GET", Site + "/downloadFiles/releases.json?ts=" + DateTime.UtcNow.Ticks, null, null, 15000, out var st);
            if (st != 200) return null;
            var o = Json.Obj(txt);
            if (!(o.TryGetValue("word", out var w) && w is Dictionary<string, object> wd)) return null;
            var r = new Release { Version = wd.Str("version"), Url = wd.Str("url"), Sha256 = wd.Str("sha256"), Notes = wd.Str("notes") };
            if (!Version.TryParse(r.Version, out var nv) || !Version.TryParse(CurrentVersion, out var cv) || nv <= cv) return null;
            if (r.Url.StartsWith("/")) r.Url = Site + r.Url;
            return r;
        }

        /// <summary>دریافت، بررسی امضای SHA-256 و اجرای نصب‌کننده (نصب در پوشهٔ نسخه‌دار؛ ورد باز می‌ماند)</summary>
        public static void Install(Release r)
        {
            var file = Path.Combine(Path.GetTempPath(), "Matnyaab-Word-Setup-" + r.Version + ".exe");
            Http.Download(r.Url, file);
            if (!string.IsNullOrEmpty(r.Sha256))
            {
                using (var sha = System.Security.Cryptography.SHA256.Create())
                using (var fs = File.OpenRead(file))
                {
                    var h = BitConverter.ToString(sha.ComputeHash(fs)).Replace("-", "").ToLowerInvariant();
                    if (!string.Equals(h, r.Sha256.Trim(), StringComparison.OrdinalIgnoreCase)) throw new InvalidOperationException("فایل دریافتی سالم نبود؛ دوباره تلاش کنید.");
                }
            }
            Process.Start(new ProcessStartInfo(file, "/SILENT /SUPPRESSMSGBOXES /NORESTART /WORDOPEN=1") { UseShellExecute = true });
        }

        /// <summary>کاتالوگ سرویس‌های هوش مصنوعی که مدیر سایت تعریف کرده است</summary>
        public static void RefreshAiCatalog()
        {
            try
            {
                var txt = Http.Send("GET", Site + "/api/client/config", null, null, 12000, out var st);
                if (st != 200) return;
                var o = Json.Obj(txt);
                var cfg = o.TryGetValue("config", out var c) && c is Dictionary<string, object> cd ? cd : o;
                var list = cfg.List("ai_catalog");
                if (list.Count == 0) return;
                Ai.Catalog = list.Where(x => x.Bool("enabled", true)).Select(x => new AiPreset
                {
                    Id = x.Str("id"), Title = x.Str("title"), Kind = x.Str("kind", "openai"), BaseUrl = x.Str("base_url"), Hint = x.Str("hint"),
                    Models = (x.TryGetValue("models", out var m) && m is System.Collections.IEnumerable e ? e.Cast<object>().Select(Convert.ToString).ToList() : new List<string>()),
                }).ToList();
                if (!Ai.Catalog.Any(p => p.Id == "custom"))
                    Ai.Catalog.Add(new AiPreset { Id = "custom", Title = "سرویس سازگار با OpenAI (دلخواه)", Kind = "openai" });
            }
            catch (Exception e) { Log.Error("ai.catalog", e); }
        }
    }
}
