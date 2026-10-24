# PVF Studio

面向 DNF ACT1 `DNF_SCRIPT_PACK` 的 PVF 读取、脚本修改与保真导出工具。纯 JVM 核心与工作区可单独使用；IntelliJ 编辑器作为可选模块接入。

这是独立的 Git 项目，位于 `G:\Game\DNF_ACT1\PVFStudio`，与 `DFLegacy` 服务端仓库为同级目录。构建、草稿、验证输出均保存在本项目或用户指定的新目录中。

当前实现针对已有 ACT1 包中现有条目的修改，保留原 key、fileNumber、路径字节与未修改密文。它不覆盖源包，也不把 key 当作普通 CRC32 重新计算。其他 PVF 变体、新增、删除及重命名条目不在当前支持范围内。客户端是否接受导出包仍需独立验证。

## 打开已构建的独立原型

本机已生成自带 IntelliJ Community 与 JBR 的 Windows 独立目录，无需安装 IDEA 或另行配置 Java：

```powershell
.\intellij-workbench\build\standalone\PVFStudio\PVFStudio.bat
# 也可以传入一个 PVF 的绝对路径：
.\intellij-workbench\build\standalone\PVFStudio\PVFStudio.bat 'G:\path\Script.pvf'
```

界面已通过真实包的搜索、中文编辑、Ctrl+S、撤销/重做、差异比较、重启恢复和新包导出检查。完整运行时尚未裁剪，当前交付为独立原型；正文搜索、智能补全、引用跳转和专用配置表单后续再加。

## 构建与运行

核心和 CLI 编译为 Java 17，运行要求 Java 17 或更高版本。项目固定 Gradle Wrapper 9.5.0；当前开发构建使用 JDK 25。可选 IntelliJ 模块的编译与运行环境见 [IntelliJ 独立程序说明](docs/intellij-product.md)。

在 PowerShell 中进入本项目，选择本机已安装的 JDK 后执行：

```powershell
Set-Location 'G:\Game\DNF_ACT1\PVFStudio'
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-25.0.4"
.\gradlew.bat check :pvf-cli:installDist

$cli = '.\pvf-cli\build\install\pvf-studio\bin\pvf-studio.bat'
& $cli --help
```

只构建核心和 CLI 时不下载 IntelliJ 发行包。`check` 覆盖独立加密夹具、读写边界、编码及草稿持久化；CLI 的真实资产验证通过下面的单独命令执行。

2026-10-24 的首轮验证已通过：22 项核心测试、5 项工作区测试和 7 项独立 Python 校验器自测；两份真实 PVF 的无修改重建、中文名称修改、草稿恢复，以及原 C# reader 的全量回读均通过。详见 [验证记录](docs/validation-2026-10-24.md)。

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

导出和提取的目标必须是新文件；存在同名文件时拒绝覆盖。重复运行示例时选择新的输出文件名。导出先写同目录临时文件，校验成功后提交目标文件。未修改条目的加密内容、补齐字节和无关布局数据保持原样。

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

先构建 CLI，再使用 Python 3.11 或更新版本执行验证脚本。该脚本使用独立 Python reader 对照目录、全部明文、未修改密文和布局，并测试无修改重建、修改中文物品名和列表注释、跨进程恢复草稿及导出。每次生成独立的 `build/validation/<运行标识>/`，报告与生成的 PVF 均留在该目录。

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

以脚本实际生成的 `summary.json` 和逐项报告为验证结果。reader 接受导出包只证明对应读取路径兼容，不能代替客户端运行验证。此流程不替换运行现场的 PVF，不启动游戏客户端。

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
