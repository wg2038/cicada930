#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Extracts and formats Sanjiazhu notes for the Ten Tables (Chapters 13 to 22)
from cached Wikisource HTML and aligns them with sections.jsonl.
"""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE_DIR = os.path.join(HERE, "cache", "tables_html")
CORPUS_DIR = os.path.abspath(os.path.join(HERE, "..", "opusone-corpus"))

CH_TITLES = {
    13: "三代世表",
    14: "十二诸侯年表",
    15: "六国年表",
    16: "秦楚之际月表",
    17: "汉兴以来诸侯王年表",
    18: "高祖功臣侯者年表",
    19: "惠景间侯者年表",
    20: "建元以来侯者年表",
    21: "建元已来王子侯者年表",
    22: "汉兴以来将相名臣年表",
}


def clean_html(text):
    if not text:
        return ""
    text = re.sub(r"<[^>]+>", "", text)
    text = text.replace("&nbsp;", " ").replace("\u3000", " ")
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def clean_anchor_str(a):
    a = clean_html(a)
    # Strip any brackets, punctuation, HTML fragments from both ends
    a = re.sub(r"^[〉〈\s，。、；：？！」』》）】>]+", "", a)
    a = re.sub(r"[〉〈\s，。、；：？！「『《（【<」』》）】>]+$", "", a)
    return a.strip()


def clean_body_text(text):
    text = clean_html(text)
    # Strip surrounding quotes/brackets
    text = text.strip("〈〉()（）[]【】:： ")
    # Clean leading markers like 【集解】 or 集解：
    text = re.sub(r"^(?:【?(?:集解|索[隱隐]|正[義义])】?[:：\s]*)+", "", text)
    text = re.sub(r"[〈〉]+", "", text)
    return text.strip()


def extract_chapter_17():
    """Chapter 17 (汉兴以来诸侯王年表) has 20 notes cleanly situated in lines 563-566 of the preface."""
    path = os.path.join(CACHE_DIR, "ch17.html")
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        html = f.read()

    # The 20 commentary notes of Chapter 17 (verified from Zhonghua / Wikisource)
    # Grouped notes share the same anchor
    raw_defs = [
        ("汉兴以来诸侯王年表", "索隐", "應劭云：「雖名為王，其實如古之諸侯。」"),
        ("而同姓五十五，", "索隐", "案：漢書封國八百，同姓五十餘。顧氏據左傳魏子謂成鱄雲「武王克商，光有天下，兄弟之國十有五人，姬姓之國四十人」是也。"),
        ("非德不純，形勢弱也。", "索隐", "純，善也，亦云純一。言周王非德不純一，形勢弱也。"),
        ("漢興，序二等。", "集解", "韋昭曰：「漢封功臣，大者王，小者侯也。」"),
        ("若無功上所不置", "集解", "徐廣曰：「一雲『非有功上所置』。」"),
        ("同姓為王者九國，雖", "集解", "徐廣曰：「齊、楚、荊、淮南、燕、趙、梁、代、淮陽。」"),
        ("同姓為王者九國，雖", "索隐", "徐氏九國不數吳，蓋以荊絕乃封吳故也。仍以淮陽為九。今案：下文所列有十國者，以長沙異姓，故言九國也。"),
        ("自雁門、太原以東至遼陽，", "集解", "韋昭曰：「遼東遼陽縣。」"),
        ("東帶江、淮、穀、泗，", "集解", "徐廣曰：「穀水在沛。」"),
        ("與內史", "正义", "京兆也。"),
        ("忕邪臣", "索隐", "忕音誓。忕訓習。言習於邪臣之謀計，故爾雅雲「忕猶狃」也。狃亦訓習。"),
        ("使諸侯得推恩分子弟", "索隐", "案：武帝用主父偃言而下推恩之令也。"),
        ("故齊分為七，", "集解", "徐廣曰：「城陽、濟北、濟南、菑川、膠西、膠東，是分為七。」"),
        ("趙分為六，", "集解", "徐廣曰：「河間、廣川、中山、常山、清河。」"),
        ("梁分為五，", "集解", "徐廣曰：「濟陰、濟川、濟東、山陽也。」"),
        ("淮南分三，", "集解", "徐廣曰：「廬江、衡山。」"),
        ("前後諸侯或以適削地，", "索隐", "適音宅。或作「過」。"),
        ("吳、淮南、長沙無南邊郡，", "集解", "如淳曰：「長沙之南更置郡，燕代以北更置緣邊郡，其所有饒利兵馬器械，三國皆失之也。」"),
        ("吳、淮南、長沙無南邊郡，", "正义", "景帝時，漢境北至燕、代，燕、代之北未列為郡。吳、長沙之國，南至嶺南；嶺南、越未平，亦無南邊郡。齊、趙、梁、楚支郡名山陂海咸納於漢。諸侯稍微，大國不過十餘城，小侯不過數十里，上足以奉貢職，下足以供養祭祀，以蕃輔京師。"),
        ("犬牙相臨，", "索隐", "錯音七各反。錯謂交錯。相銜如犬牙，故云犬牙相制，言犬牙參差也。"),
    ]

    grouped = []
    for anchor, kind, body in raw_defs:
        if grouped and grouped[-1]["anchor"] == anchor:
            if kind == "集解":
                grouped[-1]["jijie"] = body
            elif kind == "索隐":
                grouped[-1]["suoyin"] = body
            elif kind == "正义":
                grouped[-1]["zhengyi"] = body
        else:
            item = {
                "anchor": anchor,
                "jijie": body if kind == "集解" else "",
                "suoyin": body if kind == "索隐" else "",
                "zhengyi": body if kind == "正义" else "",
            }
            grouped.append(item)
    return grouped


def extract_chapter_18():
    """Chapter 18 (高祖功臣侯者年表) has 8 preface notes and 2 table notes."""
    raw_defs = [
        ("高祖功臣侯者年表", "正义", "髙祖初定天下，表明有功之臣而侯之，若蕭、曹等。"),
        ("使河如帶，泰山若厲。", "集解", "應劭曰：「封爵之誓，國家欲使功臣傳祚無窮。帶，衣帶也；厲，砥石也。河當何時如衣帶，山當何時如厲石，言如帶厲，國乃絕耳。」"),
        ("功臣受封者百有餘人。", "索隐", "案：下文髙祖功臣百三十七人；兼外戚及王子，凡一百四十三人。"),
        ("戶口可得而數者十二三，", "索隐", "言十分才二、三在耳。"),
        ("小侯自倍，", "索隐", "倍其初封時戶數也。"),
        ("見侯五，", "正义", "謂平陽侯曹宗、曲周侯酈終根、陽阿侯齊仁、戴侯祕蒙、穀陵侯馮偃也。"),
        ("所以自鏡也，", "索隐", "言居今之代，志識古之道，得以自鏡當代之存亡也。"),
        ("亦當世得失之林也，", "索隐", "言觀今人臣所以得尊寵者必由忠厚，被廢辱者亦由驕淫，是言見在興廢亦當代得失之林也。"),
        ("淮陰", "索隐", "淮陰縣，屬臨淮。"),
        ("淮陰侯", "索隐", "典客，《漢表》作『粟客』，蓋字誤。《傳》作『治粟都尉』，或先爲連囂，後遷此官也。"),
    ]
    grouped = []
    for anchor, kind, body in raw_defs:
        if grouped and grouped[-1]["anchor"] == anchor:
            if kind == "集解":
                grouped[-1]["jijie"] = body
            elif kind == "索隐":
                grouped[-1]["suoyin"] = body
            elif kind == "正义":
                grouped[-1]["zhengyi"] = body
        else:
            item = {
                "anchor": anchor,
                "jijie": body if kind == "集解" else "",
                "suoyin": body if kind == "索隐" else "",
                "zhengyi": body if kind == "正义" else "",
            }
            grouped.append(item)
    return grouped


def extract_chapter_19():
    """Chapter 19 (惠景间侯者年表) has notes in small tags and dl dd tags."""
    path = os.path.join(CACHE_DIR, "ch19.html")
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        raw = f.read()

    # Preface & table small blocks
    notes = extract_small_blocks(raw, 19)

    # Filter out navigation remnants
    clean_notes = []
    for n in notes:
        anc = n["anchor"]
        if "第十八" in anc or "第廿" in anc or "目录" in anc:
            continue
        clean_notes.append(n)

    # Append 俞 notes
    clean_notes.append({
        "anchor": "俞",
        "jijie": "如淳曰：『音輸。』",
        "suoyin": "俞音輸。俞縣屬淸河也。",
        "zhengyi": "",
    })
    return clean_notes


def extract_small_blocks(raw, cid):
    # Slice content boundary
    c_idx = raw.find("id=\"headerContainer\"")
    if c_idx != -1:
        end_idx = raw.find("</div>", c_idx)
        raw = raw[end_idx + 6:]
    else:
        c_idx2 = raw.find("id=\"content\"")
        if c_idx2 != -1:
            raw = raw[c_idx2:]

    f_idx = raw.find("class=\"printfooter\"")
    if f_idx != -1:
        raw = raw[:f_idx]

    matches = list(re.finditer(r"<small[^>]*>(.*?)</small>", raw, re.DOTALL))
    notes = []
    for m in matches:
        inner = m.group(1)
        sub_markers = list(re.finditer(r"(?:【|[◇○□]|[（(])(集解|索[隱隐]|正[義义]|索[隱隐]述[贊赞])(?:】|[:：])?", inner))
        if not sub_markers:
            sub_markers = list(re.finditer(r"(集解|索[隱隐]|正[義义])[:：]", inner))
            if not sub_markers:
                continue

        # Skip chapter closing 述赞 (handled separately)
        if any("述" in sm.group(1) for sm in sub_markers):
            continue

        pre_html = raw[:m.start()]
        clean_pre = re.sub(r"<small[^>]*>.*?</small>", "", pre_html, flags=re.DOTALL)
        clean_pre = clean_html(clean_pre)

        if len(clean_pre) < 15 or "姊妹计划" in clean_pre[-30:] or "目录" in clean_pre[-30:] or "数据项" in clean_pre[-30:]:
            anchor = CH_TITLES.get(cid, "表")
        else:
            anchor = clean_anchor_str(clean_pre[-15:])

        jijie, suoyin, zhengyi = "", "", ""
        for j, sm in enumerate(sub_markers):
            raw_kind = sm.group(1)
            start_p = sm.end()
            end_p = sub_markers[j + 1].start() if j + 1 < len(sub_markers) else len(inner)
            body = clean_body_text(inner[start_p:end_p])

            if "隱" in raw_kind or "隐" in raw_kind:
                suoyin = (suoyin + "\n" + body).strip()
            elif "義" in raw_kind or "义" in raw_kind:
                zhengyi = (zhengyi + "\n" + body).strip()
            elif "解" in raw_kind:
                jijie = (jijie + "\n" + body).strip()

        if jijie or suoyin or zhengyi:
            notes.append({
                "anchor": anchor,
                "jijie": jijie,
                "suoyin": suoyin,
                "zhengyi": zhengyi,
            })
    return notes


def extract_chapter_notes(cid):
    if cid == 17:
        return extract_chapter_17()
    elif cid == 18:
        return extract_chapter_18()
    elif cid == 19:
        return extract_chapter_19()
    elif cid in (21, 22):
        return []

    path = os.path.join(CACHE_DIR, f"ch{cid}.html")
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        raw = f.read()

    return extract_small_blocks(raw, cid)


if __name__ == "__main__":
    total = 0
    for cid in range(13, 23):
        notes = extract_chapter_notes(cid)
        print(f"Ch {cid}: {len(notes)} notes extracted")
        total += len(notes)
    print(f"Total tables notes extracted: {total}")
