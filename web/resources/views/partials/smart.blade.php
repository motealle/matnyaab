{{-- بخش «متن‌یاب هوشمند» (نسخهٔ ۳) — با پرچم smart در پنل مدیریت روشن/خاموش می‌شود --}}
<style>
    .smart-steps{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin:18px 0 8px;counter-reset:st}
    .smart-steps li{list-style:none;padding:12px 14px;border-radius:14px;border:1px dashed var(--line);background:var(--soft-card);font-size:.95rem}
    .smart-steps li::before{counter-increment:st;content:counter(st, persian);display:inline-grid;place-items:center;width:24px;height:24px;margin-inline-end:6px;border-radius:50%;background:var(--accent);color:#fff;font-size:.8rem}
    .mission{padding:16px 18px;border-radius:16px;border:1px solid var(--line);background:var(--soft-card);margin-top:14px}
    .mission q{display:block;font-size:1.05rem;line-height:2;quotes:"«" "»"}
    .ai-table{width:100%;border-collapse:collapse;margin-top:12px;font-size:.95rem}
    .ai-table th,.ai-table td{padding:10px 12px;border-bottom:1px solid var(--line);text-align:start;vertical-align:top}
    .ai-table th{font-weight:700;color:var(--muted-2)}
    .ai-tag{display:inline-block;padding:1px 9px;border-radius:999px;font-size:.78rem;border:1px solid var(--line);margin-inline-start:4px;white-space:nowrap}
    .ai-scroll{overflow-x:auto}
    .smart-note{font-size:.88rem;color:var(--muted-2);margin-top:10px}
    .smart-two{display:grid;grid-template-columns:minmax(0,1.1fr) minmax(0,.9fr);gap:22px;align-items:start;margin-top:34px}
    @media(max-width:999px){.smart-steps{grid-template-columns:repeat(2,minmax(0,1fr))}.smart-two{grid-template-columns:1fr}}
    @media(max-width:560px){.smart-steps{grid-template-columns:1fr}}
</style>
<section class="section" id="ai">
    <div class="container">
        <div class="section-head">
            <span class="eyebrow">تازه در نسخهٔ ۳ · متن‌یاب هوشمند</span>
            <h2>از جست‌وجو تا کتاب؛ کار پژوهشی را به متن‌یاب بسپارید</h2>
            <p>فیش‌برداری، منبع‌یابی، همانندجویی و ساخت کتاب روی رایانهٔ خودتان و بدون اینترنت انجام می‌شود. اگر بخواهید، «دستیار هوشمند» با سرویس هوش مصنوعیِ دلخواه شما مأموریت‌های چندمرحله‌ای را انجام می‌دهد و نتیجه را در فایل Word یا Markdown تحویل می‌دهد؛ همه با ارجاع دقیق به اسناد خودتان.</p>
        </div>

        <div class="new-grid">
            <article class="new-item"><h3>🗂 فیش با منبع، بی‌زحمت</h3><p>در پیش‌نمایش متن را انتخاب و <kbd>Ctrl+D</kbd> بزنید؛ سند، مجموعه و جای دقیق خودکار ثبت می‌شود. فیش‌ها را در پروژه و سرفصل بچینید، برچسب و یادداشت بگذارید.</p></article>
            <article class="new-item"><h3>📘 ساخت کتاب از فیش‌ها</h3><p>فصل‌ها را بچینید، قطع (وزیری، رقعی، A4، A5) و قلم را انتخاب کنید؛ فایل Word صفحه‌آرایی‌شده با صفحهٔ عنوان، فهرست مطالب، پانویس منبع و فهرست منابع تحویل بگیرید.</p></article>
            <article class="new-item"><h3>⧉ این متن از کجاست؟</h3><p>یک نقل‌قول را بچسبانید؛ حتی اگر چند واژه‌اش عوض شده باشد، کتاب و جای دقیقش را در کتابخانهٔ شما پیدا می‌کند.</p></article>
            <article class="new-item"><h3>✓ پیش‌همانندجویی</h3><p>پیش از تحویل پایان‌نامه یا مقاله، متن را جمله‌به‌جمله با منابع خودتان بسنجید و بخش‌های بی‌ارجاع را با گزارش Word اصلاح کنید.</p></article>
            <article class="new-item"><h3>✦ دستیار پژوهش (اختیاری)</h3><p>فیش‌برداری موضوعی، کدگذاری گراندد تئوری، تحلیل مضمون، پیشینهٔ پژوهش، مقایسهٔ دیدگاه‌ها، پاسخ مستند و طرح کتاب؛ نقل‌قول‌ها پیش از ثبت با متن اصلی راستی‌آزمایی می‌شوند.</p></article>
            <article class="new-item"><h3>✉ نامه و اسناد رسمی در ورد</h3><p>نامهٔ اداری، صورتجلسه، گزارش کار، درخواست و بخشنامه با صفحه‌آرایی رسمی؛ به‌همراه فیش و منبع‌یاب درون افزونهٔ ورد.</p></article>
        </div>

        <div class="smart-two">
            <div>
                <h3>یک جمله بگویید؛ دستیار بقیه را انجام می‌دهد</h3>
                <div class="mission">
                    <q>با روش گراندد تئوری، همهٔ فیش‌های موضوع «توکل» را از مجموعهٔ «منابع اخلاق» با عنوان استخراج و در پوشهٔ Documents\Matnyaab در فایل «توکل.docx» ذخیره کن.</q>
                </div>
                <ol class="smart-steps">
                    <li>برنامه‌ریزی و نمایش برای تأیید شما</li>
                    <li>جست‌وجوی هم‌خانواده و مترادف در اسناد شما</li>
                    <li>استخراج و راستی‌آزمایی نقل‌قول‌ها با متن اصلی</li>
                    <li>نوشتن فایل Word/Markdown و ثبت فیش‌ها</li>
                </ol>
                <p class="smart-note">جست‌وجو روی رایانهٔ شما انجام می‌شود و فقط بخش‌های لازم برای پردازش به سرویس انتخابی شما فرستاده می‌شود. پیش از اجرا، تعداد تقریبی درخواست‌ها را می‌بینید. برای محرمانگی کامل، Ollama را روی رایانهٔ خود نصب کنید.</p>
                <p><a class="btn btn-primary" data-release-link="kotlin" href="/downloadFiles/Matnyaab-Setup-3.1.exe">دانلود متن‌یاب ۳٫۱ <span aria-hidden="true">↓</span></a> <a class="btn" href="/docs/matnyaab-guide-3.1.pdf" target="_blank" rel="noopener">راهنمای کامل (PDF)</a></p>
            </div>
            <div>
                <h3>کلید هوش مصنوعی را از کجا بگیرم؟</h3>
                <div class="ai-scroll">
                <table class="ai-table">
                    <thead><tr><th>سرویس</th><th>ویژگی</th></tr></thead>
                    <tbody>
                        <tr><td><a href="https://avalai.ir" target="_blank" rel="noopener">AvalAI</a></td><td>درگاه ایرانی، پرداخت ریالی؛ دسترسی به مدل‌های مطرح<span class="ai-tag">ریالی</span></td></tr>
                        <tr><td><a href="https://gapgpt.app" target="_blank" rel="noopener">GapGPT</a></td><td>درگاه ایرانی، پرداخت ریالی<span class="ai-tag">ریالی</span></td></tr>
                        <tr><td><a href="https://openrouter.ai" target="_blank" rel="noopener">OpenRouter</a></td><td>صدها مدل؛ مدل‌های دارای «:free» رایگان‌اند (با سقف درخواست)<span class="ai-tag">رایگان/پولی</span></td></tr>
                        <tr><td><a href="https://aistudio.google.com" target="_blank" rel="noopener">Google AI Studio</a></td><td>Gemini؛ سطح رایگان محدود<span class="ai-tag">رایگان/پولی</span></td></tr>
                        <tr><td><a href="https://console.groq.com" target="_blank" rel="noopener">Groq</a></td><td>بسیار سریع؛ سطح رایگان محدود<span class="ai-tag">رایگان/پولی</span></td></tr>
                        <tr><td><a href="https://platform.deepseek.com" target="_blank" rel="noopener">DeepSeek</a></td><td>کم‌هزینه، فارسی خوب<span class="ai-tag">پولی</span></td></tr>
                        <tr><td><a href="https://platform.openai.com" target="_blank" rel="noopener">OpenAI</a></td><td>مدل‌های GPT<span class="ai-tag">پولی</span></td></tr>
                        <tr><td><a href="https://console.anthropic.com" target="_blank" rel="noopener">Anthropic</a></td><td>مدل‌های Claude؛ نگارش بلند و دقیق<span class="ai-tag">پولی</span></td></tr>
                        <tr><td><a href="https://ollama.com" target="_blank" rel="noopener">Ollama</a></td><td>اجرای مدل روی رایانهٔ خودتان؛ بدون اینترنت و کلید<span class="ai-tag">رایگان · آفلاین</span></td></tr>
                    </tbody>
                </table>
                </div>
                <p class="smart-note">این فهرست فقط برای راهنمایی است و متن‌یاب نمایندهٔ هیچ‌یک از این سرویس‌ها نیست. تهیهٔ کلید، هزینه، شرایط استفاده، دسترس‌پذیری و رعایت قوانین هر سرویس بر عهده و مسئولیت خود کاربر است. کلید فقط روی رایانهٔ شما و رمزنگاری‌شده نگه داشته می‌شود.</p>
            </div>
        </div>

        <div class="mission" style="margin-top:28px">
            <h3 style="margin-top:0">اگر از نرم‌افزارهای کتابخانه‌ای مانند نرم‌افزارهای نور استفاده می‌کنید</h3>
            <p style="margin-bottom:0">آن نرم‌افزارها در پیکره‌ای که خودشان فراهم کرده‌اند جست‌وجو می‌کنند؛ متن‌یاب در <strong>فایل‌های خود شما</strong> (PDF، Word، کتاب‌های الکترونیکی، یادداشت‌ها و خروجی همان نرم‌افزارها) کار می‌کند و فیش، کتاب و نامه را مستقیم به <strong>Word</strong> می‌برد. این دو مکمل یکدیگرند: منابع را از هر جا که دارید در متن‌یاب بگذارید و همه را یک‌جا جست‌وجو، فیش‌برداری و مستند کنید.</p>
        </div>
    </div>
</section>
