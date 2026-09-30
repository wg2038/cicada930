#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OpusOne 语料库共享库：格式常量、OAM 校验、读写工具。规范见 docs/CORPUS_FORMAT.md 与 docs/ANNOTATION_SPEC.md"""
import json, os, re

CORPUS_DIR = os.environ.get("CORPUS_DIR", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "opusone-corpus"))
FORMAT_ID = "opusone-corpus/1"

OAM_CODES = {
    # 历史双字母代码兼容
    "PE", "PL", "OF", "ID", "CL", "AR", "BI", "CO", "AS", "QU",
    "CY", "MV", "XV", "PV", "EV", "TX",
    # 古典单字汉化代码 (OAM v1.1)
    "人", "地", "官", "族", "群", "器", "生", "思", "历", "数",
    "语", "战", "刑", "政", "赋", "录",
}
OAM_RE = re.compile(r"⟪([A-Za-z]{2}|[\u4e00-\u9fa5]) ([^⟪⟫]+?)⟫")
STRAY_OAM = re.compile(r"⟪|⟫")
JUNK = re.compile(r"\*\*|\*|\|")
LEADING_INDEX = re.compile(r"(?m)^\s*(\[\d+(?:\.\d+)*\]|\[[0-9a-zA-Z_.]+\]|h\d+[_\s]?\d*|#{1,6}|:::|>)\s*")

TABLES = ["chapters", "sections", "entities", "occurrences",
          "chengyu", "wars", "stories", "taishigongyue", "sanjiazhu"]


def strip_oam(text: str) -> str:
    """OAM → 纯文本：显示词保留、别名索引键丢弃、剥残留包裹符。"""
    t = OAM_RE.sub(lambda m: m.group(2).split("|")[0], text)
    return STRAY_OAM.sub("", t)


def plain_of(content: str) -> str:
    """校验用：去标注 + 去残余符号 + 循环剥段首索引/标题标记（与客户端渲染口径一致）。"""
    t = JUNK.sub("", strip_oam(content))
    prev = None
    while prev != t:
        prev = t
        t = LEADING_INDEX.sub("", t)
    return t.strip()


def validate_section(content: str, plain: str, entity_keys=None):
    """返回错误列表；空列表 = 通过。规则见 docs/CORPUS_FORMAT.md。"""
    errors = []
    if plain_of(content) != (plain or "").strip():
        errors.append("TEXT_IDENTITY: 去标注文本与 plain 不一致")
    depth = 0
    for ch in content:
        if ch == "⟪":
            depth += 1
            if depth > 1:
                errors.append("GRAMMAR: ⟪ 嵌套")
                break
        elif ch == "⟫":
            depth -= 1
            if depth < 0:
                errors.append("GRAMMAR: ⟫ 多余")
                break
    if depth != 0:
        errors.append("GRAMMAR: ⟪⟫ 不配对")
    for m in OAM_RE.finditer(content):
        code = m.group(1)
        if code not in OAM_CODES:
            errors.append(f"CODE: 未知代码 {code}")
    if entity_keys is not None:
        for m in OAM_RE.finditer(content):
            body = m.group(2)
            if "|" in body:
                key = body.split("|", 1)[1].strip()
                if key and key not in entity_keys:
                    errors.append(f"ENTITY: 索引键未登记 {key}")
    return errors


def load_table(name):
    path = os.path.join(CORPUS_DIR, f"{name}.jsonl")
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        return [json.loads(ln) for ln in f if ln.strip()]


def save_table(name, rows):
    """将数据整表原子写回为 JSONL 文件。先写临时文件后通过 os.replace 替换，避免写入中断产生脏数据。"""
    path = os.path.join(CORPUS_DIR, f"{name}.jsonl")
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    os.replace(tmp, path)


def load_manifest():
    path = os.path.join(CORPUS_DIR, "manifest.json")
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    return {"format": FORMAT_ID, "chapters": {}}


def save_manifest(m):
    path = os.path.join(CORPUS_DIR, "manifest.json")
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(m, f, ensure_ascii=False, indent=1)
    os.replace(tmp, path)
