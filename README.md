<p align="center">
  <img src="assets/branding/pvf-studio-icon-256.png" width="128" height="128" alt="PVF Studio 应用图标">
</p>

# PVF Studio

**面向 DNF ACT1 的 PVF 编辑与资源预览工具。**

PVF Studio 将脚本编辑、全文搜索、游戏资源预览和副本布局检查集中在一个桌面应用中。它基于 IntelliJ Platform 的编辑器构建，提供独立的 Windows 程序、自带运行时和独立草稿工作区，可直接打开已验证的 ACT1 `DNF_SCRIPT_PACK` 配置包，无需安装 IDEA 或运行服务端。

[下载最新版](https://github.com/firekula/PVFStudio/releases/latest) · [使用方法](#使用方法) · [源码构建](#源码构建) · [文档](#文档) · [更新日志](CHANGELOG.md) · [问题反馈](https://github.com/firekula/PVFStudio/issues)

## Release 下载

当前版本：**[v0.11.1](https://github.com/firekula/PVFStudio/releases/tag/v0.11.1)**。桌面版支持 **Windows 10 / 11 x64**，已包含 JBR 运行时，无需另装 Java、IDEA 或 Visual C++ 运行库。

| 下载 | 适用方式 |
| --- | --- |
| [Windows 安装器](https://github.com/firekula/PVFStudio/releases/download/v0.11.1/PVFStudio-0.11.1-windows-x64-setup.exe) | 按向导安装到当前用户目录，提供开始菜单入口和可选桌面快捷方式 |
| [Windows 便携 ZIP](https://github.com/firekula/PVFStudio/releases/download/v0.11.1/PVFStudio-0.11.1-windows-x64-portable.zip) | 解压后运行 `PVFStudio/PVFStudio.exe`，适合独立目录或多版本使用 |
| [SHA-256 校验文件](https://github.com/firekula/PVFStudio/releases/download/v0.11.1/PVFStudio-0.11.1-SHA256SUMS.txt) | 核对安装器和便携包的文件完整性 |

历史版本和完整发布说明见 [GitHub Releases](https://github.com/firekula/PVFStudio/releases)。发行包不包含游戏 PVF / NPK，打开与预览时选择自己的文件和资源目录。

便携版包含以下两项，请一起保留和移动：

```text
PVFStudio/
├─ PVFStudio.exe
└─ app/              产品模块、平台库、JBR、启动配置和许可证
```

下载后可在 PowerShell 中执行 `Get-FileHash <文件路径> -Algorithm SHA256`，与校验文件中的对应条目比较。当前启动器和安装器未配置代码签名。

## 主要功能

| 能力 | 内容 |
| --- | --- |
| 脚本编辑 | 语法着色、撤销/重做、差异比较、编码选择、文件内查找与替换 |
| 全文搜索 | 搜索整个 PVF，包含草稿与未保存编辑；支持路径过滤、大小写、编码、取消和行列跳转 |
| 资源预览 | 直接索引 NPK / IMG，查看图标、ANI 动画和 PTL 粒子，支持播放、逐帧、时间轴与缩放 |
| 地图与副本 | MAP / TIL 场景组合、实体待机、图层与视差、通行范围、带滚动边界的 640×480 客户端视口；DGN 布局、房间候选和门状态预览 |
| 草稿与导出 | 独立草稿、持久会话、原文差异、保真导出、CRC 与回读校验、导出前备份及恢复 |
| 工作区 | 最近文件、单行标签、右键菜单、批量关闭，以及标签顺序、编码、光标和滚动位置恢复 |

## 使用方法

### 1. 打开与编辑 PVF

1. 运行 `PVFStudio.exe`，选择“文件 → 打开 PVF…”（Ctrl+O）。也可从“打开最近的文件”（Ctrl+R）恢复已有工作区。
2. 在“文件 → 打开编码”中选择配置实际使用的 GBK / CP936、MS949 / CP949 或 UTF-8。
3. 双击左侧条目进行编辑；用路径搜索或 Ctrl+P 定位文件，用 Ctrl+F / Ctrl+H 查找和替换正文。
4. 用 Ctrl+Shift+F 搜索整个 PVF。双击结果跳转到文件及具体位置；最多显示 5000 个结果。
5. 选择“查看 → 查看差异”核对修改，按 Ctrl+S 保存到独立草稿工作区。

**Ctrl+S 保存草稿；“导出新 PVF”才会生成修改后的配置包。** 编辑器保持源包只读。

也可将 PVF 路径作为启动参数：

```powershell
.\PVFStudio.exe 'C:\Game\Script.pvf'
```

### 2. 预览资源、地图与副本

在右侧“资源预览”中选择客户端目录或 `ImagePacks2`。目录关联会保存在当前工作区；PVF 同目录存在 `ImagePacks2` 时可自动关联。

| 打开的配置 | 预览方式 |
| --- | --- |
| 物品、装备等脚本 | 在下拉框中选择图标、IMG 帧或 ANI 引用 |
| `.ani` / `.ptl` | 播放、暂停、逐帧、倍速和时间轴检查；部分 IMG 路径可通过“外观参数”替换数字占位符 |
| `.map` / `.til` | 默认显示完整场景；拖动平移、滚轮缩放，按需开启碰撞范围、对象标记和客户端视口 |
| `.dgn` | 切换布局、起点和 Boss，点击布局图查看房间，并选择匹配的 MAP 候选 |

地下城碰撞层用红色表示禁行、橙色表示仅浮空可通行；城镇只显示青色可移动区域，范围为所有矩形的并集。客户端视口带边框和相机坐标，按原生地块范围限制滚动。

DGN 会按房间连接加入普通门、Boss 门和墙体，并合并门的通行格。“房间已清理”和“Boss 条件已满足”控制预览状态，点击播放或拖动时间轴查看原生开闭动画。副本方案按脚本候选生成，可复现，但不恢复某次在线会话的随机结果，也不执行 AI 或战斗。

预览支持当前脚本及已打开引用配置的未保存快照；资源目录、配置、帧号或格式错误会在面板中显示诊断。具体支持范围见 [资源预览](docs/resource-preview.md)、[地图场景](docs/map-scene-preview.md) 和 [副本预览](docs/dungeon-preview.md)。

### 3. 导出、备份与恢复

选择“文件 → 导出新 PVF…”，指定输出位置。导出默认先备份：首次导出备份原包，更新已有导出文件时备份该文件更新前的内容；备份放在输出目录的 `backups/` 中。

新包经过目录、条目 CRC、内容和布局回读校验后提交。备份失败时停止导出，导出失败时保留旧目标。原包及其文件别名不能作为覆盖目标。需要恢复时，可直接打开某个备份 PVF 检查其内容。

默认用户数据位于 `%USERPROFILE%\.pvf-studio\`：

- `workspaces/`：按源包绑定的草稿、标签会话、编码和编辑位置。
- `platform/product/`：平台配置、缓存及日志。

升级前关闭旧版。便携版解压到新目录，安装版运行新版安装器；已有草稿、资源目录关联和标签会话继续保留。卸载程序保留用户目录中的草稿与设置。

### 常用快捷键

| 快捷键 | 操作 |
| --- | --- |
| Ctrl+O / Ctrl+R | 打开 PVF / 最近文件 |
| Ctrl+P | 按路径定位文件 |
| Ctrl+F / Ctrl+H | 文件内查找 / 替换 |
| Ctrl+Shift+F | 全局正文搜索 |
| Ctrl+S | 保存当前文件的独立草稿 |
| Ctrl+Z / Ctrl+Y | 撤销 / 重做 |
| Alt+Z | 切换自动换行 |

标签操作和其他组合键见 [标签与会话说明](docs/editor-tabs.md)。

## 源码构建

### 构建环境

| 组件 | 要求 |
| --- | --- |
| JDK | 桌面产品构建使用 JDK 21+，已使用 JDK 25 验证；核心与 CLI 编译目标为 Java 17 |
| Gradle | 使用仓库中的 Gradle Wrapper 9.5.0 |
| Python | 产品依赖准备及独立验证脚本需要 Python 3.11+ |
| MinGW-w64 | 桌面版需要 x64 `gcc.exe` 和 `windres.exe`，加入 PATH |
| Inno Setup | 生成安装器时需要 6.3+；仅构建便携包不需要 |

首次构建桌面版会下载固定的 Community SDK 和无 JCEF 的 vanilla JBR，校验后用于编译和模块装配。基线记录在 [platform.lock.json](pvf-product/platform.lock.json)。SDK 作为构建输入使用，不整包复制到发行目录。

### 构建 Windows 发行包

在仓库根目录的 PowerShell 中执行：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'

# 安装器、便携 ZIP 和 SHA-256 校验文件。
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-release.ps1

# 只生成便携 ZIP 和校验文件。
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-release.ps1 -ZipOnly
```

脚本执行测试、原生启动器编译、产品装配和身份检查，然后打包。输出位置：

```text
pvf-product/build/product/PVFStudio-0.11.1/      可直接运行的程序目录
pvf-product/build/distributions/               安装器、便携 ZIP、SHA-256
```

可传入 `-IsccPath` 指定 Inno Setup 编译器，使用 `-IntellijLocalPath` / `-RuntimePath` 复用固定依赖，或用 `-StandaloneDirectory` 将产品装配到新的独立目录。

构建后运行产品，或执行带真实资源的完整检查：

```powershell
.\gradlew.bat -PwithIntellij=true :pvf-product:runProduct

python tools/check_product.py `
  --product 'pvf-product/build/product/PVFStudio-0.11.1' `
  --archive 'C:\Game\Script.pvf' `
  --npk 'C:\Game\ImagePacks2'
```

`--archive` / `--npk` 是可选的集成检查输入；只检查产品身份时可省略。验证输出写入 `build/product-checks/`。

### 构建核心与 CLI

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
.\gradlew.bat check :pvf-cli:installDist
python -B -m unittest discover -s validation -p 'test_*.py'

$cli = '.\pvf-cli\build\install\pvf-studio\bin\pvf-studio.bat'
& $cli --help
& $cli info 'C:\Game\Script.pvf'
```

核心与 CLI 运行要求 Java 17+，构建它们不会下载 IntelliJ SDK。CLI 支持目录查询、提取、字节替换、持久草稿和无修改重建；示例及验证方法见 [CLI 与开发验证](docs/development-validation.md)。

应用图标和运行资源已经包含在源码中，正常构建无需生成图像。调整图标后，可在安装 Pillow 的 Python 环境执行 `python tools/build-app-icons.py`，从原图重新导出 ICO 与平台资源；设计及来源见 [图标说明](assets/branding/README.md)。

## 支持范围与验证

当前面向已验证的 ACT1 `DNF_SCRIPT_PACK` 样本，支持修改已有条目；暂不支持新增、删除、重命名条目或其他未经验证的 PVF 变体。源路径、条目顺序、补齐和布局按保真规则处理，详情见 [格式说明](docs/pvf-format.md)。

预览展示配置中定义的视觉和通行数据，支持已实现的图层、变换、混合与粒子规则；没有执行角色 AI、战斗、寻路或完整动态碰撞。编码须能无损转换，混合换行条目暂只读。早期 0.1.0 导出的修改包需要从有效原包和草稿重新导出，原因见 [Changelog](CHANGELOG.md)。

发行验证包括 130 项 JVM 测试、14 项独立 Python 测试、产品身份和实际工作台冒烟，以及 ZIP CRC / 内容 / 摘要检查。副本验证样本覆盖 43 个 DGN、49 套布局、178 组起点/Boss 组合和 242 个房间开闭状态。测试范围、历史结果和客户端证据见下列文档。

## 文档

| 文档 | 内容 |
| --- | --- |
| [更新日志](CHANGELOG.md) | 版本功能、修复和发布记录 |
| [资源预览](docs/resource-preview.md) | NPK / IMG、ANI、PTL 与未保存配置联动 |
| [地图场景](docs/map-scene-preview.md) | MAP / TIL、实体、图层、视差、相机与通行范围 |
| [副本预览](docs/dungeon-preview.md) | DGN 布局、房间候选、门对象和预览状态 |
| [标签与会话](docs/editor-tabs.md) | 标签菜单、快捷键、草稿和重开恢复 |
| [格式说明](docs/pvf-format.md) | 文件格式、CRC、保真边界和导出语义 |
| [独立产品开发](docs/intellij-product.md) | 产品模块、平台基线、构建与二次开发 |
| [CLI 与开发验证](docs/development-validation.md) | 命令行示例、真实 PVF 验证及历史结果 |
| [客户端预览证据](docs/client-preview-evidence.md) | 原生函数对照和预览验收 |
| [客户端验证记录](docs/client-validation-2026-10-24.md) | 导出包实际加载的样本与结果 |

## 项目结构

| 模块 | 职责 |
| --- | --- |
| `pvf-core` | 按需读取、保真重建和导出校验 |
| `pvf-workspace` | 编码转换、草稿、工作区锁与导出快照 |
| `pvf-cli` | 查询、提取、替换和草稿操作 |
| `pvf-preview` | NPK / IMG、动画、粒子、地图与副本解析和渲染 |
| `pvf-product` | IntelliJ 桌面产品、原生启动器装配和集成检查 |
| `tools` / `validation` | 构建、发行与独立验证工具 |
| `assets/branding` | 应用图标原图、预览与设计说明 |

游戏 PVF / NPK、提取资源、个人草稿和验证输出不属于项目源码，请保存在 `build/` 或仓库外。问题反馈可在 [GitHub Issues](https://github.com/firekula/PVFStudio/issues) 中提供版本、复现步骤和相关诊断信息。

## 许可证

项目源码采用 [BSD Zero Clause License（BSD-0-Clause）](LICENSE)。IntelliJ Platform、JBR 和第三方组件继续适用各自的许可证；发行包在 `app/LICENSE`、`app/licenses/` 和 `app/jbr/legal/` 中保留相应文件。
