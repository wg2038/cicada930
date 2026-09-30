#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OpusOne 三家注全量锚定对齐与离线绑定工具 (Sanjiazhu Alignment & Binding Engine).

解决痛点：
1. 全库 14,042 条注疏此前仅 529 条有 sentence_id，运行时模糊匹配导致 4,286 条（30.5%）注疏失联隐藏。
2. 通过异体字归一化（如 悳/德, 戰/战, 筴/策, 貙/䝙, 謌/歌）、去标点滑动窗口与单调时序拓扑对齐，
   将三家注与 sections.jsonl 的段落 pn (sentence_id) 实现高精度离线绑定。

用法：
    python3 etl/audit_sanjiazhu.py            # 评估对齐率并输出诊断报告
    python3 etl/audit_sanjiazhu.py --write    # 将推导出的 sentence_id 回填落盘到 sanjiazhu.jsonl
"""
import argparse
import collections
import json
import os
import re
import sys
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
CORPUS_DIR = os.path.abspath(os.path.join(HERE, "..", "opusone-corpus"))
ASSETS_DIR = os.path.abspath(os.path.join(HERE, "..", "app", "src", "main", "assets"))
sys.path.insert(0, HERE)
import corpus as C  # noqa: E402


def build_normalizer():
    t2s = {}
    s2t_path = os.path.join(ASSETS_DIR, "s2t_chars.txt")
    if os.path.exists(s2t_path):
        with open(s2t_path, encoding="utf-8") as f:
            for line in f:
                parts = line.strip().split("\t")
                if len(parts) >= 2:
                    s, t = parts[0], parts[1]
                    if t in t2s:
                        if "\u4e00" <= s <= "\u9fa5" and not ("\u4e00" <= t2s[t] <= "\u9fa5"):
                            t2s[t] = s
                    else:
                        t2s[t] = s

    # 扩充经典古籍异体字与避讳字映射表
    EXTRA_VARIANTS = {
        "悳": "德", "𢧐": "战", "戰": "战", "䝙": "貙", "䇲": "筴", "爲": "为", "為": "为",
        "衞": "卫", "後": "后", "餘": "馀", "並": "并", "說": "说",
        "裡": "里", "裏": "里", "穀": "谷", "於": "于", "徵": "征",
        "澂": "澄", "賍": "赃", "俛": "俯", "餼": "饩", "鵾": "鲲",
        "謌": "歌", "巿": "市", "阸": "隘", "濞": "濞", "駮": "驳",
        "鬲": "鬲", "阼": "阼", "繆": "缪", "隲": "陟", "脩": "修",
        "啟": "启", "啓": "启", "鴈": "雁", "槩": "概", "硃": "朱",
        "鼂": "晁", "薑": "姜", "蒧": "点", "彫": "雕", "冄": "冉",
        "犇": "奔", "駵": "骝", "騮": "骝", "臞": "癯", "蘼": "蘼",
        "筴": "策", "藉": "籍", "點": "点", "匄": "丐", "囏": "艰",
        "遬": "遫", "濅": "寖", "罙": "穼", "冞": "穼",
    }
    t2s.update(EXTRA_VARIANTS)

    def normalize(text):
        if not text:
            return ""
        text = "".join(t2s.get(c, c) for c in text)
        return "".join(c for c in text if unicodedata.category(c).startswith("L") or unicodedata.category(c).startswith("N"))

    return normalize


def align_sanjiazhu(notes, sections, norm):
    # 按章节整理正文段落
    ch_secs = collections.defaultdict(list)
    ch_pns = collections.defaultdict(set)
    for s in sections:
        cid = s["chapter_id"]
        ch_secs[cid].append({
            "id": s.get("id"),
            "pn": s.get("pn"),
            "order": s.get("order", 0),
            "plain": s.get("plain", ""),
            "norm": norm(s.get("plain", "")),
        })
        ch_pns[cid].add(s.get("pn"))

    ch_notes = collections.defaultdict(list)
    for n in notes:
        ch_notes[n["chapter_id"]].append(n)

    aligned_results = []
    unmatched_notes = []

    for cid, nlist in ch_notes.items():
        secs = ch_secs.get(cid, [])
        valid_pns = ch_pns.get(cid, set())
        if not secs:
            unmatched_notes.extend(nlist)
            continue

        curr_sec_idx = 0
        total_secs = len(secs)
        ch_aligned_notes = []

        for n in nlist:
            raw_anchor = n.get("anchor_text", "").strip()
            raw_before = n.get("before_context", "").strip()
            raw_after = n.get("after_context", "").strip()
            existing_sid = (n.get("sentence_id") or "").strip()

            target_pn = None
            match_method = "none"

            # 篇首空锚点（通常为篇题注）直接绑定篇首段落
            if not raw_anchor and not raw_before:
                target_pn = secs[0]["pn"]
                match_method = "chapter_head"

            # 策略 1：原始 anchor 归一化后匹配（长度 >= 2）
            anchor_norm = norm(raw_anchor)
            if not target_pn and len(anchor_norm) >= 2:
                # 优先沿时序向后找
                for idx in range(curr_sec_idx, total_secs):
                    if anchor_norm in secs[idx]["norm"]:
                        target_pn = secs[idx]["pn"]
                        curr_sec_idx = idx
                        match_method = "anchor_forward"
                        break
                # 若未找到，在全章范围内找（容忍局部顺序颠倒）
                if not target_pn:
                    for idx in range(0, curr_sec_idx):
                        if anchor_norm in secs[idx]["norm"]:
                            target_pn = secs[idx]["pn"]
                            curr_sec_idx = idx
                            match_method = "anchor_full"
                            break

            # 策略 2：截取 prefix + anchor + after 滑动窗口子串匹配
            if not target_pn:
                prefix = raw_before
                if raw_anchor and prefix.endswith(raw_anchor):
                    prefix = prefix[:-len(raw_anchor)]
                combo = norm(prefix[-8:] + raw_anchor + raw_after[:8])

                sub_anchors = []
                if len(anchor_norm) >= 4:
                    sub_anchors.append(anchor_norm[:4])
                    sub_anchors.append(anchor_norm[-4:])
                if len(combo) >= 2:
                    sub_anchors.append(combo)
                    if len(combo) >= 4:
                        sub_anchors.append(combo[:4])
                        sub_anchors.append(combo[-4:])
                    if len(combo) >= 6:
                        sub_anchors.append(combo[:6])
                        sub_anchors.append(combo[-6:])

                after_clean = norm(raw_after)
                if len(after_clean) >= 2:
                    sub_anchors.append(after_clean[:4])
                    sub_anchors.append(after_clean[:3])
                    sub_anchors.append(after_clean[:2])

                for sub in sub_anchors:
                    for idx in range(curr_sec_idx, total_secs):
                        if sub in secs[idx]["norm"]:
                            target_pn = secs[idx]["pn"]
                            curr_sec_idx = idx
                            match_method = "window_sub"
                            break
                    if target_pn:
                        break
                    for idx in range(0, curr_sec_idx):
                        if sub in secs[idx]["norm"]:
                            target_pn = secs[idx]["pn"]
                            curr_sec_idx = idx
                            match_method = "window_full"
                            break
                    if target_pn:
                        break

            # 策略 3：纯标点或单字短锚点，取 prefix 末尾 2~5 字在当前游标附近匹配
            if not target_pn and raw_before:
                prefix_clean = norm(raw_before)
                for wlen in [4, 3, 2]:
                    if len(prefix_clean) >= wlen:
                        sub = prefix_clean[-wlen:]
                        for idx in range(curr_sec_idx, min(curr_sec_idx + 5, total_secs)):
                            if sub in secs[idx]["norm"]:
                                target_pn = secs[idx]["pn"]
                                curr_sec_idx = idx
                                match_method = "prefix_tail_forward"
                                break
                        if target_pn:
                            break
                        for idx in range(max(0, curr_sec_idx - 3), curr_sec_idx):
                            if sub in secs[idx]["norm"]:
                                target_pn = secs[idx]["pn"]
                                curr_sec_idx = idx
                                match_method = "prefix_tail_back"
                                break
                        if target_pn:
                            break

            # 策略 4：若已有有效 sentence_id 且存在于该章段落中，予以保留
            if not target_pn and existing_sid and existing_sid in valid_pns:
                target_pn = existing_sid
                match_method = "preserved_existing"

            ch_aligned_notes.append({
                "note": n,
                "note_id": n["note_id"],
                "chapter_id": cid,
                "target_pn": target_pn,
                "old_sid": existing_sid,
                "method": match_method,
                "anchor": raw_anchor,
            })

        # 第二轮拓扑优化：上下文三明治夹逼补齐 (Sandwich Interpolation)
        for i in range(len(ch_aligned_notes)):
            cur = ch_aligned_notes[i]
            if not cur["target_pn"]:
                prev_pn = ch_aligned_notes[i - 1]["target_pn"] if i > 0 else None
                next_pn = ch_aligned_notes[i + 1]["target_pn"] if i + 1 < len(ch_aligned_notes) else None
                if prev_pn and next_pn and prev_pn == next_pn:
                    cur["target_pn"] = prev_pn
                    cur["method"] = "sandwich_interpolation"

        for a in ch_aligned_notes:
            if a["target_pn"]:
                aligned_results.append(a)
            else:
                unmatched_notes.append(a["note"])

    return aligned_results, unmatched_notes


def main():
    parser = argparse.ArgumentParser(description="Audit and Align Sanjiazhu Notes")
    parser.add_argument("--write", action="store_true", help="Write aligned sentence_id back to sanjiazhu.jsonl")
    parser.add_argument("--report", default=os.path.join(HERE, "sanjiazhu_audit_report.json"), help="Path to save report")
    args = parser.parse_args()

    print("=========================================================")
    print("      三家注全量锚定对齐与离线绑定工具 (Phase 2)        ")
    print("=========================================================")

    print("[1/3] 加载典籍正文与三家注语料...")
    sections = C.load_table("sections")
    notes = C.load_table("sanjiazhu")
    total_notes = len(notes)
    old_valid_sid = sum(1 for n in notes if (n.get("sentence_id") or "").strip())
    print(f"      三家注条目总数: {total_notes}")
    print(f"      原有已绑定 sentence_id: {old_valid_sid} 条 ({old_valid_sid/total_notes*100:.2f}%)")

    print("[2/3] 运行单调时序拓扑与异体字滑动窗口对齐引擎...")
    norm = build_normalizer()
    aligned, unmatched = align_sanjiazhu(notes, sections, norm)

    new_match_count = len(aligned)
    print(f"      新对齐成功条数: {new_match_count} 条")
    print(f"      新对齐覆盖率: {new_match_count/total_notes*100:.2f}% (提升 +{(new_match_count-old_valid_sid)/total_notes*100:.2f}%)")
    print(f"      仍未对齐孤儿数: {len(unmatched)} 条")

    # 方法分布统计
    method_dist = collections.Counter(a["method"] for a in aligned)
    print(f"      对齐策略分布: {dict(method_dist)}")

    # 导出诊断报告
    report_data = {
        "total_notes": total_notes,
        "initially_bound": old_valid_sid,
        "newly_aligned": new_match_count,
        "coverage_rate": f"{new_match_count/total_notes*100:.2f}%",
        "method_distribution": dict(method_dist),
        "unmatched_sample": [
            {
                "chapter_id": u["chapter_id"],
                "note_id": u["note_id"],
                "anchor": u.get("anchor_text", ""),
                "before": u.get("before_context", "")[-15:],
                "after": u.get("after_context", "")[:15],
            } for u in unmatched[:30]
        ]
    }
    with open(args.report, "w", encoding="utf-8") as f:
        json.dump(report_data, f, ensure_ascii=False, indent=2)
    print(f"\n      诊断分析报告已保存至: {args.report}")

    # 回填落盘
    if args.write:
        print("\n[3/3] 将推导出的 sentence_id 回填落盘至 sanjiazhu.jsonl...")
        id_to_pn = {(a["chapter_id"], a["note_id"]): a["target_pn"] for a in aligned}
        updated_count = 0
        for n in notes:
            cid = n.get("chapter_id")
            nid = n.get("note_id")
            key = (cid, nid)
            if key in id_to_pn:
                new_pn = id_to_pn[key]
                if n.get("sentence_id") != new_pn:
                    n["sentence_id"] = new_pn
                    updated_count += 1
        C.save_table("sanjiazhu", notes)
        print(f"      已成功回填并更新 {updated_count} 条注疏的 sentence_id 字段！")
    else:
        print("\n提示：当前为 Dry-Run 模式，若需将绑定结果写入语料库，请追加参数 --write。")


if __name__ == "__main__":
    main()
