#!/usr/bin/env python3
"""把 docs/spec.md 轉成可列印的 HTML（給 tech lead review 用）。

用法：python3 scripts/build_spec_html.py <輸出路徑>
產出的 HTML 需用瀏覽器列印成 PDF，mermaid 由 CDN 於載入時渲染。
"""
import subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
md = (ROOT / "docs/spec.md").read_text(encoding="utf-8")
body = subprocess.run(["pandoc", "-f", "gfm", "-t", "html",
                       "--syntax-highlighting=none", "--wrap=preserve"],
                      input=md, capture_output=True, text=True).stdout

HEAD = """<!doctype html><html lang="zh-Hant"><head><meta charset="utf-8">
<title>reMockable Backend Spec</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Noto+Sans+TC:wght@400;500;700;900&family=JetBrains+Mono:wght@400;500;600&display=swap">
<style>
  @page { size: A4 portrait; margin: 15mm 13mm; }
  :root {
    --bg:#fff; --surface:#fff; --surface-2:#f6f9fb; --ink:#171b21;
    --ink-muted:#565f6e; --line:#d7dee6; --accent:#1f6e8c;
    --warn-fg:#9a4b12; --warn-bg:#fbe9d6; --draft-fg:#8a5a0d; --draft-bg:#f8ecd6;
  }
  * { box-sizing:border-box; }
  body { background:var(--bg); color:var(--ink); line-height:1.68; margin:0;
    font-family:"Noto Sans TC","Hiragino Sans","PingFang TC",sans-serif; }
  .wrap { max-width:1000px; margin:0 auto; padding:0 4px 60px; }
  code, pre { font-family:"JetBrains Mono",ui-monospace,SFMono-Regular,monospace; }

  h1 { font-size:30px; font-weight:900; margin:0 0 6px; letter-spacing:-.02em; }
  h1 + table { margin-top:14px; }
  h1 { border-bottom:3px solid var(--ink); padding-bottom:12px; }
  h2 { font-size:23px; font-weight:900; margin:44px 0 8px; padding-bottom:9px;
    border-bottom:2px solid var(--ink); letter-spacing:-.01em; page-break-before:always; }
  h1 + * ~ h2:first-of-type { page-break-before:avoid; }
  h3 { font-size:17.5px; font-weight:700; margin:30px 0 8px; color:var(--accent); }
  h4 { font-size:15px; font-weight:700; margin:22px 0 6px; }
  h5 { font-size:13.5px; font-weight:600; margin:16px 0 5px; color:var(--ink-muted); }
  h2,h3,h4,h5 { page-break-after:avoid; text-wrap:balance; }
  p { margin:10px 0; max-width:76ch; }

  code { font-size:.9em; background:var(--surface-2); border:1px solid var(--line);
    border-radius:4px; padding:1px 5px; }
  pre { background:var(--surface-2); border:1px solid var(--line);
    border-left:3px solid var(--accent); border-radius:7px; padding:12px 14px;
    overflow-x:auto; font-size:11.6px; line-height:1.55; margin:12px 0;
    page-break-inside:avoid; }
  pre code { background:none; border:none; padding:0; font-size:inherit; }
  pre.mermaid { background:#fff; border:1px solid var(--line); border-left:1px solid var(--line);
    text-align:center; padding:16px; }

  table { border-collapse:collapse; width:100%; margin:14px 0; font-size:12.6px;
    font-variant-numeric:tabular-nums; page-break-inside:avoid; }
  th { background:var(--surface-2); text-align:left; font-weight:600; }
  th,td { border:1px solid var(--line); padding:6px 9px; vertical-align:top; }

  blockquote { margin:14px 0; padding:11px 15px; background:var(--warn-bg);
    border-left:3px solid var(--warn-fg); border-radius:0 7px 7px 0;
    font-size:13.2px; page-break-inside:avoid; }
  blockquote p { margin:0 0 6px; max-width:none; } blockquote p:last-child { margin:0; }

  ul,ol { padding-left:22px; max-width:76ch; } li { margin:4px 0; }
  /* 驗收條件 checkbox */
  li input[type=checkbox] { margin-right:7px; }
  ul.task-list { list-style:none; padding-left:2px; }
  ul.task-list li { font-size:13px; }
  hr { border:none; border-top:1px solid var(--line); margin:26px 0; }
  a { color:var(--accent); }
  .stamp { display:inline-block; font-family:"JetBrains Mono",monospace; font-size:11.5px;
    font-weight:600; letter-spacing:.08em; padding:3px 11px; border-radius:999px;
    color:var(--draft-fg); background:var(--draft-bg); border:1px solid var(--draft-fg);
    margin:10px 0 0; }
  .vh { margin-top:44px; page-break-inside:avoid; }
  .vh caption { text-align:left; font-weight:700; font-size:15px; padding-bottom:8px; }
</style></head><body><div class="wrap">
"""

VH = """
<div class="vh">
<table>
  <caption>版本紀錄</caption>
  <tr><th style="width:9%">編號</th><th style="width:15%">時間</th><th style="width:12%">人員</th><th style="width:11%">版號</th><th>說明</th></tr>
  <tr><td>—</td><td>—</td><td>Lyon</td><td>DRAFT</td><td>尚未發布。內部審閱中，此階段的修改不列入版本紀錄。</td></tr>
</table>
</div>
"""

TAIL = """
</div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/mermaid/11.15.0/mermaid.min.js"></script>
<script>
mermaid.initialize({startOnLoad:false,securityLevel:'loose',theme:'base',
  sequence:{useMaxWidth:true}, er:{useMaxWidth:true},
  themeVariables:{background:'#ffffff',mainBkg:'#ffffff',primaryColor:'#f6f9fb',
    primaryTextColor:'#171b21',lineColor:'#565f6e',primaryBorderColor:'#7a8794',
    fontSize:'13px',fontFamily:'"Noto Sans TC","PingFang TC",sans-serif'}});
(async()=>{const ps=[...document.querySelectorAll('pre.mermaid')];
 for(let i=0;i<ps.length;i++){
   try{const {svg}=await mermaid.render('m'+i, ps[i].textContent);
       const d=document.createElement('div'); d.className='mermaid-out';
       d.style.textAlign='center'; d.innerHTML=svg; ps[i].replaceWith(d);}
   catch(e){console.error('MERMAID_FAIL',i,e.message);}}
})();
</script></body></html>
"""

Path(sys.argv[1]).write_text(HEAD + body + VH + TAIL, encoding="utf-8")
print(f"寫出 {sys.argv[1]}")
