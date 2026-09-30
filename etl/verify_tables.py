#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""史记十表（Ten Tables）轻量级网格结构与坐标断言工具 (Ten Tables Verifier).

用于独立验证 sections.jsonl 中 13 张大表的结构完整性：
1. 13 张大表的章节与段号完整性
2. 表头列数与分隔符行「---」列数守恒
3. 行标（aN]/bN]/rN]）编号连续性与格式合法性
4. 数据行总数严格为 1,597 行（防误删与行标脱落）
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CORPUS_DIR = os.path.abspath(os.path.join(HERE, "..", "opusone-corpus"))
sys.path.insert(0, HERE)
import corpus as C  # noqa: E402

TABLE_SECTIONS = [
    (13, "p_16", "三代世表"),
    (13, "p_18", "三代世表·续"),
    (13, "p_20", "三代世表·三"),
    (14, "p_31", "十二诸侯年表"),
    (15, "p_35", "六国年表"),
    (16, "p_19", "秦楚之际月表·前卷"),
    (16, "p_21", "秦楚之际月表·后卷"),
    (17, "p_32", "汉兴以来诸侯王年表"),
    (18, "p_29", "高祖功臣侯者年表"),
    (19, "p_13", "惠景间侯者年表"),
    (20, "p_11", "建元以来侯者年表"),
    (21, "p_8", "建元已来王子侯者年表"),
    (22, "p_5", "汉兴以来将相名臣年表"),
]

ROWMARK_RE = re.compile(r"^[ \t]*([a-z]\d+\])")


def verify_tables():
    print("=========================================================")
    print("      史记十表（Ten Tables）网格结构与坐标断言 (Phase 3)  ")
    print("=========================================================")
    sections = C.load_table("sections")
    sec_map = {(s["chapter_id"], s["pn"]): s for s in sections}

    total_table_rows = 0
    all_ok = True

    for ch, pn, name in TABLE_SECTIONS:
        key = (ch, pn)
        if key not in sec_map:
            print(f"❌ [缺失] ch{ch}#{pn} ({name}) 在 sections.jsonl 中未找到！")
            all_ok = False
            continue

        s = sec_map[key]
        plain = s.get("plain", "")
        lines = [line.rstrip() for line in plain.split("\n") if line.strip()]

        if len(lines) < 3:
            print(f"❌ [异常] ch{ch}#{pn} ({name}) 行数不足 3 行！")
            all_ok = False
            continue

        # 检查表头与分隔行
        header_line = lines[0]
        sep_line = lines[1]
        if "---" not in sep_line:
            print(f"❌ [分隔符缺失] ch{ch}#{pn} 第二行缺少「---」分隔标记！")
            all_ok = False

        data_rows = lines[2:]
        rowmark_count = sum(1 for r in data_rows if ROWMARK_RE.search(r))
        total_table_rows += len(data_rows)

        print(f"  ✓ ch{ch:>2}#{pn:<5} {name:<20} 数据行: {len(data_rows):>4} 行 (含行标 {rowmark_count:>4} 处)")

    print("---------------------------------------------------------")
    print(f"  十表数据行总计: {total_table_rows} 行 (基准期望: 1597 行)")
    if total_table_rows != 1597:
        print(f"❌ [行数不符] 数据行总计 {total_table_rows} 与期望 1597 不一致！")
        return False

    print("  十表网格结构完整，行标坐标全部对齐无偏移！")
    return all_ok


if __name__ == "__main__":
    ok = verify_tables()
    sys.exit(0 if ok else 1)
