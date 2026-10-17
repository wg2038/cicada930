#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Check that the committed asset DB matches a fresh build from the corpus.

The corpus is the source of truth; ``app/src/main/assets/opusone.db`` is a
compiled artifact.  This guard rebuilds into a temporary file and compares
every table row-for-row, so a corpus edit without a rebuilt asset fails CI.

Usage:
    python3 etl/verify_db_freshness.py
    python3 etl/verify_db_freshness.py --committed app/src/main/assets/opusone.db
"""
import argparse
import hashlib
import json
import os
import sqlite3
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DEFAULT_COMMITTED = os.path.join(ROOT, "app", "src", "main", "assets", "opusone.db")
BUILD = os.path.join(HERE, "build_from_corpus.py")

# (table, ORDER BY clause)
TABLES = [
    ("chapters", "id"),
    ("sections", "chapter_id, pn_index"),
    ("entities", "id"),
    ("entity_occurrences", "entity_id, chapter_id, section_pn"),
    ("chengyu", "word, chapter_id, pn"),
    ("wars", "war_id, name"),
    ("stories", "id"),
    ("taishigongyue", "chapter_id, id"),
    ("sanjiazhu_notes", "chapter_id, note_id"),
]


def table_digest(conn, table, order_by):
    rows = conn.execute(f'SELECT * FROM "{table}" ORDER BY {order_by}').fetchall()
    payload = "\n".join(
        json.dumps(list(row), ensure_ascii=False, separators=(",", ":")) for row in rows
    )
    return len(rows), hashlib.sha256(payload.encode()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--committed", default=DEFAULT_COMMITTED)
    args = parser.parse_args()

    if not os.path.exists(args.committed):
        sys.exit(f"committed DB 不存在：{args.committed}")

    with tempfile.TemporaryDirectory() as tmpdir:
        fresh = os.path.join(tmpdir, "opusone.db")
        subprocess.run(
            [sys.executable, BUILD, fresh],
            check=True,
            stdout=subprocess.DEVNULL,
        )
        old_conn = sqlite3.connect(f"file:{args.committed}?mode=ro", uri=True)
        new_conn = sqlite3.connect(f"file:{fresh}?mode=ro", uri=True)

        errors = []
        for table, order_by in TABLES:
            old_n, old_hash = table_digest(old_conn, table, order_by)
            new_n, new_hash = table_digest(new_conn, table, order_by)
            status = "ok" if (old_n, old_hash) == (new_n, new_hash) else "DIFF"
            print(f"  {table:20} {old_n:>6} rows  {status}")
            if status == "DIFF":
                errors.append(
                    f"{table}: committed {old_n} rows/{old_hash[:12]}, "
                    f"fresh {new_n} rows/{new_hash[:12]}"
                )

        old_version = old_conn.execute("PRAGMA user_version").fetchone()[0]
        new_version = new_conn.execute("PRAGMA user_version").fetchone()[0]
        if old_version != new_version:
            errors.append(f"user_version: committed {old_version}, fresh {new_version}")

        old_conn.close()
        new_conn.close()

    if errors:
        for err in errors:
            print(f"[ERROR] {err}")
        print("committed asset DB 与语料重建结果不一致；请重新运行 build_from_corpus.py")
        return 1

    print(f"committed asset DB 与语料重建结果一致（user_version={new_version}）。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
