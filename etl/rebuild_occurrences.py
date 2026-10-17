#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Rebuild entity_occurrences from the OAM tags that actually exist.

The old occurrence table was produced from an earlier annotation snapshot:
about 37% of its rows point at a ``(chapter_id, section_pn)`` that no longer
exists.  This pass derives every row from the current ``sections.jsonl``.

Resolution is deliberately conservative:

1. explicit index key that names an entity id (``显示词|id``)
2. one exact type-compatible label match
3. one type-compatible alias match
4. for genuinely ambiguous labels, a chapter / section context score

A tag is left unresolved instead of being linked to a wrong entity.  The OAM
code must be compatible with the entity type even for an exact label match:
``⟪战 伐⟫`` is the verb "to attack", not the star 伐, and ``⟪思 礼⟫`` is the
ritual, not the person named 礼.  Surname entities (``type == "xing"``) are
never used for a bare state or given-name key, and a title-like key such as
``代王`` is not linked to a person whose label does not contain that title.

Usage:
    python3 etl/rebuild_occurrences.py --check
    python3 etl/rebuild_occurrences.py --apply
"""
import argparse
import collections
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import corpus  # noqa: E402

REPORT = "/tmp/occurrence_rebuild_report.json"

# OAM code -> entity types that can legitimately carry that code.
CODE_TYPES = {
    "人": {"person", "identity"},
    "地": {"place", "state", "侯国", "jun", "map"},
    "族": {"tribe", "dynasty", "state", "侯国", "identity", "place"},
    "群": {"identity", "tribe", "state", "侯国", "person", "official"},
    "官": {"official", "person", "identity"},
    "器": {"artifact"},
    "生": {"biology"},
    "思": {"concept", "ritual", "skill", "institution", "legal"},
    "历": {"astronomy", "time", "year", "timeline"},
    "数": {"quantity", "time", "year", "timeline"},
    "语": {"chengyu", "concept"},
    "战": {"verb", "event", "skill"},
    "刑": {"verb", "legal", "institution"},
    "政": {"verb", "institution", "ritual", "event"},
    "赋": {"verb", "institution"},
    "录": {
        "book", "chapter", "story", "sanwen", "chengyu",
        "taishigongyue", "overview", "event",
    },
    # Legacy two-letter codes.
    "PE": {"person"},
    "PL": {"place", "state", "侯国", "jun", "map"},
    "OF": {"official", "person", "identity"},
    "ID": {"identity", "tribe", "state", "侯国", "person", "official"},
    "CL": {"tribe", "dynasty", "state", "侯国", "identity", "place"},
    "AR": {"artifact"},
    "BI": {"biology"},
    "CO": {"concept", "ritual", "skill", "institution", "legal"},
    "AS": {"astronomy", "time", "year", "timeline"},
    "QU": {"quantity", "time", "year", "timeline"},
    "CY": {"chengyu", "concept"},
    "MV": {"verb", "event", "skill"},
    "XV": {"verb", "legal", "institution"},
    "PV": {"verb", "institution", "ritual", "event"},
    "EV": {"verb", "institution", "ritual", "event"},
    "TX": {
        "book", "chapter", "story", "sanwen", "chengyu",
        "taishigongyue", "overview", "event",
    },
}

# Synthetic umbrella entities whose short aliases are date words.  Linking
# every ``三年`` / ``二年`` tag to one "duration" row would fabricate meaning.
GENERIC_ALIAS_EXCLUDE = {"时长"}

# Titles that are too generic to bind to a lone person candidate whose label
# does not contain the title (e.g. 代王 -> 苏代 is wrong even though 苏代 has a
# polluted ``代王`` alias).
TITLE_RE = re.compile(r"(王|公|侯|君|帝|后|太子|将军|司徒|司马|司空|太傅|公子)$")

# Used only as a weak chapter-subject prior for ambiguous titles.
POLITIES = (
    "周", "秦", "齐", "楚", "燕", "赵", "魏", "韩", "鲁", "晋", "宋",
    "卫", "郑", "吴", "越", "陈", "蔡", "曹", "滕", "杞", "田", "汉",
    "匈奴", "南越", "东越", "朝鲜", "西南夷", "大宛", "淮南", "衡山",
    "梁", "代", "长沙", "吕", "荆", "五宗", "三王",
)

MIN_ROWS = 35350
MIN_RESOLVED_TAGS = 60000
MAX_UNRESOLVED_KEYS = 3000


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


def tag_key(body):
    return body.split("|", 1)[1].strip() if "|" in body else body.strip()


def chapter_polity(title):
    for p in sorted(POLITIES, key=len, reverse=True):
        if title.startswith(p):
            return p
    return ""


def chapter_subject(title):
    """Person subject for 本纪/列传 chapters; empty for 世家 and treatises.

    世家 titles name a polity or family rather than one person, so using them
    to confirm a generic title would bind every 赵王 to one late Zhao king.
    """
    for suffix in ("本纪", "列传"):
        if title.endswith(suffix):
            return title[:-len(suffix)]
    return ""


class Resolver:
    def __init__(self, entities, chapters):
        self.by_id = {e["id"]: e for e in entities}
        self.by_label = collections.defaultdict(list)
        self.by_alias = collections.defaultdict(list)
        for e in entities:
            self.by_label[e["label"]].append(e)
            for a in get_aliases(e):
                self.by_alias[a].append(e)
        self.polity = {c["id"]: chapter_polity(c["title"]) for c in chapters}
        self.subject = {c["id"]: chapter_subject(c["title"]) for c in chapters}
        self.sec_ctx = collections.Counter()
        self.chap_ctx = collections.Counter()
        self.unresolved_keys = collections.Counter()
        self.multi_keys = collections.Counter()

    def _subject_match(self, e, chapter_id):
        """15 when the entity's own name names the chapter subject, 5 via alias."""
        subject = self.subject.get(chapter_id, "")
        if not subject:
            return 0
        tokens = [subject]
        tokens += [p for p in POLITIES if len(p) >= 2 and p in subject]

        def hit(names):
            return any(
                len(name) >= 2 and (name in token or token in name)
                for token in tokens for name in names
            )

        if hit([e["id"], e["label"]]):
            return 15
        if hit(get_aliases(e)):
            return 5
        return 0

    def _candidates(self, code, key, chapter_id):
        raw = {}
        for e in self.by_label.get(key, []) + self.by_alias.get(key, []):
            raw[e["id"]] = e

        out = []
        for e in raw.values():
            exact = e["label"] == key
            if e["id"] in GENERIC_ALIAS_EXCLUDE and not exact:
                continue
            if e["type"] == "xing":
                # A surname entry is only useful when the tag is explicitly a
                # surname; otherwise the same key is a place or a person.
                if not key.endswith("姓"):
                    continue
                out.append(e)
                continue
            if e["type"] not in CODE_TYPES.get(code, set()):
                continue
            out.append(e)

        # A lone generic title candidate is not trustworthy when its own label
        # does not contain the title.  Aliases are polluted often enough
        # (苏代 carries ``代王``, 韩信 carries ``齐王``) that an alias match is
        # not sufficient evidence on its own.  The chapter's own subject can
        # vouch for the candidate (汉王 in 高祖本纪 -> 刘邦).
        if len(out) == 1 and len(key) >= 2 and TITLE_RE.search(key):
            e = out[0]
            if key not in e["label"] and not self._subject_match(e, chapter_id):
                return []
        return out

    def _polity_bonus(self, e, polity):
        if not polity:
            return 0
        names = [e["id"], e["label"]] + get_aliases(e)
        return 10 if any(n.startswith(polity) for n in names) else 0

    def _score(self, e, chapter_id, section_pn, key):
        polity = self.polity.get(chapter_id, "")
        title_like = len(key) >= 2 and bool(TITLE_RE.search(key))
        return (
            self.sec_ctx[(chapter_id, section_pn, e["id"])] * 20
            + self.chap_ctx[(chapter_id, e["id"])] * 3
            + self._polity_bonus(e, polity)
            + self._subject_match(e, chapter_id)
            + (5 if key in e["label"] else 0)
            + (0 if title_like else (5 if key in "".join(get_aliases(e)) else 0))
            + (5 if e["label"] == key else 0)
        )

    def resolve_unambiguous(self, chapter_id, section_pn, code, key, explicit_key):
        # ``显示词|id`` is an explicit index key: the annotator named the row,
        # so trust it even when the code and the row type disagree.
        if explicit_key and key in self.by_id:
            return self.by_id[key]
        ok = self._candidates(code, key, chapter_id)
        exact = [e for e in ok if e["label"] == key]
        if len(exact) == 1:
            return exact[0]
        if len(ok) == 1:
            return ok[0]
        return None

    def resolve_ambiguous(self, chapter_id, section_pn, code, key, explicit_key):
        ok = self._candidates(code, key, chapter_id)
        if len(ok) < 2:
            return None
        scored = sorted(
            ((self._score(e, chapter_id, section_pn, key), e) for e in ok),
            key=lambda pair: -pair[0],
        )
        top_score, top = scored[0]
        margin = top_score - scored[1][0]
        if top_score > 0 and margin >= 8:
            # A title-like winner must be corroborated by its own label, the
            # chapter subject, or the chapter's polity.  Otherwise a polluted
            # alias plus local noise (武帝 -> 段干子) is not enough.
            if len(key) >= 2 and TITLE_RE.search(key):
                polity = self.polity.get(chapter_id, "")
                corroborated = (
                    key in top["label"]
                    or top["label"] == key
                    or self._polity_bonus(top, polity) > 0
                    or self._subject_match(top, chapter_id)
                )
                if not corroborated:
                    self.multi_keys[key] += 1
                    return None
            return top
        self.multi_keys[key] += 1
        return None

    def add_context(self, chapter_id, section_pn, entity):
        self.sec_ctx[(chapter_id, section_pn, entity["id"])] += 1
        self.chap_ctx[(chapter_id, entity["id"])] += 1


def collect_tags(sections):
    tags = []
    for s in sections:
        if s.get("type") != "paragraph":
            continue
        for m in corpus.OAM_RE.finditer(s.get("content", "")):
            body = m.group(2)
            explicit = "|" in body
            tags.append(
                (s["chapter_id"], s["pn"], m.group(1), tag_key(body), explicit)
            )
    return tags


def rebuild(entities, chapters, sections):
    resolver = Resolver(entities, chapters)
    tags = collect_tags(sections)

    resolved = {}
    for tag in tags:
        entity = resolver.resolve_unambiguous(*tag)
        if entity:
            resolved[tag] = entity
            resolver.add_context(tag[0], tag[1], entity)

    for tag in tags:
        if tag in resolved:
            continue
        entity = resolver.resolve_ambiguous(*tag)
        if entity:
            resolved[tag] = entity

    unresolved_keys = collections.Counter()
    for tag in tags:
        if tag not in resolved:
            unresolved_keys[tag[3]] += 1

    triples = sorted({
        (e["id"], chapter_id, section_pn)
        for (chapter_id, section_pn, _code, _key, _explicit), e in resolved.items()
    })
    rows = [
        {"entity_id": entity_id, "chapter_id": chapter_id, "section_pn": section_pn}
        for entity_id, chapter_id, section_pn in triples
    ]
    resolved_occurrences = sum(1 for tag in tags if tag in resolved)
    return rows, {
        "tags_total": len(tags),
        "tags_resolved": resolved_occurrences,
        "tags_unresolved": len(tags) - resolved_occurrences,
        "resolved_unique_tags": len(resolved),
        "unresolved_keys": unresolved_keys,
        "multi_keys": resolver.multi_keys,
    }


def apply_counts(entities, rows):
    counts = collections.Counter(r["entity_id"] for r in rows)
    for e in entities:
        e["occurrences_count"] = counts.get(e["id"], 0)
    return counts


def main():
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="只检查，不写盘（默认）")
    mode.add_argument("--apply", action="store_true", help="写回 occurrences/entities")
    args = parser.parse_args()

    entities = corpus.load_table("entities")
    chapters = corpus.load_table("chapters")
    sections = corpus.load_table("sections")
    current = corpus.load_table("occurrences")

    rows, stats = rebuild(entities, chapters, sections)

    section_keys = {(s["chapter_id"], s["pn"]) for s in sections}
    entity_ids = {e["id"] for e in entities}
    missing_section = [r for r in rows if (r["chapter_id"], r["section_pn"]) not in section_keys]
    missing_entity = [r for r in rows if r["entity_id"] not in entity_ids]
    duplicate = len(rows) - len({(r["entity_id"], r["chapter_id"], r["section_pn"]) for r in rows})

    counts = collections.Counter(r["entity_id"] for r in rows)
    old_counts = {e["id"]: int(e.get("occurrences_count") or 0) for e in entities}
    big_changes = [
        {
            "id": eid,
            "old": old_counts[eid],
            "new": counts.get(eid, 0),
        }
        for eid in old_counts
        if old_counts[eid] > 0 and (
            counts.get(eid, 0) > old_counts[eid] * 2
            or counts.get(eid, 0) * 2 < old_counts[eid]
        )
    ]
    big_changes.sort(key=lambda x: (abs(x["new"] - x["old"]), x["id"]), reverse=True)

    report = {
        "before_rows": len(current),
        "after_rows": len(rows),
        "unique_triples": len(rows),
        "tags_total": stats["tags_total"],
        "tags_resolved": stats["tags_resolved"],
        "tags_unresolved": stats["tags_unresolved"],
        "resolved_unique_tags": stats["resolved_unique_tags"],
        "unresolved_keys": len(stats["unresolved_keys"]),
        "unresolved_top": stats["unresolved_keys"].most_common(50),
        "multi_top": stats["multi_keys"].most_common(50),
        "missing_section": len(missing_section),
        "missing_entity": len(missing_entity),
        "duplicate_triples": duplicate,
        "entities_with_rows": len(counts),
        "entities_at_zero": len(entities) - len(counts),
        "top_entities": [
            {"id": eid, "count": n}
            for eid, n in counts.most_common(50)
        ],
        "big_count_changes": big_changes[:200],
    }
    print(json.dumps({k: v for k, v in report.items() if not isinstance(v, list)},
                     ensure_ascii=False, indent=2))
    print("unresolved top:", report["unresolved_top"][:20])
    print("multi top:", report["multi_top"][:20])

    errors = []
    if len(rows) < MIN_ROWS:
        errors.append(f"occurrence 行数 {len(rows)} 低于下限 {MIN_ROWS}")
    if stats["tags_resolved"] < MIN_RESOLVED_TAGS:
        errors.append(f"已解析标签 {stats['tags_resolved']} 低于下限 {MIN_RESOLVED_TAGS}")
    if len(stats["unresolved_keys"]) > MAX_UNRESOLVED_KEYS:
        errors.append(f"未解析 key {len(stats['unresolved_keys'])} 超过上限 {MAX_UNRESOLVED_KEYS}")
    if missing_section:
        errors.append(f"{len(missing_section)} 行无法命中 section")
    if missing_entity:
        errors.append(f"{len(missing_entity)} 行 entity_id 不存在")
    if duplicate:
        errors.append(f"{duplicate} 个重复三元组")
    if errors:
        for err in errors:
            print(f"[ERROR] {err}")
        _write_report(report)
        return 1

    if args.apply:
        corpus.save_table("occurrences", rows)
        apply_counts(entities, rows)
        corpus.save_table("entities", entities)
        _write_report(report)
        print(f"occurrence 重建完成：{len(current)} -> {len(rows)} 行；report -> {REPORT}")
        return 0

    same_rows = current == rows
    same_counts = all(old_counts[e["id"]] == counts.get(e["id"], 0) for e in entities)
    report["applied"] = same_rows and same_counts
    _write_report(report)
    if not same_rows or not same_counts:
        print("[check] occurrences/entities 计数仍有待应用改动；运行 --apply 写盘")
        return 1
    print("[check] occurrence 与实体计数均已应用，无待处理改动")
    return 0


def _write_report(report):
    with open(REPORT, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    sys.exit(main())
