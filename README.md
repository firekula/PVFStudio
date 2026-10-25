# PVF Studio

基于 IntelliJ 平台的独立 PVF 编辑器，面向 DNF ACT1 `DNF_SCRIPT_PACK` 配置包，提供目录浏览、脚本编辑、草稿恢复、差异比较和保真导出。核心与 CLI 可独立使用。

当前发布版本为 **[v0.10.0](https://github.com/firekula/PVFStudio/releases/tag/v0.10.0)**，支持 NPK 图片与动画、MAP/TIL 完整场景、实体和粒子预览，以及菜单、最近文件和标签会话恢复。产品基于 IntelliJ Platform，使用自己的产品描述、入口和主模块，随包提供按依赖选取的平台库与无 JCEF 的 JBR 21。无需安装 IDEA，也不再加载单独的 PVF 插件。版本变化见 [Changelog](CHANGELOG.md)。

## 已有功能

- 顶部菜单栏按文件、编辑、选择、查看、转到、帮助组织；最近打开的 15 个 PVF 持久保存，重开恢复标签和编辑位置。
- 目录树按需展开，按虚拟路径搜索，按需读取条目内容。
- 文件树按文件夹、脚本、列表、文本、配置及资源类型显示矢量图标；仅选中行显示选中高亮，其余行与空白区域背景一致。
- 使用 IntelliJ 文本编辑器，支持脚本着色、撤销/重做及原文差异比较。
- 选择客户端或 ImagePacks2 目录，按配置中的 IMG 路径、帧号渲染资源；支持 ANI 播放、暂停、逐帧、时间轴、倍速、循环及缩放。
- 图标、场景图片及 ANI 引用共用资源预览面板；编辑尚未保存的配置或被引用 ANI 时自动刷新，支持装备外观路径的数字占位符。
- MAP 组合基础/扩展 TIL、分层 ANI 及重复背景；TIL 通行格可手动叠加，区分禁行与仅浮空可通行，并显示城镇移动区域。
- 地图动画使用持续前进的公共时钟，各自按 DELAY/LOOP 独立播放；支持暂停、定位、倍速、拖动平移、滚轮缩放和滚动视差。
- NPC、怪物和被动对象按 MAP 中的位置播放待机，支持 NPC 朝向、原生多部位装配、装备层级、对象标记及条件怪物开关；无待机或缺资源时保留位置与诊断。
- 地图粒子和独立 PTL 可直接预览，支持 ANI、IMG 和被动对象粒子、运动/寿命/透明度及稳定时间采样。
- 碰撞叠加层同时标记 TIL 通行格、被动物品占用范围与城镇蓝框可移动区域。
- 标签固定单行顺序，提供右键菜单与 VS Code 快捷键；同一 PVF 重开时恢复标签、排列、编码、光标和横纵滚动位置。
- 手动选择 GBK/CP936、MS949/CP949 或 UTF-8，严格转换编码并保持支持的原始换行。
- Ctrl+S 保存独立草稿，重新打开同一原包时恢复已保存的修改。
- 导出到新 PVF，校验源包一致性、目录与全部条目 CRC，并回读检查内容和布局。
- 导出默认自动备份旧版本，支持备份后更新已有导出文件，保留每个版本以便恢复。
- CLI 支持查询、提取、字节替换、草稿管理及无修改重建。

这是独立 Git 项目，与 `DFLegacy` 服务端仓库分开。编辑器不依赖运行中的服务端，构建与测试输出默认写入本项目的 `build/`。

## 近期迭代

0.10.0 是自 0.2.1 后的集中发布，包含以下开发迭代；逐项变化见 [Changelog](CHANGELOG.md)。

| 版本 | 主要变化 |
| --- | --- |
| 0.10.0 | 顶部菜单、持久化最近 PVF 列表、编辑与导航命令 |
| 0.9.0 | 按 PVF 恢复标签顺序、当前标签、编码、光标与滚动 |
| 0.8.0 | 固定单行标签、右键菜单、拖动与 VS Code 快捷键 |
| 0.7.0 | PTL 粒子、被动物品碰撞、多区段装备层级与 ANI 插值 |
| 0.6.0 | 依据客户端修正地图原点、通行格、变换锚点及隐式怪物部件 |
| 0.5.0 | NPC、怪物、装备和被动对象的场景待机预览 |
| 0.4.0 | MAP/TIL 组合、碰撞叠加、独立动画时钟、视差与缩放 |
| 0.3.0 | NPK/IMG 解码、ANI 播放、配置引用及未保存内容联动 |

使用和验证细节见 [资源预览](docs/resource-preview.md)、[地图场景](docs/map-scene-preview.md)、[客户端证据](docs/client-preview-evidence.md) 与 [标签和菜单](docs/editor-tabs.md)。

## 快速开始

Windows 10/11 x64 用户可从 [v0.10.0 Release](https://github.com/firekula/PVFStudio/releases/tag/v0.10.0) 下载 [EXE 安装包](https://github.com/firekula/PVFStudio/releases/download/v0.10.0/PVFStudio-0.10.0-windows-x64-setup.exe)，按向导安装后从开始菜单启动；安装器默认仅安装到当前用户目录，无需管理员权限，可选创建桌面快捷方式。

也可使用完整的 [便携 ZIP](https://github.com/firekula/PVFStudio/releases/download/v0.10.0/PVFStudio-0.10.0-windows-x64-portable.zip)，解压后双击其中的 `PVFStudio/PVFStudio.exe`，直接打开编辑器，不会弹出命令行窗口。便携目录只包含以下两项，请一起保留、移动：

```text
PVFStudio/
  PVFStudio.exe
  app/            产品与平台库、JBR、启动配置、模块元数据和许可证
```

两种发行包均自带运行时，不需要另装 Java、IDEA 或 Visual C++ 运行库，不包含游戏 PVF。开发文档、验证记录、截图、README 和 Changelog 仅保留在源码仓库，发行包中保留必要的许可证及第三方声明。

下载后的文件可用 [SHA-256 校验文件](https://github.com/firekula/PVFStudio/releases/download/v0.10.0/PVFStudio-0.10.0-SHA256SUMS.txt) 核对。

在源码目录中已经构建产品时，也可以这样启动：

```powershell
.\pvf-product\build\product\PVFStudio-0.10.0\PVFStudio.exe
# 直接打开一个外部 PVF，带空格的路径加引号。
.\pvf-product\build\product\PVFStudio-0.10.0\PVFStudio.exe 'G:\path\Script.pvf'
```

1. 选择“文件 → 打开 PVF…”（Ctrl+O），或“文件 → 打开最近的文件”（Ctrl+R）；左侧显示目录树，也可输入路径片段搜索。
2. 在“文件 → 打开编码”选择编码，双击条目进行编辑。使用 Ctrl+Z 撤销，Ctrl+Shift+Z / Ctrl+Y 重做。
3. 用“查看 → 查看差异”核对修改，按 Ctrl+S 或选择“文件 → 保存草稿”。**保存草稿不会改写原包。**
4. 选择“文件 → 导出新 PVF…”，选择目标文件；默认先备份旧版本，完成导出和回读校验后才提交新包。选择已有导出文件时需确认更新，原包始终保持只读。

默认草稿保存在用户目录下 `.pvf-studio/workspaces/`，独立产品的平台配置、缓存和日志保存在 `.pvf-studio/platform/product/`，与旧原型和现有 IDEA 分开。草稿绑定源路径及内容 SHA-256；重启恢复已保存草稿，撤销历史仅属于当前编辑会话。打开的标签、排列顺序、当前标签、各自编码、光标及横纵滚动位置自动保存在同一工作区的 `editor-session.properties`，下次打开同一 PVF 自动恢复；关闭全部标签也会保留空状态。会话只记录界面位置，未保存正文仍按关闭时的保存/放弃选择处理。

### 配置与 NPK 资源预览（0.3.0）

1. 打开 PVF，在右侧“资源预览”中点击“选择 NPK 目录”，选择客户端目录或 `ImagePacks2`。目录选择保存在当前 PVF 的独立工作区；PVF 同目录存在 `ImagePacks2` 时可自动关联。
2. 打开 `.ani`，点击“播放”；用左右按钮或时间轴逐帧查看，可调整循环、倍速和缩放。位置、比例、旋转和 RGBA 使用配置参数，原生 IMG 裁剪偏移保持有效。
3. 打开物品、怪物等配置，在下拉框选择 `[icon]`、`[field image]`、`[waiting motion]` 等资源引用。ANI 按当前脚本相对路径、PVF 根路径依次查找；已打开的 ANI 优先使用编辑器中的未保存内容，否则读取草稿或原包。
4. 修改配置后停顿约 350 ms 自动刷新。装备路径中的 `%02d%02d` 等数字占位符用“外观参数”按顺序替换，输入如 `0,0` 后回车；这些参数只影响预览。

支持本地 ACT1 的 IMG v1，以及 IMG v2、明文或 XOR 路径、ARGB1555/4444/8888、原始或 zlib 数据及链接帧。资源缺失、重复 IMG、越界帧、配置错误和不支持的格式会显示原因。重复帧标签或声明帧数不符时按文件中的实际帧顺序预览并提示。支持普通混合及 LINEARDODGE 灯光；场景支持参数插值；暂不模拟其他特殊混合、玩家角色装备、阴影、声音和游戏逻辑。详细格式、验证命令及边界见 [资源预览说明](docs/resource-preview.md)。

### MAP/TIL 场景与碰撞层（0.4.0）

打开 `.map` 或 `.til` 后，下拉框默认选择“完整场景”。MAP 将基础地块横向拼接，扩展地块接在下方并换行，同时加载 `[animation]` 与 `[background animation]`；保留层顺序、坐标、Z 高度和重复背景。点“碰撞区域”可开关 TIL 通行格：红色为 2（禁行），橙色为 1（仅浮空单位可通行），0 保持透明；悬停查看来源文件和格坐标。蓝框显示城镇 `[virtual movable area]` 的可移动区域；同一叠加层还会标记带碰撞属性的被动对象。

点击播放后场景时间持续前进，各 ANI 根据自己的帧延时和循环属性采样；非循环动画结束后停在末帧。“强制循环”可让它们也分别循环。时间轴以滚动的 60 秒窗口定位，跨分钟继续播放；箭头按 16 ms 步进。支持拖动平移、滚轮围绕鼠标缩放，以及 25%–400% 缩放。地图中的远/中/近景滚动比例在拖动时生效。修改未保存的引用 TIL 或 ANI 后，场景重新读取编辑器快照。

此模式展示 MAP/TIL/ANI 中定义的场景与通行数据；0.5.0 增加下述实体待机预览，尚未执行实体的 AI 和动态碰撞逻辑。0.6.0 的地图坐标和部件装配依据原生客户端函数修正，细节见 [地图场景说明](docs/map-scene-preview.md)。

### NPC、怪物与被动对象（0.5.0）

打开 MAP 的“完整场景”后，预览会读取 `npc/npc.lst`、`monster/monster.lst`、`passiveobject/passiveobject.lst`，按编号找到定义及对应的 field animation / waiting motion / basic motion，依据地图坐标、Z 高度、NPC 朝向和绘制层摆放。怪物装备的待机动画与被动对象配置的附加动画也会加载。

每条配置显示一个实体，共用场景时钟播放待机；特殊被动对象只显示容器本身。勾选“对象标记”查看脚下原点及编号，悬停查看定义路径、坐标和状态；没有可用初始视觉的逻辑对象或索引缺失的实体以标记保留。“条件怪物”默认关闭，勾选可同时查看候选放置，未判断游戏中的出现条件。修改未保存的索引列表、NPC/MOB/OBJ、装备或待机 ANI 后，重新选中 MAP 会使用当前编辑器快照。

### 导出备份与恢复

“文件 → 导出前自动备份”默认勾选。首次导出到新文件时备份当前原包；更新已经存在的导出文件时，备份该文件更新前的完整内容。备份是独立文件，保存到导出目录的 `backups/`，例如 `Script-edited.before-export-20261024-210000-123-xxxxxxxx.pvf`，时间戳和随机后缀保证各次版本分别保留，不自动清理。

备份经过 SHA-256 对照校验；备份失败时停止导出。新包仍须完成全部 CRC、内容和布局回读校验，更新既有导出文件时才进行安全替换；导出失败会保留旧目标和已完成的备份。原包本身及其文件别名不允许覆盖。取消自动备份后，恢复为只能导出到新文件的行为；CLI 的现有导出命令同样继续拒绝覆盖。

需要恢复时，在编辑器中直接打开 `backups/` 里的某个 PVF 即可检查该版本。也可先关闭正在使用待恢复文件的程序，再把选中的备份复制回所需路径；不会因更新或卸载编辑器自动删除这些备份。

### 从 0.1.0 升级

**0.1.0 导出的修改包必须从有效原包和既有草稿重新导出。** 旧版本遗漏了客户端的带初值 CRC32 校验；仅服务端 reader 回读成功不能证明旧修改包可被客户端接受。请使用完整的 0.1.2 或更新的编辑器目录；无修改且逐字节一致的旧重建包不受该错误影响。详细原因见 [Changelog](CHANGELOG.md) 和 [格式说明](docs/pvf-format.md)。

0.1.x 原型升级到 0.2.1 时，便携版请解压到新目录；安装版请先卸载旧版或选择新安装目录。安装器会阻止覆盖仍含旧完整 IDEA 布局的目录，以免混入旧运行文件。既有用户草稿和导出目录中的备份继续保留。

从 0.2.x 或本地 0.3.0～0.9.0 升级到 0.10.0 前，请先关闭旧版。便携版解压到新目录，安装版运行新版安装器；用户目录中的草稿、资源目录关联和已有标签会话继续保留。0.3.0～0.9.0 为本轮开发迭代，本次统一收入 0.10.0，未单独发布二进制版本。

## 支持范围与限制

当前支持已验证的 ACT1 `DNF_SCRIPT_PACK` 样本，仅修改现有条目，不支持新增、删除、重命名条目或其他未经验证的 PVF 变体。F 盘腾讯目录中的原包与已有仓库样本逐字节相同，不代表新增了另一种格式版本支持。

导出保留未修改明文及补齐、原始路径字节、fileNumber、条目顺序、间隙和尾部数据。内容或物理偏移变化时重新计算 CRC/key 并加密；无修改重建要求整包逐字节一致。原包不会被覆盖；界面中更新已有导出文件前会先保留独立备份。

文本编辑保持 CRLF、LF、CR 及是否有文件末尾换行；混合换行条目暂只读，含 NUL 的内容拒绝文本编辑。编码由用户明确选择，不能无损解码或编码时报告错误。

当前按固定平台版本的模块依赖和基础服务声明组装，不包含 Java/Kotlin/Gradle/Git 等语言及工程插件。上游公共 JAR 内仍有共用基础实现，没有做逐类裁剪，也没有重编全部上游源码。支持当前编辑器内的查找/替换；尚未提供跨条目的全文搜索、智能补全、引用跳转或专用配置表单。脚本可读写不代表客户端已实现其中引用的所有职业或技能。

## 从源码构建

核心与 CLI 编译目标为 Java 17，运行要求 Java 17+；IntelliJ 模块的编译目标为 Java 21。项目固定 Gradle Wrapper 9.5.0；以下开发构建使用已验证的 JDK 25。只构建核心和 CLI 时不下载 IntelliJ 宿主。

在 PowerShell 中进入本项目，将 `JAVA_HOME` 设置为本机 JDK：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
.\gradlew.bat check :pvf-cli:installDist
python -B -m unittest discover -s validation -p 'test_*.py'

$cli = '.\pvf-cli\build\install\pvf-studio\bin\pvf-studio.bat'
& $cli --help

# 可选：先准备并构建独立产品，再启动产品本身。
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-release.ps1 -ZipOnly
.\gradlew.bat -PwithIntellij=true :pvf-product:runProduct
```

Python 验证及产品依赖准备脚本要求 Python 3.11+；核心与 CLI 构建不依赖 Python。首次产品构建需要下载固定平台 SDK 和 vanilla JBR，SDK 只作为编译与装配输入，不整包复制进产物。开发结构见 [独立产品说明](docs/intellij-product.md)。

### 构建 Windows Release

构建机需要 JDK 21+（本项目使用 JDK 25 验证）、x64 MinGW-w64 GCC（`gcc.exe` 和 `windres.exe` 在 PATH 中）和 [Inno Setup 6.3 或更新版本](https://jrsoftware.org/isdl.php)。原生 EXE 是独立的 Windows GUI 程序，构建后仅使用 Windows 自带 DLL。只运行发行包无需这些构建工具。在项目根目录执行：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-release.ps1

# ISCC.exe 不在常见安装目录或 PATH 时，显式指定。
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-release.ps1 `
  -IsccPath 'C:\path\to\Inno Setup 6\ISCC.exe'

# 仅构建便携 ZIP，不需要安装 Inno Setup。
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-release.ps1 -ZipOnly
```

脚本先准备固定平台与 vanilla JBR，执行 JVM 检查并构建原生 EXE 和产品主模块。产品通过身份、核心类加载器、基础设置服务及无额外插件的启动检查后，才生成 ZIP、安装器与校验文件。可用 `-IntellijLocalPath` 复用固定基线的 Community SDK，不能使用 IU 宿主分发。

默认产品目录包含版本号。也可用 `-StandaloneDirectory 'G:\path\PVFStudio-0.10.0'` 将新便携目录装配到独立位置，避免更新正在使用的程序文件。

将 `pvf-product/build/distributions/` 中本次生成的以下文件添加到同一 Release，版本号随根项目的 `version` 更新：

- `PVFStudio-0.10.0-windows-x64-setup.exe`：单文件安装器，安装、开始菜单入口、可选桌面入口和卸载。
- `PVFStudio-0.10.0-windows-x64-portable.zip`：解压运行的完整便携包。
- `PVFStudio-0.10.0-SHA256SUMS.txt`：本次构建的 SHA-256 摘要；用 `Get-FileHash <文件> -Algorithm SHA256` 核对。

安装器是单个下载文件；便携版为原生 EXE 加一个 `app/` 目录，启动过程不依赖批处理或 PowerShell，也不会每次自解压。产品代码合并为 `app/lib/pvf-studio.jar`，随平台模块加载器进入核心类加载器；不再提供插件 ZIP 或完整 IDEA 宿主。当前 EXE 和安装器未配置代码签名。卸载保留用户目录中的 `.pvf-studio/` 草稿与设置。

## 验证状态

0.10.0 通过 110 项 JVM 测试和 14 项独立 Python 测试。完整产品检查验证资源与场景预览、粒子/碰撞、多部件怪物、标签操作、会话重开、菜单与最近文件，以及 48,326 条目 PVF 的编辑、导出、备份和恢复；普通 EXE 窗口验证了菜单及 Ctrl+O/Ctrl+R。安装器、便携 ZIP 和 SHA-256 文件随同发布，ZIP 的全部 CRC 与打包内容已检查。

0.6.0～0.9.0 分别通过 83、98、102、106 项 JVM 测试，并持续通过 14 项 Python 测试。0.6.0 的全部 289 个怪物定义完成四个时间点的绘制检查，其中 117 个为多部件；0.7.0 覆盖地图引用的 17 种粒子工厂。0.8.0 验证标签关闭范围与保存隔离；0.9.0 验证光标/纯滚动自动保存、立即关闭的最终写入、隐藏标签跨两次重开及不同 PVF 隔离。对应证据见上述专题文档。

0.5.0 通过 78 项 JVM 测试和 14 项 Python 测试。全部实际地图实体记录解析通过；地图引用的 576 种实体中，538 种成功解析并解码待机资源，26 种无基础动画，12 种原始索引缺失或被注释，后两类保留诊断标记。森林样本的 74 个实体、城镇 NPC、特殊容器、装备叠层、NPC 翻转、地面深度、独立待机时钟和未保存定义覆盖均有验证；产品界面还检查条件怪物与对象标记开关。

0.4.0 通过 66 项 JVM 测试和 14 项 Python 测试。真实样本的 1,092 个 MAP、619 个 TIL 全部通过解析；森林、城镇、扩展地图及独立 TIL 完成组合与碰撞层渲染。新增测试覆盖扩展地块换行、层级与 Z 坐标、通行类型、重复背景与视差、不同周期的独立相位、暂停/倍速/定位和长时间播放。产品界面检查包含场景播放、碰撞开关、跨分钟时间轴与未保存 TIL 覆盖。

0.3.0 通过 51 项 JVM 测试和 14 项 Python 测试。真实 ACT1 目录的 425 个 NPK 中索引到 12,103 个有效 IMG，30,294 个 ANI 均通过解析；代表样本的 14 个动画/图标帧完成解码与渲染。产品实际界面通过动画播放、逐帧、图标、ANI 引用、未保存覆盖、丢失资源、过期回调和目录保存检查；PVF 编辑、导出、备份与恢复的原有冒烟也通过。这些结果覆盖所述样本及功能，不代表所有动画效果与客户端画面一致。

0.2.1 的产品身份、核心类加载器、基础设置服务及无额外插件检查通过；vanilla JBR 产品通过真实 EditorImpl 的撤销/重做、Diff、行尾、48,326 条目 PVF 导出、备份及恢复冒烟。文件树通过 12 组实际平台绘制检查，覆盖多层展开、切换选择、折叠再展开、取消选择以及焦点变化；便携 ZIP 的全部 CRC 与两个发行文件的 SHA-256 校验通过。

0.1.2 的 51 项自动测试通过：27 项核心、10 项工作区和 14 项独立 Python 验证。新增工作区检查覆盖首次备份、连续版本恢复、备份失败、原包及硬链接别名保护、导出失败后保留旧目标和已完成备份。

0.1.2 的真实编辑器冒烟还验证了 48,326 个条目的原包备份、再次导出的旧版本备份与恢复；备份内容与原包或上一版导出逐字节一致，原包 SHA-256 未变。

0.1.1 已通过 46 项自动测试：27 项核心、5 项工作区、10 项独立 Python 校验器和 4 项隔离准备测试。两份原始 PVF 共 96,669 个条目的 CRC 关系均已核验，无修改重建、修改导出和草稿跨进程恢复也通过全量对照。

两份指定修改导出包已分别在独立 ACT1 客户端和服务端副本中进入游戏，物品 5642 显示了 `_PVF验证` 和 `_外部包测试` 后缀。真实界面中的编辑、保存、撤销/重做、差异比较及导出也已验证。包摘要、截图与原环境复核见 [客户端验证记录](docs/client-validation-2026-10-24.md)；这些结果限于所记录的客户端与导出包。

0.1.0 的历史检查及其更正保留在 [初轮验证记录](docs/validation-2026-10-24.md)，不能用其旧 reader 回读结果代替修复后的客户端验收。

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

完整参数以 `--help` 和 [CLI 源码](pvf-cli/src/main/java/studio/pvf/cli/Main.java) 为准。正常退出码为 `0`，参数错误为 `2`，读写或校验失败为 `3`。

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

以脚本实际生成的 `summary.json` 和逐项报告为验证结果。原 C# reader 没有检查原生客户端所需的 CRC 关系；即使该 reader 接受导出包，也不能代替带初值 CRC 校验或客户端运行验证。上述离线脚本不替换运行现场的 PVF，不启动游戏客户端。隔离客户端测试的约束见 [隔离说明](docs/client-isolation-notes.md)，实际加载结果见 [客户端验证记录](docs/client-validation-2026-10-24.md)。

## IntelliJ 界面

`-PwithIntellij=true` 启用 `pvf-product` 独立产品模块，构建与运行产品本身。源码结构、平台依赖、二次开发方式及启动命令见 [独立产品说明](docs/intellij-product.md)。

## 项目结构

| 路径 | 内容 |
| --- | --- |
| `pvf-core` | 按需读取、保真重建、导出前后校验 |
| `pvf-workspace` | 编码严格转换、草稿持久化、工作区锁与导出快照 |
| `pvf-cli` | 目录查询、提取、替换、草稿操作 |
| `pvf-preview` | 只读 NPK 索引、IMG 帧解码、配置引用、ANI 参数与渲染 |
| `pvf-product` | 独立产品主模块、平台模块装配、产品描述与启动检查 |
| `validation` | 独立 Python 对照器与原 C# reader 测试宿主 |
| `docs/pvf-format.md` | 已实现格式、保真边界和提交语义 |

不把 PVF、提取的游戏资源、草稿或生成的验证包提交到 Git；它们不属于本项目源码。`.gitignore` 已排除 PVF、构建输出及默认工作区目录。将自定义草稿与导出文件放在 `build/` 或仓库外目录。

## 许可证

本项目源码采用 [BSD Zero Clause License（BSD-0-Clause）](LICENSE)。使用的 IntelliJ Platform、JBR 及第三方组件继续适用各自的许可证和声明；发行包在 `app/LICENSE`、`app/licenses/` 和 `app/jbr/legal/` 中保留相关文件。
