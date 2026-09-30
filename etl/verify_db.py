#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Verification assertions for compiled SQLite database artifacts.

Usage:
    python3 etl/verify_db.py <db_path>
    python3 etl/verify_db.py app/src/main/assets/opusone.db --committed
"""
import json
import os
import re
import sqlite3
import sys

# Minimum expected row count thresholds for data integrity checks
MIN_COUNTS = {
    "chapters": 130,
    "sections": 13152,
    "entities": 20000,
    "entity_occurrences": 35350,
    "sanjiazhu_notes": 14490,
}

EXPECTED_INDEXES = {
    "idx_sections_ch_order",
    "idx_occurrences_entity",
    "idx_entities_label",
    "idx_sanjiazhu_ch",
    "idx_sections_pn",
}

# FTS5 tables are deprecated; search paths use LIKE matching on indexed tables
REMOVED_FTS = ["sections_fts", "entities_fts", "chengyu_fts", "wars_fts", "stories_fts", "sanjiazhu_fts"]

SIZE_WARN_BYTES = 50 * 1024 * 1024
SIZE_FAIL_BYTES = 64 * 1024 * 1024


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit("用法: verify_db.py <db_path> [--committed]")
    db_path = sys.argv[1]
    committed = "--committed" in sys.argv[2:]
    label = "committed" if committed else "built"

    if not os.path.exists(db_path):
        sys.exit(f"数据库不存在：{db_path}")

    sz = os.path.getsize(db_path)
    sz_mb = sz / (1024 * 1024)
    print(f"[{label}] {db_path}: {sz_mb:.2f} MB ({sz} bytes)")
    if sz >= SIZE_FAIL_BYTES:
        sys.exit(f"体积 {sz_mb:.2f} MB 超过硬上限 {SIZE_FAIL_BYTES // 1024 // 1024} MB")
    if sz >= SIZE_WARN_BYTES:
        print(
            f"::warning::数据库体积 {sz_mb:.2f} MB 已超过 "
            f"{SIZE_WARN_BYTES // 1024 // 1024} MB 提醒线，请关注 APK 体积"
        )

    conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    c = conn.cursor()

    # Validate minimum record counts across primary tables
    print("-- 行数 --")
    for table, minimum in MIN_COUNTS.items():
        n = c.execute(f'SELECT count(*) FROM "{table}"').fetchone()[0]
        print(f"  {table:20} = {n}")
        assert n >= minimum, f"{table} 行数 {n} 少于下限 {minimum}"

    redirect_n = c.execute("SELECT count(*) FROM entities WHERE type = 'redirect'").fetchone()[0]
    assert redirect_n == 0, f"entities 表仍残留 {redirect_n} 条重定向占位条目"

    # Verify presence of essential B-Tree indexes
    indexes = {r[0] for r in c.execute("SELECT name FROM sqlite_master WHERE type='index'")}
    missing_idx = EXPECTED_INDEXES - indexes
    assert not missing_idx, f"缺少索引: {missing_idx}"

    # Ensure deprecated FTS5 tables are not present
    print("-- FTS5 --")
    table_names = {r[0] for r in c.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    leftover = [t for t in REMOVED_FTS if t in table_names]
    assert not leftover, f"检测到已废弃的 FTS 表 {leftover}，请使用 LIKE 检索"
    print(f"  已移除的 6 张 FTS 表均不存在（当前库表: {len(table_names)} 张）")

    # Smoke test LIKE search query
    hits = c.execute(
        "SELECT count(*) FROM sections WHERE tagged_content LIKE ?", ("%高祖%",)
    ).fetchone()[0]
    assert hits > 0, "sections 正文 LIKE 检索无结果"
    print(f"  LIKE smoke test: sections LIKE %高祖% → {hits} hits")

    # Verify section paragraph index constraints
    bad_pn = c.execute(
        "SELECT count(*) FROM sections WHERE pn_index IS NULL OR pn_index = ''"
    ).fetchone()[0]
    assert bad_pn == 0, f"{bad_pn} 行的 sections.pn_index 为空"

    # Verify entities.aliases array format
    bad_alias = 0
    for (aliases,) in c.execute(
        "SELECT aliases FROM entities WHERE aliases IS NOT NULL AND aliases <> ''"
    ):
        try:
            arr = json.loads(aliases)
        except Exception:
            bad_alias += 1
            continue
        if not isinstance(arr, list) or any(not isinstance(v, str) for v in arr):
            bad_alias += 1
    assert bad_alias == 0, f"{bad_alias} 条 entities.aliases 含非字符串元素或非法 JSON"
    print("  entities.aliases 均为字符串数组")

    # Verify database schema version matches OpusOneDatabaseHelper.DB_VERSION
    kotlin = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "..", "app", "src", "main", "java", "dev", "x", "opusone",
        "data", "OpusOneDatabaseHelper.kt",
    )
    m = re.search(r"const\s+val\s+DB_VERSION\s*=\s*(\d+)", open(kotlin, encoding="utf-8").read())
    assert m, "未能从 OpusOneDatabaseHelper.kt 解析出 DB_VERSION"
    expected = int(m.group(1))
    actual = c.execute("PRAGMA user_version").fetchone()[0]
    assert actual == expected, (
        f"库内 user_version={actual} 与客户端 DB_VERSION={expected} 不一致"
    )
    print(f"  user_version = {actual}（与客户端 DB_VERSION 一致）")

    conn.close()
    print(f"[{label}] DB 产物校验通过。")


if __name__ == "__main__":
    main()
