# DGN 副本预览

打开 `dungeon/` 下的 `.dgn`，资源下拉框默认选择“副本布局”。选择 NPK 目录后，面板会读取 DGN、`dungeon/dungeon.lst` 和 `map/map.lst`，为选定布局准备房间候选。

0.13.5 的布局、起点/Boss 和“换一组房间”位于“预览设置”中；房间小地图、MAP 候选和清理状态保持显示。资源路径、通路门原始坐标及诊断通过“显示详情”展开，折叠时仍显示提示数量。小地图高度按布局行数分配，避免一行布局占用多余画布。

- “副本布局”切换 DGN 内的各个 `[maze info]`；起点和 Boss 下拉框使用各自的坐标列表。
- 点击布局图里的房间查看完整 MAP。绿框表示起点，橙框表示 Boss，蓝框表示当前房间；连线表示两端对应的通路。红边表示没有匹配的 MAP。
- 布局下方可切换当前房间的 MAP 候选；离开再返回时保留手动选择与该房间的清理状态。
- “换一组房间”更换 MAP 候选组合，保持当前布局、起点与 Boss。详情显示方案编号，同一输入和编号的结果可复现。
- “房间已清理”控制有通路的门是否开启。DGN 配置了 Boss 入口条件时，还可切换“Boss 条件已满足”。这些是预览状态，不执行战斗、任务或物品条件。
- 门使用原生开门/关门 ANI。场景初始暂停在动画起点；点击播放、逐帧或拖动时间轴检查门的变化。切换门状态会保留当前相机与缩放。
- 房间继续使用原有的全图、640×480 客户端视口、滚动边界、碰撞层、对象标记及粒子时间轴。

只有 DGN 提供副本排列上下文。独立打开 MAP 仍是单房间场景，不推断它在某个副本中的门状态。

## 配置与排列

`[size]` 表示布局宽高；当前 ACT1 的 `[greed]` 使用 `AA`～`PP`，编码值为 0～15。方向位为右 1、上 2、左 4、下 8，`AA` 为未使用格。原生布局初始化为零，因此短于 size 的 greed 剩余格保持空白；教程地图的 `AA` 起点仍作为单房间预览。Boss 坐标 `-1 -1` 表示无 Boss。

自动候选来自 MAP 的 `[dungeon]` 注册、`[type]` 以及 `[greed]` 支持的形状。普通房间优先使用坐标的 `[map specification]`；Boss 房优先使用 `[boss map specification]`。0.13.3 中，显式指定列表按编号保留对应普通/Boss 类型的 MAP，greed 差异改为诊断，不再按自动候选规则排除。普通指定列表没有 Boss MAP 时，使用副本注册的 Boss 类型候选。编号缺失、文件不存在或读取失败仍显示诊断，不以猜测的文件名或无关地图填充。

MAP greed 的形状/入口值均保留；目前没有玩家出生和房间间移动模拟。客户端加载已经确定的 MAP 编号；编辑器按脚本候选生成可复现的预览方案，不能恢复某次在线会话的随机结果。原始 MAP/DGN、草稿与编辑器中的未保存快照共用已有读取机制，预览不改写脚本。

解析限制为每份 DGN 最多 256 套布局、每套最多 50×50 格、MAP 索引最多 8192 项。未知形状、越界坐标、无匹配候选或缺失门资源会显示错误或诊断。布局中指向地图外或未被相邻格反向连接的出口会关闭并提示。

## 86 版二进制脚本

0.13.1 修复二进制脚本的字符串投影在 greed 中包含 `\r\n`、`\n` 或 `\t` 时的解析。形状码解析将这些序列仅作为空白分隔，原始正文和二进制字符串不变；不接受其他未知转义或形状。

DOF86 的 449 个 DGN 中，431 个、563 套布局解析成功。剩余 18 个属于所选 Big5 编码无法无损解码、特殊结构整数参数或没有静态 maze/warroom 布局等情况，继续保留具体诊断。

实际工作台验证普鲁兹 `fruz_station`、科勒 `kohlepowerstation`、格兰迪 `grandine`、觉醒版和团队版 `blackvolcano` 的布局、普通/Boss 房间、门状态和播放/逐帧。普鲁兹的 15 个房间全部匹配 MAP。0.13.3 之后，科勒第二套布局的默认起点 `(0,1)` 会按 DGN 明确指定的 MAP 显示，保留门型差异说明。

在仓库根目录执行（JDK 21+）：

```powershell
.\gradlew.bat check :pvf-workspace:classes :pvf-preview:classes
& "$env:JAVA_HOME\bin\java.exe" -Xmx2g `
  -cp 'pvf-core/build/classes/java/main;pvf-workspace/build/classes/java/main;pvf-preview/build/classes/java/main' `
  validation/DungeonCompatibilityAudit.java 'C:\Game86\Script.pvf' 'build/dgn86-audit'
python -B tools/check_product.py `
  --product 'pvf-product/build/product/PVFStudio-0.13.1' `
  --archive 'C:\Game86\Script.pvf' --npk 'C:\Game86\ImagePacks2' --dungeon-86
```

审计输出全部解析失败原因及所选副本的候选统计；工作台检查输出截图和结果到独立的 `build/product-checks/` 子目录，不修改源包或个人工作区。

## 100 级样本的显式房间

已验证样本为“100级真女鬼剑单机第八版”的新客户端，UUID 容器含 929,093 个条目。本次针对 DGN 关联及通路检查，不代表验证了该版本的全部配置和导出效果。

格兰迪 `(4,1)` 的 DGN 明确指定编号 `15415`，map.lst 指向 `map/grandine/15413(2,11)normal.map`；该文件的 greed 为 PP，而布局要求 FF。0.13.2 将其按门型过滤后显示为空。0.13.3 保留显式关联并显示差异，按 DGN 布局判定通路，使用 MAP 原始 pathgate pos；不根据文件名里的坐标替换资源。

真实工作台检查普鲁兹、科勒、格兰迪共 8 套默认起点/Boss 布局、105 个房间，均有关联 MAP。另核对格兰迪 `(1,2)` 的上门 `(779,107)`、`(4,1)` 的左门 `(46,195)` 和右侧 Boss 门 `(847,195)`，以及开闭状态。房间详情新增通路门的原始坐标及相邻房间。地面尺寸、门的坐标和投影没有在此版本中调整。

```powershell
python -B tools/check_product.py `
  --product 'pvf-product/build/product/PVFStudio-0.13.3' `
  --archive 'C:\Game100\Script.pvf' --npk 'C:\Game100\ImagePacks2' --dungeon-100
```

该检查使用上述三个副本及编号，结果和截图仍保存在独立的 `build/product-checks/` 子目录。

## 客户端证据

沿用 [场景客户端证据](client-preview-evidence.md) 中的同一份 ACT1 客户端与 PVF。原始提取结果位于 `build/client-coordinate-evidence/dungeon-*.txt`。

| 客户端位置 | 规则 |
| --- | --- |
| `0x725CE0` | 读取 DGN 的门对象、布局宽高、形状及普通/Boss 指定列表、起点与 Boss 列表。左右各 3 个对象，上下各 2 个对象。 |
| `0x748D40` | 读取 MAP 的 dungeon、type、greed 与四个 pathgate pos，门坐标顺序为左、右、上、下。 |
| `0x46B870 → 0x464A70 → 0x464540` | 用已选 MAP 编号与布局形状加载具体房间。 |
| `0x462E10` | 方向索引 0/1/2/3 分别检测左 4、右 1、上 2、下 8。 |
| `0x467190` | 无出口时选墙体，有出口时选普通门；当前房间或邻居为 Boss 房时选 Boss 门。对象放在 MAP pathgate pos 的坐标。上下方向没有墙体槽时不创建对象。 |
| `0x57C6E0` | 门 OBJ 的 string data 与 int data 逐项对应。ANI 类型 0 常驻、1 关闭时、2 开启时；variation 为 -1 时直接加载，否则使用 `(0, variation)`。TIL 项的整数表示门中心在通行格内的位置。 |
| `0x57C350 / 0x57C420` | 门状态改变时添加或移除关闭/开启动画，开门动画重新计时；房间状态为 1 时保持关闭。 |
| `0x57C480 / 0x462C40` | 门通行格按角色地面 x/16、y/12 与配置中心对齐，以 max 合并到房间；左右边列向对应地图边界延伸。 |

普通指定列表与 Boss 候选池的编辑器选择策略属于预览方案生成，不声称重现在线实例的抽样顺序。门创建、图层、动画状态、variation 和通行格合并按上述客户端函数实现。

## 验证

`validation/DungeonRegressionProbe.java` 只读审计真实脚本，并渲染房间开闭状态。例如：

```powershell
java -Xmx768m -cp 'build/dungeon-probe;pvf-core/build/classes/java/main;pvf-preview/build/classes/java/main;pvf-preview/build/resources/main' DungeonRegressionProbe G:\Game\DNF_ACT1\DFLegacy\DNF2008\Script.pvf build/dungeon-audit-new G:\Game\DNF_ACT1\DF\ImagePacks2
```

2026-10-25：43 个真实 DGN、49 套布局、178 组不同起点/Boss 组合完成候选检查；242 个房间开闭状态完成真实 NPK 渲染，结果在 `build/dungeon-native-final/`。没有门素材或 variation 解析错误；既有逻辑对象无待机、部分本地化 pass type 等诊断继续保留。

JVM 回归覆盖拓扑方向、Boss 门与墙体、清理/入口条件、普通与 Boss 候选隔离、固定方案、无效连接、教程空格、稀疏布局、门动画层序/variation、通行格合并，以及实际 Swing 点击、房间 MAP 选择和清理状态恢复。产品冒烟使用真实 Lorien DGN 检查开闭 ANI、相机保持、Boss MAP 和第二套布局，并保留界面截图。

最终 130 项 JVM 测试、14 项独立 Python 测试、产品结构检查及冒烟通过。0.11.0 正式发行目录为 `pvf-product/build/product/PVFStudio-0.11.0/`，发行产品检查目录为 `build/product-checks/20261025-231343-f2f5a981/`。安装器、便携包和校验文件见 [v0.11.0 Release](https://github.com/firekula/PVFStudio/releases/tag/v0.11.0)。

## 雪山帐篷与冰刺的遮挡

`Client_test2/Script.pvf` 的 `map/charnakridge/s5601.map` 在房间 (0,3) 使用 604 号右门 `passiveobject/mapobject/pathgate/act5/01rightnormal.obj`。门定义为 `[bottom]`，MAP 中 `Animation/Grocery.ani` 的帐篷定义为 `[normal]`，因此帐篷会覆盖门动画重叠部分。原版门对象把常驻、关闭/打开和灯光 ANI 挂在同一对象上，没有为冰刺另设前景层，见 [客户端分析证据](client-preview-evidence.md)。

这不是 0.13.5 的排序回归：0.13.4 与 0.13.5 在开/关门、0/500/60061ms 的 6 张场景图逐字节相同，目录为 `build/snow-baseline-0134/` 与 `build/snow-before-0136/`。仅在内存中将 604 的图层换成 normal 的对照位于 `build/snow-normal-layer-preview-0136/`，原始 PVF 未修改。该模板由多个房间共用；实际改动其图层会作用于使用它的所有右侧普通门。
