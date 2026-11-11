using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Windows.Forms;
using System.Xml;
using Matnyaab.Word.Core;
using Matnyaab.Word.UI;

/// <summary>آزمون‌های منطق افزونه + تولید تصاویر معرفی پنل (بدون نیاز به ورد)</summary>
internal static class Program
{
    private static int _fail, _pass;

    private static void Eq<T>(T expected, T actual, string name)
    {
        if (Equals(expected, actual)) { _pass++; return; }
        _fail++; Console.WriteLine("FAIL " + name + ": expected [" + expected + "] got [" + actual + "]");
    }

    private static void True(bool c, string name) => Eq(true, c, name);

    [STAThread]
    private static int Main(string[] args)
    {
        Environment.SetEnvironmentVariable("MATNYAAB_HOME", Path.Combine(Path.GetTempPath(), "mx-word-tests-" + Guid.NewGuid().ToString("N")));
        Logic();
        Ribbon();
        Console.WriteLine("passed " + _pass + ", failed " + _fail);
        var i = Array.IndexOf(args, "--shots");
        if (i >= 0 && i + 1 < args.Length) Shots(args[i + 1]);
        return _fail == 0 ? 0 : 1;
    }

    private static void Logic()
    {
        // اصطلاحات لاتین
        var t = LatinTerms.Find("تزکیه (Self-purification) و جهاد اکبر (Greater Jihad) نزد عارفان (Smith, 2020) و (p. 12) آمده است.\r");
        Eq(2, t.Count, "terms.count");
        Eq("Self-purification", t[0].Term, "terms.first");
        True(!LatinTerms.IsTerm("Smith, 2020"), "terms.citation");
        True(!LatinTerms.IsTerm("نفس"), "terms.persian");
        Eq(0, LatinTerms.Find("(Introduction) is a heading").Count, "terms.english-paragraph");

        // سرفصل‌ها
        var paras = new List<ParaInfo>
        {
            new ParaInfo { Index = 0, Text = "فصل اول: کلیات پژوهش\r", Size = 16, Bold = true },
            new ParaInfo { Index = 1, Text = "این یک بند طولانی از متن اصلی است که هیچ نشانه‌ای از عنوان بودن ندارد و با نقطه پایان می‌یابد.\r", Size = 13 },
            new ParaInfo { Index = 2, Text = "۱-۲- پیشینهٔ پژوهش\r", Size = 13, Bold = true },
            new ParaInfo { Index = 3, Text = "۱-۲-۳- پیشینهٔ داخلی\r", Size = 13 },
            new ParaInfo { Index = 4, Text = "مقدمه\r", Size = 13 },
            new ParaInfo { Index = 5, Text = "این یک بند دیگر از متن اصلی است که کمی بلند است و نقطه دارد و عنوان نیست اصلاً.\r", Size = 13 },
            new ParaInfo { Index = 6, Text = "یک بند کوتاه معمولی.\r", Size = 13 },
        };
        var h = HeadingDetector.Detect(paras);
        Eq("0:1,2:2,3:3,4:1", string.Join(",", h.Select(x => x.Index + ":" + x.Level)), "headings");

        // یکدست‌سازی
        var plan = Cleanup.Plan("كتاب ها را مي خوانم  و می شود ، خوب", Cleanup.Rules.Select(r => r.Id));
        var fixedText = Cleanup.Apply("كتاب ها را مي خوانم  و می شود ، خوب", plan);
        Eq("کتاب\u200Cها را می\u200Cخوانم و می\u200Cشود، خوب", fixedText, "cleanup");
        Eq("سال ۱۴۰۲ و ISBN 978", Cleanup.Apply("سال 1402 و ISBN 978", Cleanup.Plan("سال 1402 و ISBN 978", new[] { "digits-fa" })), "cleanup.digits");

        // APA
        var fa = new SourceInfo { Authors = "رحیمی، محمدکاظم\nرمضانی، محمد", Year = "1402", Title = "اخلاق پژوهش", Container = "نشر دانشگاهی", Kind = "book" };
        Eq("(رحیمی و رمضانی، ۱۴۰۲، ص. ۱۲)", Apa.InText(fa, "12"), "apa.intext.fa");
        Eq("رحیمی، محمدکاظم و رمضانی، محمد. (۱۴۰۲). *اخلاق پژوهش*. نشر دانشگاهی.", Apa.Reference(fa), "apa.ref.fa");
        var en = new SourceInfo { Authors = "Smith, John Adam\nLee, Kim\nPark, Ji", Year = "2020", Title = "Moral education", Container = "Journal of Ethics", Volume = "12", Issue = "3", Pages = "45-67", Kind = "article" };
        Eq("(Smith et al., 2020, pp. 45-46)", Apa.InText(en, "45-46"), "apa.intext.en");
        Eq("Smith, J. A., Lee, K., & Park, J. (2020). Moral education. *Journal of Ethics, 12*(3), 45-67.", Apa.Reference(en), "apa.ref.en");
        Eq("1399", Apa.Guess(@"C:\lib\رسالهٔ اخلاق 1399.pdf").Year, "apa.guess");

        // قالب‌ها
        True(TemplateStore.BuiltIn.Count >= 10, "templates.count");
        True(TemplateStore.BuiltIn.Count(x => x.Category == "thesis") >= 3, "templates.thesis");
        var d = TemplateStore.Duplicate(TemplateStore.BuiltIn[0]);
        True(!d.BuiltIn && TemplateStore.User.Any(x => x.Id == d.Id), "templates.duplicate");
        TemplateStore.Delete(d);
        True(TemplateStore.User.All(x => x.Id != d.Id), "templates.delete");
        TemplateStore.Delete(TemplateStore.BuiltIn[0]);
        True(TemplateStore.All.All(x => x.Id != TemplateStore.BuiltIn[0].Id), "templates.hide");
        TemplateStore.RestoreBuiltIns();

        // تنظیمات و رمزنگاری کلید
        var p = new AiProvider { Title = "x", Model = "m" }; p.Key = "sk-secret";
        Eq("sk-secret", p.Key, "dpapi.roundtrip");
        True(!Json.Write(p).Contains("sk-secret"), "dpapi.notplain");
        AddinSettings.Current.AiProviders.Add(p); AddinSettings.Save(false);
        True(File.ReadAllText(Paths.SettingsFile).Contains("KeyProtected") && !File.ReadAllText(Paths.SettingsFile).Contains("sk-secret"), "settings.saved");
    }

    private static void Ribbon()
    {
        var asm = typeof(Matnyaab.Word.Com.Connect).Assembly;
        string xml;
        using (var s = asm.GetManifestResourceStream("Matnyaab.Word.Ribbon.xml")) using (var r = new StreamReader(s)) xml = r.ReadToEnd();
        var doc = new XmlDocument(); doc.LoadXml(xml);
        var methods = typeof(Matnyaab.Word.Com.Connect).GetMethods(BindingFlags.Public | BindingFlags.Instance).Select(m => m.Name).ToHashSet();
        foreach (XmlAttribute a in doc.SelectNodes("//@*"))
            if (new[] { "onAction", "getVisible", "onLoad" }.Contains(a.Name)) True(methods.Contains(a.Value), "ribbon.callback " + a.Value);
        var ids = doc.SelectNodes("//@id").Cast<XmlAttribute>().Select(x => x.Value).ToList();
        Eq(ids.Count, ids.Distinct().Count(), "ribbon.unique-ids");
        True(typeof(Matnyaab.Word.Com.Connect).GetCustomAttributes(typeof(ComVisibleAttribute), false).Cast<ComVisibleAttribute>().All(x => x.Value), "com.visible");
    }

    // ---------------------------------------------------------------- تصاویر معرفی

    [DllImport("user32.dll")] private static extern bool PrintWindow(IntPtr hwnd, IntPtr hdc, uint flags);

    private static void Shots(string dir)
    {
        Directory.CreateDirectory(dir);
        Application.EnableVisualStyles();
        Kit.Init();
        var demo = new Dictionary<string, object>
        {
            ["ok"] = true, ["phrases"] = new object[] { "تهذیب نفس", "جهاد اکبر", "مبارزه با نفس" }, ["totalFiles"] = 3, ["totalMatches"] = 27,
            ["results"] = new object[]
            {
                R("اخلاق ناصری؛ یادداشت‌ها.docx", "کتابخانهٔ پژوهشی", 12, new object[] { 7, 3, 2 }, "خواجه نصیرالدین در بخش نخست، ", "تهذیب نفس", " را مقدمهٔ هر سعادتی می‌داند و آن را با تدبیر منزل و سیاست مدن پیوند می‌زند."),
                R("جامع‌السعادات — گزیده.pdf", "منابع اخلاق", 9, new object[] { 4, 5, 0 }, "نراقی ", "جهاد اکبر", " را پیکاری پیوسته با هوای نفس می‌خواند که بی‌مراقبه و محاسبه به ثمر نمی‌رسد."),
                R("طرح پژوهش.docx", "یادداشت‌های پژوهشی (ورد)", 6, new object[] { 2, 1, 3 }, "پرسش اصلی: نسبت ", "مبارزه با نفس", " و تربیت اجتماعی در متون اخلاقی قرن چهارم تا هشتم چیست؟"),
            },
        };
        foreach (var theme in new[] { "blue", "night" })
        {
            AddinSettings.Current.Theme = theme; Kit.ApplyTheme();
            foreach (var tab in new[] { "search", "writing", "templates", "cite", "ai", "settings" })
            {
                using (var f = new Form { ClientSize = new Size(420, 860), StartPosition = FormStartPosition.Manual, Location = new Point(40, 20), FormBorderStyle = FormBorderStyle.None, ShowInTaskbar = false })
                {
                    var pane = new PaneView { Dock = DockStyle.Fill };
                    f.Controls.Add(pane);
                    f.Show(); Application.DoEvents();
                    pane.Open(tab);
                    if (tab == "search") pane.Search?.DemoShow("تهذیب نفس\nجهاد اکبر\nمبارزه با نفس", demo);
                    if (tab == "writing") pane.Writing?.Demo();
                    pane.SetStatus("متصل به متن‌یاب ۲٫۳", "ok");
                    for (var k = 0; k < 20; k++) { Application.DoEvents(); System.Threading.Thread.Sleep(30); }
                    using (var bmp = new Bitmap(f.Width, f.Height, PixelFormat.Format32bppArgb))
                    {
                        using (var g = Graphics.FromImage(bmp)) { var hdc = g.GetHdc(); PrintWindow(f.Handle, hdc, 2); g.ReleaseHdc(hdc); }
                        bmp.Save(Path.Combine(dir, "word-" + tab + "-" + theme + ".png"), ImageFormat.Png);
                    }
                    f.Close();
                }
            }
            if (theme == "night") break;
        }
        Console.WriteLine("SHOTS_DONE " + dir);
    }

    private static Dictionary<string, object> R(string name, string col, int n, object[] per, string before, string match, string after) => new Dictionary<string, object>
    {
        ["name"] = name, ["path"] = @"D:\پژوهش\" + name, ["collection"] = col, ["matches"] = n, ["perPhrase"] = per,
        ["snippets"] = new object[]
        {
            new Dictionary<string, object> { ["before"] = before, ["match"] = match, ["after"] = after, ["start"] = 10, ["end"] = 20, ["phrase"] = 0 },
            new Dictionary<string, object> { ["before"] = "در ادامه ", ["match"] = match, ["after"] = " دوباره مطرح می‌شود.", ["start"] = 90, ["end"] = 99, ["phrase"] = 0 },
        },
    };
}
