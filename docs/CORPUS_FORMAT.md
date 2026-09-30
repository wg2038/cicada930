# OpusOne Corpus Format（自有语料中间格式）v1

> `opusone-corpus/` 目录是「一家言」的**第一方语料源**。
> 构建管线（build_from_corpus.py）只认识本格式；
> 外部来源（如旧版数据导出、将来的维基文库标注）一律通过导入器转换为本格式后进入管线。

## 目录结构

```
opusone-corpus/
├── manifest.json      # 数据集清单与标注溯源
├── chapters.jsonl     # 篇章元数据
├── sections.jsonl     # 段落（含 OAM 标注正文）
├── entities.jsonl     # 实体词条
├── occurrences.jsonl  # 实体出处
├── chengyu.jsonl      # 成语典故
├── wars.jsonl         # 战役
├── stories.jsonl      # 历史演义
├── taishigongyue.jsonl# 太史公曰
└── sanjiazhu.jsonl    # 三家注
```

## 行格式（JSONL，每行一个 JSON 对象）

- `sections.jsonl`：
  `{chapter_id, order, pn, type, heading_level, heading_text, content, plain, translation}`
  - `content`：OAM v1.0 标注正文（规范见 ANNOTATION_SPEC.md）
  - `plain`：剥除标注后的纯文本。**硬约束：content 去标注、去段首索引后必须与 plain 逐字符相等**
- `manifest.json`：
  `{format: "opusone-corpus/1", generated_at, chapters: {"<id>": {annotation, source, validated_at}}}`
  - `annotation`: `legacy`（继承标注）| `opusone-v1`（第一方标注）

## 校验规则（validate_corpus.py / annotate.py 共用）

1. **文本同一性**：strip_oam(content) 去段首索引标记后 == plain，逐字符相等（防文本误增删字）
2. **文法**：⟪⟫ 全部成对且匹配 `⟪[A-Za-z]{2} 正文⟫`；代码 ∈ 规范表
3. **别名**：`|` 后为索引键；键不在实体词典时记入 pending（词典可生长）
4. **密度哨兵**：每章跨度数与基线偏差 >50% 时告警（人工复核，不阻断）
