#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Validates OpusOne corpus: OAM grammar, plain text identity, quotes, and registered entity keys."""
import json
import os
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

    # Verify occurrences foreign keys
    occurrences = corpus.load_table("occurrences")
    print(f"Validating {len(occurrences)} entity occurrences references...")
    bad_occ_ch = [o for o in occurrences if o["chapter_id"] not in chapter_ids]
    assert not bad_occ_ch, f"Found {len(bad_occ_ch)} orphan entity occurrences (invalid chapter_id)"

    print("All sanjiazhu and occurrences passed foreign key and anchor integrity verification!")


if __name__ == "__main__":
    main()
