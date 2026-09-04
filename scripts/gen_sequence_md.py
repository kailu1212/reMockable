#!/usr/bin/env python3
"""從 docs/user-story-sequence.html 產生 docs/user-story-sequence.md。

markdown 版的用途是「在 GitHub 上直接看得到圖」—— GitHub 原生渲染 ```mermaid，
HTML 檔在 repo 裡點開只會看到原始碼。兩份同源，不會漂。
"""
import html as H, re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
src = (ROOT / "docs/user-story-sequence.html").read_text(encoding="utf-8")

def txt(s):
    s = re.sub(r"<br\s*/?>", " ", s)
    s = re.sub(r"<code[^>]*>(.*?)</code>", r"`\1`", s, flags=re.S)
    s = re.sub(r"<b>(.*?)</b>", r"**\1**", s, flags=re.S)
    s = re.sub(r"<[^>]+>", "", s)
    return re.sub(r"\s+", " ", H.unescape(s)).strip()

out = ["# reMockable User Story 時序圖（FE / BE / DB）", "",
       "**狀態：DRAFT — 內部工作稿**", ""]

lede = re.search(r'<p class="lede">(.*?)</p>', src, re.S)
if lede:
    out += [txt(lede.group(1)), ""]

callout = re.search(r'<div class="callout">.*?<span>\s*(.*?)\s*</span>\s*</div>', src, re.S)
if callout:
    out += ["> " + txt(callout.group(1)), ""]

out += ["---", ""]

for m in re.finditer(r'<section class="flow" id="s\d">(.*?)</section>', src, re.S):
    blk = m.group(1)
    num = txt(re.search(r'<span class="flow-num">(.*?)</span>', blk).group(1))
    h2 = txt(re.search(r"<h2>(.*?)</h2>", blk, re.S).group(1))
    blurb = re.search(r'<p class="blurb">(.*?)</p>', blk, re.S)
    mer = re.search(r'<pre class="mermaid">(.*?)</pre>', blk, re.S).group(1)
    out += [f"## {num}｜{h2}", ""]
    if blurb:
        out += [txt(blurb.group(1)), ""]
    out += ["```mermaid", H.unescape(mer).strip(), "```", ""]

out += ["---", "", "## 版本紀錄", "",
        "| 編號 | 時間 | 人員 | 版號 | 說明 |", "|---|---|---|---|---|",
        "| — | — | Lyon | DRAFT | 尚未發布。內部審閱中，此階段的修改不列入版本紀錄。 |", "",
        "> 本檔由 `scripts/gen_sequence_md.py` 從 `docs/user-story-sequence.html` 產生，請勿手改。"]

dest = ROOT / "docs/user-story-sequence.md"
dest.write_text("\n".join(out) + "\n", encoding="utf-8")
print(f"寫出 {dest.relative_to(ROOT)}：{len(out)} 行，{sum(1 for l in out if l=='```mermaid')} 張圖")
