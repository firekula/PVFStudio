# ACT1 场景预览的客户端证据

2026-10-25，针对全地图装饰、怪物上移及部分敌人缺少头/四肢的问题，检查本机 ACT1 客户端的加载、投影、地块绘制和怪物构造流程。0.6.0 使用以下规则。

## 输入身份与分析范围

- 客户端：`DF/DNF.exe`，9,846,272 字节，SHA-256 `6b4850beaf67ec61adb04ae17f119f16278dd05e97b8ffca871a9bdb547de0d5`。
- 脚本包：`DFLegacy/DNF2008/Script.pvf`，SHA-256 `bc397d1463f1f0f749bbd169400b640d0c8a5d1f6e6e97cfda797099f995ac27`。
- 使用现有 32 位 IDB 的独立副本分析，原客户端、原 IDB、PVF 和 NPK 未改写。虚拟地址对应上述 EXE；原始反编译输出仅放在忽略的 `build/client-coordinate-evidence/`，不分发客户端代码或资源。
- `validation/inspect_client_coordinates.py` 是 IDAPython 查询脚本，支持按地址提取函数、交叉引用、参数及构造流程。分析工具不是运行产品的依赖。

## 地图投影与通行格

| 客户端位置 | 可观察行为 | 预览规则 |
| --- | --- | --- |
| `0x748D40` | MAP 的动画和实体放置读取原始 x/y/z | 保留配置坐标供排序和悬停显示 |
| `0x4C4F20` / `0x4C4F60` | x 扣除摄像机位置；y 为 `y-z*rate+120`，再应用摄像机与滚动比例 | 静止相机下的对象锚点是 `(x,y-z+120)`；重复背景不加此偏移 |
| `0x463940` / `0x4638F0` | 基础 TIL 图片从图像 y=0 绘制；扩展图像从 `baseWorldHeight+120` 开始，每行加 120 | 基础图不整体平移；扩展行高 120 |
| `0x464DC0` | 基础世界高度为首块 TIL 实际通行行数乘 12 | 通常为 360，但 20 行的雪山空地块为 240；图像范围另包含顶部 120 |
| `0x463AE0` / `0x463720` | x 按 16、y 按 12 查询通行格；0/1/2 分别为通行、需浮空、禁行 | 地块格子是 16×12；基本碰撞格原点在图像 y=120 |
| `0x464B00` | 扩展地块数除以基础地块数，使用整数商 | 不完整末行不参与渲染，预览给出提示 |
| `0x4632D0` | 背景按图像周期沿 x 重复；y 只使用相机滚动 | 背景只横向重复 |

`lorien/104.map` 中怪物的 `(526,219,0)` 因而对应图像锚点 `(526,339)`；其所在基础块从 x=448 开始，对应通行格 `(4,18)`。这是从客户端规则得到的结果，没有按截图试凑偏移。实体标记、命中区域、碰撞悬停和城镇虚拟移动区域使用同一投影。

## ANI 的变换锚点

`0x82C160` 将 `IMAGE POS` 加到绘制位置，同时把其负值传给 `0x4E3FF0` 作为旋转/缩放枢轴；底层图像函数 `0x8D36E0` 再组合原生裁剪偏移。对应二维变换为 `R(rotation) × S(rateX,rateY) × T(imagePos+trim)`，对象位置在最外层相加。左向翻转在 rateX 上取负，先于旋转。

旧实现围绕完整 IMG 画布中心旋转，并且没有缩放 IMAGE POS，会使有缩放/旋转的附属部件离开身体。0.6.0 的普通与镜像边界、实际绘制均使用客户端的对象锚点。

## 客户端隐式部件

MOB 的 `[equipment]` 只覆盖部分敌人。客户端工厂 `0x666DA0` 依怪物编号选择类，构造函数中还会加载 EQU，部分通过 vtable 的 1440 字节槽调用专用装配函数。经过构造链与这些虚调用核对，`act1-equipment.tsv` 收录 104 个编号变体、65 组配方；每组保留对应构造/初始化虚拟地址。

代表性证据：

- 石巨人：31 的 `0x65D290 → 0x69E5E0` 额外装配 SG 头、左臂、右臂和腿。32/33/35 使用 ST/CL/GD；34 先调用钢铁构造再以 `0x69ECA0` 换成 BR；3001 的 `0x69EF10` 使用 PT 五部件。身体继续来自 MOB 待机 ANI。
- 牛头怪：`0x6CC390`、`0x6CCF90`、`0x6CD850`、`0x6D07A0` 等添加武器、角、毛发、护腕及服饰；队长的各 EQU 有自己的 `[variation]`。
- 骑士：700 的 `0x6AB0B0`，701 的 `0x6AB160`，702 的 `0x6AB210`，704 的 `0x6AB440`，705 的 `0x6AB390`；通过各自的装配数组加载腿、头、双臂及光效。
- 守护者：数组 `0xB15F54` 包含六个 Guard 部件；`0x6AB020/0x6AAF00/0x6AAF90/0x6AAD40/0x6AAE20` 分别使用 0/1/2/3/4 色变体。光与风另有 Handle。
- 熔岩巨人：Atlas/Goliath/Titan 的虚装配函数为 `0x67B0F0/0x67AF40/0x67B2A0`；同构的普通、首领与召唤编号均保留。
- 装备替换：`0x488840` 按装备类型槽位替换旧装备并返回旧实例；预览据此避免铜巨人把钢铁、青铜两套四肢同时叠上。
- 外观参数：`0x449860` 在没有构造参数覆盖时读取装备自身的 variation；`0x6ABF00` 支持显式覆盖。Krazy Ivan 的三个部件分别使用 0/1/2，队长变体的第二参数为 1。

配方只关联配置与资源，仍从当前编辑器快照/草稿读取 EQU 和 ANI，不复制图像。未知编号继续使用 MOB 明确配置的装备，不根据文件夹中相似名称猜测。这里只展示各部分待机；攻击、破损、变身、觉醒、AI 和玩家交互状态不在待机模拟范围内；0.7.0 另加入下述地图粒子工厂。

## 验证与产物

- 83 项 JVM、14 项 Python 测试通过，包括 120 原点、16×12 格子、120 扩展行、20 行基础块、旋转/翻转枢轴、继承装备替换和各部件独立 variation。
- 全部 1,092 个 MAP、619 个 TIL 解析通过。地图引用的 576 种类型/编号仍为 538 种待机可绘制、26 种逻辑对象无 basic motion、12 种原包未注册；缺失条目继续明确提示。
- `validation/MultipartProbe.java` 对 monster.lst 中全部 289 个怪物在 0/160/400/60061ms 解码绘制，无资源错误；117 个怪物合成多个待机部件，包含原生装配和脚本装备。
- 产品自动界面检查覆盖 `lorien/104.map` 的投影和格坐标，以及 `ocean/b2401.map` 的石巨人、铜巨人、黄金巨人首领和斧骑士的 5/5/6/7 个部件。另覆盖开关、播放、编辑器快照、导出与恢复。
- 正常产品另以 computer-use 操作窗口，打开原包、关联资源目录、选择地图并核对预览。记录见本次工作区 `build/` 中的截图与检查输出。

0.6.0 历史产物为 `pvf-product/build/product/PVFStudio-0.6.0/`。产品检查目录为 `build/product-checks/20261025-155539-c474f59d/`，其中 check 和 smoke 均通过；地图与实体审计位于 `build/scene-native-final/`、`build/multipart-native-final/`。普通窗口验证了 lorien/104 的碰撞开关、播放到 52.293 秒后暂停，以及 ocean/b2401 的完整石巨人和骑士。测试窗口已关闭，没有保存或导出编辑。

产品 `app/lib/pvf-studio.jar` 的 SHA-256 为 `eb3d8c6b71af6ba6f25bbca5a2140359b55d29f60470d49bf68dde785ca0e84e`。

这些证据确认实现遵循所检查 ACT1 客户端的坐标和静态装配规则；没有声称所有动画状态或原生客户端像素完全一致。


## 0.7.0：粒子、碰撞和 EQU 区段

以下地址继续对应同一客户端摘要。预览只重建视觉，不运行游戏 AI 或攻击/销毁触发器。

| 客户端位置 | 证据与实现 |
| --- | --- |
| `0x7674D0` / `0x433560` / `0x437A20` | PTL 字段、默认三轴 stop、默认无限创建数/时间、无重力、100% 透明度值及 normal 绘制层。`0x9013C0` 的 2700 是保护字段元数据，并非默认创建数。 |
| `0x56E6A0` / `0x556DE0` | 原生被动对象编号分派 CNParticleFactory；预览只把已核对的这些编号的 string data[0] 当作 PTL。 |
| `0x57C120` / `0x78B380` / `0x78BA90` | int data[0] 设置初始粒子时钟，首轮按初始进度生成；不是延迟。随后累计频率小数，限制创建时间/总数。 |
| `0x57BCA0` | 每次更新以 MAP 位置覆盖 PTL 默认创建坐标；int data[2]=1 时把区域扩展为地图宽度，并按宽度比例调整数量和频率。 |
| `0x78B450` | 生成区域偏移、角度范围和运动/寿命随机变化；镜像跟随所有者方向。预览固定随机种子以支持可靠回退。 |
| `0x78A8F0` / `0x78B030` | 速度积分、sin/cos 绝对相位、keep changing/keep last value/ping pong、旋转运动向量、重力 `-g*t²/2000000` 和落地策略。坐标轴比例数据 `0xB51B1C` 为 100/29/100，故粒子 Y 运动和生成范围使用 0.29 比例。 |
| `0x789F80` | animation 类型的 -1 对应 ANI，其他非负编号对应 IMG 帧；passive object 类型走被动对象工厂。动画使用 PTL layer，被动对象保留自身初始视觉定义。 |
| `0x82F720` / `0x82CB90` | INTERPOLATION 标志开启后，保留当前图片，向下一帧插值位置、缩放、旋转及 RGBA；末帧仅在循环时连接首帧。 |
| `0x756160` / `0x475E30` | OBJ 的 width 读取为 X/Y 地面宽度，pass type 的 0/1/2 对应 pass all / pass only float type / do not pass。碰撞叠加使用该配置脚下矩形，并保留定义来源。 |

EQU 层级问题也有直接配置证据：`equipment/monster/soceress/hair01.equ` 的 waiting motion 位于 LAYER -200，后续 LAYER 400 只有攻击；`weapon01.equ` 的待机位于 -100，后续 800 也只有攻击。旧实现保留首个待机路径却误用文件末尾层号；现按每个 LAYER 区段关联动作，女法师的法杖、后发与身体恢复前后关系。

城镇蓝框来自 MAP 的 `[virtual movable area]`，是可移动区域列表的边界。与 TIL 格及对象脚下范围一起受“碰撞区域”开关控制，图例和悬停都标明蓝框含义。它不是缺失素材。

### 检查结果与边界

- 98 项 JVM、14 项 Python 测试通过。产品结构和实际 Swing 面板的扩展冒烟均通过，目录 `build/product-checks/20261025-180411-5148e554/`；覆盖 MAP 粒子、对象碰撞、独立 PTL、未保存 PTL 覆盖与时间轴定位。
- 17 种地图引用的粒子工厂（共 2889 处放置）在 0/500/1500/5000/60061/1200001 ms 检查，无资源错误。Lorien/104 为 6 个生成器/36 个粒子，Ocean/b2401 为 27/159，碰撞范围分别为 4/17 处。时间轴回退结果可复现。暴风雪生成器也覆盖地图宽度。详见 `validation/ParticleProbe.java`、`build/particle-scene-release/`。
- 751 个 PTL 中 741 个可解析；10 个含未知枚举或缺失参数而保留诊断。另有旧韩文字段的文件保留未识别字段提示。两种天空城远景引用了重复帧标签的 ANI，按文件顺序预览并提示。
- 1,092 MAP / 619 TIL 解析通过；576 种被引用类型/编号中，555 种可解析初始视觉（含 17 种粒子工厂）、9 种纯逻辑对象、12 种原始索引未注册。另有 3 个 OBJ 的 pass type 是未解析的本地化链接，保留图像并明确省略其碰撞范围。详见 `build/scene-particle-release/`。
- 全部 289 个怪物在四个时间点再次检查通过，117 个为多部位；`build/multipart-layer-final/`。女法师等 LAYER 区段另有实际资源合成图。
- 模拟使用可复现随机分布，未复刻运行中客户端的随机序列；复杂振荡落地采用有界求交。每个生成器最多 1024 个活动粒子，全场景最多 8192 个、嵌套最多 4 层，并限制配置数量和像素缓存。独立 ANI 面板检查关键帧，场景模式实现参数插值。

最终可运行产物为 `pvf-product/build/product/PVFStudio-0.7.0/PVFStudio.exe`。`app/lib/pvf-studio.jar` SHA-256：`5ac0ebfe9cbd3f51dbb39b5e38eb31a9a111ff986a6e0f1d2b8f6aa00bb3f379`。

普通 0.7.0 EXE 窗口另外打开原包与 `map/ocean/b2401.map`，确认 27/159 粒子、17 处对象碰撞，展开预览区域，连续播放至 55.069 秒并开启碰撞层后暂停；资源目录从工作区设置恢复。未修改配置或保存草稿。

## 2026-10-25：Darkhell 平铺、Alfhlyra 层级和相机

仍使用上述客户端与脚本包身份，对现有独立 IDB 副本重新提取函数，原始输出留在 `build/client-coordinate-evidence/`。

| 客户端位置 | 静态证据与修正 |
| --- | --- |
| `0x56E6A0 → 0x56DBC0` | 333～337 号对象创建原生滚动场景类。不能把带 basic motion 的这些对象都作为单份普通实体。 |
| `0x578A80 / 0x578AF0` | int data[0] 为横向副本间距；初始化时复制基础及附加动画到地图宽度以外。int data[1] 是由场景状态控制的纵向行程，不是动画时长。预览按前者平铺并保留 ANI 插值；全图模式补齐可见范围。 |
| `0x4E8DF0 / 0x718090` | 层按 5、4、3、2、1、0 绘制，对应 distantback、middleback、closeback、bottom、normal、close；每层按 below、对象、upper 处理。 |
| `0x463940` | 地块在 closeback/below 调用内绘制，位于该层 below 背景之后、对象之前。旧“地块在所有 closeback 对象之后”以及“close/below 提前到地块前”的规则作废。 |
| `0x4C4F20 / 0x4C4F60 / 0x462D50 / 0x462DB0` | 相机位置乘所属层滚动比例，坐标与摄像机一起投影。旧预览只对拖动量应用比例，忽略居中相机。现从居中 640×480 相机计算完整视差；全图模式使用同一相机参考，另可裁剪为客户端视口。 |
| `0x748D40` | `[virtual movable area]` 保留 x/y/宽/高；Alfhlyra 下方区域为 `(1003,433,76,36)`，投影后 y=553。青框继续使用地面坐标；udgate 的 close 层使用 110% 视差，不对青框加试凑偏移。 |

真实资源回归使用 `validation/MapRegressionProbe.java`：两张地图分别在 0、10000、60061 ms 渲染，干净图、碰撞图和图层清单位于 `build/map-regression-final/`。Darkhell 天空在全宽与循环位移过程中保持覆盖；Alfhlyra closeback 建筑覆盖地面，门与青框使用上述相机规则。所有六个时间点均无资源诊断。原生场景推进、战斗和客户端像素级一致性未作为本轮验收结论。

本地修复产物为 `build/PVFStudio-map-search-fix/PVFStudio.exe`，沿用 0.10.0 产品版本号，尚未发布。产品 JAR SHA-256 为 `927fda329b04b9a386a3786af66c93b55b64b2782a3fd9bc02fd1937bd7f6a75`。115 项 JVM、14 项 Python 测试通过；最终产品检查在 `build/product-checks/20261025-211934-36666660/`，包括 Darkhell 平铺、Alfhlyra 图层/青框/客户端视口拖动，以及文件内查找栏可见、真实替换/撤销、草稿和未保存内容的全包搜索、精确跳转、取消后原包可继续读取。搜索遍历 48,326 条目样本，不创建全包编辑器。

## 2026-10-25：客户端视口的滚动边界与逐轴投影

以下复核修正上一轮视口只有裁剪、可以无限拖动及纵向误用横向百分比的问题。原始反编译记录为 `camera-limits.txt`、`camera-scroll-load.txt`、`camera-script-reset.txt`，均在上述 `build/client-coordinate-evidence/`。

| 客户端位置 | 静态结论 |
| --- | --- |
| `0x464BD0` | X 上限为 `max(0,基础地块数×224−640)`，窄地图不允许横向滚动。 |
| `0x464B00 / 0x463030 / 0x4E6140` | Y 上限为完整扩展地块行数×120，X/Y 都夹在零与上限之间；较矮的基础通行区不改变该 Y 上限，不能用图片高度减 480 替代。 |
| `0x74F840 / 0x748D40 / 0x464540` | MAP 数据的六个滚动参数先初始化为 100，三个 sight scroll 标签仅覆盖横向三项；加载时六项复制到场景对象。故当前 ACT1 的纵向三项都保持 100%。 |
| `0x462D50 / 0x462DB0` | 每层相机偏移使用整数百分比相乘后除以 100；浮点近似不能产生额外的一像素误差。 |
| `0x463940 / 0x4C4F20 / 0x4C4F60` | 地块与对象在屏幕坐标下绘制。原生视口按 640×480 裁剪，不按编辑器里的地块图片外框裁掉装饰与背景。 |

`SceneCamera` 持有独立相机位置和 MAP 边界；拖动到边界立即丢弃越界量，反向移动不会有粘滞。渲染采用整数相机坐标；缩放和窗口尺寸变化只改变显示变换。边框和相机坐标标签绘制在原生画面之外，外围暗化；独立 TIL/PTL 不启用客户端相机。原生角色跟随的死区（X 270～370、Y 160～240）已在 `0x463030` 中观察到，但当前没有跟随角色对象，仍由用户手动移动相机；不模拟该跟随状态、场景事件转场或摇屏。

122 项 JVM、14 项 Python 测试通过。最终产品检查目录为 `build/product-checks/20261025-214204-1867764f/`；包含实际 Canvas 的拖动、反向边界、缩放/resize 保持位置、边框可见性、Alfhlyra 左上/右下/门口截图，以及原生 AWT WINDOW_CLOSING 的取消、保存、放弃和重开恢复。关闭按钮原先在 `captureEditorState → CaretImpl.getOffset` 触发缺少 read-action 的断言，现用 WriteIntentReadAction 进入既有关闭流程。测试通过原生事件队列投递关闭事件，避免用已持锁的直接调用掩盖问题。

`validation/MapRegressionProbe.java` 同时输出原生 640×480 的四角/居中相机图。Darkhell 与 Alfhlyra 的 0、10000、60061 ms 产物位于 `build/map-native-viewport-final/`，所有时间点无资源诊断。这些是本地客户端函数的静态对照和预览渲染验收，并非与运行中客户端所有动态状态逐像素比对。

本轮可运行产物为 `build/PVFStudio-viewport-fix/PVFStudio.exe`，仍沿用 0.10.0 版本号、尚未发布；包含上一轮搜索与地图修复。产品 JAR SHA-256：`d5a37841e8542e5672bc5d6e8a5538d8564f550514a06918592d3e26ab590200`。

## 2026-10-25：城镇青框与 TIL 红格的判定关系

Alfhlyra 的红色 TIL 通行格可以落在青色 `[virtual movable area]` 内。这与相机滚动无关：城镇角色的地图通行判定使用虚拟矩形列表的并集，不把 TIL 红格与青框取交集。

| 客户端位置 | 静态证据 |
| --- | --- |
| `0x464540`（`0x46470C`～`0x464719`） | 将脚本 `+0xE4` 的 virtual movable area 复制到当前地图 `+0x150`；矩形数据位于 `+0x154`～`+0x158`，每项 16 字节。 |
| `0x9988D8 / 0x9988DC / 0x998970` | RTTI 标识 `CNRDVirtualCharacter`，其虚表从 `0x9988DC` 开始；通行判定槽 `+0x94` 指向 `0x479EF0`。 |
| `0x479EF0 → 0x776C60` | 检查候选角色坐标是否在任意一个上述矩形内；条件为 `x >= left && x < left + width && y >= top && y < top + height`。此分支没有调用 TIL 通行查询。 |
| `0x4A8160 → 0x4C5480 / 0x463AE0` | 通用角色分支检查对象碰撞及 TIL 通行格；不能把这套规则直接套到城镇虚拟角色上。 |

原包 `sample/mapsample.map` 的韩文注释也说明：virtual movable area 是城镇角色可移动矩形列表，实际移动区域为列表并集，非城镇地图无意义；`[town movable area]` 则另存目标城镇与 AREA 编号，表示区域转换/进入地下城的触发区。

`map/alfhlyra/alfhlyra.map` 的主矩形为 `(16,184,2423,258)`，地面 y 范围为 `[184,442)`；扩展地块的红格带位于其中，并不会因此阻止城镇角色行走。预览中的红格表示 TIL 原始通行数据，不能在城镇中单独解释为玩家最终禁行区。这里只核实静态判定，没有运行客户端角色实走；本次未修改渲染或产品产物。原始反编译输出为 `build/client-coordinate-evidence/town-virtual-check.txt`、`town-virtual-class.txt`、`town-movement-entry.txt`，与脚本注释相互印证。

## 2026-10-25：按城镇区分碰撞叠加

按后续显示需求，含非空 `[virtual movable area]` 或 `[town movable area]` 的 MAP 采用城镇通行叠加：只绘制可移动区域青框，隐藏 TIL 和对象的红/橙标记。悬停仅说明在区域并集内、区域外或未配置区域；图例与详情随场景切换。地下城与独立 TIL 保留原有通行格及对象碰撞提示，原始数据仍保留供读取。

123 项 JVM 测试通过；覆盖全图和客户端视口的红/橙格隐藏、青框保留、多矩形并集、对象范围不覆盖城镇提示、缺失虚拟区域以及独立 TIL。真实产品检查与冒烟通过，目录 `build/product-checks/20261025-221447-34c286c5/`；Alfhlyra 图例和详情断言通过，并检查左上角视口截图只有青框。

本轮可运行产物为 `build/PVFStudio-town-overlay-fix/PVFStudio.exe`，沿用 0.10.0，包含此前修复，尚未发布。产品 JAR SHA-256：`62d223ed05f0ec3960cb047051103c4aa70a7ad1c59be49fa6d5f51945a8768f`。

## 2026-10-25：DGN 布局、房间与原生门

DGN 可切换多套布局与起点/Boss，点击布局图切换房间，并选择匹配的 MAP。普通门、Boss 门和墙体按方向连接与 Boss 坐标生成；清房及 Boss 入口条件作为显式预览状态。门的常驻/关闭/开启 ANI、variation 和 TIL 通行格合并已接入现有场景。静态函数证据、排列策略与使用说明见 [副本预览](dungeon-preview.md)。

真实脚本覆盖 43 DGN、49 套布局、178 组不同起点/Boss 组合；242 个房间开闭状态完成 NPK 渲染。原始结果在 `build/dungeon-native-final/`。门素材没有新增资源错误；既有逻辑对象和本地化字段诊断保留。这不是对在线副本随机种子、AI/战斗或玩家过门流程的运行验收。

130 项 JVM 测试通过。最终产品检查及冒烟通过，目录 `build/product-checks/20261025-224856-b3bfc49d/`，包含实际面板中的 Lorien 布局、开闭门动画、Boss MAP、第二套布局与切换清房状态保持相机位置；另有 Swing 点击和返回房间保留 MAP/清理状态回归。

本轮可运行产物为 `build/PVFStudio-dungeon-preview/PVFStudio.exe`，仍沿用 0.10.0，包含此前全部本地修复，尚未发布。产品 JAR SHA-256：`c1270611c6c1ff214e470e4a81a966a9dcde2579a05208983aa5fe867f857cf8`。

## 2026-10-25：0.11.0 发行构建

上述未发布修复统一收入 0.11.0。源码、产品描述、原生启动器与安装器版本均为 0.11.0；正式产品目录为 `pvf-product/build/product/PVFStudio-0.11.0/`，产品 JAR SHA-256 为 `34908adadff9d2c84d46c35555a0a26b1348cca28729d26c565f0eb345962eac`。

130 项 JVM 测试、14 项独立 Python 测试及正式产品检查/完整冒烟通过。发行产品检查目录为 `build/product-checks/20261025-231343-f2f5a981/`；便携 ZIP 的 590 个条目全部 CRC 通过，并确认产品身份、JAR/EXE 与本地正式目录一致，不包含游戏数据、开发文档或旧宿主目录。发行包和校验文件在 `pvf-product/build/distributions/`，校验记录在 `build/release/package-validation-v0.11.0.json`。

| 发行文件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `PVFStudio-0.11.0-windows-x64-portable.zip` | 243440945 | `139edbfbe8cb56322f7bf333694d2ffe116fe7a3e2fbec41fcb018c3fcf535e3` |
| `PVFStudio-0.11.0-windows-x64-setup.exe` | 186174298 | `53a8a918f645ec30a9323087d5da164db84f422e538892d30b3be925eb5e9cd8` |

发布入口为 [v0.11.0 Release](https://github.com/firekula/PVFStudio/releases/tag/v0.11.0)。

## 2026-10-25：0.11.1 图标与软件文档发行

0.11.1 将应用图标统一接入原生 EXE、窗口/任务栏、平台产品信息及安装器，并重整 README 的软件简介、下载、使用、构建和文档入口。历史 CLI 示例与详细验证记录保留在 [CLI 与开发验证](development-validation.md)。CLI 帮助页改为读取构建版本。

130 项 JVM 测试、14 项 Python 测试和正式产品检查/完整冒烟通过，目录为 `build/product-checks/20261025-234206-c1edd44b/`。原生 EXE 与安装器版本均为 0.11.1，提取的图标一致且与 ICO 源图核对通过。便携包的 590 个条目 CRC、产品版本、JAR/EXE 一致性和发行内容检查通过；产品 JAR SHA-256 为 `e440ffcf6b7cbe0e2f45fc7a43aab7c519e013e7816a404431cf9323bbb58fa4`。

| 发行文件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `PVFStudio-0.11.1-windows-x64-portable.zip` | 243749861 | `f22ac81c9add8ce250c6a4d7f2c0d0aa8c47e000b049e9aae72f9c3ac7478aa0` |
| `PVFStudio-0.11.1-windows-x64-setup.exe` | 186620717 | `731792ed208b4fd6317c98df20079c68d5992913c947927e306c303ecf47f2fa` |

正式产品目录为 `pvf-product/build/product/PVFStudio-0.11.1/`。发布入口为 [v0.11.1 Release](https://github.com/firekula/PVFStudio/releases/tag/v0.11.1)，本地校验记录在 `build/release/package-validation-v0.11.1.json`。

## 2026-10-26：0.13.5 分层排序与小窗口预览

使用同一 ACT1 `DNF.exe` 的独立 IDB 副本提取，原始结果为 `build/client-layer-evidence/order.txt`、`sort.txt`。`0x4C53D0` 遍历指定层的对象数组，仅在原生 layer=1 时调用 `0x4C5230`；`0x718090` 将 normal 映射为 1。`0x4C5230` 按虚函数 +136 返回的地面 Y 做稳定插入排序，相等时不移动。其他层在该绘制流程中沿数组顺序绘制。原“所有层统一按地面 Y 排序”的预览推断作废，地块在 `0x463940` 中的位置保持不变。

0.13.5 的六层像素测试同时覆盖总览和客户端视口，确认 normal 按 Y 排序，其余层按加入顺序。100 级实物资源 `map/kohlepowerstation/18008(4,1)b.map` 在 0/46038ms 无资源诊断，楼梯入口先前被栏杆覆盖的像素恢复，middleback 上行楼梯也恢复。全景前后图在 `build/powerstation-before-0135/`、`build/powerstation-after-0135/`。这轮依据 ACT1 静态函数和新版 PVF/IMG 实物资源核对，没有声称完成 100 级原生客户端运行画面的像素一致性验收。

预览设置和详情默认折叠，保留房间小地图、清理状态、MAP 候选、客户端视口及播放倍速；诊断数量在折叠按钮上仍可见。172 项 JVM 与 14 项 Python 测试通过，实际 100 级工作台覆盖 1264×811、1024×720 窗口、展开/收起和相机保留。

最终工作台证据位于 `build/product-checks/20261026-122941-4a8ba9d8/smoke-results/`：按原分栏宽度测试，1264×811 的画布为 394×355，1024×720 的画布为 288×264。设置/详情收起后恢复原高度与相机，房间小地图及播放控件保持可见。ACT1 完整产品回归通过，日志为 `build/product-checks/20261026-122748-f9119f27/`；同时确认切换碰撞层不会因图例出现而改变画布和自适应缩放。

## 2026-10-26：雪山右门的 bottom 图层核对（无运行时代码改动）

针对 `G:/Game/DNF_ACT1/Client_test2/Script.pvf`、`map/charnakridge/s5601.map` 排查帐篷盖住冰刺。604 号 `01rightnormal.obj` 原配置为 bottom；帐篷 `Animation/Grocery.ani` 在 MAP 中为 normal、Y=299。右门坐标为 (865,399)，其图层优先级低于 normal 帐篷，不能仅按 Y 判断遮挡关系。

原版静态链路：`0x475E30` 将模板图层写入对象 +220（对象信息 +12）；`0x4C7AE0` 按这个字段注册到对应图层，没有把通路门强制改为 normal。`0x556860 → 0x57C6E0` 加载门的各 ANI，调用虚函数 +220 对应的 `0x472FC0` 挂为同对象的附加动画；`0x472400` 在对象的绘制调用中画这些附加动画，不为尖刺建立独立前景对象。原始提取为 `build/client-layer-evidence/gate-layer.txt`、`gate-register.txt`，门加载函数另见 `build/client-coordinate-evidence/tile-and-object-draw.txt`。

开/关门在 0、500、60061ms 的 6 张实际场景 PNG，与 0.13.4 输出逐字节一致。将门图层仅在内存改为 normal 的对照可恢复帐篷前的尖刺，证据目录见 [DGN 说明](dungeon-preview.md#雪山帐篷与冰刺的遮挡)。新增像素回归确认门的常驻和关闭附加动画继承对象图层，bottom 受 normal 场景遮挡，normal 则按地面 Y 排序；这是配置层级核对，不修改原包或改变渲染器的原版分层规则。
