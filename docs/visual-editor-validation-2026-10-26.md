# 可视化编辑验证记录（2026-10-26）

版本：0.14.0 本地开发版。数据文件和截图保留于隔离工作树的 build/，不随源码或程序发行。

| 样本 | 容器 | 校验条目 | 原生 MAP / DGN 回读 | 深渊修改 |
|---|---|---:|---|---|
| act1 | ACT1 | 48,326 | 通过 | 通过 |
| single | LEGACY | 4,263 | 通过 | 样本无配置 |
| 70s2 | UUID | 369,711 | 通过 | 通过 |
| 86 | UUID | 666,933 | 通过 | 通过 |
| 100 | UUID | 929,093 | 通过 | 通过 |

所有样本均执行原生字段编辑、草稿重开、导出、导出后回读；独立 Python 容器解析核对所有修改及未修改载荷、目录元数据、原有间隙和尾部数据，并核对原包 SHA-256。B0 D0 文件另以独立 token / 字符串表解码与预期源码核对。校验产物位于 `build/visual-audit-{act1,single,70s2,86,100}-20261026/independent-report.json`。

原生窗口证据：

- ACT1 原有编辑 / 标签 / 搜索 / 会话 / 菜单 / 资源 / 保存 / 导出 / 备份流程：`build/product-checks/20261026-183714-bb13f026`。
- 86 MAP 画布拖动、Z 保留、撤销、原生资源添加、保存：`build/product-checks/20261026-183302-2764e430`。
- 100 DGN 普通 / 深渊房实际资源、Boss 独立、1264 / 1024 窗口、克隆整体撤销：`build/product-checks/20261026-183302-2e14c390`。
- ACT1 扩展名重命名、Document / Editor 生命周期、关闭重开后整体撤销 / 重做、重命名来源后返回副本：`build/product-checks/20261026-183525-8fc21419`。
- 86 DGN 同类原生窗口操作：`build/product-checks/20261026-183526-0f1c25b1`。
- 86 既有 MAP 文件 / ANI 切换、相机与像素一致、文件树变更、重命名、重开和导出回归：`build/product-checks/20261026-183526-e229439c`。

这些结果不构成 100 级游戏内加载或战斗验证。现有缩减平台还会记录已知的 application-info / consentMap 诊断噪声；本报告依据明确检查项及 smoke 成功标记，不声称日志完全无错误。

复现入口：`validation/VisualSceneEditAudit.java`、`validation/verify_visual_scene.py`、`tools/check_product.py --visual-map / --visual-dungeon / --visual-scene / --map-files`。输入须是本地真实样本，输出须为新目录。

最终打包：`tools/build-release.ps1 -ZipOnly` 成功，257 项 JVM 测试及 14 项 Python 测试零失败。便携 ZIP 解压到新目录后，实际 `PVFStudio.exe --check` 返回 PASS；解压产物的 86 MAP 原生操作再次通过：`build/product-checks/20261026-184038-438f77d2`。程序含 307 个模块、66 个平台 JAR，无捆绑插件和游戏数据。

## 独立整分支审阅与修复

独立审阅范围 `b47bb46..eef44db`，只读完成；无 Critical，提出 7 项 Important 与 1 项 Minor。7 项 Important 均在一次修复阶段补回归并完成失败→通过：

1. 预览与编辑共用原生怪物边界，涵盖带特性精英、超级精英及 NPC 怪物，另验证解析来源的命中和拖动。
2. 渲染结果绑定精确 MAP 路径和源码，旧画面不能对新记录序号执行点击写入。
3. 克隆整体撤销核对所有当前关联文档，包含克隆后首次打开的 map.lst；后续编辑先拒绝，不会产生部分写入。原生晚开索引 / 重开副本撤销回归通过：`build/product-checks/20261026-185653-b58cad66`。
4. 多个 monster 区段新增时，怪物与 AI 辅助项仍逐项对应。
5. 不完整 TIL 区段阻止所有结构修改，不再丢弃未知 token 后面的引用。
6. 复制首套 maze 时，显式带上其前置作用域中生效的深渊 MAP / 坐标 / 排除字段。
7. 非首个 Boss 候选的预览、打开 MAP 和克隆绑定使用同一角色选择。

修复后 265/265 JVM、14/14 Python 测试通过。五份样本再次执行编辑 / 重开 / 导出，`build/visual-audit-final-{act1,single,70s2,86,100}-20261026/edited.pvf` 与此前独立验证产物的 SHA-256 全部一致，因此当前实现产物亦覆盖相同的 2,018,326 条目独立校验。

暂缓的 Minor：将 MAP / DGN 重命名为普通扩展名后，保留的设计 / 分屏按钮仍可能报错；使用代码模式可继续，改回 MAP / DGN 扩展名可恢复设计器。该问题未进入本次重要问题修复阶段。

审阅明确未评估游戏内加载、战斗与服务器 / WDM 入口，也未将已知 consentMap / application-info 日志噪声算作新回归。前者的代价是文件成功不代表游戏入口成功；后者的代价是启动日志并非零错误。

最终修复版便携包已重新打包并从新目录解压。实际 EXE 启动检查通过；解压产物原生窗口检查全部通过：86 MAP `build/product-checks/20261026-190201-13228e9f`、100 DGN `20261026-190201-64d1d8d2`、ACT1 晚开索引 / 重开副本撤销与来源重命名 `20261026-190201-6a227922`。最终 ZIP SHA-256：`2a7a001c6d98068a7b044593a009870e6d9d135a371685a1d0ec66ec9257656b`。

实施取舍（保留代价）：

- 本聊天关联的是另一个仓库，使用已授权的 PVFStudio Git worktree；代价是它不作为 Codex 管理的工作树附件显示。位置为 `G:/Game/DNF_ACT1/PVFStudio/.worktrees/map-dungeon-editor`，分支 `codex/visual-map-dungeon-editor`。
- 没有 escape hell 支持证据时保守禁用该写入；代价是某些实际支持的包仍需要明确的能力声明。
- 整体撤销保留没有改动旧索引的追加字符串表；代价是可能留下无害的未使用字符串，避免破坏其他草稿。
- 100 级仅声明文件 / 编辑器验证；代价是没有原生游戏内加载、AI / 战斗或服务器 / WDM 入口成功证据。
- 保留已确认的平台日志基线分类；代价是 consentMap / application-info 日志噪声仍在，不能声称启动日志零错误。
