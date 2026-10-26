# 经典二进制 ANI（0.12.3）

DOF86 的大部分 `.ani` 使用独立的二进制动画结构，不是 `B0 D0` token 脚本。0.12.3 将已识别的内容显示为 `#PVF_File`、`[FRAME MAX]`、`[FRAME000]`、`[IMAGE]` 等标签，接入现有编辑器、差异、搜索和动画预览。Ctrl+S 编译为二进制草稿，“导出新 PVF”写入原容器；源包保持只读。

编码仍使用当前标签选择的编码。86 验证使用 Big5；无法无损解码的字符串会给出原因，可按实际资源编码手动切换。

## 结构与编辑

已识别结构采用小端序：16 位帧数和图片表数量、带 32 位长度的图片路径、全局指令表，以及逐帧判定框、图片引用、图片帧号、位置和属性表。负图片编号表示无图片，省略图片帧号字段。指令编号与字段宽度参考公开的 [binaryAni.ts 格式实现](https://github.com/zjysun/dnf-pvf-parser-ts/blob/main/src/pvf/binaryAni.ts)，本项目使用独立的类型化编解码实现，并以真实样本检查原始字节。

| 范围 | 已识别标签 |
| --- | --- |
| 全局 | LOOP、SHADOW、COORD、SPECTRUM 及子字段、OPERATION |
| 帧 | IMAGE、IMAGE POS、IMAGE RATE、IMAGE ROTATE、RGBA、INTERPOLATION、GRAPHIC EFFECT、DELAY |
| 其他帧属性 | DAMAGE TYPE、DAMAGE BOX、ATTACK BOX、PLAY SOUND、PRELOAD、SET FLAG、FLIP TYPE、LOOP START、LOOP END、CLIP，以及帧内 LOOP / SHADOW / COORD |

帧号从 FRAME000 连续排列，数量必须与 FRAME MAX 一致。整数按字段宽度检查，浮点数必须为有限值；路径和声音字符串用反引号包围，支持与二进制 token 脚本相同的转义。新增图片追加到原图片表。未知标签、错误参数数量或越界值阻止保存，已有草稿保留。

无修改重编译保留原图片表字节和顺序、未使用项、重复项、指向空字符串的正引用、负图片编号、指令顺序、有限浮点位模式及最多 3 字节尾部填充。表项不会因修改或删除引用而自动回收。

## 预览边界

图片、位置、延迟、缩放、旋转、RGBA、已实现的混合及翻转接入现有预览器，支持播放、逐帧和时间轴。资源需选择同版本客户端的 ImagePacks2。

残影 SPECTRUM、CLIP、局部 LOOP START / LOOP END 和 OPERATION 保留在文本和编译结果中，预览显示尚未模拟的诊断；判定框、伤害类型、标记及音效也不等于执行游戏逻辑。解析成功不保证所有客户端视觉效果均已复现。

未知指令、未知枚举、截断、非法图片引用、无法无损解码的字符串和超过 3 字节的未识别尾部会返回具体原因，编辑器继续显示只读十六进制，搜索跳过这些条目。

## 验证

2026-10-26，DOF86 源包 SHA-256 为 `f218ee22493e4c78963c8e99ab5cbf3a8dd13a7b845930705607defb08ca2fc0`。全量扫描 404,197 个 `.ani`：

| 结果 | 数量 |
| --- | ---: |
| 二进制解码后重编译字节完全一致 | 402,701 个，3,481,734 帧 |
| 暂不支持，保留原始内容 | 493 个 |
| 其他脚本格式或空/极短内容 | 1,003 个 |
| 重编译字节不一致 | 0 个 |

实际 IntelliJ 工作台验证 `map/246_energycut/animation/back_steam.ani`（9 帧）、`boil_1.ani`（5 帧）、`bones_1.ani`（1 帧）的图片预览，以及播放、逐帧、文本编辑、差异、保存和导出重开。独立 Python 检查确认 back_steam 的 9 个延迟从 150 改为 175 后，其余字节完全不变。该验证是编辑器和归档验收，尚未执行修改包的游戏客户端运行验收。

在仓库根目录执行（JAVA_HOME 为 JDK 21+）：

```powershell
.\gradlew.bat check :pvf-workspace:classes
& "$env:JAVA_HOME\bin\java.exe" -Xmx2g `
  -cp 'pvf-core/build/classes/java/main;pvf-workspace/build/classes/java/main' `
  validation/BinaryAniAudit.java 'C:\Game\Script.pvf' 'build/ani-audit'

python -B tools/check_product.py `
  --product 'pvf-product/build/product/PVFStudio-0.12.3' `
  --archive 'C:\Game\Script.pvf' --npk 'C:\Game\ImagePacks2' --binary-ani
```

`--binary-ani` 工作台检查使用上述 86 样本路径；输出写入独立的 `build/product-checks/` 子目录。游戏文件、提取资源、导出包和截图只作为本地验证产物，不加入源码或发行包。
