# 0.15.0 本地预览版验证记录

日期：2026-11-11。实现分支 `codex/visual-map-dungeon-editor`；桌面源码基线 `9d03b91`，本地构建的实现提交 `50170b9`，开发在已有隔离工作树内完成。没有合并、推送或外部发布。操作和支持矩阵见 [game-effect-preview.md](game-effect-preview.md)。

## 实际结果

已交付可运行的离线预览：ACT1 EQU/SKL/STK 默认进入中央预览，支持五职业身体和穿戴层组合、当前物品替换、同包搭配、称号、动作、独立动画时钟及播放控制。属性读取当前本地化配置，不伪造实例强化和随机品级。未保存的顶层及已打开依赖快照参与预览，代码/分屏共用原文档。普通和蓄力激光炮采用已检查的 ACT1 原生资源与几何规则。

以下验证层次分别成立，不能互相替代：

| 层次 | 本轮证据 | 结论 |
| --- | --- | --- |
| 配置解析 | 五职业 × 待机/移动/普攻/跳跃，制服、吞日者、护甲、称号、普通/蓄力激光、杰克爆弹；29 个样本 | 装配及引用可解析；基础动作和制服无诊断 |
| 离线绘制 | 每样本 0/150/500/1050/1200ms，共 145 PNG；像素/时序回归 | 实际 IMG 可绘制，射程和延迟修改参与配方；缺失部件局部诊断 |
| 原生代码对照 | 同版本 EXE 的激光状态、枪体、生成点、平铺及蓄力分支；杰克爆弹 23008；吞日者 23008/48030 工厂映射 | 已确认资源关系和文档所列参数；没有把单个动画片段标为完整战斗流程 |
| 实际桌面 | 初始化 IntelliJ 工作台打开真实 PVF/NPK，1264×811 和 1024×720 截图 | EQU/SKL/STK、模式切换、循环/seek/朝向、等级、顶层/LAY 修改撤销、快速切标签、重建资源索引通过；纯预览未写脚本草稿 |
| 实拍/逐像素动态对照 | 用户四张静态参考图；本轮没有新游戏录屏 | **未完成全流程实拍验收**；称号锚点仍为明确标注的近似对齐 |

截图四项样本均定位到原包：激光炮 `skill/gunner/laserrifle.skl`、称号 `joytron_handlingfeeling.equ`、护甲 `amr_ha_northmyre1.equ`（诺斯玛尔 : 戾龙重甲胸铠）、吞日者 `n_cann_suneater.equ`。护甲未定义独立穿戴动画，预览说明该原因并显示参考角色。

## 可复查输出

以下路径相对于本工作树，均为本机忽略的 build 输出，不含入库的游戏素材：

- `build/game-preview-reviewed-final.log`：Gradle `check -PwithIntellij=true :pvf-product:test :pvf-product:assembleProduct` 成功。当前 JVM XML 共 321 项、0 失败/错误/跳过；审阅前还运行过全量 `--rerun-tasks`（317 项）。
- `build/game-preview-python-reviewed.log`：14 项 Python 验证成功。
- `build/game-preview-audit-reviewed/report.txt`：29 项实际资源审计、时刻和性能记录；对应 PNG 在同目录。
- `build/product-checks/20261111-003850-d2b4ac75/smoke-results`：修正后的完整新增预览工作台验证、七张实际 UI 截图。
- `build/product-checks/20261111-003945-6d36313f/smoke-results`：修正后原有完整桌面烟测成功，覆盖原编辑、地图/副本、草稿、导出及标签工作台。
- `build/game-native-evidence`：私有 IDB 副本及原生依据；源码文档仅记录地址/规则索引。
- `build/game-preview-resource-integrity.txt`：425 个 NPK 全量 SHA-256 与验证阶段基线一致，PVF 摘要一致。
- `build/game-preview-package-integrity.txt`：便携 ZIP 全部 CRC 通过，69 个 JAR 与已验证的产品目录逐一一致，版本/启动器均为 0.15.0，无 PVF/NPK 游戏数据。

便携包：`pvf-product/build/distributions/PVFStudio-0.15.0-windows-x64-portable.zip`（244287538 字节）。SHA-256：`871517deacd342a9c362f0111812bd91a3fccc1e9b5be268e205f3161b8a3417`。可直接运行 `pvf-product/build/product/PVFStudio-0.15.0/PVFStudio.exe`；压缩包内顶级目录为 `PVFStudio`。该构建未公开发布。

ACT1 输入：PVF SHA-256 `288d58aace8ed4724a0a94aaa4cd0957b1d52c633771132ede2f96363c1b506f`；425 NPK、12103 IMG。完整 NPK 基线在验证阶段开始时采集，不宣称它覆盖此前每一次读操作。源 EXE 与原有 IDB未修改。

## 性能样本和边界

本机 AMD Ryzen 9 5900XT、JDK 25，命令行同一进程、资源扫描后逐项构建：首个剑士待机模型 91ms；后续五职业动作 14–48ms；普通激光 90ms、蓄力 51ms。每模型连续采样 120 帧，激光采样 P95 为 217µs / 129µs。它们是后台模型/绘制准备时间，不是窗口端到端延迟或显示帧率承诺。Java 堆各池峰值用量之和约 441MiB，非工作台进程峰值，亦非 RSS。

本实现每个模型最多 256 轨、256 条诊断，准备像素缓存最多 128 项/32MiB；单脚本最多 800 万字符/25 万 token。整次模型读取最多 4096 次/32Mi 字符；每次角色装配最多 1024 次读取/16Mi 字符、256 个展开节点/16Mi 字符。重复 include 仍合法但消耗同一总量预算。本地化最多 2048 次引用、单字段 64Ki 字符、合计 256Ki 字符，超限截断并诊断。沿用底层资源缓存。旧请求只失效结果，不中断共享资源 I/O；重复载入受单后台工作线程约束。

## 独立审阅及修正

独立只读全变更审阅没有发现 Critical 和单独的 Minor，发现三项 Important。已在一次修正中加入四个测试并分别观察失败，再修复并通过整套 321 项测试：

- 后台绘帧期间保留最后一次 seek/逐帧/朝向请求，不让旧时间覆盖当前滑块；测试 `pausedSeekStepAndFacingKeepTheLatestRequestDuringBackgroundRendering`。
- 普通非 LOOP 动作和效果在勾选循环时按各轨自身时钟重播，保留激光组合及称号时钟规则；测试 `loopRepeatsNonLoopingTracksOnTheirOwnClocks`。
- 给无环高扇出 include 和本地化增加总读取、节点、字符、引用及诊断预算；测试 `acyclicIncludeFanoutIsBoundedAndKeepsTheValidBody`、`acyclicLocalizedFanoutCannotExpandIntoUnboundedHtml`。

失败依据 `build/game-preview-review-red.log`，通过依据 `build/game-preview-review-green.log` 和 `build/game-preview-reviewed-final.log`。没有遗留的审阅 Minor。

## 明确未完成项

- 称号锚点尚未做原生逐像素校准；当前按角色可见边界上方对齐。
- 杰克爆弹和吞日者提供初始视觉、关联动画及原始触发条件，完整施放、飞行、命中/落地、概率和目标选择未执行。
- 激光按基础延迟和射程组合，蓄力按被动 Lv.1；未计算角色攻速、手动按键释放、伤害和命中。其几何/引用有原生代码依据，全动态实拍对照仍待完成。
- 其他技能仅显示已识别的显式资源和缺口。未验证版本继续原有预览，不启用 ACT1 配方。

后续校准应使用相同 EXE/PVF/NPK 在游戏内采集标题与技能的逐阶段录像，再比对锚点、帧时长和结束状态。此次本地包不将这些开放项称为已通过的游戏动态验收。

## 实施取舍记录

- 用户已确认方案和布局，直接在现有隔离分支执行；未再要求重复选择执行方式。若判断不符，局部实现仍可调整，无外部发布影响。
- Windows 下用 PowerShell 维护同一计划的执行记录，没有运行 Bash 记账脚本；代价是缺少脚本自动生成的 brief 元数据。
- 渲染核心通过后先接桌面，再继续核对原生时序，便于用实际窗口观察；代价是中间接口可能返工，最终已由整体验证覆盖。
- 本地交付保留分支和工作树，未合并或推送。原主目录既有的 57 个产品源码删除保持原样。
- 审阅未评判称号逐像素及全动态实拍：保留为公开未完成项，当前近似对齐可用于本地预览；代价是尚不能承诺与游戏锚点一致。
- 审阅未评判杰克爆弹/吞日者完整战斗流程及激光运行时修正：按已明确的首批部分视觉范围交付，不宣称战斗模拟验收；代价是这些完整流程仍需后续实现和校准。
