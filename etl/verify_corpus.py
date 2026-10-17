#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Validates OpusOne corpus: OAM grammar, plain text identity, quotes, and registered entity keys."""
import collections
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import corpus  # noqa: E402


def main() -> None:
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

    entities = corpus.load_table("entities")
    entity_keys = {e["id"] for e in entities}
    entity_keys |= {a for e in entities for a in get_aliases(e)}

    sections = corpus.load_table("sections")
    print(f"Validating {len(sections)} sections against {len(entity_keys)} registered entity keys...")

    bad_oam = 0
    bad_quotes = 0
    for s in sections:
        errs = corpus.validate_section(s["content"], s["plain"], entity_keys)
        if errs:
            bad_oam += 1
            if bad_oam <= 5:
                print(f"[OAM Error] ch{s.get('chapter_id')}#{s.get('pn')}: {errs}")

        t = s.get("translation", "")
        if t:
            if '"' in t:
                bad_quotes += 1
                print(f"[ASCII Quote] ch{s.get('chapter_id')}#{s.get('pn')}")
            if t.count("“") != t.count("”") or t.count("‘") != t.count("’"):
                bad_quotes += 1
                print(f"[Quote Mismatch] ch{s.get('chapter_id')}#{s.get('pn')}")

    if bad_oam or bad_quotes:
        sys.exit(f"语料校验失败：{bad_oam} 段 OAM/索引键问题，{bad_quotes} 段引号问题")

    print("All sections passed OAM, entity-key and quote validation!")

    chapters = corpus.load_table("chapters")
    chapter_ids = {c["id"] for c in chapters}
    section_pns = {(s["chapter_id"], s["pn"]) for s in sections}

    # Verify sanjiazhu foreign keys and section anchoring
    sanjiazhu = corpus.load_table("sanjiazhu")
    print(f"Validating {len(sanjiazhu)} sanjiazhu notes references...")
    bad_sjz_ch = [n for n in sanjiazhu if n["chapter_id"] not in chapter_ids]
    assert not bad_sjz_ch, f"Found {len(bad_sjz_ch)} orphan sanjiazhu notes (invalid chapter_id)"

    bad_sjz_sec = [n for n in sanjiazhu if n.get("sentence_id") and (n["chapter_id"], n["sentence_id"]) not in section_pns]
    assert not bad_sjz_sec, f"Found {len(bad_sjz_sec)} sanjiazhu notes with invalid sentence_id"

    # Verify occurrences foreign keys, section anchors, uniqueness and counts
    occurrences = corpus.load_table("occurrences")
    print(f"Validating {len(occurrences)} entity occurrences references...")
    bad_occ_ch = [o for o in occurrences if o["chapter_id"] not in chapter_ids]
    assert not bad_occ_ch, f"Found {len(bad_occ_ch)} orphan entity occurrences (invalid chapter_id)"

    entity_ids = {e["id"] for e in entities}
    bad_occ_entity = [o for o in occurrences if o["entity_id"] not in entity_ids]
    assert not bad_occ_entity, f"Found {len(bad_occ_entity)} orphan entity occurrences (invalid entity_id)"

    bad_occ_sec = [
        o for o in occurrences
        if (o["chapter_id"], o["section_pn"]) not in section_pns
    ]
    assert not bad_occ_sec, f"Found {len(bad_occ_sec)} occurrences with a stale section anchor"

    triples = [(o["entity_id"], o["chapter_id"], o["section_pn"]) for o in occurrences]
    duplicate_count = len(triples) - len(set(triples))
    assert duplicate_count == 0, f"Found {duplicate_count} duplicate occurrence triples"

    expected_counts = collections.Counter(o["entity_id"] for o in occurrences)
    bad_counts = [
        e["id"] for e in entities
        if int(e.get("occurrences_count") or 0) != expected_counts.get(e["id"], 0)
    ]
    assert not bad_counts, f"Found {len(bad_counts)} entities whose occurrences_count is stale"

    # Entity hygiene: redirect rows and punctuation-only label duplicates
    redirects = [
        e["id"] for e in entities
        if str(e.get("type", "")).upper() == "REDIRECT"
        or (e.get("description") or "").startswith("REDIRECT ")
    ]
    assert not redirects, f"Found {len(redirects)} unresolved redirect entities"

    label_noise = re.compile(
        r"[\s“”\"‘’'「」『』《》〈〉\[\]【】（）()—\-–·:：,，.。、;！!?？]"
    )
    label_groups = collections.defaultdict(list)
    for e in entities:
        normalized = label_noise.sub("", e.get("label") or "")
        if normalized:
            label_groups[normalized].append(e["id"])
    near_duplicates = {k: v for k, v in label_groups.items() if len(v) > 1}
    assert not near_duplicates, f"Found {len(near_duplicates)} near-duplicate label groups"

    assert len(entities) >= 20000, f"Entity table shrank to {len(entities)} rows"

    print("All sanjiazhu and occurrences passed foreign key and anchor integrity verification!")
    print(
        f"Occurrence checks: {len(occurrences)} unique anchors, "
        f"{len(expected_counts)} entities linked, 0 duplicates, 0 stale counts."
    )
    print(f"Entity hygiene: {len(entities)} entities, 0 redirects, 0 near-duplicate labels.")


if __name__ == "__main__":
    main()
