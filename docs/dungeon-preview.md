# DGN 副本预览

打开 `dungeon/` 下的 `.dgn`，资源下拉框默认选择“副本布局”。选择 NPK 目录后，面板会读取 DGN、`dungeon/dungeon.lst` 和 `map/map.lst`，为选定布局准备房间候选。

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

候选来自 MAP 的 `[dungeon]` 注册、`[type]` 以及 `[greed]` 支持的形状。普通房间优先使用坐标的 `[map specification]`；Boss 房优先使用 `[boss map specification]`。普通指定列表没有 Boss MAP 时，使用副本注册的 Boss 类型候选。没有有效候选时显示诊断，不以无关地图填充。

MAP greed 的形状/入口值均保留；目前没有玩家出生和房间间移动模拟。客户端加载已经确定的 MAP 编号；编辑器按脚本候选生成可复现的预览方案，不能恢复某次在线会话的随机结果。原始 MAP/DGN、草稿与编辑器中的未保存快照共用已有读取机制，预览不改写脚本。

解析限制为每份 DGN 最多 256 套布局、每套最多 50×50 格、MAP 索引最多 8192 项。未知形状、越界坐标、无匹配候选或缺失门资源会显示错误或诊断。布局中指向地图外或未被相邻格反向连接的出口会关闭并提示。

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
