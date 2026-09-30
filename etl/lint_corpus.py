#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OpusOne 语料多维度全量质检工具 (Corpus Quality Linter).

用于系统化发现《史记》语料中的：
1. 形近字、OCR 讹误与异体错字
2. 标点符号体例畸变（配对失衡、连续重叠标点、ASCII 残留）
3. 文本结构异常（空白段、极短正文、空译文、文白长度比失调）
4. OAM 实体标记与出处索引的语法/内容缺陷
5. 三家注 (Sanjiazhu) 锚点失配与孤儿注疏排查

用法：
    python3 etl/lint_corpus.py                    # 终端格式化打印质检汇总
    python3 etl/lint_corpus.py --report out.json  # 导出结构化缺陷报告供下游审查
"""
import argparse
import collections
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CORPUS_DIR = os.path.abspath(os.path.join(HERE, "..", "opusone-corpus"))
sys.path.insert(0, HERE)
import corpus as C  # noqa: E402

# Optical character recognition and homophone typo replacement rules
KNOWN_TYPO_RULES = [
    (re.compile(r"诸候"), "诸侯", "形近字：诸候 → 诸侯"),
    (re.compile(r"草管人命"), "草菅人命", "成语形近字：草管人命 → 草菅人命"),
    (re.compile(r"作崇"), "作祟", "形近字：作崇 → 作祟"),
    (re.compile(r"自已(?=[，。、 ！？」』）])"), "自己", "形近字：自已 → 自己"),
    (re.compile(r"而己(?=[，。、 ！？」』）])"), "而已", "形近字：而己 → 而已"),
    (re.compile(r"己经"), "已经", "形近字：己经 → 已经"),
    (re.compile(r"剌客"), "刺客", "形近字：剌客 → 刺客"),
    (re.compile(r"雎阳"), "睢阳", "古地名形近字：雎阳 → 睢阳"),
    (re.compile(r"雎水"), "睢水", "古水名形近字：雎水 → 睢水"),
    (re.compile(r"统冶"), "统治", "形近字：统冶 → 统治"),
    (re.compile(r"号日"), "号曰", "形近字：号日 → 号曰"),
    (re.compile(r"地土将军"), "地士将军", "史记特有形近：地土将军 → 地士将军"),
    (re.compile(r"戌漕"), "戍漕", "戍/戌形近：戌漕 → 戍漕"),
    (re.compile(r"太後"), "太后", "异体简繁杂揉：太後 → 太后"),
    (re.compile(r"皇後"), "皇后", "异体简繁杂揉：皇後 → 皇后"),
]

# 角色/动词后接日/曰检查
ROLE_YUE_RE = re.compile(
    r"(?<![甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉戌亥吉明翌即某数度当首其此半終终生朔望晦])"
    r"([王公侯帝后皇后太后夫人君父卿相臣妾博士舍人先生客奴使主翁媪妪叟]|[\u4e00-\u9fa5]{2})日([：:「])"
)

# 连续重复标点
DOUBLE_PUNC_RE = re.compile(r"([。，、！？：；])\1+")

# 半角残余
ASCII_PUNC_RE = re.compile(r"[\"';]")

# 十表章节列表
TEN_TABLE_CHAPTERS = {13, 14, 15, 16, 17, 18, 19, 20, 21, 22}


def lint_sections(sections, entity_keys):
    issues = []
    
    # 统计信息
    stat_types = collections.Counter(s.get("type") for s in sections)
    
    # 篇章跨段的括号配对追踪（全库统计）
    for s in sections:
        ch = s.get("chapter_id")
        pn = s.get("pn")
        sec_type = s.get("type", "paragraph")
        plain = s.get("plain", "") or ""
        content = s.get("content", "") or ""
        trans = s.get("translation", "") or ""
        loc = f"sections:ch{ch}#{pn}"

        # Common typos and character confusions
        for pat, fix, desc in KNOWN_TYPO_RULES:
            for m in pat.finditer(plain):
                start = max(0, m.start() - 12)
                end = min(len(plain), m.end() + 15)
                issues.append({
                    "category": "TYPO",
                    "severity": "HIGH",
                    "location": loc,
                    "matched": m.group(0),
                    "suggestion": fix,
                    "desc": desc,
                    "snippet": plain[start:end],
                })

        for m in ROLE_YUE_RE.finditer(plain):
            start = max(0, m.start() - 10)
            end = min(len(plain), m.end() + 15)
            issues.append({
                "category": "TYPO",
                "severity": "MEDIUM",
                "location": loc,
                "matched": m.group(0),
                "suggestion": m.group(1) + "曰" + m.group(2),
                "desc": "可疑日/曰混淆（角色+日+冒号/引号）",
                "snippet": plain[start:end],
            })

        # Punctuation formatting checks
        for m in DOUBLE_PUNC_RE.finditer(plain):
            start = max(0, m.start() - 10)
            end = min(len(plain), m.end() + 10)
            issues.append({
                "category": "PUNCTUATION",
                "severity": "LOW",
                "location": loc,
                "matched": m.group(0),
                "desc": f"连续标点重复「{m.group(0)}」",
                "snippet": plain[start:end],
            })

        # Residual ASCII punctuation
        for m in ASCII_PUNC_RE.finditer(plain):
            start = max(0, m.start() - 10)
            end = min(len(plain), m.end() + 10)
            issues.append({
                "category": "PUNCTUATION",
                "severity": "MEDIUM",
                "location": loc,
                "matched": m.group(0),
                "desc": f"正文中存在半角 ASCII 标点「{m.group(0)}」",
                "snippet": plain[start:end],
            })

        # Unmatched ASCII brackets outside ten tables
        if ch not in TEN_TABLE_CHAPTERS and ("]" in plain or "[" in plain):
            issues.append({
                "category": "PUNCTUATION",
                "severity": "MEDIUM",
                "location": loc,
                "matched": "[" if "[" in plain else "]",
                "desc": "非十表章节正文潜存英文方括号 [ 或 ]",
                "snippet": plain[:60],
            })

        # Bracket and quotation balancing
        for left, right, name in [("《", "》", "书名号"), ("（", "）", "全角圆括号")]:
            lc = plain.count(left)
            rc = plain.count(right)
            if lc != rc:
                issues.append({
                    "category": "PUNCTUATION",
                    "severity": "LOW",
                    "location": loc,
                    "matched": f"{left}x{lc}, {right}x{rc}",
                    "desc": f"段内{name}开闭数量不平衡",
                    "snippet": plain[:60],
                })

        # OAM grammar and plain text congruence
        oam_errs = C.validate_section(content, plain, entity_keys)
        for err in oam_errs:
            issues.append({
                "category": "OAM_SYNTAX",
                "severity": "HIGH",
                "location": loc,
                "desc": f"OAM/同一性校验失败: {err}",
                "snippet": content[:80],
            })

        # Text and translation completeness
        if sec_type == "paragraph":
            p_len = len(plain.strip())
            t_len = len(trans.strip())
            if p_len > 80 and t_len == 0 and ch not in TEN_TABLE_CHAPTERS:
                issues.append({
                    "category": "TRANSLATION",
                    "severity": "LOW",
                    "location": loc,
                    "desc": f"正文长达 {p_len} 字但缺少白话译文",
                    "snippet": plain[:50],
                })
            # 文白长度比极度畸变
            if p_len > 40 and t_len > 0:
                ratio = t_len / p_len
                if ratio < 0.25 or ratio > 7.0:
                    issues.append({
                        "category": "TRANSLATION",
                        "severity": "LOW",
                        "location": loc,
                        "desc": f"文白长度比例异常 (比率: {ratio:.2f}, 原文:{p_len}字, 译文:{t_len}字)",
                        "snippet": f"原:{plain[:30]}... 译:{trans[:30]}...",
                    })

    return issues


def lint_sanjiazhu(notes, sections):
    issues = []
    # 简易简繁映射（或基础正则清洗）来测试三家注在正文中的命中
    # 构造章节正文
    ch_text = collections.defaultdict(str)
    for s in sections:
        ch_text[s["chapter_id"]] += s.get("plain", "")

    # 常见三家注常用繁体字到简体字的快速归一化映射
    T2S_SAMPLE = str.maketrans({
        "靈": "灵", "齊": "齐", "敦": "敦", "聰": "聪", "明": "明",
        "黃": "黄", "帝": "帝", "顓": "颛", "頊": "顼", "嚳": "喾",
        "堯": "尧", "舜": "舜", "湯": "汤", "禹": "禹", "禮": "礼",
        "樂": "乐", "書": "书", "傳": "传", "紀": "纪", "贊": "赞",
        "長": "长", "為": "为", "國": "国", "漢": "汉", "楚": "楚",
        "秦": "秦", "趙": "赵", "魏": "魏", "韓": "韩", "燕": "燕",
        "齊": "齐", "項": "项", "劉": "刘", "說": "说", "鄭": "郑",
        "馬": "马", "遷": "迁", "駰": "骃", "貞": "贞", "節": "节",
        "義": "义", "隱": "隐", "解": "解", "注": "注", "集": "集",
        "從": "从", "後": "后", "復": "复", "應": "应", "聲": "声",
        "號": "号", "師": "师", "將": "将", "軍": "军", "相": "相",
    })

    unmatched_count = 0
    empty_anchors = 0
    for n in notes:
        nid = n.get("note_id")
        cid = n.get("chapter_id")
        anchor = n.get("anchor_text", "").strip()
        loc = f"sanjiazhu:ch{cid}#{nid}"
        
        if not anchor:
            empty_anchors += 1
            continue

        # 清除标点
        clean_anchor = "".join(c for c in anchor if "\u4e00" <= c <= "\u9fa5")
        if len(clean_anchor) < 2:
            continue

        clean_simp = clean_anchor.translate(T2S_SAMPLE)
        body = ch_text.get(cid, "")
        if clean_anchor not in body and clean_simp not in body:
            unmatched_count += 1
            if unmatched_count <= 20:
                issues.append({
                    "category": "SANJIAZHU",
                    "severity": "MEDIUM",
                    "location": loc,
                    "matched": anchor,
                    "desc": "三家注锚点字句在同章正文中未能命中（可能存在版本分歧或字句脱讹）",
                    "snippet": anchor,
                })

    issues.append({
        "category": "SANJIAZHU_SUMMARY",
        "severity": "INFO",
        "location": "sanjiazhu_notes",
        "desc": f"三家注总数 {len(notes)} 条，空锚点 {empty_anchors} 条，初步无法直接命中的锚点 {unmatched_count} 条",
    })
    return issues


def main():
    parser = argparse.ArgumentParser(description="OpusOne Corpus Multi-Dimensional Linter")
    parser.add_argument("--report", help="Path to write structured JSON issues report")
    args = parser.parse_args()

    print("=================================================================")
    print("        OpusOne 语料多维度全量质检 (Corpus Quality Linter)        ")
    print("=================================================================")

    print("[1/4] 正在加载实体表与出处索引...")
    entities = C.load_table("entities")
    entity_keys = {e["id"] for e in entities}
    def get_aliases(e):
        a = e.get("aliases", [])
        if isinstance(a, list): return [x for x in a if isinstance(x, str)]
        if isinstance(a, str):
            try:
                p = json.loads(a)
                if isinstance(p, list): return [x for x in p if isinstance(x, str)]
            except: pass
            if a.strip(): return [a.strip()]
        return []
    entity_keys |= {a for e in entities for a in get_aliases(e)}
    print(f"      已登记实体索引键: {len(entity_keys)} 个")

    print("[2/4] 正在全量扫描 sections.jsonl (正文、标点、OAM与体例)...")
    sections = C.load_table("sections")
    section_issues = lint_sections(sections, entity_keys)
    print(f"      正文段落扫描完成，检出潜在告警: {len(section_issues)} 处")

    print("[3/4] 正在全量审计 sanjiazhu.jsonl (三家注锚定与覆盖率)...")
    notes = C.load_table("sanjiazhu")
    sanjiazhu_issues = lint_sanjiazhu(notes, sections)
    print(f"      三家注审计完成。")

    all_issues = section_issues + sanjiazhu_issues

    # 分类汇总统计
    by_cat = collections.Counter(i["category"] for i in all_issues)
    by_sev = collections.Counter(i["severity"] for i in all_issues)

    print("\n------------------------- 质检结果汇总 -------------------------")
    print(f"  缺陷总数: {len(all_issues)}")
    print(f"  严重程度分布: {dict(by_sev)}")
    print(f"  问题类别分布: {dict(by_cat)}")
    print("-----------------------------------------------------------------")

    # 重点打印 HIGH 级别及典型形近字问题
    highs = [i for i in all_issues if i["severity"] == "HIGH"]
    if highs:
        print(f"\n【重要缺陷清单】(共 {len(highs)} 条)")
        for h in highs[:15]:
            print(f"  - [{h['category']}] {h['location']}: {h['desc']}")
            if "matched" in h:
                print(f"    匹配:「{h['matched']}」→ 建议:「{h.get('suggestion','')}」")
            if "snippet" in h:
                print(f"    上下文: ...{h['snippet']}...")

    # 如果指定报告输出
    if args.report:
        with open(args.report, "w", encoding="utf-8") as f:
            json.dump(all_issues, f, ensure_ascii=False, indent=2)
        print(f"\n完整缺陷报告已写入: {args.report}")

    return 0 if len(highs) == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
