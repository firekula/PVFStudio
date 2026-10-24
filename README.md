# PVF Studio

基于 IntelliJ 平台的独立 PVF 编辑器，面向 DNF ACT1 `DNF_SCRIPT_PACK` 配置包，提供目录浏览、脚本编辑、草稿恢复、差异比较和保真导出。核心与 CLI 可独立使用。

当前版本为 **0.1.1**，Windows 独立原型自带 IntelliJ Community 2025.2.6.3 与 JBR 21，无需另装 IDEA。版本变化见 [Changelog](CHANGELOG.md)。

## 已有功能

- 目录树按需展开，按虚拟路径搜索，按需读取条目内容。
- 使用 IntelliJ 文本编辑器，支持脚本着色、撤销/重做及原文差异比较。
- 手动选择 GBK/CP936、MS949/CP949 或 UTF-8，严格转换编码并保持支持的原始换行。
- Ctrl+S 保存独立草稿，重新打开同一原包时恢复已保存的修改。
- 导出到新 PVF，校验源包一致性、目录与全部条目 CRC，并回读检查内容和布局。
- CLI 支持查询、提取、字节替换、草稿管理及无修改重建。

这是独立 Git 项目，与 `DFLegacy` 服务端仓库分开。编辑器不依赖运行中的服务端，构建与测试输出默认写入本项目的 `build/`。

## 快速开始

使用完整的 `PVFStudio-0.1.1-windows-prototype.zip`，解压后双击其中的 `PVFStudio/PVFStudio.bat`。保留同目录中的 `runtime/`、`plugins/` 和启动配置；发行包自带运行时，不包含游戏 PVF。

在源码目录中已经构建独立原型时，也可以这样启动：

```powershell
.\intellij-workbench\build\standalone\PVFStudio\PVFStudio.bat
# 直接打开一个外部 PVF，带空格的路径加引号。
.\intellij-workbench\build\standalone\PVFStudio\PVFStudio.bat 'G:\path\Script.pvf'
```

1. 点击“打开 PVF”，选择原包；左侧显示目录树，也可输入路径片段搜索。
2. 选择“打开编码”，双击条目进行编辑。使用 Ctrl+Z 撤销，Ctrl+Shift+Z / Ctrl+Y 重做。
3. 用“查看差异”核对修改，按 Ctrl+S 或点击“保存草稿”。**保存草稿不会改写原包。**
4. 点击“导出新 PVF”，选择尚不存在的新文件名；完成导出和回读校验后，才会得到新包。

默认草稿保存在用户目录下 `.pvf-studio/workspaces/`，平台配置、缓存和日志保存在 `.pvf-studio/platform/`，与现有 IDEA 配置分开。草稿绑定源路径及内容 SHA-256；重启恢复已保存草稿，撤销历史仅属于当前编辑会话。

### 从 0.1.0 升级

**0.1.0 导出的修改包必须从有效原包和既有草稿重新导出。** 旧版本遗漏了客户端的带初值 CRC32 校验；仅服务端 reader 回读成功不能证明旧修改包可被客户端接受。请使用完整的 0.1.1 编辑器目录；无修改且逐字节一致的旧重建包不受该错误影响。详细原因见 [Changelog](CHANGELOG.md) 和 [格式说明](docs/pvf-format.md)。

## 支持范围与限制

当前支持已验证的 ACT1 `DNF_SCRIPT_PACK` 样本，仅修改现有条目，不支持新增、删除、重命名条目或其他未经验证的 PVF 变体。F 盘腾讯目录中的原包与已有仓库样本逐字节相同，不代表新增了另一种格式版本支持。

导出保留未修改明文及补齐、原始路径字节、fileNumber、条目顺序、间隙和尾部数据。内容或物理偏移变化时重新计算 CRC/key 并加密；无修改重建要求整包逐字节一致。原包及已有导出目标均不会被覆盖。

文本编辑保持 CRLF、LF、CR 及是否有文件末尾换行；混合换行条目暂只读，含 NUL 的内容拒绝文本编辑。编码由用户明确选择，不能无损解码或编码时报告错误。

当前为完整 Community 宿主上的独立原型，平台尚未裁剪，未提供正式安装器、正文搜索、智能补全、引用跳转或专用配置表单。脚本可读写不代表客户端已实现其中引用的所有职业或技能。

## 从源码构建

核心与 CLI 编译目标为 Java 17，运行要求 Java 17+；IntelliJ 模块的编译目标为 Java 21。项目固定 Gradle Wrapper 9.5.0；以下开发构建使用已验证的 JDK 25。只构建核心和 CLI 时不下载 IntelliJ 宿主。

在 PowerShell 中进入本项目，将 `JAVA_HOME` 设置为本机 JDK：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
.\gradlew.bat check :pvf-cli:installDist
python -B -m unittest discover -s validation -p 'test_*.py'

$cli = '.\pvf-cli\build\install\pvf-studio\bin\pvf-studio.bat'
& $cli --help

# 可选：启动 IntelliJ 编辑器，或生成自带运行时的完整 Windows ZIP。
.\gradlew.bat -PwithIntellij=true :intellij-workbench:runIde
.\gradlew.bat -PwithIntellij=true :intellij-workbench:standaloneZip
```

Python 验证脚本要求 Python 3.11+；核心与 CLI 构建不依赖 Python。首次 IntelliJ 构建需要下载较大的平台依赖。开发宿主和产物区别见 [IntelliJ 独立程序说明](docs/intellij-product.md)。

## 验证状态

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

可选模块通过 `-PwithIntellij=true` 启用。开发宿主、独立发行目录、启动命令和交付限制见 [IntelliJ 独立程序说明](docs/intellij-product.md)。插件 ZIP 与自带运行时的独立程序是两种产物，应分别检查。

## 项目结构

| 路径 | 内容 |
| --- | --- |
| `pvf-core` | 按需读取、保真重建、导出前后校验 |
| `pvf-workspace` | 编码严格转换、草稿持久化、工作区锁与导出快照 |
| `pvf-cli` | 目录查询、提取、替换、草稿操作 |
| `intellij-workbench` | 可选的 IntelliJ 编辑器接入与独立原型装配 |
| `validation` | 独立 Python 对照器与原 C# reader 测试宿主 |
| `docs/pvf-format.md` | 已实现格式、保真边界和提交语义 |

不把 PVF、提取的游戏资源、草稿或生成的验证包提交到 Git；它们不属于本项目源码。`.gitignore` 已排除 PVF、构建输出及默认工作区目录。将自定义草稿与导出文件放在 `build/` 或仓库外目录。

## 许可证

本项目源码采用 [BSD Zero Clause License（BSD-0-Clause）](LICENSE)。随独立包提供的 IntelliJ Community、JBR 及第三方组件继续适用各自的许可证和声明，完整原型保留宿主中的相关文件。
