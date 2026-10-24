# PVF Studio 独立产品

## 0.2.1 当前架构

`pvf-product` 是普通 Java/Gradle 产品模块，不再使用 IntelliJ 插件 Gradle 构建。它将自己的界面代码、`pvf-core` 和 `pvf-workspace` 合并为 `pvf-studio.jar`，用自有 `PvfStudioMain`、`PvfStudioApplicationInfo.xml`、PVS 产品代码和 `product-modules.xml` 定义独立产品。PVF 代码与平台共用核心类加载器；发行包不含 PVF 插件目录、插件 ZIP、完整 IDEA 或 `idea64.exe`。

平台本身通过 XML 描述组件和服务，这也是 IntelliJ 自己的核心注册机制。`PvfStudioPlugin.xml` 是产品核心描述，不是单独安装的插件；加载检查要求只有框架自身的 `com.intellij` 核心项，PVF 类必须与平台类处于同一核心类加载器。

平台基线与源码提交锁定在 `pvf-product/platform.lock.json`。装配器读取上游运行时模块仓库，计算 `platform-roots.txt` 和基础服务声明的依赖闭包，生成独立模块仓库；只复制闭包引用的公共 JAR。共享第三方库即使在上游 SDK 的某个插件目录中，也只作为独立库重定位，不携带该插件。运行时使用官方 SHA-512 校验的 vanilla JBR，不包含 JCEF。

上游发布包把 IDEA 的 ApplicationInfo 注入了字节码，因此产品启动必须启用 `intellij.platform.load.app.info.from.resources` 并从 JVM 启动阶段指定自己的平台前缀；否则仍会要求 Java/JSON 必需插件。基础内容模块的服务描述由装配器从同一固定 SDK 的已嵌入描述中生成，避免遗漏 `SettingsController` 等基础注册。

源码文档和验证证据仅保留在仓库。发行目录只有原生 EXE 和 `app/`，其中为 `lib/`、`jbr/`、`bin/`、`modules/`、产品信息及必要许可证。配置与缓存位于 `.pvf-studio/platform/product/`；既有 `.pvf-studio/workspaces/` 草稿继续使用。

## 产品开发与构建

在仓库根目录使用 JDK 21+、Python 3.11+、x64 MinGW-w64 构建；本项目已使用 JDK 25 验证：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-release.ps1 -ZipOnly
.\gradlew.bat -PwithIntellij=true :pvf-product:runProduct
# 可直接指定要打开的包。
.\gradlew.bat -PwithIntellij=true -PpvfPath='G:\path\Script.pvf' :pvf-product:runProduct
```

产品目录为 `pvf-product/build/product/PVFStudio-0.2.1/`，发行产物位于 `pvf-product/build/distributions/`。不带 `-ZipOnly` 时还需要 Inno Setup。`verifyProduct` 是发行前置检查，使用独立测试目录和无窗口模式，不更改用户配置。

```powershell
python -B .\tools\check_product.py `
  --product .\pvf-product\build\product\PVFStudio-0.2.1 `
  --archive 'G:\path\Script.pvf'
```

以上额外验证真实编辑器、撤销/重做、Diff、行尾、PVF 导出、备份和恢复，测试日志与生成 PVF 写入 `build/product-checks/`。产品身份、核心类加载器及设置服务检查通过后才执行真实编辑器检查，验证模式禁用启动错误弹窗。

二次开发应用功能时直接修改 `pvf-product/src/main/java`、产品描述和资源；增加平台能力时调整根模块和服务声明，再运行检查。当前使用固定上游公共二进制模块，不是重新编译整个 IntelliJ 源码。若要修改 IntelliJ 编辑器等平台内部实现，应基于锁定提交重编平台 SDK，再通过 `-IntellijLocalPath` 指定同一基线的修改版。公共 JAR 内仍含共用实现，裁剪粒度是模块和公共库，不是逐类裁剪。

## 0.1.x 原型说明（历史记录）

本模块将 PVF 核心与工作区接入 IntelliJ 的真实文本编辑器，不依赖服务端进程。

**版本修正：0.1.0 独立原型的 writer 漏做原生带初值 CRC，旧修改包必须重新导出。** 0.1.1 修正条目重定位、内容变化和目录更新后的 CRC/key；不能继续使用旧 0.1.0 ZIP 或仍在运行的旧编辑器生成客户端包。下面的历史 UI 交互验收不等于客户端格式验收；新包的客户端实机结果见 [客户端验证记录](client-validation-2026-10-24.md)。

## 构建边界

- 默认核心构建不包含本模块，避免下载 IntelliJ 发行包。
- 使用 `-PwithIntellij=true` 才启用本模块。
- 平台基线固定为 IntelliJ IDEA Community 2025.2.6.3，IntelliJ Platform Gradle Plugin 2.19.0；编译需要 JDK 21 或更新版本，运行使用发行包自带的 JetBrains Runtime。
- `buildPlugin` 仅产生内置能力的插件 ZIP。它不是独立程序。
- `assembleStandalone` 将原生 `PVFStudio.exe` 放在便携根目录，将完整、未改品牌的 Community 宿主、JBR、PVF 插件、配置和文档统一放进 `app/`，用户无需另装 IDEA。该目录是独立运行原型，尚未裁剪平台。
- 原生入口是无控制台的 Windows GUI 程序，通过 `CreateProcessW` 直接运行 `app/runtime/bin/idea64.exe pvf-studio`，不经过 BAT、PowerShell 或命令解释器；保留 Unicode 参数和调用者的工作目录。构建入口额外需要 x64 MinGW-w64 GCC 与 windres。
- `tools/build-release.ps1` 在完整目录基础上用 Inno Setup 构建单个 Windows 安装 EXE，同时生成便携 ZIP 和 SHA-256 校验文件；具体命令见根目录 README 的 Windows Release 一节。
- 原型通过 `com.intellij.appStarter` 命令 `pvf-studio` 启动自有窗口，不打开普通 IDEA 项目窗口。保留宿主许可证和第三方声明。

## 命令

在项目根目录运行，设置 `JAVA_HOME` 指向 JDK 21 或更新版本：

```powershell
.\gradlew.bat -PwithIntellij=true :intellij-workbench:compileJava
.\gradlew.bat -PwithIntellij=true :intellij-workbench:runIde
# 可直接打开某个 PVF；该参数为本机文件路径，不写入构建配置。
.\gradlew.bat -PwithIntellij=true -PpvfPath='G:\path\Script.pvf' :intellij-workbench:runIde

.\gradlew.bat -PwithIntellij=true :intellij-workbench:assembleStandalone
.\gradlew.bat -PwithIntellij=true :intellij-workbench:standaloneZip
```

生成目录：`intellij-workbench/build/standalone/PVFStudio/`。

当前 Windows 完整原型 ZIP 为 `intellij-workbench/build/distributions/PVFStudio-0.1.2-windows-prototype.zip`，当前摘要由同目录的 `PVFStudio-0.1.2-SHA256SUMS.txt` 提供。0.1.0 ZIP 已不适合继续导出修改包。解压新版完整包后双击其中的 `PVFStudio/PVFStudio.exe`，或用下面的命令传入 PVF 路径。ZIP 自带平台与 JBR，不包含游戏 PVF。根目录只保留 EXE 和 `app/`，请完整解压至新目录，避免叠加旧便携包的目录。

```powershell
.\intellij-workbench\build\standalone\PVFStudio\PVFStudio.exe 'G:\path\Script.pvf'
```

首次平台构建需要从 JetBrains 下载较大的宿主依赖。核心的读取、修改、回写和验证可独立运行。

开发者可以用 `-PintellijLocalPath='D:\path\to\IntelliJ'` 指定本机已安装的平台，只读使用其 SDK 并在隔离 sandbox 中启动。必须以 `product-info.json` 为准判断真实版本，安装目录名可能在升级后未变。统一版 IU 只作为本地开发宿主，`assembleStandalone` 会拒绝将其作为开源 Community 原型分发。

真实编辑器集成冒烟（输出目录必须尚不存在）在 `runIde` 命令上额外指定 `-PpvfPath='G:\path\Script.pvf' -PsmokeOutputDir='G:\path\new-smoke-output'`。它创建真实 IntelliJ Editor，修改 `creature/script/creature.lst` 的副本，持久化草稿并导出新 PVF，生成 `report.properties` 后退出。该验证不能代替图形交互或官方客户端加载验收。

## 原型操作

1. 打开 PVF 后显示目录树；展开目录时才创建对应 UI 节点。路径搜索只查元数据，不解密整包。
2. 选择打开编码（CP936、CP949 或 UTF-8）并双击脚本，使用 IntelliJ EditorFactory 提供的编辑器；轻量 lexer 为标签、反引号字符串、数字和注释着色。
3. Ctrl+S 或“保存草稿”将修改保存到用户目录下 `.pvf-studio/workspaces/`。工作区按源路径分隔，并由 workspace 模块校验原包内容身份；原包不写入。
4. 文本送入 IntelliJ Document 前规范为 LF，保存时恢复原有 CRLF、LF 或 CR 风格，保留是否有文件末尾换行。混合换行文件暂只读，避免插入或删除行后意外改动其他行的行尾。未改文档不会被重新编码或 stage。
5. “查看差异”比较原始 PVF 内容和当前编辑文档，包含已存草稿与未存编辑。
6. “导出新 PVF”先持久化当前文档快照。0.1.2 默认备份旧版本，再生成并校验新包；更新既有导出文件时先确认并保存独立备份，原包保持只读。核心仍校验目录和全部条目的带初值 CRC32。长度变化导致未改条目移动时，其明文和补齐保真，但 CRC/key 与密文需要随新 offset 更新。导出期间继续输入的内容留为未保存编辑，不混入正在生成的包。
7. 关闭或切包时处理未保存编辑。关闭标签会释放对应 IntelliJ Editor，避免保留不再使用的文档。

编辑器使用平台的 defaultProject 作为命令上下文，不创建或打开普通 IDEA 项目。Ctrl+Z 撤销，Ctrl+Shift+Z / Ctrl+Y 重做；它们使用 IntelliJ 原生 UndoManager，不保存每次按键的全文快照。撤销历史属于当前编辑会话，重启后恢复的是已保存草稿。

平台配置、缓存与日志保存在 `.pvf-studio/platform/`，与用户现有 IDEA 配置隔离。开发 `runIde` 使用 Gradle 的隔离 sandbox。草稿目录同时绑定原包规范路径与内容 SHA-256；同一路径被替换成另一份 PVF 后会使用新工作区，保留旧包的草稿。

独立目录下 `app/plugins/pvf-studio-workbench` 通过官方 `plugin.path` 属性显式加载。宿主的 bundled plugin 清单是预生成的，不能只向 `app/runtime/plugins` 添加目录就假设插件会被发现。启动器预建本程序的默认配置目录，不从其他 IDE 导入配置；通过环境变量指定随包 JBR、配置及 VM 选项，不使用用户现有 IDEA 的运行配置。

启动器会先检查工作台、核心、工作区 JAR、启动配置和随包 JBR；缺失时用图形对话框报告错误并退出，避免不完整解压后误入普通 IDEA 窗口。

当前原型尚未提供正文搜索、完整可写 PVF VFS、PSI/ID 引用跳转或可视化表单。Windows 安装器仍包含完整 Community 宿主。包含 NUL 的条目暂拒绝文本编辑，不将未知二进制当作文本改写。编码失败保留原内容并显示错误，用户可用其他编码重新打开。

## 0.1.2 文件树与导出备份

树节点使用透明渲染器，未选中文本与树的空白部分保持同一背景，整行选择由平台绘制；目录和常见 PVF 文件类型使用独立矢量图标，搜索结果使用相同类型映射。

“导出前自动备份”默认开启。首次导出保存原包的独立副本，更新已有导出文件时保存目标的旧版本；备份位于导出目录的 `backups/`，保留所有版本。备份校验失败时停止操作，原包始终只读。只有校验完成的新包才能替换导出目标；不支持安全替换的文件系统保留旧目标并要求使用新文件名。恢复方式见根目录 README。

## 2026-10-24 的 0.1.0 历史界面验收

本节保留原型交互与装配的实际观察。后续已发现 0.1.0 writer 的 CRC 缺陷；本节旧导出产物不可作为客户端兼容证据，也不是 0.1.1 的新验收结果。

最终平台为官方 IC 2025.2.6.3 / build 252.28539.97，使用发行包自带 JBR 21。SDK ZIP 的 SHA-256 已与官方核对：`ee3bb2bf315298da0758f5d72ee97a9c5e017f22d129c12db80b69a392f4673e`。本机名称带 Community 的旧安装目录实际已升级为 IU 2026.2.3，仅用于早期开发验证，没有复制进最终独立包。

- IC 实际编辑器集成冒烟通过：`build/validation/intellij-smoke-ic-undo-diff-2/report.properties` 为 PASS，包含真实 `EditorImpl`、原生 undo/redo、嵌入式 DiffRequestPanel、CRLF/LF/CR 与末尾换行检查，以及草稿持久化和 PVF 导出。
- 通过打包目录中的 `PVFStudio.bat` 启动独立程序，确认显式载入 PVF 插件，显示自有 PVF Studio 窗口，使用独立平台配置。没有依赖本机安装的 IDEA，也没有把 PVF 当作普通二进制文件交给 IDEA LightEdit。
- Windows 实际图形交互通过：路径搜索、打开 GBK 中文脚本、语法高亮、输入中文注释、Ctrl+S 保存草稿、导出新包；重启后恢复已存中文草稿。
- 修复版实际键盘交互通过：新输入 `// UNDO_CHECK`，Ctrl+Z 删除，Ctrl+Shift+Z 恢复，再 Ctrl+Z 返回已保存草稿，未保存标记消失。
- 修复版 Diff 实际显示左右两栏，显示预期中文注释造成的 1 处差异；关闭 Diff 子窗后主编辑器仍正常。Diff 使用自有 JDialog 和显式释放的 DiffRequestPanel，避免普通 IDEA 窗口包装器依赖 IdeFrame。
- 图形导出的 `build/validation/ui-interactive/interactive-export.pvf` 曾由 0.1.0 Python 验证器与原 C# reader 全量回读：48,326 个条目、1 项预期 GBK 中文注释修改，48,325 个未改条目的密文原样；源副本 SHA-256 未变。这两个旧 reader 没有检查原生带初值 CRC，因此该项不能证明客户端兼容。

交互测试使用 `build/validation/ui-interactive/Script.pvf` 的独立副本。没有替换运行现场的服务端/客户端 PVF；这些验证不代表官方客户端加载验收。

## 0.1.1 修复与客户端实机验收

0.1.1 已重新构建正式 IC/JBR21 的编辑器、插件和完整 Windows 独立包，包含带偏移初值 CRC32 的核心修复。真实编辑器冒烟的 Undo/Redo、Diff、行尾、草稿及严格 CRC 导出均通过。指定修改包随后由独立游戏客户端实际加载、进城，并显示修改后的物品名；具体范围和截图见 [客户端验证记录](client-validation-2026-10-24.md)。

本节验收时的完整包为 `intellij-workbench/build/distributions/PVFStudio-0.1.1-windows-prototype.zip`，历史 SHA-256 为 `77ccaa6e196f0eb93ca2490799bb8d249ae235242f94efe94c31594f5b54326a`。当时 2,849 条目 CRC 和关键文件一致性检查通过；发行包不包含游戏 PVF。后续 Release 装配增加项目文档、许可证及安装器，当前产物摘要以同目录本次生成的 `PVFStudio-0.1.1-SHA256SUMS.txt` 为准。

## 后续正式产品装配

在读写与交互验证稳定后，固定同一开源 release tag 建立产品模块，配置产品标识、图标、必要 bundled plugins、JBR、启动器和 Windows 安装器。按需移除 Java/Kotlin 构建、调试等与 PVF 无关的插件，并重新验证编辑器、Diff 与文本查找服务依赖。不能通过删除发行目录中的任意 JAR 来替代依赖明确的产品构建。

## 官方依据

- [平台可用于构建独立 IDE，功能按插件组织](https://plugins.jetbrains.com/docs/intellij/intellij-platform.html)
- [固定基线 ApplicationStarter：自定义命令、EDT 初始化后运行、非 headless 支持](https://github.com/JetBrains/intellij-community/blob/idea/252.28539.97/platform/ide-core/src/com/intellij/openapi/application/ApplicationStarter.kt)
- [平台 Gradle 任务：buildPlugin 与 runIde](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html)
- [平台 Gradle 扩展：platformPath 等](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html)
- [2025.2 官方发布版本记录](https://youtrack.jetbrains.com/articles/IDEA-A-2100662434/IntelliJ-IDEA-2025.2-Latest-Builds)

编译、启动和真实 PVF 验证的实际结果应以项目根目录 README 与验证报告为准；本文件描述构建方式与功能边界。
