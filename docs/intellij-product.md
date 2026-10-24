# IntelliJ 独立工作台

本模块将 PVF 核心与工作区接入 IntelliJ 的真实文本编辑器，不依赖服务端进程。

## 构建边界

- 默认核心构建不包含本模块，避免下载 IntelliJ 发行包。
- 使用 `-PwithIntellij=true` 才启用本模块。
- 平台基线固定为 IntelliJ IDEA Community 2025.2.6.3，IntelliJ Platform Gradle Plugin 2.19.0；编译需要 JDK 21 或更新版本，运行使用发行包自带的 JetBrains Runtime。
- `buildPlugin` 仅产生内置能力的插件 ZIP。它不是独立程序。
- `assembleStandalone` 将完整、未改品牌的 Community 宿主、JBR、PVF 插件和 `PVFStudio.bat` 放在一个目录，用户无需另装 IDEA。该目录是独立运行原型，尚未裁剪平台或制作正式品牌安装器。
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

Windows 完整原型 ZIP：`intellij-workbench/build/distributions/PVFStudio-0.1.0-windows-prototype.zip`。解压后双击其中的 `PVFStudio/PVFStudio.bat`，或用下面的命令传入 PVF 路径。ZIP 自带平台与 JBR，不包含游戏 PVF。

```powershell
.\intellij-workbench\build\standalone\PVFStudio\PVFStudio.bat 'G:\path\Script.pvf'
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
6. “导出新 PVF”先持久化当前文档快照，再导出到新文件并由核心回读校验。导出期间继续输入的内容留为未保存编辑，不混入正在生成的包。
7. 关闭或切包时处理未保存编辑。关闭标签会释放对应 IntelliJ Editor，避免保留不再使用的文档。

编辑器使用平台的 defaultProject 作为命令上下文，不创建或打开普通 IDEA 项目。Ctrl+Z 撤销，Ctrl+Shift+Z / Ctrl+Y 重做；它们使用 IntelliJ 原生 UndoManager，不保存每次按键的全文快照。撤销历史属于当前编辑会话，重启后恢复的是已保存草稿。

平台配置、缓存与日志保存在 `.pvf-studio/platform/`，与用户现有 IDEA 配置隔离。开发 `runIde` 使用 Gradle 的隔离 sandbox。草稿目录同时绑定原包规范路径与内容 SHA-256；同一路径被替换成另一份 PVF 后会使用新工作区，保留旧包的草稿。

独立目录下 `plugins/pvf-studio-workbench` 通过官方 `plugin.path` 属性显式加载。宿主的 bundled plugin 清单是预生成的，不能只向 `runtime/plugins` 添加目录就假设插件会被发现。启动器预建本程序的默认配置目录，不从其他 IDE 导入配置。

启动器会先检查工作台、核心、工作区 JAR 和随包 JBR；缺失时报告错误并退出，避免不完整解压后误入普通 IDEA 窗口。

当前原型尚未提供正文搜索、完整可写 PVF VFS、PSI/ID 引用跳转、可视化表单或正式安装器。包含 NUL 的条目暂拒绝文本编辑，不将未知二进制当作文本改写。编码失败保留原内容并显示错误，用户可用其他编码重新打开。

## 2026-10-24 实际验收

最终平台为官方 IC 2025.2.6.3 / build 252.28539.97，使用发行包自带 JBR 21。SDK ZIP 的 SHA-256 已与官方核对：`ee3bb2bf315298da0758f5d72ee97a9c5e017f22d129c12db80b69a392f4673e`。本机名称带 Community 的旧安装目录实际已升级为 IU 2026.2.3，仅用于早期开发验证，没有复制进最终独立包。

- IC 实际编辑器集成冒烟通过：`build/validation/intellij-smoke-ic-undo-diff-2/report.properties` 为 PASS，包含真实 `EditorImpl`、原生 undo/redo、嵌入式 DiffRequestPanel、CRLF/LF/CR 与末尾换行检查，以及草稿持久化和 PVF 导出。
- 通过打包目录中的 `PVFStudio.bat` 启动独立程序，确认显式载入 PVF 插件，显示自有 PVF Studio 窗口，使用独立平台配置。没有依赖本机安装的 IDEA，也没有把 PVF 当作普通二进制文件交给 IDEA LightEdit。
- Windows 实际图形交互通过：路径搜索、打开 GBK 中文脚本、语法高亮、输入中文注释、Ctrl+S 保存草稿、导出新包；重启后恢复已存中文草稿。
- 修复版实际键盘交互通过：新输入 `// UNDO_CHECK`，Ctrl+Z 删除，Ctrl+Shift+Z 恢复，再 Ctrl+Z 返回已保存草稿，未保存标记消失。
- 修复版 Diff 实际显示左右两栏，显示预期中文注释造成的 1 处差异；关闭 Diff 子窗后主编辑器仍正常。Diff 使用自有 JDialog 和显式释放的 DiffRequestPanel，避免普通 IDEA 窗口包装器依赖 IdeFrame。
- 图形导出的 `build/validation/ui-interactive/interactive-export.pvf` 已由独立 Python 验证器与原 C# reader 全量回读：48,326 个条目、1 项预期 GBK 中文注释修改，48,325 个未改条目的密文原样；源副本 SHA-256 未变。

交互测试使用 `build/validation/ui-interactive/Script.pvf` 的独立副本。没有替换运行现场的服务端/客户端 PVF；这些验证不代表官方客户端加载验收。

## 后续正式产品装配

在读写与交互验证稳定后，固定同一开源 release tag 建立产品模块，配置产品标识、图标、必要 bundled plugins、JBR、启动器和 Windows 安装器。按需移除 Java/Kotlin 构建、调试等与 PVF 无关的插件，并重新验证编辑器、Diff 与文本查找服务依赖。不能通过删除发行目录中的任意 JAR 来替代依赖明确的产品构建。

## 官方依据

- [平台可用于构建独立 IDE，功能按插件组织](https://plugins.jetbrains.com/docs/intellij/intellij-platform.html)
- [固定基线 ApplicationStarter：自定义命令、EDT 初始化后运行、非 headless 支持](https://github.com/JetBrains/intellij-community/blob/idea/252.28539.97/platform/ide-core/src/com/intellij/openapi/application/ApplicationStarter.kt)
- [平台 Gradle 任务：buildPlugin 与 runIde](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html)
- [平台 Gradle 扩展：platformPath 等](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html)
- [2025.2 官方发布版本记录](https://youtrack.jetbrains.com/articles/IDEA-A-2100662434/IntelliJ-IDEA-2025.2-Latest-Builds)

编译、启动和真实 PVF 验证的实际结果应以项目根目录 README 与验证报告为准；本文件描述构建方式与功能边界。
