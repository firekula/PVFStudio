# CLI 与开发验证

本页保留 CLI 操作、真实 PVF 验证方法及历史验证记录。软件安装和日常使用见 [README](../README.md)，逐版功能见 [Changelog](../CHANGELOG.md)。

先在仓库根目录设置 JAVA_HOME 并执行 `.\gradlew.bat check :pvf-cli:installDist`，再将 `$cli` 设置为 `.\pvf-cli\build\install\pvf-studio\bin\pvf-studio.bat`。以下命令均在仓库根目录执行。

## 历史验证状态


0.11.0 通过 130 项 JVM 测试、14 项独立 Python 测试和完整产品检查。副本预览覆盖 43 个真实 DGN、49 套布局、178 组起点/Boss 组合及 242 个房间开闭状态；产品界面验证布局与 Boss 房切换、原生开闭门动画、清房状态保持相机、文件内和全局搜索、客户端视口边界及原生窗口关闭的保存/放弃/取消。发行 ZIP 完成全部 CRC 和内容检查，安装器与便携包提供 SHA-256。

0.10.0 通过 110 项 JVM 测试和 14 项独立 Python 测试。完整产品检查验证资源与场景预览、粒子/碰撞、多部件怪物、标签操作、会话重开、菜单与最近文件，以及 48,326 条目 PVF 的编辑、导出、备份和恢复；普通 EXE 窗口验证了菜单及 Ctrl+O/Ctrl+R。安装器、便携 ZIP 和 SHA-256 文件随同发布，ZIP 的全部 CRC 与打包内容已检查。

0.6.0～0.9.0 分别通过 83、98、102、106 项 JVM 测试，并持续通过 14 项 Python 测试。0.6.0 的全部 289 个怪物定义完成四个时间点的绘制检查，其中 117 个为多部件；0.7.0 覆盖地图引用的 17 种粒子工厂。0.8.0 验证标签关闭范围与保存隔离；0.9.0 验证光标/纯滚动自动保存、立即关闭的最终写入、隐藏标签跨两次重开及不同 PVF 隔离。对应证据见上述专题文档。

0.5.0 通过 78 项 JVM 测试和 14 项 Python 测试。全部实际地图实体记录解析通过；地图引用的 576 种实体中，538 种成功解析并解码待机资源，26 种无基础动画，12 种原始索引缺失或被注释，后两类保留诊断标记。森林样本的 74 个实体、城镇 NPC、特殊容器、装备叠层、NPC 翻转、地面深度、独立待机时钟和未保存定义覆盖均有验证；产品界面还检查条件怪物与对象标记开关。

0.4.0 通过 66 项 JVM 测试和 14 项 Python 测试。真实样本的 1,092 个 MAP、619 个 TIL 全部通过解析；森林、城镇、扩展地图及独立 TIL 完成组合与碰撞层渲染。新增测试覆盖扩展地块换行、层级与 Z 坐标、通行类型、重复背景与视差、不同周期的独立相位、暂停/倍速/定位和长时间播放。产品界面检查包含场景播放、碰撞开关、跨分钟时间轴与未保存 TIL 覆盖。

0.3.0 通过 51 项 JVM 测试和 14 项 Python 测试。真实 ACT1 目录的 425 个 NPK 中索引到 12,103 个有效 IMG，30,294 个 ANI 均通过解析；代表样本的 14 个动画/图标帧完成解码与渲染。产品实际界面通过动画播放、逐帧、图标、ANI 引用、未保存覆盖、丢失资源、过期回调和目录保存检查；PVF 编辑、导出、备份与恢复的原有冒烟也通过。这些结果覆盖所述样本及功能，不代表所有动画效果与客户端画面一致。

0.2.1 的产品身份、核心类加载器、基础设置服务及无额外插件检查通过；vanilla JBR 产品通过真实 EditorImpl 的撤销/重做、Diff、行尾、48,326 条目 PVF 导出、备份及恢复冒烟。文件树通过 12 组实际平台绘制检查，覆盖多层展开、切换选择、折叠再展开、取消选择以及焦点变化；便携 ZIP 的全部 CRC 与两个发行文件的 SHA-256 校验通过。

0.1.2 的 51 项自动测试通过：27 项核心、10 项工作区和 14 项独立 Python 验证。新增工作区检查覆盖首次备份、连续版本恢复、备份失败、原包及硬链接别名保护、导出失败后保留旧目标和已完成备份。

0.1.2 的真实编辑器冒烟还验证了 48,326 个条目的原包备份、再次导出的旧版本备份与恢复；备份内容与原包或上一版导出逐字节一致，原包 SHA-256 未变。

0.1.1 已通过 46 项自动测试：27 项核心、5 项工作区、10 项独立 Python 校验器和 4 项隔离准备测试。两份原始 PVF 共 96,669 个条目的 CRC 关系均已核验，无修改重建、修改导出和草稿跨进程恢复也通过全量对照。

两份指定修改导出包已分别在独立 ACT1 客户端和服务端副本中进入游戏，物品 5642 显示了 `_PVF验证` 和 `_外部包测试` 后缀。真实界面中的编辑、保存、撤销/重做、差异比较及导出也已验证。包摘要、截图与原环境复核见 [客户端验证记录](client-validation-2026-10-24.md)；这些结果限于所记录的客户端与导出包。

0.1.0 的历史检查及其更正保留在 [初轮验证记录](validation-2026-10-24.md)，不能用其旧 reader 回读结果代替修复后的客户端验收。

## 常用操作

示例采用现有仓库样本，但不会写入该样本：

```powershell
$pvf = 'G:\Game\DNF_ACT1\DFLegacy\DNF2008\Script.pvf'
$entry = 'stackable/recipe/nrcp_tra_rudechantre.stk'
New-Item -ItemType Directory -Path '.\build\examples' -Force | Out-Null

& $cli info $pvf
& $cli list $pvf 'stackable/recipe/'
& $cli cat $pvf $entry GBK
& $cli extract $pvf $entry '.\build\examples\item.stk'
```

`info` 显示条目数、大小、SHA-256 和 `open_ms`。当前打开流程包含完整摘要与快照一致性校验，`open_ms` 不等于只解析目录的时间。`cat` 默认编码为 GBK，也可明确传入 `MS949` 或 `UTF-8`；不能无损解码时会失败。

用支持原编码的编辑器修改提取的文件，保留所需换行。CLI 的替换操作使用本地文件的原始字节，不自动转换编码：

```powershell
# 无修改重建，验证目录与数据布局的保真性。
& $cli export $pvf '.\build\examples\roundtrip.pvf'

# 将本地文件替换到既有虚拟路径；可重复传入 --replace 修改多个条目。
& $cli export $pvf '.\build\examples\edited.pvf' `
  --replace $entry '.\build\examples\item.stk'
```

导出和提取的目标必须是新文件；存在同名文件时拒绝覆盖。重复运行示例时选择新的输出文件名。导出先写同目录临时文件，校验成功后提交目标文件。未修改条目的明文及补齐字节保持原样；若其物理偏移改变，则重新计算 CRC/key 并重加密，无关间隙和尾部数据保持原样。

需要多次编辑和恢复草稿时，使用持久工作区：

```powershell
$work = '.\build\examples\workspace'
& $cli stage $pvf $work $entry '.\build\examples\item.stk'
& $cli status $pvf $work
& $cli workspace-export $pvf $work '.\build\examples\workspace-edited.pvf'

# 撤销一个条目的草稿；导出不会自动清除草稿。
& $cli revert $pvf $work $entry
```

工作区绑定原包完整 SHA-256，并对草稿做内容摘要校验。关闭进程后可继续使用同一目录恢复；一个工作区同一时间只允许一个持有者。源包变化或草稿损坏时停止操作，避免把修改错误地套到另一份包上。

完整参数以 `--help` 和 [CLI 源码](../pvf-cli/src/main/java/studio/pvf/cli/Main.java) 为准。正常退出码为 `0`，参数错误为 `2`，读写或校验失败为 `3`。

## 真实 PVF 验证

先构建 CLI，再使用 Python 3.11 或更新版本执行验证脚本。新版独立 Python 对照器校验目录与条目的带初值 CRC32、全部明文及补齐、条件性密文保留和布局，并测试无修改重建、修改中文物品名和列表注释、跨进程恢复草稿及导出。每次生成独立的 `build/validation/<运行标识>/`，报告与生成的 PVF 均留在该目录。

如需同时验证服务端现有 C# reader，先用 .NET 10 SDK 构建测试宿主。它只引用同级 `DFLegacy` 仓库中的原 reader 源码，不修改或启动服务端：

```powershell
dotnet build .\validation\server-reader\ServerReader.csproj `
  -c Release -o .\build\validation-tools\server-reader

python .\validation\run_real_pvf.py `
  --input 'G:\Game\DNF_ACT1\DFLegacy\DNF2008\Script.pvf' `
  --input 'G:\Game\DNF_ACT1\DF\Script.pvf' `
  --java "$env:JAVA_HOME\bin\java.exe" `
  --server-reader '.\build\validation-tools\server-reader\ServerReader.dll'
```

`--input` 可重复传入不同样本；`--server-reader` 可省略，此时只执行 JVM 导出与独立 Python 对照。C# 宿主项目默认从同级 `DFLegacy` 寻找源码，其他布局可在 `dotnet build` 时指定 `-p:DFLegacyRoot=<服务端仓库绝对路径>`。

以脚本实际生成的 `summary.json` 和逐项报告为验证结果。原 C# reader 没有检查原生客户端所需的 CRC 关系；即使该 reader 接受导出包，也不能代替带初值 CRC 校验或客户端运行验证。上述离线脚本不替换运行现场的 PVF，不启动游戏客户端。隔离客户端测试的约束见 [隔离说明](client-isolation-notes.md)，实际加载结果见 [客户端验证记录](client-validation-2026-10-24.md)。
