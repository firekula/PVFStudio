# MAP/TIL 场景预览

0.4.0 默认将 `.map` / `.til` 打开为“完整场景”，原有的单个 IMG/ANI 引用仍可从下拉框选择。资源目录、草稿和编辑器快照继续沿用资源预览机制。

0.5.0 增加地图实体待机：NPC 使用 `npc/npc.lst` 与 `[field animation]`；怪物使用 `monster/monster.lst` 与 `[waiting motion]`；被动对象使用 `passiveobject/passiveobject.lst` 与 `[basic motion]`。定义路径先相对于列表解析，动画相对于其定义解析；索引不要求连续，注释条目不参与解析，重复编号会报错。

## 实体待机与标记

NPC、怪物及普通/特殊被动对象依 MAP 的 x/y/z 放置，绘制坐标为 `(x,y-z+120)`（0.6.0 按原生客户端修正）。NPC 的 left 在实体脚下原点翻转，right 保持资源方向；不围绕裁剪后的小图中心翻转。未配置方向的怪物与物体使用资源原始方向。normal 层按地面 y 与同层 ANI 一起排序，Z 不改变地面深度；其他层保留创建顺序。

怪物 `[equipment]` 相对于 PVF 的 `equipment/` 解析，装备各自的 waiting motion 根据数字 LAYER 排在身体（0）前后。0.6.0 另加载客户端隐式装配的 104 个怪物编号变体（石巨人、牛头怪、骑士、守护者、熔岩巨人等），同装备槽位由后装部件替换；每件装备使用自身 variation，客户端显式参数按配方覆盖。被动对象 `[add object effect]` 也按附加层序绘制。各层共用绝对场景时间，保留各自 DELAY/LOOP。

每条放置配置预览一个实体，不依据玩家数复制 half/ratio 怪物。`[special passive object]` 同时支持同行/次行的内容数量，以及 item/monster/trap/quest 内容；仅渲染容器，不提前生成内容。`[monster condition]` 通过“条件怪物”开关单独显示，默认关闭；开启只显示候选放置，不判断条件是否满足。

“对象标记”显示实体脚下原点和编号；悬停可查看索引、定义、坐标、方向及异常。没有基础动画的工厂/逻辑对象和未注册索引保留标记，不擅自把 etc motion 或被注释的列表项作为待机。当前帧的 IMG 解码失败也会显示提示。位于地图边界外的实体仍遵循地图裁剪。

列表、定义、装备与 ANI 的读取都使用同一编辑器快照/草稿覆盖层。未保存修改后重新选择 MAP，即可看到更新；实体状态不保存回源包。

0.13.2 按字段结构拆分二进制脚本投影中同一行的多个实体，兼容旧文本行。86 版 MOB/OBJ 没有直接待机 ANI 时，会沿 waiting action/basic action 读取 ACT 初始 MOTION 的基础和零起始帧子动画；部件按层序和 XY/XYZ 偏移叠加。没有运行 ACT 的触发器和行为，完整支持范围及真实样本见 [ACT 实体预览](action-preview.md)。

## 地块与区域

0.6.0 的坐标和部件装配以 [客户端分析证据](client-preview-evidence.md) 为准；旧版样例推断的 16×16 通行格、160 扩展行及无 120 偏移已修正。

格式依据 ACT1 包内 `sample/mapsample.map`、`sample/tilesample.til` 的说明，以及真实地图与 NPK 帧元数据验证。

- TIL 的 IMAGE 列表按文件顺序叠加，保留 IMG 裁剪偏移。常规 basic 图像区域为 224×480（顶部 120 + 世界高度 360），extended 为 224×120；基础世界高度取首块 TIL 实际行数乘 12。
- 0.13.4 读取新版 TIL 的 `[img pos]`：基础图在上述原点减去该值，通行格与物件的世界坐标不变。已验证的 86/100 级素材常为 224×560，配置 80 后衔接至 y=480；扩展图仍从 y=480 开始，按 120 像素逐行拼接，不再额外扣除 80。未配置时取 0，保留 ACT1 原点。这是地块图片的偏移，不是统一修改所有层的坐标，也不把 DGN 的同值标签再次叠加。
- `[pass type]` 为 14 列、16×12 的格子；basic 通常 30 行，extended 每行使用 10 行。0 为全部可通行，1 为仅浮空单位可通行，2 为全部不可通行。basic 按实际行数显示，20 行基础块合法；没有通行格会提示，extended 多余行不参与叠加。
- MAP 的 `[tile]` 横向拼接。设基础地块数量为 N，`[extended tile]` 的第 i 块放在 `x=(i%N)*224`、`y=baseWorldHeight+120+(i/N)*120`，其中 i 从 0 开始、除法向下取整；不完整扩展末行按客户端规则忽略并提示。
- MAP 的客户端相机范围由地块决定，边界外的装饰不会改变相机限制。0.13.0 的总览自适应包含当前可绘制装饰和远景的视觉范围，不再按地块矩形裁掉上下及边缘装饰；重复背景横向仍限制在地图范围。
- 地下城和独立 TIL 的碰撞开关控制半透明红/橙格；悬停查看 TIL 路径、格坐标和类型。城镇地图只显示 `[virtual movable area]` 的青色边框，可移动范围是所有矩形的并集，不显示 TIL 或对象的红/橙碰撞标记；悬停说明当前位置在城镇可移动区域内还是区域外。

这是通行数据的可视化，没有执行角色移动、寻路或动态物体碰撞。

## 动画、图层与视差

MAP 的 `[animation]` 按路径、层、x/y/z 解析，屏幕位置使用 `(x,y-z+120)` 加 ANI 自身参数。按客户端实际顺序绘制 distantback、middleback、closeback、bottom、normal、close；地块位于 closeback/below 背景之后、closeback 对象之前。0.13.5 按原版 `0x4C53D0 / 0x4C5230` 修正：仅 normal 层按地面 Y 稳定排序，同深度保留创建顺序；其余层按创建顺序绘制，不能将 close、middleback 等层也按 Y 重排。`[background animation]` 从原点沿 x 重复铺绘，按 below/upper 放在所属层之前或之后；close/below 属于前景，不能提前到地块之前。

真实 `map/kohlepowerstation/18008(4,1)b.map` 的 close 层先配置 5 份 rail_4，再配置 downstair_1、downstair_2。旧排序因 Y=350 小于栏杆的 377，把 downstair_1 提前而被栏杆覆盖；middleback 的 upstair 原本位于配置末尾，旧排序将其提前到大柱背景之前，导致上行楼梯被覆盖。保留配置的创建顺序后，楼梯入口和上行楼梯恢复。原版静态证据见 [客户端分析](client-preview-evidence.md)，实际资源前后渲染位于 `build/powerstation-before-0135/`、`build/powerstation-after-0135/`。视口仍按 640×480 裁剪，视口下边缘以外的楼梯应通过移动相机或切回完整场景查看。

0.13.0 同时接受经典平铺三元组和 86 版嵌套的 `[ani info]`，从其中的 `[filename]`、`[layer]`、`[order]` 生成背景轨道；每个块须以 `[/ani info]` 结束。真实黑色火山 `map/247_blackvolcano/58461(0,1)s.map` 的 `Animation/Far.ani` 已验证图片解码和场景绘制。视口选项与画布状态保持同步，MAP → ANI → 关闭视口 → MAP，以及 MAP → MAP → 关闭视口 → 原 MAP 均通过总览像素一致检查。

远/中/前景横向使用 far/middle/near sight scroll 的百分比，包括居中相机和平移两部分；实体也遵循所属层的视差。MAP 的纵向比例在客户端保持 100%，不把横向百分比复用于纵向。地块、碰撞及普通图层随地图平移。全图预览以居中的原生 640×480 相机为参考。青框始终表示真实地面区域，不跟随前景变形。全图缩放支持自适应、25%–400% 和以鼠标位置为锚点的滚轮缩放。

“客户端视口”仅对 MAP 启用，按 640×480 显示原生画面。蓝色双层边框标明视口边界，外围变暗，上方显示相机位置及最大范围；边框在场景裁剪后单独绘制。左键在边框内开始拖动移动相机，抵达地图边界停止，反向拖动立即生效；缩放只改变视口显示尺寸，保持相机位置，调整窗口大小也不会重置相机。

相机 X 范围为 `0…max(0,基础地块数×224−640)`，Y 为 `0…floor(扩展地块数/基础地块数)×120`。不足一整行的扩展块不增加范围；较矮的基础图也不会缩小 Y 范围。客户端视口使用整数相机坐标和整除后的逐层横向偏移，画面仅按视口裁剪，超出地块图像的装饰仍可显示；没有内容处显示黑色。独立 TIL/PTL 使用全图预览。此模式还没有角色跟随目标或事件驱动的摇屏/相机转场，不声称完整游戏所有动态状态的像素级一致性。

Darkhell 的 333～337 号对象由原生滚动场景类创建。预览按 OBJ 的 int data[0] 水平平铺，保留放置坐标、ANI 插值和所属层视差；平铺周期独立于 IMG 的画布宽度。全图模式覆盖可见区域，避免背景随 ANI 负向位移离开画面。int data[1] 是原生场景事件控制的纵向行程，预览保留初始放置，不把它错误地当作自动循环时间。

0.13.4 补充实测列车海贼 723 号对象 `pirateonthetrainscroll_2.obj`：间距为 556，ANI 从 x=0 插值至 -556、周期 8000ms。此前被当作普通单份物件，导致右侧空白。只扩展已核对的场景对象编号，不泛化所有带 int data 的 OBJ。`validation/MapAlignmentAudit.java` 在真实 100 级列车目录的 28 张 MAP、204 个基础地块及 5 个时刻核对背景覆盖，包含循环前后与左右客户端相机；结果与渲染图位于 `build/pirate-alignment-0134/`。工作台同时检查格兰迪基础图/扩展行衔接、列车全景和右端客户端视口。

同轮检查覆盖 100 级格兰迪 19 张及 86 版普鲁兹 16 张 MAP，5 个时刻均无资源诊断。ACT1 的 Darkhell/Alfhlyra 总览、通行层和客户端视口共 36 张渲染图与 0.13.3 的输出逐字节一致，证据位于 `build/act1-alignment-baseline-0133/` 与 `build/act1-alignment-0134/`。100 级实际工作台截图位于 `build/product-checks/20261026-114848-1e566f36/smoke-results/`。

普通透明混合和 LINEARDODGE 源透明度叠加可用。0.7.0 支持场景帧间参数插值，其他特殊混合仍会提示，不把未实现的效果当作与客户端一致。

## 持续播放

使用 `System.nanoTime()` 的单调时钟记录场景时间。每次刷新用同一个绝对毫秒值分别查询各 ANI 的累积 DELAY：循环 ANI 对自己的周期取模，非循环 ANI 到达末帧后保持。零时长帧直接跳过，全零时长动画停在末帧。无需计算所有动画周期的最小公倍数，也不会在一个动画循环时重置其他轨道。

播放目标刷新间隔为 16 ms；耗时解码后按当前经过时间继续采样，避免逐帧等待累计漂移。暂停、恢复、倍速和定位保留相位关系。“强制循环”覆盖 ANI 的非循环标记，各轨道仍独立取模。时间轴窗口每 60 秒向前移动，场景时间本身继续增长；用户可以暂停后拖动或按 16 ms 步进检查边界。

后台解析/解码与过期结果隔离继续有效；切换配置不会把旧场景回填到新标签。修改当前 MAP/TIL 或已打开的被引用 TIL/ANI 后重新加载编辑器快照；未打开文件从工作区草稿或原 PVF 读取。

## 边界与验证

已加载 NPC、怪物与被动对象的待机及附属动画，0.7.0 已预览原生地图粒子工厂；未执行 AI、战斗、移动、销毁、掉落或动态碰撞；不使用浮动高度参数额外改变 MAP 的显式 Z。多 IMAGE TIL 按源顺序绘制。图层、视差、朝向及光效尚未逐项与原生客户端截图进行像素级对照。部分资源错误会留下其余可绘制内容，并在面板中列出诊断。

66 项 JVM、14 项 Python 测试通过。真实 ACT1 样本的 1,092 个 MAP、619 个 TIL 全部解析通过；森林、城镇、含两排扩展地块的地图以及独立 TIL 完成组合渲染。产品界面检查覆盖碰撞开关、播放/暂停/步进、跨分钟定位、未保存 TIL 和切换配置后的过期回调，原有 PVF 编辑/导出/备份恢复冒烟继续执行。

0.5.0 总计 78 项 JVM、14 项 Python 测试通过。实际地图实体放置记录没有解析错误；教学样例里用作说明的 6 行文本单独记录在 template-notes。对地图引用的 576 个类型/编号组合逐个查索引、读取待机与附属动画并解码：538 个成功，26 个没有 basic motion，12 个没有注册索引（NPC 43、46–54，被动对象 5100、5101）。森林样本显示 74 个配置实体；城镇、NPC 翻转、装备层序、特殊容器、条件开关、对象标记及未保存 OBJ 通过测试。

运行命令（先构建产品，设置 JAVA_HOME）：

```powershell
.\gradlew.bat check -PwithIntellij=true :pvf-product:verifyProduct
python -B tools/check_product.py --product pvf-product/build/product/PVFStudio-0.10.0 `
  --archive 'G:\path\Script.pvf' --npk 'G:\path\ImagePacks2'
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 `
  -cp 'pvf-core/build/classes/java/main;pvf-preview/build/classes/java/main;pvf-preview/build/resources/main' `
  -d build/preview-probe validation/SceneProbe.java
& "$env:JAVA_HOME\bin\java.exe" '-Djava.awt.headless=true' `
  -cp 'build/preview-probe;pvf-core/build/classes/java/main;pvf-preview/build/classes/java/main;pvf-preview/build/resources/main' `
  SceneProbe 'G:\path\Script.pvf' 'G:\path\ImagePacks2' 'build/scene-new-run'
```

输出为 `build/` 下的新目录，包含解析失败列表、逐场景提示、0/450ms 的正常及碰撞层 PNG。另采样 1234ms 和 60061ms 检查长时间播放。原始游戏数据保持只读，提取图片和游戏数据不提交源码仓库。

实体检查额外输出 `entity-audit.txt`（全部引用编号）和 `entities-N.txt`（每个样本的实体定义、动画和状态），保留原包缺失索引及逻辑对象的证据。

0.6.0 增加客户端坐标与合成部件验证：83 项 JVM、14 项 Python 测试通过；全部 289 个 monster.lst 定义在四个时间点绘制无资源错误，117 个包含多个部件。产品截图新增 lorien/104 和 ocean/b2401，详见客户端证据记录。

## 粒子、对象碰撞与多区段装备（0.7.0）

地图还会加载经 ACT1 客户端核对的粒子工厂，显示 PTL 创建的 ANI、IMG 或被动对象视觉。粒子共用绝对时钟，并按自身绘制层、地面深度和视差参与排序；不放在场景最上方。原生初始进度会预填光束/鱼群等长寿命效果；覆盖地图宽度的暴风雪按地图尺寸扩展生成区域和频率。

地下城的“碰撞区域”同时叠加被动对象 `[width]` 定义的脚下矩形：红色为 `[do not pass]`，橙色为 `[pass only float type]`；编号和悬停信息可追溯到 OBJ。范围表示地面占用，并非 ANI 的受击/攻击框。无法识别的 pass type 会单独提示，仍保留对象图像。城镇只显示 `[virtual movable area]` 可移动区域的青框，随碰撞层一起开关；不会将对象或 TIL 通行数据提示为城镇玩家禁行区。

EQU 中每个 `[LAYER]` 开启独立区段，waiting motion 使用当前区段的层号；后续只定义攻击的区段不会覆盖其层号。多个待机层都参与合成。女法师的后层头发、法杖与身体遮挡已据实际 EQU/ANI 验证。
