// ساخت PDF زیبای راهنما از Markdown.  اجرا:
//   npm i marked@12.0.2 @fontsource/vazirmatn@5.0.21   (در یک پوشهٔ موقت)
//   NODE_PATH=<آن پوشه>/node_modules node ops/docs/build-guide.mjs docs/guide/matnyaab-guide.md web/public/docs/matnyaab-guide-3.1.pdf
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { marked } = require('marked');
const { chromium } = require('playwright');

const [src, out] = process.argv.slice(2);
let md = fs.readFileSync(src, 'utf8');
const meta = {};
md = md.replace(/^---\n([\s\S]*?)\n---\n/, (_, y) => { y.split('\n').forEach(l => { const i = l.indexOf(':'); if (i > 0) meta[l.slice(0, i).trim()] = l.slice(i + 1).trim(); }); return ''; });

const font = (w) => fs.readFileSync(require.resolve(`@fontsource/vazirmatn/files/vazirmatn-arabic-${w}-normal.woff2`)).toString('base64');
const fontL = (w) => fs.readFileSync(require.resolve(`@fontsource/vazirmatn/files/vazirmatn-latin-${w}-normal.woff2`)).toString('base64');
const fa = (n) => String(n).replace(/\d/g, d => '۰۱۲۳۴۵۶۷۸۹'[d]);

// شماره‌گذاری فصل‌ها و فهرست
let h1 = 0, h2 = 0; const toc = [];
const renderer = new marked.Renderer();
renderer.heading = (text, level) => {
  if (level === 1) { h1++; h2 = 0; toc.push({ l: 1, t: text, id: `s${h1}` }); return `<h1 id="s${h1}"><span class="num">${fa(h1)}</span>${text}</h1>`; }
  if (level === 2) { h2++; toc.push({ l: 2, t: text, id: `s${h1}-${h2}` }); return `<h2 id="s${h1}-${h2}">${text}</h2>`; }
  return `<h${level}>${text}</h${level}>`;
};
renderer.link = (href, title, text) => `<a href="${href}">${text}</a>`;
const body = marked.parse(md, { renderer });
const tocHtml = toc.map(e => `<li class="t${e.l}"><a href="#${e.id}">${e.t}</a></li>`).join('');

const html = `<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8"><style>
@font-face{font-family:V;src:url(data:font/woff2;base64,${font(400)}) format('woff2');font-weight:400;unicode-range:U+0600-06FF,U+200C-200F,U+FB50-FDFF,U+FE70-FEFF}
@font-face{font-family:V;src:url(data:font/woff2;base64,${font(700)}) format('woff2');font-weight:700;unicode-range:U+0600-06FF,U+200C-200F,U+FB50-FDFF,U+FE70-FEFF}
@font-face{font-family:V;src:url(data:font/woff2;base64,${fontL(400)}) format('woff2');font-weight:400}
@font-face{font-family:V;src:url(data:font/woff2;base64,${fontL(700)}) format('woff2');font-weight:700}
:root{--ink:#172338;--muted:#5b6b82;--accent:#14669f;--accent2:#5bc0ff;--soft:#eef5fb;--line:#d9e4ef}
*{box-sizing:border-box}
html{font-family:V,sans-serif;color:var(--ink);font-size:10.6pt;line-height:1.95}
body{margin:0}
.cover{height:257mm;display:flex;flex-direction:column;justify-content:center;padding:0 18mm;background:linear-gradient(160deg,#0b2a46 0%,#14669f 65%,#1f86c9 100%);color:#fff;border-radius:6mm;position:relative;overflow:hidden;page-break-after:always}
.cover:before{content:"";position:absolute;inset:auto -40mm -60mm auto;width:150mm;height:150mm;border-radius:50%;border:18mm solid rgba(255,255,255,.07)}
.cover .mark{width:22mm;height:22mm;border-radius:6mm;background:rgba(255,255,255,.12);display:grid;place-items:center;margin-bottom:12mm}
.cover h1{font-size:30pt;margin:0 0 4mm;line-height:1.4;border:0;padding:0;color:#fff;page-break-before:avoid;display:block}
.cover p{font-size:13pt;opacity:.9;margin:0 0 3mm;max-width:140mm}
.cover .ver{margin-top:14mm;font-size:10pt;opacity:.8;letter-spacing:.2px}
.toc{page-break-after:always}
.toc h2{font-size:16pt;color:var(--accent);border:0;margin:0 0 6mm}
.toc ol{list-style:none;padding:0;margin:0;columns:1}
.toc li{padding:.9mm 0;border-bottom:1px dotted var(--line);font-size:9.6pt}
.toc li.t1{font-weight:700;margin-top:1mm}.toc li.t2{padding-inline-start:8mm;color:var(--muted);font-size:9pt}
.toc a{color:inherit;text-decoration:none}
h1{font-size:17pt;color:var(--accent);margin:0 0 5mm;padding-bottom:3mm;border-bottom:2px solid var(--line);page-break-before:always;display:flex;gap:4mm;align-items:center}
h1 .num{display:inline-grid;place-items:center;min-width:10mm;height:10mm;border-radius:3mm;background:var(--accent);color:#fff;font-size:12pt}
h2{font-size:13pt;margin:7mm 0 2mm;color:#0f3f66}
h3{font-size:11.5pt;margin:5mm 0 1mm}
p{margin:0 0 3mm;text-align:justify}
a{color:var(--accent)}
ul,ol{margin:0 0 3mm;padding-inline-start:7mm}
li{margin:.6mm 0}
code{font-family:Consolas,monospace;background:var(--soft);border:1px solid var(--line);border-radius:2mm;padding:0 1.4mm;font-size:9pt;direction:ltr;unicode-bidi:embed}
blockquote{margin:4mm 0;padding:3mm 5mm;background:var(--soft);border-inline-start:3px solid var(--accent2);border-radius:2.5mm;color:#203550}
blockquote p:last-child{margin:0}
table{width:100%;border-collapse:separate;border-spacing:0;margin:3mm 0 5mm;font-size:9.6pt;border:1px solid var(--line);border-radius:3mm;overflow:hidden;page-break-inside:avoid}
th{background:#0f3f66;color:#fff;font-weight:700;text-align:start;padding:2.2mm 3mm}
td{padding:2mm 3mm;border-top:1px solid var(--line);vertical-align:top}
tr:nth-child(even) td{background:#f7fafd}
strong{color:#0b2a46}
</style></head><body>
<section class="cover"><div class="mark"><svg width="46" height="46" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round"><circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5 21 21"/><path d="M7.5 10.5h6"/></svg></div>
<h1>${meta.title || 'راهنمای متن‌یاب'}</h1><p>${meta.subtitle || ''}</p><div class="ver">${meta.version || ''} · matnyaab.ir</div></section>
<nav class="toc"><h2>فهرست</h2><ol>${tocHtml}</ol></nav>
${body}
</body></html>`;

const browser = await chromium.launch();
const page = await browser.newPage();
await page.setContent(html, { waitUntil: 'load' });
await page.evaluate(() => document.fonts.ready);
fs.mkdirSync(path.dirname(out), { recursive: true });
await page.pdf({
  path: out, format: 'A4', printBackground: true,
  margin: { top: '18mm', bottom: '20mm', left: '16mm', right: '16mm' },
  displayHeaderFooter: true,
  headerTemplate: '<div></div>',
  footerTemplate: '<div style="width:100%;font-size:8px;color:#8796aa;padding:0 16mm;display:flex;justify-content:space-between;font-family:sans-serif"><span>matnyaab.ir</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>',
});
await browser.close();
console.log('PDF:', out, fs.statSync(out).size, 'bytes');
