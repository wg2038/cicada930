#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Builds SQLite database from OpusOne Corpus intermediate format.

Ensures schema alignment with app/src/main/assets/opusone.db (DB_VERSION 13).
"""
import os
import sys
import json
import sqlite3

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import corpus

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app/src/main/assets/opusone.db"
)

# Load and validate all tables in memory before touching destination database
TABLES = [
    "chapters", "sections", "entities", "occurrences",
    "chengyu", "wars", "taishigongyue", "stories", "sanjiazhu"
]

data = {}
for _name in TABLES:
    try:
        data[_name] = corpus.load_table(_name)
    except Exception as _e:
        sys.exit(f"语料表 {_name} 加载失败，拒绝构建：{_e}")

def _get_aliases(e):
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

entities = data["entities"]
entity_keys = {e["id"] for e in entities}
entity_keys |= {a for e in entities for a in _get_aliases(e)}

bad = 0
for s_ in data["sections"]:
    errs = corpus.validate_section(s_["content"], s_["plain"], entity_keys)
    if errs:
        bad += 1
        if bad <= 5:
            print(f"[校验失败] ch{s_['chapter_id']}#{s_['pn']}: {errs}")
if bad:
    sys.exit(f"共 {bad} 段未通过校验，拒绝构建")

# Validate required schema fields before database creation
REQUIRED_FIELDS = {
    "chapters": ["id", "category", "title", "summary", "word_count", "section_count"],
    "sections": ["chapter_id", "pn", "type", "heading_level", "heading_text", "content", "plain", "order"],
    "entities": ["id", "label", "type", "type_name_zh", "aliases", "description", "tags", "occurrences_count"],
    "occurrences": ["entity_id", "chapter_id", "section_pn"],
    "chengyu": ["id", "word", "chapter_id", "chapter_title", "pn", "quote", "meaning", "context"],
    "wars": ["id", "war_id", "name", "chapter_num", "chapter_title", "description", "full_description"],
    "taishigongyue": ["chapter_id", "chapter_title", "content", "plain_content"],
    "stories": ["id", "chapter_id", "chapter_title", "title", "summary", "original", "translation", "source_pns"],
    "sanjiazhu": ["id", "chapter_id", "note_id", "anchor_text", "before_context", "after_context",
                  "jijie", "suoyin", "zhengyi", "other_notes", "sentence_id"],
}
for _name, _keys in REQUIRED_FIELDS.items():
    for _i, _row in enumerate(data[_name]):
        _missing = [k for k in _keys if k not in _row]
        if _missing:
            sys.exit(f"语料表 {_name} 第 {_i} 行缺字段 {_missing}，拒绝构建")

# Build into temporary database file and atomically swap upon completion
TMP_DB = OUT + ".tmp"
for _stale in (TMP_DB, TMP_DB + "-journal", TMP_DB + "-wal", TMP_DB + "-shm"):
    if os.path.exists(_stale):
        os.remove(_stale)
con = sqlite3.connect(TMP_DB)
cur = con.cursor()
cur.executescript("""
CREATE TABLE chapters (id INTEGER PRIMARY KEY, category TEXT NOT NULL, title TEXT NOT NULL,
    summary TEXT, word_count INTEGER NOT NULL, section_count INTEGER NOT NULL);
CREATE TABLE sections (id INTEGER PRIMARY KEY AUTOINCREMENT, chapter_id INTEGER NOT NULL,
    pn_index TEXT NOT NULL, section_type TEXT NOT NULL, heading_level INTEGER NOT NULL,
    heading_text TEXT, tagged_content TEXT NOT NULL, plain_text TEXT NOT NULL,
    translation TEXT, order_in_chapter INTEGER NOT NULL, FOREIGN KEY(chapter_id) REFERENCES chapters(id));
CREATE TABLE entities (id TEXT PRIMARY KEY, label TEXT NOT NULL, type TEXT NOT NULL,
    type_name_zh TEXT NOT NULL, aliases TEXT, description TEXT, tags TEXT, occurrences_count INTEGER DEFAULT 0);
CREATE TABLE entity_occurrences (id INTEGER PRIMARY KEY AUTOINCREMENT, entity_id TEXT NOT NULL,
    chapter_id INTEGER NOT NULL, section_pn TEXT NOT NULL, FOREIGN KEY(chapter_id) REFERENCES chapters(id));
CREATE TABLE chengyu (id INTEGER PRIMARY KEY AUTOINCREMENT, word TEXT NOT NULL, chapter_id INTEGER NOT NULL,
    chapter_title TEXT NOT NULL, pn TEXT, quote TEXT, meaning TEXT, context TEXT);
CREATE TABLE wars (id INTEGER PRIMARY KEY AUTOINCREMENT, war_id TEXT, name TEXT NOT NULL,
    chapter_num TEXT, chapter_title TEXT, description TEXT, full_description TEXT);
CREATE TABLE taishigongyue (id INTEGER PRIMARY KEY AUTOINCREMENT, chapter_id INTEGER NOT NULL,
    chapter_title TEXT NOT NULL, content TEXT NOT NULL, plain_content TEXT NOT NULL);
CREATE TABLE stories (id TEXT PRIMARY KEY, chapter_id INTEGER NOT NULL, chapter_title TEXT NOT NULL,
    title TEXT NOT NULL, summary TEXT, original TEXT NOT NULL, translation TEXT NOT NULL, source_pns TEXT);
CREATE TABLE sanjiazhu_notes (id INTEGER PRIMARY KEY AUTOINCREMENT, chapter_id INTEGER NOT NULL,
    note_id TEXT, anchor_text TEXT, before_context TEXT, after_context TEXT, jijie TEXT, suoyin TEXT,
    zhengyi TEXT, other_notes TEXT, sentence_id TEXT);
""")

def sanitize_str_list(x):
    """Normalizes string array fields (aliases, tags, source_pns) into JSON string arrays.
    
    Guarantees homogeneous string elements to ensure reliable deserialization on client.
    """
    if isinstance(x, str):
        try:
            x = json.loads(x)
        except Exception:
            return json.dumps([x], ensure_ascii=False)
    if not isinstance(x, list):
        return json.dumps([], ensure_ascii=False)
    return json.dumps([v for v in x if isinstance(v, str) and v.strip()], ensure_ascii=False)

for c in data["chapters"]:
    cur.execute("INSERT INTO chapters VALUES (?,?,?,?,?,?)",
                (c["id"], c["category"], c["title"], c["summary"], c["word_count"], c["section_count"]))
for s_ in data["sections"]:
    cur.execute("INSERT INTO sections (chapter_id,pn_index,section_type,heading_level,heading_text,tagged_content,plain_text,translation,order_in_chapter) VALUES (?,?,?,?,?,?,?,?,?)",
                (s_["chapter_id"], s_["pn"], s_["type"], s_["heading_level"], s_["heading_text"], s_["content"], s_["plain"], s_.get("translation"), s_["order"]))
valid_entity_ids = set()
for e in data["entities"]:
    desc = (e.get("description") or "").strip()
    if e.get("type") == "redirect" or desc.startswith("REDIRECT "):
        continue
    valid_entity_ids.add(e["id"])
    cur.execute("INSERT INTO entities VALUES (?,?,?,?,?,?,?,?)",
                (e["id"], e["label"], e["type"], e["type_name_zh"], sanitize_str_list(e["aliases"]), e["description"], sanitize_str_list(e["tags"]), e["occurrences_count"]))
for o in data["occurrences"]:
    if o["entity_id"] not in valid_entity_ids:
        continue
    cur.execute("INSERT INTO entity_occurrences (entity_id,chapter_id,section_pn) VALUES (?,?,?)",
                (o["entity_id"], o["chapter_id"], o["section_pn"]))
for c in data["chengyu"]:
    cur.execute("INSERT INTO chengyu VALUES (?,?,?,?,?,?,?,?)",
                (c["id"], c["word"], c["chapter_id"], c["chapter_title"], c["pn"], c["quote"], c["meaning"], c["context"]))
for w in data["wars"]:
    cur.execute("INSERT INTO wars VALUES (?,?,?,?,?,?,?)",
                (w["id"], w["war_id"], w["name"], w["chapter_num"], w["chapter_title"], w["description"], w["full_description"]))
for t in data["taishigongyue"]:
    cur.execute("INSERT INTO taishigongyue (chapter_id,chapter_title,content,plain_content) VALUES (?,?,?,?)",
                (t["chapter_id"], t["chapter_title"], t["content"], t["plain_content"]))
for s_ in data["stories"]:
    cur.execute("INSERT INTO stories VALUES (?,?,?,?,?,?,?,?)",
                (s_["id"], s_["chapter_id"], s_["chapter_title"], s_["title"], s_["summary"], s_["original"], s_["translation"], sanitize_str_list(s_["source_pns"])))
for n in data["sanjiazhu"]:
    cur.execute("INSERT INTO sanjiazhu_notes VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                (n["id"], n["chapter_id"], n["note_id"], n["anchor_text"], n["before_context"], n["after_context"],
                 n["jijie"], n["suoyin"], n["zhengyi"], n["other_notes"], n["sentence_id"]))

# Schema version identifier aligned with OpusOneDatabaseHelper.DB_VERSION
cur.execute("PRAGMA user_version = 13")

# Primary B-Tree indexes for chapter navigation and entity lookup
cur.executescript("""
CREATE INDEX idx_sections_ch_order ON sections(chapter_id, order_in_chapter);
CREATE INDEX idx_occurrences_entity ON entity_occurrences(entity_id);
CREATE INDEX idx_entities_label ON entities(label);
CREATE INDEX idx_sanjiazhu_ch ON sanjiazhu_notes(chapter_id);
""")

# Composite index for entity occurrences JOIN sections
cur.execute("CREATE INDEX idx_sections_pn ON sections(chapter_id, pn_index)")

# Partial index for paginated non-redirect entities ordered by occurrence count
cur.execute("CREATE INDEX idx_entities_visible ON entities(occurrences_count DESC) WHERE type <> 'redirect'")

cur.execute("ANALYZE")
con.commit()
cur.execute("VACUUM")

counts = {t: cur.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]
          for t in ["chapters", "sections", "entities", "entity_occurrences", "chengyu", "wars", "stories", "taishigongyue", "sanjiazhu_notes"]}
con.close()
os.replace(TMP_DB, OUT)
print("构建完成 →", OUT)
print(counts)
