#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 SQLite 语料库导出 OpusOne Corpus 中间格式（基线导出 / 往返校验用）。"""
import os, sys, sqlite3, argparse
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import corpus

default_db = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app/src/main/assets/opusone.db")

parser = argparse.ArgumentParser(description="从 SQLite 语料库导出 OpusOne Corpus 中间格式（基线导出 / 往返校验用）。")
parser.add_argument("db", nargs="?", default=default_db, help="SQLite 数据库路径 (默认: assets/opusone.db)")
parser.add_argument("--confirm-overwrite", "-f", "--force", action="store_true", help="确认覆盖现有语料库文件")
args = parser.parse_args()

DB = args.db
if not os.path.exists(DB):
    sys.exit(f"错误: 数据库文件不存在: {DB}")

# 安全防护：防止误操作覆盖生产语料库
if os.path.exists(corpus.CORPUS_DIR) and os.listdir(corpus.CORPUS_DIR):
    if not args.confirm_overwrite:
        print(f"⚠️  安全保护触发：目标语料库目录已存在数据 [{corpus.CORPUS_DIR}]。")
        print("为了防止误操作覆盖已有精校标注，请添加 --confirm-overwrite 或 -f 标志确认执行覆盖导出。")
        sys.exit(1)

con = sqlite3.connect(DB)
cur = con.cursor()

def rows_to_jsonl(sql, keys):
    for row in cur.execute(sql):
        yield dict(zip(keys, row))

corpus.save_table("chapters", rows_to_jsonl(
    "SELECT id, category, title, summary, word_count, section_count FROM chapters ORDER BY id",
    ["id", "category", "title", "summary", "word_count", "section_count"]))
corpus.save_table("sections", rows_to_jsonl(
    "SELECT chapter_id, order_in_chapter, pn_index, section_type, heading_level, heading_text, tagged_content, plain_text, translation FROM sections ORDER BY chapter_id, order_in_chapter",
    ["chapter_id", "order", "pn", "type", "heading_level", "heading_text", "content", "plain", "translation"]))
corpus.save_table("entities", rows_to_jsonl(
    "SELECT id, label, type, type_name_zh, aliases, description, tags, occurrences_count FROM entities ORDER BY id",
    ["id", "label", "type", "type_name_zh", "aliases", "description", "tags", "occurrences_count"]))
corpus.save_table("occurrences", rows_to_jsonl(
    "SELECT entity_id, chapter_id, section_pn FROM entity_occurrences ORDER BY entity_id, chapter_id",
    ["entity_id", "chapter_id", "section_pn"]))
corpus.save_table("chengyu", rows_to_jsonl(
    "SELECT id, word, chapter_id, chapter_title, pn, quote, meaning, context FROM chengyu ORDER BY id",
    ["id", "word", "chapter_id", "chapter_title", "pn", "quote", "meaning", "context"]))
corpus.save_table("wars", rows_to_jsonl(
    "SELECT id, war_id, name, chapter_num, chapter_title, description, full_description FROM wars ORDER BY id",
    ["id", "war_id", "name", "chapter_num", "chapter_title", "description", "full_description"]))
corpus.save_table("stories", rows_to_jsonl(
    "SELECT id, chapter_id, chapter_title, title, summary, original, translation, source_pns FROM stories ORDER BY chapter_id",
    ["id", "chapter_id", "chapter_title", "title", "summary", "original", "translation", "source_pns"]))
corpus.save_table("taishigongyue", rows_to_jsonl(
    "SELECT chapter_id, chapter_title, content, plain_content FROM taishigongyue ORDER BY chapter_id",
    ["chapter_id", "chapter_title", "content", "plain_content"]))
corpus.save_table("sanjiazhu", rows_to_jsonl(
    "SELECT id, chapter_id, note_id, anchor_text, before_context, after_context, jijie, suoyin, zhengyi, other_notes, sentence_id FROM sanjiazhu_notes ORDER BY id",
    ["id", "chapter_id", "note_id", "anchor_text", "before_context", "after_context", "jijie", "suoyin", "zhengyi", "other_notes", "sentence_id"]))

m = corpus.load_manifest()
m["format"] = corpus.FORMAT_ID
m["generated_at"] = __import__("datetime").datetime.now().isoformat(timespec="seconds")
m.setdefault("chapters", {})
for (cid,) in cur.execute("SELECT DISTINCT chapter_id FROM sections ORDER BY chapter_id"):
    m["chapters"].setdefault(str(cid), {"annotation": "legacy", "source": "opusone-v1 import"})
m["chapters"].setdefault("1", {"annotation": "legacy", "source": "opusone-v1 import"})  # 待第一方标注覆盖，不覆盖已存在的精标元数据
corpus.save_manifest(m)

for t in corpus.TABLES:
    n = len(corpus.load_table(t))
    print(f"{t}: {n} 行")
con.close()
print("导出完成 →", corpus.CORPUS_DIR)
