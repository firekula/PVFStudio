# 2026-10-24 核心验证记录

本记录针对 PVF 核心、持久化工作区和 CLI。IntelliJ 界面的构建与运行验证单独记录；本轮没有更换运行现场的 PVF 或启动游戏客户端。

## 环境与方法

- Windows，本地 JDK 25.0.4 构建，Java 源码目标 17；本机 JDK 17.0.16 的 CLI `info` 运行检查通过。
- Gradle 9.5.0；`gradlew check :pvf-cli:installDist` 成功，22 项核心测试与 5 项工作区测试全部通过。
- `python -B -m unittest discover -s validation -p 'test_*.py'`：7 项校验器自测全部通过。
- 真实导出由 `validation/run_real_pvf.py` 调用 CLI 完成，JVM 设置 `-Xmx256m`。这表示验证在该堆上限下成功，不代表进程实际峰值内存测量。
- Python 校验器有独立的位移解密实现，不导入 JVM 或服务端实现；逐项对比全部明文、未修改密文及补齐、路径原字节、fileNumber、key、目录填充、间隙与尾部数据。
- 另建只读 C# 验证宿主，链接 `../DFLegacy/src/DFLegacy.Protocol/PvfArchiveReader.cs` 原源码，对每个条目进行回读。所用 reader 源文件 SHA-256：`3325ae84da448af3c09b4d6b2bb969bcfed262dc653f24fa998e59af0b496b0b`。
- 运行结果保存在 `build/validation/20261024T063846Z-d7877ded/summary.json` 及其子目录；时间戳为 UTC，对应北京时间 2026-10-24 14:38:46 开始。

## 真实样本

| 项目 | 仓库样本 `../DFLegacy/DNF2008/Script.pvf` | 客户端目录样本 `../DF/Script.pvf` |
| --- | --- | --- |
| 原包字节数 | 66,392,567 | 65,951,131 |
| 条目数 | 48,326 | 48,343 |
| 原包 SHA-256 | `bc397d1463f1f0f749bbd169400b640d0c8a5d1f6e6e97cfda797099f995ac27` | `5fcd4b94dfb081294701f7288345fff6ca5586d780fcc7130e4614c237eb6ad1` |
| 无修改重新构建 | 与原文件逐字节相同 | 与原文件逐字节相同 |
| 修改导出 | 预期 2 项变化；其余 48,324 项密文原样保留 | 预期 2 项变化；其余 48,341 项密文原样保留 |
| 原 C# reader 全量回读 | 无修改与修改导出均通过 48,326 项 | 无修改与修改导出均通过 48,343 项 |
| 草稿跨进程恢复后导出 | 通过全部条目检查，预期 1 项变化 | 通过全部条目检查，预期 1 项变化 |
| 全流程后原包 SHA-256 | 未改变 | 未改变 |

修改操作是对 `stackable/recipe/nrcp_tra_rudechantre.stk` 的 CP936 中文 `[name]` 增加 `_PVF验证`，并在 `creature/script/creature.lst` 末尾增加 ASCII 注释。测试输出仅用于验证，不应作为正式游戏配置发布。

修改后导出 SHA-256：

- 仓库样本：`c0a28bd47469ebf3a210d805cca84088d7ef30a999e1ebd874c4d930624376e6`。
- 客户端目录样本：`bd326b32914aa21c6e68759c9ec315f55528636f2b5e7b201c837f0d6499e8e6`。

单次导出内部计时（包含导出、回读检查及源快照复核；不包含进程启动和 Python/C# 外部检查）：仓库样本无修改 2.250 秒、修改 2.499 秒；客户端目录样本无修改 1.796 秒、修改 1.838 秒。这些是本机单次运行数据，未进行冷缓存控制或统计基准。

## 已覆盖边界与剩余验证

合成夹具覆盖不同目录/物理顺序、非零补齐、间隙和尾部数据、同长/变长/变短/清空及四字节边界修改、多个空条目共享偏移、二次无修改重建、损坏输入、源包变化与禁止覆盖。工作区测试覆盖重启恢复、草稿损坏、独占锁、不同源包拒绝和严格编码转换。

以上证明当前 ACT1 格式与现有 reader 的读写兼容性。未证明官方客户端会接受修改后的包，也未验证其他 PVF 格式、新增/删除/重命名条目或客户端的额外完整性校验。

## IntelliJ Community 运行时集成

正式验证使用官方 IntelliJ IDEA Community 2025.2.6.3（`productCode=IC`、build `252.28539.97`）及其自带 JBR 21。原始 Windows ZIP 的 SHA-256 已与官方校验文件核对：`ee3bb2bf315298da0758f5d72ee97a9c5e017f22d129c12db80b69a392f4673e`。本机另有名称未更新的统一版 IDEA 目录，它仅用于早期开发验证，没有复制进独立发行目录。

`intellij-workbench` 针对正式 IC SDK 编译通过，真实编辑器冒烟报告位于 `build/validation/intellij-smoke-ic/report.properties`：

- 创建的编辑器为 `com.intellij.openapi.editor.impl.EditorImpl`，在平台命令与写操作中修改真实 IntelliJ Document。
- 将编辑结果按 CRLF 恢复，持久化草稿并导出新 PVF；均一 CRLF/LF/CR 与混合换行只读检查通过。
- 独立 Python 验证导出包全部 48,326 项，预期仅 `creature/script/creature.lst` 一项变化，另 48,325 项密文原样保留；目录与布局校验通过。
- 同级服务端的原 C# reader 对该导出包全量回读通过。

该集成冒烟不等同于图形界面的按键和文件选择器交互验收。独立装配还需要显式通过 `plugin.path` 加载产品插件；将插件放进原厂 `runtime/plugins` 不能自动加入平台预生成的 bundled-plugin 清单。发行目录保留原厂运行时，将 PVF 插件放在自己的 `plugins/` 下。

## 独立程序手动交互验收

使用 computer-use 控制实际独立目录启动的窗口，自带正式 IC/JBR，通过源包的独立测试副本完成以下操作：

1. 读取 48,326 条目，按路径搜索并打开 `stackable/recipe/nrcp_tra_rudechantre.stk`；GBK 中文和语法高亮显示正常。
2. 在原文末尾输入 `// 界面保真验证` 并保留 CRLF，编辑器有未保存标记；Ctrl+S 后标记消失，工作区显示一份草稿。
3. 通过实际文件选择器导出 `build/validation/ui-interactive/interactive-export.pvf`，界面报告生成成功。
4. 该手工导出经独立 Python 检查全部 48,326 条目，只有预期一项变化，48,325 项未修改密文原样保留，源副本 SHA-256 未变；原 C# reader 也全量回读通过。报告为 `build/validation/ui-interactive/independent-report.json`。
5. 正常关闭并重新启动独立程序，已保存的中文草稿成功恢复。
6. 修复自有窗口的项目上下文后，重新输入 `// UNDO_CHECK`，Ctrl+Z 能撤销，Ctrl+Shift+Z 能重做，再次撤销能精确回到已保存内容。
7. 自有差异窗口正常显示原始 PVF 与当前草稿的左右比较，预期中文注释为唯一一处差异；关闭差异窗口后主编辑器保持可用。

验收中发现并修复了普通 IntelliJ 窗口设施对项目/IdeFrame 的假设：编辑器使用平台 defaultProject 记录撤销，但不打开普通项目；差异组件嵌入自有 JDialog，并显式释放。最终修复另经真实 IC 编辑器集成断言验证 Undo/Redo、DiffRequestPanel 与行尾处理。
