#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Conservative entity-table cleanup for the OpusOne corpus.

Phase ``structure``:
  * merge label variants that differ only by quotation marks / punctuation
  * resolve the leaked uppercase ``REDIRECT`` row
  * strip curation meta-text and remove index/provenance-only entries
  * apply display-only ``type_name_zh`` labels (``type`` is never changed)

Phase ``derived`` (run after rebuild_occurrences.py):
  * replace fabricated ``出现于《史记》N处`` counts with real counts

Usage:
    python3 etl/cleanup_entities.py --phase structure --check
    python3 etl/cleanup_entities.py --phase structure --apply
    python3 etl/cleanup_entities.py --phase derived --apply
"""
import argparse
import copy
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import corpus  # noqa: E402

REPORT = "/tmp/entity_cleanup_report.json"

TYPE_NAME_ZH = {
    "person": "人物",
    "event": "历史事件",
    "place": "地名",
    "official": "官职",
    "artifact": "器物名物",
    "quantity": "数量度量",
    "institution": "典章制度",
    "time": "时间纪年",
    "year": "年份纪年",
    "overview": "通览综述",
    "identity": "身份群体",
    "story": "历史故事",
    "concept": "思想天命",
    "biology": "生物",
    "chengyu": "成语典故",
    "侯国": "侯国",
    "legal": "刑法刑律",
    "sanwen": "散文篇章",
    "astronomy": "天文历法",
    "state": "邦国",
    "book": "典籍著作",
    "skill": "权谋术略",
    "dynasty": "朝代",
    "tribe": "族群氏族",
    "chapter": "典籍篇目",
    "taishigongyue": "太史公曰",
    "xing": "姓氏",
    "shihao": "谥号",
    "verb": "动词",
    "list": "名物清单",
    "mythical": "神话传说",
    "map": "地理图志",
    "bihui": "避讳",
    "timeline": "年表时序",
    "ritual": "礼制",
    "disambiguation": "歧义辨析",
    "jun": "郡",
}

# Index / provenance-only rows.  They are not historical entities.
META_REMOVE_IDS = {
    "史记中的成语典故",
    "史记经典名句",
    "庄子的思想与作品",
    "申不害的治思想与韩国改革",
    "申不害的术治思想与韩国改革",
    "老莱子身份疑辨",
}

# Chapter descriptions that leaked a curation note in the tail.
CHAPTER_DESC_TRIM = {
    "034_燕召公世家",
    "108_韩长孺列传",
    "111_卫将军骠骑列传",
}

# High-confidence duplicate pairs that label normalisation cannot catch.
# (duplicate id, canonical id)
MANUAL_MERGES = [
    ("康叔", "卫康叔"),
]

# Aliases copied onto the wrong entity by the original curation pass.  These
# are the titles of Emperor Wu (今上/孝武/武帝) sitting on a Warring States
# strategist (段干子) and an unrelated 殇侯, plus 刘彻 on 殇侯.
MANUAL_ALIAS_REMOVE = {
    "段干子": {"今上", "孝武", "孝武帝", "武帝", "武", "胶东王"},
    "殇侯": {"刘彻", "武帝"},
}
MANUAL_ALIAS_ADD = {
    "孝武皇帝": {"今上", "孝武", "孝武帝", "武帝"},
}

LABEL_NOISE_RE = re.compile(
    r"[\s“”\"‘’'「」『』《》〈〉\[\]【】（）()—\-–·:：,，.。、;；!！?？]"
)
FACT_PREFIX_RE = re.compile(r"^Fact\s*\d+\s*[：:]\s*")
META_TAIL_RE = re.compile(r"\s*本章为.*$", re.S)
TEMPLATE_COUNT_RE = re.compile(r"[，,]\s*出现于《史记》\d+处")


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


def norm_label(s):
    return LABEL_NOISE_RE.sub("", s or "")


def dedupe_keep_order(values):
    seen = set()
    out = []
    for v in values:
        v = (v or "").strip()
        if v and v not in seen:
            seen.add(v)
            out.append(v)
    return out


def add_aliases(entity, values):
    aliases = get_aliases(entity)
    for v in values:
        if v and v != entity["label"] and v != entity["id"]:
            aliases.append(v)
    entity["aliases"] = dedupe_keep_order(aliases)


def tag_keys(sections):
    keys = set()
    for s in sections:
        for m in corpus.OAM_RE.finditer(s.get("content", "")):
            body = m.group(2)
            keys.add(body.split("|", 1)[1].strip() if "|" in body else body.strip())
    return keys


def merge_label_variants(entities, report):
    groups = {}
    for e in entities:
        k = norm_label(e["label"])
        if k:
            groups.setdefault(k, []).append(e)
    remove_ids = set()
    for key, group in groups.items():
        if len(group) < 2:
            continue
        def quote_pref(e):
            label = e.get("label") or ""
            return (label.count("“") + label.count("”")) * 2 + label.count("「") + label.count("」")

        group.sort(key=lambda e: (
            -(e.get("occurrences_count") or 0),
            -len(e.get("description") or ""),
            -len(get_aliases(e)),
            -quote_pref(e),
            e["id"],
        ))
        canonical = group[0]
        merged = []
        for other in group[1:]:
            if other["id"] == canonical["id"]:
                continue
            add_aliases(canonical, [other["id"], other["label"]] + get_aliases(other))
            remove_ids.add(other["id"])
            merged.append(other["id"])
        report["merged_groups"].append({
            "normalized": key,
            "keep": canonical["id"],
            "removed": merged,
        })
    return [e for e in entities if e["id"] not in remove_ids]


def merge_manual(entities, report):
    by_id = {e["id"]: e for e in entities}
    remove_ids = set()
    for dup_id, keep_id in MANUAL_MERGES:
        if dup_id in remove_ids or dup_id not in by_id or keep_id not in by_id:
            continue
        dup, keep = by_id[dup_id], by_id[keep_id]
        add_aliases(keep, [dup["id"], dup["label"]] + get_aliases(dup))
        keep["occurrences_count"] = int(keep.get("occurrences_count") or 0) + int(
            dup.get("occurrences_count") or 0
        )
        remove_ids.add(dup_id)
        report["merged_groups"].append({
            "normalized": "manual",
            "keep": keep_id,
            "removed": [dup_id],
        })
    return [e for e in entities if e["id"] not in remove_ids]


def fix_manual_aliases(entities, report):
    by_id = {e["id"]: e for e in entities}
    for eid, bad in MANUAL_ALIAS_REMOVE.items():
        e = by_id.get(eid)
        if not e:
            continue
        before = get_aliases(e)
        after = [a for a in before if a not in bad]
        if after != before:
            e["aliases"] = after
            report["aliases"].append({"id": eid, "removed": sorted(set(before) - set(after))})
    for eid, good in MANUAL_ALIAS_ADD.items():
        e = by_id.get(eid)
        if not e:
            continue
        before = get_aliases(e)
        add_aliases(e, sorted(good))
        after = get_aliases(e)
        if after != before:
            report["aliases"].append({"id": eid, "added": sorted(set(after) - set(before))})
    return entities


def resolve_redirects(entities, referenced, report):
    by_label = {}
    by_id = {}
    for e in entities:
        by_label.setdefault(e["label"], []).append(e)
        by_id[e["id"]] = e

    out = []
    for e in entities:
        desc = e.get("description") or ""
        is_redirect = str(e.get("type", "")).upper() == "REDIRECT" or desc.startswith("REDIRECT ")
        if not is_redirect:
            out.append(e)
            continue
        m = re.search(r"\[\[([^\]]+)\]\]", desc)
        target_name = m.group(1).strip() if m else None
        targets = by_label.get(target_name, []) if target_name else []
        if not targets and target_name in by_id:
            targets = [by_id[target_name]]
        if len(targets) == 1:
            target = targets[0]
            add_aliases(target, [e["id"], e["label"]] + get_aliases(e))
            report["redirects"].append({
                "removed": e["id"], "target": target["id"], "status": "merged",
            })
        else:
            report["redirects"].append({
                "removed": e["id"], "target": target_name, "status": "removed_no_target",
                "referenced": e["id"] in referenced,
            })
        # A redirect row is never kept, even when its target cannot be resolved.
    return out


def strip_meta(entities, referenced, report):
    out = []
    for e in entities:
        eid = e["id"]
        if eid in META_REMOVE_IDS:
            is_referenced = eid in referenced or e["label"] in referenced
            if is_referenced:
                e["description"] = "《史记》相关综述条目。"
                report["meta"].append({"id": eid, "action": "description_reset_referenced"})
                out.append(e)
            else:
                report["meta"].append({"id": eid, "action": "removed"})
            continue

        desc = e.get("description") or ""
        if eid in CHAPTER_DESC_TRIM and "本章为" in desc:
            e["description"] = META_TAIL_RE.sub("", desc).rstrip("，, ")
            report["meta"].append({"id": eid, "action": "tail_removed"})

        label = e.get("label") or ""
        if FACT_PREFIX_RE.match(label):
            old = label
            e["label"] = FACT_PREFIX_RE.sub("", label).strip()
            report["meta"].append({"id": eid, "action": "fact_prefix_removed", "old": old})
        out.append(e)
    return out


def phase_structure(entities, sections):
    referenced = tag_keys(sections)
    report = {
        "merged_groups": [], "redirects": [], "meta": [],
        "type_names": 0, "aliases": [],
    }
    # Strip meta/label prefixes before merging so a de-prefixed label can
    # itself be detected as a duplicate in the same run.
    entities = resolve_redirects(entities, referenced, report)
    entities = strip_meta(entities, referenced, report)
    entities = merge_label_variants(entities, report)
    entities = merge_manual(entities, report)
    entities = fix_manual_aliases(entities, report)

    for e in entities:
        new_name = TYPE_NAME_ZH.get(e.get("type"))
        if new_name and e.get("type_name_zh") != new_name:
            e["type_name_zh"] = new_name
            report["type_names"] += 1

    if len(entities) < 20000:
        raise SystemExit(f"实体数降为 {len(entities)}，低于保守清理下限 20000，拒绝写盘")
    return entities, report


def phase_derived(entities):
    report = {"count_rewrites": 0, "count_removals": 0}
    for e in entities:
        desc = e.get("description") or ""
        if "出现于《史记》" not in desc:
            continue
        actual = int(e.get("occurrences_count") or 0)
        new_desc = re.sub(r"出现于《史记》\d+处", f"出现于《史记》{actual}处", desc)
        if actual == 0:
            new_desc = TEMPLATE_COUNT_RE.sub("", new_desc)
        if new_desc != desc:
            e["description"] = new_desc
            if actual == 0:
                report["count_removals"] += 1
            else:
                report["count_rewrites"] += 1
    return entities, report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--phase", choices=["structure", "derived"], required=True)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="只检查，不写盘（默认）")
    mode.add_argument("--apply", action="store_true", help="写回 entities.jsonl")
    args = parser.parse_args()

    entities = corpus.load_table("entities")
    original = copy.deepcopy(entities)
    before = len(entities)

    if args.phase == "structure":
        sections = corpus.load_table("sections")
        new_entities, report = phase_structure(entities, sections)
    else:
        new_entities, report = phase_derived(entities)

    report["before"] = before
    report["after"] = len(new_entities)
    report["changed"] = new_entities != original

    print(f"phase={args.phase} entities {before} -> {len(new_entities)}")
    print(json.dumps(report, ensure_ascii=False, indent=2)[:4000])

    if args.apply:
        if report["changed"]:
            corpus.save_table("entities", new_entities)
        with open(REPORT, "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=2)
        print(f"已写回 entities.jsonl；report -> {REPORT}")
        return 0

    if report["changed"]:
        print(f"[check] {args.phase} 阶段仍有待应用改动；运行 --apply 写盘")
        return 1
    print(f"[check] {args.phase} 阶段已应用，无待处理改动")
    return 0


if __name__ == "__main__":
    sys.exit(main())
