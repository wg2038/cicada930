#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Apply reviewed text-restoration patches to sections.jsonl.

The patch manifest uses visible-text anchors.  The same insertion is mapped
back into ``content`` while preserving OAM tags, so ``plain_of(content)``
always stays identical to ``plain``.

Usage:
    python3 etl/restore_text.py --check
    python3 etl/restore_text.py --apply
"""
import argparse
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import corpus  # noqa: E402

MANIFEST = os.path.join(HERE, "patches", "text_restoration.json")
REPORT = "/tmp/text_restoration_report.json"

# The thirteen Ten-Tables grid sections are protected by the Android golden
# fixture and must never be touched by this pass.
PROTECTED_SECTIONS = {
    (13, "p_16"), (13, "p_18"), (13, "p_20"),
    (14, "p_31"), (15, "p_35"), (16, "p_19"), (16, "p_21"),
    (17, "p_32"), (18, "p_29"), (19, "p_13"), (20, "p_11"),
    (21, "p_8"), (22, "p_5"),
}


def load_patches():
    with open(MANIFEST, encoding="utf-8") as f:
        return json.load(f)


def get_aliases(e):
    a = e.get("aliases", [])
    if isinstance(a, list):
        return [x for x in a if isinstance(x, str)]
    if isinstance(a, str):
        try:
            parsed = json.loads(a)
            if isinstance(parsed, list):
                return [x for x in parsed if isinstance(x, str)]
        except Exception:
            pass
        if a.strip():
            return [a.strip()]
    return []


def visible_units(content):
    """Return (visible_text, units) where each unit maps a visible run to content.

    A unit is either one plain character or one complete OAM tag.  Mapping whole
    tags prevents inserting text inside ``⟪人 非子⟫``.
    """
    units = []
    i = 0
    while i < len(content):
        m = corpus.OAM_RE.match(content, i)
        if m:
            display = m.group(2).split("|", 1)[0]
            units.append({"text": display, "start": i, "end": m.end()})
            i = m.end()
            continue
        ch = content[i]
        if ch in "*|":  # corpus.JUNK
            i += 1
        else:
            units.append({"text": ch, "start": i, "end": i + 1})
            i += 1
    return units


def apply_leading_index_stripping(units):
    """Mirror corpus.plain_of's repeated LEADING_INDEX removal."""
    chars = []
    owner = []
    for ui, u in enumerate(units):
        for ch in u["text"]:
            chars.append(ch)
            owner.append(ui)

    while True:
        text = "".join(chars)
        match = corpus.LEADING_INDEX.search(text)
        if not match:
            break
        del chars[match.start():match.end()]
        del owner[match.start():match.end()]

    # plain_of() calls .strip() at the end
    left, right = 0, len(chars)
    while left < right and chars[left].isspace():
        left += 1
    while right > left and chars[right - 1].isspace():
        right -= 1
    chars = chars[left:right]
    owner = owner[left:right]
    return "".join(chars), units, owner


def content_insert_index(content, plain, visible_index):
    """Map a visible-text offset to a safe content offset (never inside a tag)."""
    units = visible_units(content)
    visible, units, owner = apply_leading_index_stripping(units)
    if visible != (plain or "").strip():
        raise ValueError(
            "plain/content 映射失败：可见文本与 plain 不一致\n"
            f"visible={visible[:120]!r}\nplain={plain[:120]!r}"
        )
    if not owner:
        return 0
    if visible_index <= 0:
        return units[owner[0]]["start"]
    if visible_index >= len(visible):
        return units[owner[-1]]["end"]
    if owner[visible_index - 1] == owner[visible_index]:
        raise ValueError("插入点落在 OAM 标签内部，拒绝切标签")
    return units[owner[visible_index]]["start"]


def locate(plain, patch):
    """Return ('applied'|'apply'|'error', visible_index, message)."""
    before = patch.get("anchor_before", "")
    after = patch.get("anchor_after", "")
    insert = patch["insert_plain"]

    if insert in plain:
        if before and after:
            if (before + insert) in plain or (insert + after) in plain:
                return "applied", None, ""
        elif before:
            if plain.rstrip().endswith(before + insert):
                return "applied", None, ""
        elif after:
            if plain.lstrip().startswith(insert + after):
                return "applied", None, ""
        return "error", None, "补文已存在，但锚点上下文不匹配"

    if before and after:
        pattern = re.escape(before) + r"\s*" + re.escape(after)
        matches = list(re.finditer(pattern, plain))
        if len(matches) != 1:
            return "error", None, f"锚点对匹配 {len(matches)} 次（要求恰好 1 次）"
        m = matches[0]
        return "apply", m.end() - len(after), ""

    if before:
        if not plain.rstrip().endswith(before):
            return "error", None, "段末锚点不在段末"
        return "apply", plain.rstrip().rfind(before) + len(before), ""

    if after:
        if not plain.lstrip().startswith(after):
            return "error", None, "段首锚点不在段首"
        return "apply", plain.find(after), ""

    return "error", None, "anchor_before 与 anchor_after 至少需要一个"


def check_translation(text, label):
    if '"' in text:
        raise ValueError(f"{label}: 译文含 ASCII 双引号")
    if text.count("“") != text.count("”") or text.count("‘") != text.count("’"):
        raise ValueError(f"{label}: 译文引号不配对")


def update_translation(translation, patch):
    t = translation or ""
    replacement = patch.get("translation_replace")
    addition = patch.get("translation_add")
    changed = False
    if replacement:
        old, new = replacement
        if new in t:
            pass
        elif old in t:
            t = t.replace(old, new, 1)
            changed = True
        else:
            raise ValueError("译文替换目标不存在")
    if addition:
        if addition not in t:
            t = t + addition
            changed = True
    return t, changed


def main():
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="只检查，不写盘（默认）")
    mode.add_argument("--apply", action="store_true", help="写回 sections/chapters")
    parser.add_argument("--manifest", default=MANIFEST)
    args = parser.parse_args()
    do_apply = args.apply

    patches = load_patches()
    sections = corpus.load_table("sections")
    chapters = corpus.load_table("chapters")
    entities = corpus.load_table("entities")

    entity_keys = {e["id"] for e in entities}
    entity_keys |= {a for e in entities for a in get_aliases(e)}

    sec_map = {(s["chapter_id"], s["pn"]): s for s in sections}
    chapter_map = {c["id"]: c for c in chapters}

    report = {"applied": [], "pending": [], "errors": [], "chapters_word_delta": {}}
    changed_sections = False
    changed_chapters = False

    for patch in patches:
        cid, pn = patch["chapter_id"], patch["pn"]
        label = f"ch{cid}#{pn}"
        if (cid, pn) in PROTECTED_SECTIONS:
            report["errors"].append({"patch": label, "error": "目标属于受保护的表 section"})
            continue
        sec = sec_map.get((cid, pn))
        if not sec:
            report["errors"].append({"patch": label, "error": "section 不存在"})
            continue
        if sec.get("type") != "paragraph":
            report["errors"].append({"patch": label, "error": f"section type={sec.get('type')} 不是 paragraph"})
            continue
        if (cid, pn) in PROTECTED_SECTIONS:
            report["errors"].append({"patch": label, "error": "受保护 section"})
            continue

        state, vis_idx, err = locate(sec["plain"], patch)
        if state == "error":
            report["errors"].append({"patch": label, "error": err})
            continue
        if state == "applied":
            report["applied"].append({"patch": label, "state": "already_applied"})
            continue

        try:
            content_idx = content_insert_index(sec["content"], sec["plain"], vis_idx)
        except ValueError as e:
            report["errors"].append({"patch": label, "error": str(e)})
            continue

        insert = patch["insert_plain"]
        new_content = sec["content"][:content_idx] + insert + sec["content"][content_idx:]
        new_plain = sec["plain"][:vis_idx] + insert + sec["plain"][vis_idx:]

        errs = corpus.validate_section(new_content, new_plain, entity_keys)
        if errs:
            report["errors"].append({"patch": label, "error": f"补丁后校验失败: {errs}"})
            continue

        try:
            new_translation, t_changed = update_translation(sec.get("translation", ""), patch)
            check_translation(new_translation, label)
        except ValueError as e:
            report["errors"].append({"patch": label, "error": str(e)})
            continue

        delta = sum(1 for ch in insert if "一" <= ch <= "鿿")
        item = {
            "patch": label,
            "state": "applied" if do_apply else "would_apply",
            "insert_cjk": delta,
            "translation_changed": t_changed,
        }
        if do_apply:
            sec["content"] = new_content
            sec["plain"] = new_plain
            sec["translation"] = new_translation
            changed_sections = True
            report["chapters_word_delta"][cid] = report["chapters_word_delta"].get(cid, 0) + delta
        report["pending"].append(item)

    if do_apply and report["chapters_word_delta"]:
        for cid, delta in report["chapters_word_delta"].items():
            chapter_map[cid]["word_count"] += delta
        changed_chapters = True

    # A pending item means the corpus still needs this pass.
    if report["errors"]:
        for e in report["errors"]:
            print(f"[ERROR] {e['patch']}: {e['error']}")
        print(f"补字失败：{len(report['errors'])} 处错误")
        _write_report(report)
        return 1

    if changed_sections:
        corpus.save_table("sections", sections)
    if changed_chapters:
        corpus.save_table("chapters", chapters)

    for item in report["pending"]:
        print(f"[{item['state']}] {item['patch']} +{item['insert_cjk']} CJK")
    for item in report["applied"]:
        print(f"[already_applied] {item['patch']}")

    _write_report(report)
    if do_apply:
        print(
            f"补字完成：新增 {len(report['pending'])} 处，已存在 {len(report['applied'])} 处，"
            f"章节字数增量 {report['chapters_word_delta']}"
        )
        return 0

    if report["pending"]:
        print(f"[check] 还有 {len(report['pending'])} 处补丁待应用；运行 --apply 写盘")
        return 1
    print(f"[check] 全部 {len(report['applied'])} 处补丁已应用")
    return 0


def _write_report(report):
    with open(REPORT, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"report -> {REPORT}")


if __name__ == "__main__":
    sys.exit(main())
