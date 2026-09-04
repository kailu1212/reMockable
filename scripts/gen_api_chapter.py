#!/usr/bin/env python3
"""從 openapi.yaml + spec.md §4 產生「Endpoint 規格」章節的 HTML。

每支 endpoint 固定四段：Endpoint / Request / Response / 失敗情況，
欄位說明表由 openapi.yaml 的 schema 產生，所以不會跟實作漂掉。
"""
import json, re, sys, yaml
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "docs/spec.md"
OAS  = ROOT / "docs/openapi.yaml"
JAVA = ROOT / "src/main/java/com/remockable/api/exception/RespErrCode.java"
AC = {}

d = yaml.safe_load(OAS.read_text(encoding="utf-8"))
COMP = d["components"]

# ---------- 錯誤碼表（來源：RespErrCode.java，程式碼即事實） ----------
ERR = {}
for m in re.finditer(r'^\s{4}([A-Z_]+)\((\d{3}),\s*"([a-z_]+)",\s*(true|false)\)', 
                     JAVA.read_text(encoding="utf-8"), re.M):
    ERR[m.group(1)] = dict(status=int(m.group(2)), key=m.group(3),
                           retryable=(m.group(4) == "true"))

def deref(node):
    while isinstance(node, dict) and "$ref" in node:
        ref = node["$ref"].split("/")[-1]
        sec = node["$ref"].split("/")[-2]
        node = COMP[sec][ref]
    return node

def merged(schema):
    """把 allOf 併起來，回傳可讀的 (type, properties, required)。"""
    s = deref(schema)
    if "allOf" in s:
        props, req, out = {}, [], {}
        for part in s["allOf"]:
            p = merged(part)
            props.update(p.get("properties") or {})
            req += p.get("required") or []
            out.update({k: v for k, v in p.items() if k not in ("properties", "required")})
        out["properties"], out["required"] = props, req
        return out
    return s

def sample(schema, depth=0):
    """從 schema 造一個範例值。"""
    s = merged(schema)
    if depth > 6: return "…"
    if "example" in s: return s["example"]
    if "oneOf" in s:   return sample(s["oneOf"][0], depth + 1)
    if "enum" in s:    return s["enum"][0]
    t = s.get("type")
    if t == "object" or "properties" in s:
        return {k: sample(v, depth + 1) for k, v in (s.get("properties") or {}).items()}
    if t == "array":
        return [sample(s.get("items", {}), depth + 1)]
    if t == "integer": return 0
    if t == "number":  return 0.0
    if t == "boolean": return True
    if s.get("nullable"): return None
    fmt = s.get("format")
    if fmt == "date-time": return "2026-09-10T02:11:07Z"
    if fmt == "email":     return "user@example.com"
    if fmt == "binary":    return "<binary>"
    return "…"

def typename(schema):
    s = deref(schema)
    if "allOf" in s: return "object"
    if "oneOf" in s: return "object"
    if "enum" in s:  return "enum"
    t = s.get("type", "object")
    if t == "array":
        it = schema.get("items", {})
        inner = it["$ref"].split("/")[-1] if "$ref" in it else deref(it).get("type", "object")
        return f"array&lt;{inner}&gt;"
    if s.get("format") in ("date-time", "email", "binary"): return f'{t}<span class="fmt">/{s["format"]}</span>'
    return t

def rows(schema, prefix="", depth=0, out=None):
    """攤平成欄位表的列：(欄位, 型別, 必填, 說明)。"""
    out = out if out is not None else []
    s = merged(schema)
    req = set(s.get("required") or [])
    for name, raw in (s.get("properties") or {}).items():
        sub = deref(raw)
        desc = raw.get("description") or sub.get("description") or ""
        if sub.get("enum"):
            vals = " / ".join(f"<code>{json.dumps(e, ensure_ascii=False)}</code>" for e in sub["enum"])
            desc = (desc + "　值：" + vals).strip("　")
        nullable = raw.get("nullable") or sub.get("nullable")
        out.append((prefix + name, typename(raw) + ("?" if nullable else ""),
                    "必填" if name in req else "", desc))
        if depth < 2:
            if sub.get("type") == "object" and sub.get("properties"):
                rows(sub, prefix + name + ".", depth + 1, out)
            elif sub.get("type") == "array":
                it = deref(sub.get("items", {}))
                if it.get("properties"):
                    rows(it, prefix + name + "[].", depth + 1, out)
    return out

def esc(x):
    return (str(x).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))

def table(rs, caption):
    if not rs: return ""
    body = "".join(
        f"<tr><td><code>{esc(f)}</code></td><td class='ty'>{t}</td>"
        f"<td class='rq'>{r}</td><td>{dsc}</td></tr>" for f, t, r, dsc in rs)
    return (f'<div class="fieldtable"><p class="tcap">{caption}</p><div class="scroll-x"><table>'
            f'<tr><th style="width:26%">欄位</th><th style="width:15%">型別</th>'
            f'<th style="width:8%">必要</th><th>說明</th></tr>{body}</table></div></div>')

def jsonblock(obj, label):
    return (f'<p class="tcap">{label}</p>'
            f'<pre><code>{esc(json.dumps(obj, ensure_ascii=False, indent=2))}</code></pre>')

# ---------- 從 spec.md §4 取出分節結構與人工註記 ----------
md = SPEC.read_text(encoding="utf-8")
sec4 = md[md.index("\n# 4. 功能規格"): md.index("\n# 5. 資料表定義")]

SECTION_RAW = {}
RAW_ERRS = {}
SECTIONS = []           # [(章節標題, [ (METHOD, path, 註記html) ... ])]
cur = None
for chunk in re.split(r"\n(?=## )", sec4):
    h2 = re.match(r"## (.+)", chunk)
    if not h2:
        continue
    cur = (h2.group(1).strip(), [])
    SECTIONS.append(cur)
    SECTION_RAW[cur[0]] = chunk
    for blk in re.split(r"\n(?=### )", chunk)[1:]:
        head = re.match(r"### `?([A-Z]+) (/api[^`\s]*)`?(.*)", blk)
        if not head:
            # 4.4 這種沒有 endpoint、只有說明的小節
            cur[1].append((None, None, blk[blk.index("\n"):].strip()))
            continue
        method, path = head.group(1), head.group(2)
        body = blk[blk.index("\n") + 1:]
        RAW_ERRS[f"{method} {path}"] = body
        acm = re.search(r"\*\*驗收條件\*\*\n\n((?:- \[ \] .*\n)+)", body)
        if acm:
            AC[f"{method} {path}"] = [ln[6:].strip() for ln in acm.group(1).strip().split("\n")]
            body = body.replace(acm.group(0), "")                        # 剝除前先留一份給錯誤碼用
        body = re.sub(r"```json.*?```", "", body, flags=re.S)      # JSON 改由 schema 產
        body = re.sub(r"^\*\*(Request body|Response|Job result)\*\*.*$", "", body, flags=re.M)
        body = re.sub(r"^\*\*(錯誤|失敗)\*\*[:：].*?(?=\n\n|\Z)", "", body, flags=re.M | re.S)
        body = re.sub(r"^\*\*參數\*\*\n\n\|.*?(?=\n\n)", "", body, flags=re.M | re.S)
        cur[1].append((method, path, body.strip()))

# 該 endpoint 在 spec 提到的錯誤碼（保留 spec 的分辨，程式碼補型別）
def errs_for(method, path):
    src = RAW_ERRS.get(f"{method} {path}", "")
    codes = [c for c in re.findall(r"`([A-Z][A-Z_]{3,})`", src) if c in ERR]
    base = ["VALIDATION_ERROR"] if method in ("POST", "PUT", "PATCH") else []
    if "{" in path: base.append("NOT_FOUND")
    # P2C 起 Authorization 必填，缺少或無效一律 401
    if path not in ("/api/health", "/api/auth/email/request",
                    "/api/auth/email/verify", "/api/auth/google"):
        base.append("ACCESS_DENIED")
    base.append("INTERNAL_ERROR")
    seen, out = set(), []
    for c in codes + base:
        if c not in seen:
            seen.add(c); out.append(c)
    return out

def errtable(codes):
    body = "".join(
        f"<tr><td><code>{c}</code></td><td class='st st{ERR[c]['status']//100}'>{ERR[c]['status']}</td>"
        f"<td><code>{ERR[c]['key']}</code></td>"
        f"<td class='rq'>{'是' if ERR[c]['retryable'] else '否'}</td></tr>" for c in codes)
    return ('<div class="scroll-x"><table><tr><th style="width:32%">code</th>'
            '<th style="width:10%">HTTP</th><th style="width:32%">messageKey</th>'
            f'<th style="width:10%">可重試</th></tr>{body}</table></div>')

def wrap_resp(inner, paged=False):
    if paged:
        return {"timestamp": 1788289513681, "size": None, "page": 0,
                "totalPages": 1, "total": 5, "data": inner}
    return {"timestamp": 1788289513681, "data": inner}

import subprocess
def md2html(text):
    if not text.strip(): return ""
    return subprocess.run(["pandoc", "-f", "gfm", "-t", "html"], input=text,
                          capture_output=True, text=True).stdout

PATHS = d["paths"]
def find_op(method, path):
    key = path[len("/api"):] or "/"
    item = PATHS.get(key)
    return (item or {}).get(method.lower()), key

def render(method, path, op, notes, n_ep):
    O = []
    summary = op.get("summary", "")
    ok_codes = sorted(c for c in op.get("responses", {}) if c.startswith("2"))
    async_ = "202" in ok_codes
    if len(ok_codes) > 1:
        badge = '<span class="pill dual">同步／非同步</span>'
    elif async_:
        badge = '<span class="pill async">非同步</span>'
    else:
        badge = '<span class="pill sync">同步</span>'

    O.append(f'<div class="ep" id="ep-{n_ep}">')
    O.append(f'<h4 class="ephead"><span class="mth m{method}">{method}</span>'
               f'<code class="path">{esc(path)}</code>{badge}</h4>')
    if summary: O.append(f'<p class="epsum">{esc(summary)}</p>')
    if notes:
        O.append(f'<div class="epnotes">{md2html(notes)}</div>')
    elif op.get("description"):
        O.append(md2html(op["description"]))

    # ── Request ──
    O.append('<p class="part">Request</p>')
    params = [deref(p) for p in op.get("parameters", [])]
    rb = op.get("requestBody")
    ctype = next(iter(rb["content"])) if rb else None
    hdr = [f"{method} {path}"]
    if ctype: hdr.append(f"Content-Type: {ctype}")
    hdr.append("Authorization: Bearer {token}        # P2C 起必填，之前後端忽略")
    if method == "POST": hdr.append("Idempotency-Key: {uuid v4}       # 建議帶，避免重整造成重複扣模型成本")
    O.append(f'<pre><code>{esc(chr(10).join(hdr))}</code></pre>')

    prs = [(p["name"], p["in"], "必填" if p.get("required") else "",
            typename(p.get("schema", {})),
            (p.get("description") or "") +
            ("　值：" + " / ".join(f"<code>{e}</code>" for e in deref(p.get("schema", {})).get("enum", []))
             if deref(p.get("schema", {})).get("enum") else ""))
           for p in params]
    if prs:
        body = "".join(f"<tr><td><code>{esc(a)}</code></td><td class='ty'>{b}</td>"
                       f"<td class='rq'>{c}</td><td class='ty'>{t}</td><td>{dsc}</td></tr>"
                       for a, b, c, t, dsc in prs)
        O.append('<div class="fieldtable"><p class="tcap">參數</p><div class="scroll-x"><table>'
                   '<tr><th style="width:20%">參數</th><th style="width:12%">位置</th>'
                   '<th style="width:9%">必要</th><th style="width:13%">型別</th>'
                   f'<th>說明</th></tr>{body}</table></div></div>')
    if rb:
        sc = rb["content"][ctype].get("schema", {})
        if ctype == "multipart/form-data":
            O.append(table(rows(sc), "表單欄位"))
        else:
            O.append(jsonblock(sample(sc), "Request body"))
            O.append(table(rows(sc), "Request 欄位說明"))
    elif not prs:
        O.append('<p class="none">無參數、無 request body。</p>')

    # ── Response ──
    if len(ok_codes) > 1:
        O.append('<p class="part">Response — 這支有兩種成功回應</p>')
        O.append('<p class="dualnote">同一個請求依情況回不同狀態碼，'
                 '<b>前端要用 HTTP status 分流</b>：'
                 + "；".join(f"<code>{c}</code> {deref(op['responses'][c]).get('description','')}"
                             for c in ok_codes) + '。</p>')
    for okc in ok_codes:
        resp = deref(op["responses"][okc])
        desc = resp.get("description", "")
        if len(ok_codes) > 1:
            O.append(f'<p class="part sub">HTTP {okc} — {esc(desc)}</p>')
        else:
            O.append(f'<p class="part">Response — HTTP {okc}</p>')
        if okc == "204" or "content" not in resp:
            O.append('<p class="none">無回應內容（204 No Content）。</p>'); continue
        rsc = resp["content"]["application/json"]["schema"]
        m = merged(rsc)
        inner = (m.get("properties") or {}).get("data", {})
        paged = "CommonPageResp" in json.dumps(rsc)
        O.append(jsonblock(wrap_resp(sample(inner), paged), "完整回應（含外層包裝）"))
        rs = ([("timestamp", "integer", "必填", "後端產生此回應的 epoch millis")] +
              ([("size", "integer?", "", "每頁筆數；未分頁為 null"),
                ("page", "integer", "", "目前頁碼，從 0 開始"),
                ("totalPages", "integer", "", "總頁數"),
                ("total", "integer", "", "總筆數")] if paged else []) +
              [("data" + ("[]" if paged else ""), typename(inner), "必填", "以下為 data 的內容")] +
              [("data" + ("[]." if paged else ".") + f, t, r, dsc) for f, t, r, dsc in rows(inner)])
        O.append(table(rs, "回應欄位說明"))

    # ── 失敗情況 ──
    acs = AC.get(f"{method} {path}", [])
    if acs:
        O.append('<p class="part">驗收條件</p>')
        O.append('<ul class="ac">' + "".join(
            f'<li>{md2html(a)[3:-5] if md2html(a).startswith("<p>") else esc(a)}</li>'
            for a in acs) + '</ul>')
    O.append('<p class="part">失敗情況</p>')
    O.append(errtable(errs_for(method, path)))
    if async_:
        O.append('<p class="none">非同步 endpoint 的失敗也可能發生在 job 執行期間 —— '
                   '此時 <code>POST</code> 仍回 202，錯誤出現在 <code>GET /api/jobs/{jobId}</code> '
                   '的 <code>data.error</code>，欄位與上表相同。</p>')
    O.append("</div>")
    return "\n".join(O)

HAS_HEADING = {f"{me} {pa}" for _t, eps in SECTIONS for me, pa, _n in eps if me}

out = []
n_ep = 0
EMITTED = set()
for si, (title, eps) in enumerate(SECTIONS, start=1):
    out.append(f'<h3 class="sec">{esc(title)}</h3>')
    for method, path, notes in eps:
        if method is None:
            out.append(md2html(notes)); continue
        op, key = find_op(method, path)
        if op is None:
            out.append(f'<p class="miss">⚠ openapi.yaml 找不到 {method} {path}</p>'); continue
        EMITTED.add(f"{method} {path}")
        n_ep += 1
        out.append(render(method, path, op, notes, n_ep))

    # 這一節的表格裡提到、但沒有獨立 ### 標題的 endpoint（例如 4.10 的登入表）
    raw = SECTION_RAW.get(title, "")
    pat = r"`([A-Z]+) (/api/[^`]+)`|`?([A-Z]+)`?\s*\|?\s*`(/api/[^`]+)`"
    for mm in re.finditer(pat, raw):
        meth = mm.group(1) or mm.group(3)
        pth  = mm.group(2) or mm.group(4)
        sig = f"{meth} {pth}"
        if sig in HAS_HEADING: continue   # 別的小節有它自己的標題，交給主迴圈
        if sig in EMITTED: continue
        op2, _ = find_op(meth, pth)
        if op2 is None: continue
        EMITTED.add(sig); n_ep += 1
        out.append(render(meth, pth, op2, "", n_ep))

Path(sys.argv[1]).write_text("\n".join(out), encoding="utf-8")
print(f"產生 {n_ep} 支 endpoint，{len(SECTIONS)} 個小節")
