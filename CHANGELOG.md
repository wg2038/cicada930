# 版本更新记录 (Changelog)

本文档记录「一家言 (Cicada)」各版本的重大变更、功能演进与缺陷修复。

---

## [v0.2] - 2026-10-14

v0.2 版本聚焦于**核心数据稳定性、多线程并发安全、Compose UI 鲁棒性与 CI/CD 自动化校验**的全方位系统加固。

### 1. 数据与工具层架构加固 (Data & Util)

- **底本数据库原子替换与多线程并发保护**：
  - 为 `OpusOneDatabaseHelper` 中的数据库拷贝初始化逻辑增加 `@Synchronized` 互斥锁，彻底杜绝多线程冷启动或版本覆盖时的并发冲突。
  - 改为先复制至临时文件 `opusone.db.tmp`，执行 `FileOutputStream.fd.sync()` 强制刷盘，并校验文件非空与前 16 字节 SQLite Header（`SQLite format 3\0`）合法性后，再通过原子重命名替换正式文件，防止进程异常中断导致数据库文件损坏。
- **简繁转换异步加载闪烁消除**：
  - 将离线词典超时卡死恢复阈值从 `30_000L` 缩短至 `5_000L`，并在 `MainActivity.onCreate` 生命周期尽早预热 `ChineseConverter` 字典。
  - `LocalChineseScript` 接入 `neverEqualPolicy()` 与 `StateFlow<Boolean>` 就绪状态流，词典在后台完全载入后自动驱动全应用 Compose 界面即时重组，解决冷启动繁体模式首屏停留简体的闪烁问题。
- **阅读历史持久化与防抖控制**：
  - `UserDataHelper` 升级为 v2，新增 `reading_history` 独立表用于分章节记录用户段落进度。
  - `ReaderViewModel` 引入 500ms Debounce 防抖写入机制，结合页面退出 `DisposableEffect(onDispose)` 即时落盘，避免阅读器高速滑动时对磁盘造成高频 IO 争用。
  - 严格审查所有 DAO 查询路径，确保所有 `rawQuery` / `query` 调用均由 `cursor.use { ... }` 安全自动释放。
- **SQLite 通配符与 FTS 特殊字符安全转义**：
  - 在 DAO 层对所有 LIKE 条件查询的输入参数转义 `%`、`_` 与 `\`，并统一搭配 `ESCAPE '\'` 语法。
  - 增加 `sanitizeFtsQuery` 工具函数，过滤特殊符号并安全以双引号包裹词段，防范语法异常崩溃。

### 2. Compose UI 与交互层优化 (UI & Navigation)

- **富文本 OAM 标注边界安全性加固**：
  - 在 `OpusOneTagParser` 和 `ReaderScreen` 构建 `AnnotatedString`、挂载 `addStyle`、`addStringAnnotation` 及 `addLink` 时，对所有 `[start, end)` 字符区间强制实行 `.coerceIn(0, text.length)` 校验与 `start < end` 断言，彻底解决因简繁转换长度差异导致的 `IndexOutOfBoundsException`。
- **搜索输入协程竞态与旧结果覆盖修复**：
  - 在 `EntityWikiScreen` 中引入 250ms 防抖，并在每次输入变动时显式取消前一次在途的查询 Job，杜绝快速输入时慢查询晚于新查询返回覆盖结果的竞态问题。
- **人物图谱 Canvas 缩放约束与除零保护**：
  - 手势缩放与全貌自适应比例统一施加 `scale.coerceIn(0.3f, 4.0f)` 约束，防止缩放无界。
  - 视口尺寸、节点间距与中心点归一化处增加 `> 1e-4f` 非零保护，规避重叠节点产生的 `NaN` 坐标导致的 Canvas 白屏。
- **跨模块跳转段落精准定位与返回栈优化**：
  - `ReaderScreen` 中改由 `LaunchedEffect(chapterId, targetSectionPn, uiState.sections)` 驱动，确保从百科、成语、战役跳转至阅读器指定卷段时准确触发 `scrollToItem`。
  - `Navigation.kt` 为阅读器跳转引入 `launchSingleTop` 语义，若栈顶已是阅读器则直接替换当前栈顶，避免返回栈冗余堆叠。

### 3. Python ETL 数据管线与 CI 工作流 (ETL & CI)

- **CI 强制版本一致性断言**：
  - `OpusOneDatabaseHelper` 明确暴露 `const val ASSET_DB_VERSION = 13`。
  - `etl/verify_db.py` 增加正则动态提取客户端代码版本常量，并在 CI 流程中强制断言其与 `assets/opusone.db` 内的 `PRAGMA user_version` 完全一致。
- **启用建库外键约束并严查孤立引用**：
  - `etl/build_from_corpus.py` 在连接初始化后强制开启 `PRAGMA foreign_keys = ON;`。
  - `etl/verify_corpus.py` 新增对 14,595 条三家注与 35,388 条实体出处记录的 `chapter_id` 和段落锚点断言校验。
- **Web 端跨仓同步工作流容错**：
  - `.github/workflows/dispatch-web-sync.yml` 添加 `if: github.repository == 'wg2038/cicada930'` 仓库归属过滤，并在发送 `repository_dispatch` 前检测 `TOKEN` 环境变量非空，避免 Fork 仓库或 Secret 未配置时产生误报失败。

---

## [v0.1] - 2026-10-12

- 初始公开发布版本。
- 支持《史记》全本 130 篇沉浸式精读，三家注疏（集解、索隐、正义）行内对齐。
- 支持十表网格解析平滑滚动与行首冻结。
- 内置先秦两汉人物关系图谱、统一编年长河、成语典故与经典战役。
