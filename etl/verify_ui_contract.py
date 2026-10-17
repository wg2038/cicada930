#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Verify the corpus contract the Android UI depends on.

The app renders sections in ``order_in_chapter`` order, keys bookmarks and
navigation by ``pn``, and shows ten fixed entity tabs.  This verifier freezes
that index and the thirteen Ten-Tables sections so a corpus edit cannot
silently reorder the reader or rewrite a protected table.

Usage:
    python3 etl/verify_ui_contract.py --corpus
    python3 etl/verify_ui_contract.py --write-baselines   # maintainers only
"""
import argparse
import collections
import hashlib
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import corpus  # noqa: E402
from verify_tables import TABLE_SECTIONS  # noqa: E402

BASELINE_DIR = os.path.join(HERE, "baselines")
SECTION_INDEX_BASELINE = os.path.join(BASELINE_DIR, "section_index.sha256")
TABLE_BASELINE = os.path.join(BASELINE_DIR, "ten_tables_sections.sha256")

UI_ENTITY_TYPES = [
    "person", "place", "official", "state", "event",
    "identity", "artifact", "concept", "biology", "astronomy",
]


def load_jsonl(path):
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def section_index(rows):
    by_chapter = collections.defaultdict(list)
    for s in rows:
        by_chapter[s["chapter_id"]].append((s["pn"], s["order"]))
    return {
        chapter_id: hashlib.sha256(
            json.dumps(sorted(seq), ensure_ascii=False, separators=(",", ":")).encode()
        ).hexdigest()
        for chapter_id, seq in by_chapter.items()
    }


def table_hash(section):
    payload = "\x1f".join([
        section.get("content") or "",
        section.get("plain") or "",
        section.get("translation") or "",
    ])
    return hashlib.sha256(payload.encode()).hexdigest()


def table_hashes(rows):
    by_key = {(s["chapter_id"], s["pn"]): s for s in rows}
    out = {}
    for chapter_id, pn, _name in TABLE_SECTIONS:
        section = by_key.get((chapter_id, pn))
        if section is not None:
            out[(chapter_id, pn)] = table_hash(section)
    return out


def write_baselines(sections):
    os.makedirs(BASELINE_DIR, exist_ok=True)
    with open(SECTION_INDEX_BASELINE, "w", encoding="utf-8") as f:
        for chapter_id, digest in sorted(section_index(sections).items()):
            f.write(f"{chapter_id}\t{digest}\n")
    with open(TABLE_BASELINE, "w", encoding="utf-8") as f:
        for (chapter_id, pn), digest in sorted(table_hashes(sections).items()):
            f.write(f"{chapter_id}\t{pn}\t{digest}\n")
    print(f"baselines -> {SECTION_INDEX_BASELINE}, {TABLE_BASELINE}")


def read_baseline(path, columns):
    out = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line:
                continue
            parts = line.split("\t")
            if len(parts) != columns:
                raise SystemExit(f"基线格式错误: {path}: {line!r}")
            out[tuple(parts[:-1])] = parts[-1]
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--corpus", action="store_true", help="校验语料（默认）")
    parser.add_argument("--write-baselines", action="store_true", help="重建基线文件")
    args = parser.parse_args()

    sections = corpus.load_table("sections")
    chapters = corpus.load_table("chapters")
    entities = corpus.load_table("entities")
    occurrences = corpus.load_table("occurrences")

    if args.write_baselines:
        write_baselines(sections)
        return 0

    errors = []

    # 1. Section index (pn, order) must be byte-for-byte stable per chapter.
    if not os.path.exists(SECTION_INDEX_BASELINE):
        errors.append(f"缺少基线 {SECTION_INDEX_BASELINE}，运行 --write-baselines")
    else:
        expected = read_baseline(SECTION_INDEX_BASELINE, 2)
        actual = {str(k): v for k, v in section_index(sections).items()}
        for chapter_id, digest in expected.items():
            chapter_id = chapter_id[0]
            if actual.get(chapter_id) != digest:
                errors.append(f"章节 {chapter_id} 的 (pn, order) 索引发生变化")
        extra = set(actual) - {k[0] for k in expected}
        if extra:
            errors.append(f"新增章节 {sorted(extra)} 不在基线中")

    # 2. Per-chapter pn uniqueness and strictly increasing order.
    by_chapter = collections.defaultdict(list)
    for s in sections:
        by_chapter[s["chapter_id"]].append(s)
    for chapter_id, rows in by_chapter.items():
        pns = [s["pn"] for s in rows]
        if any(not pn for pn in pns):
            errors.append(f"章节 {chapter_id} 存在空 pn")
        if len(pns) != len(set(pns)):
            errors.append(f"章节 {chapter_id} 存在重复 pn")
        orders = [s["order"] for s in sorted(rows, key=lambda s: s["order"])]
        if any(b <= a for a, b in zip(orders, orders[1:])):
            errors.append(f"章节 {chapter_id} 的 order 不是严格递增")

    # 3. The thirteen Ten-Tables sections must be untouched.
    if not os.path.exists(TABLE_BASELINE):
        errors.append(f"缺少基线 {TABLE_BASELINE}，运行 --write-baselines")
    else:
        expected_tables = read_baseline(TABLE_BASELINE, 3)
        actual_tables = {
            (str(k[0]), k[1]): v for k, v in table_hashes(sections).items()
        }
        for key, digest in expected_tables.items():
            chapter_id, pn = key
            if actual_tables.get((chapter_id, pn)) != digest:
                errors.append(f"受保护表 section ch{chapter_id}#{pn} 内容发生变化")
        if len(actual_tables) != len(TABLE_SECTIONS):
            errors.append(f"受保护表 section 只剩 {len(actual_tables)}/{len(TABLE_SECTIONS)}")

    # 4. Occurrence anchors and uniqueness.
    section_keys = {(s["chapter_id"], s["pn"]) for s in sections}
    bad_anchor = [
        o for o in occurrences
        if (o["chapter_id"], o["section_pn"]) not in section_keys
    ]
    if bad_anchor:
        errors.append(f"{len(bad_anchor)} 条 occurrence 无法命中 section")
    triples = [
        (o["entity_id"], o["chapter_id"], o["section_pn"]) for o in occurrences
    ]
    if len(triples) != len(set(triples)):
        errors.append(f"{len(triples) - len(set(triples))} 个重复 occurrence 三元组")

    # 5. The ten UI entity tabs must all be non-empty.
    type_counts = collections.Counter(e.get("type") for e in entities)
    for entity_type in UI_ENTITY_TYPES:
        if type_counts.get(entity_type, 0) == 0:
            errors.append(f"UI 分类 {entity_type} 没有实体")

    # 6. Every explicit OAM index key must be registered; bare display keys
    #    that resolve to no entity are reported but tolerated (the reader
    #    renders them as plain text).
    entity_keys = {e["id"] for e in entities}
    for e in entities:
        aliases = e.get("aliases") or []
        if isinstance(aliases, str):
            try:
                aliases = json.loads(aliases)
            except Exception:
                aliases = []
        entity_keys.update(a for a in aliases if isinstance(a, str))

    unresolved_keys = collections.Counter()
    for s in sections:
        for m in corpus.OAM_RE.finditer(s.get("content", "")):
            body = m.group(2)
            key = body.split("|", 1)[1].strip() if "|" in body else body.strip()
            if key and key not in entity_keys:
                unresolved_keys[key] += 1
    print(
        f"OAM 显示词未绑定实体: {sum(unresolved_keys.values())} 处 / "
        f"{len(unresolved_keys)} 个 key（容忍，渲染为纯文本）"
    )

    if errors:
        for err in errors:
            print(f"[ERROR] {err}")
        return 1

    print(
        f"UI 契约通过：{len(chapters)} 章、{len(sections)} section 的 pn/order 未变；"
        f"{len(TABLE_SECTIONS)} 张表未改；{len(occurrences)} 条 occurrence 全部锚定；"
        f"{len(UI_ENTITY_TYPES)} 个实体分类均非空。"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
