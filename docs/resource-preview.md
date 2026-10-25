# 配置联动资源预览

0.3.0 在 PVF 文本编辑器旁增加资源面板；0.4.0 扩展为 MAP/TIL 场景、碰撞层与公共时间轴。PVF 提供脚本与动画参数，NPK 提供 IMG 帧；无需先解包资源或启动客户端。地图的组合规则见 [地图场景说明](map-scene-preview.md)。

## 使用

选择客户端根目录或 ImagePacks2，后台只读取各 NPK 的目录记录，之后按需加载实际使用的 IMG 元数据与帧像素。目录设置保存在当前 PVF 工作区的 `preview.properties`；若 PVF 同目录有 ImagePacks2，会自动尝试该目录。重新选择同一目录可更新 NPK 索引。

打开 ANI 后可播放/暂停，用箭头或时间轴选帧，调整循环、倍速及缩放。“自适应”以原生完整画布和原点确定固定视口，避免裁剪范围变化导致逐帧居中跳动；可选择 25%/50%/100%/200%/400%，并拖动平移或围绕鼠标滚轮缩放。实际绘制包含原生裁剪偏移、IMAGE POS、IMAGE RATE、IMAGE ROTATE（弧度）和 RGBA。

其他文本配置自动列出带帧号的 IMG、ANI 和 PTL 路径，例如物品的 icon/field image，以及怪物的 waiting motion/attack motion。ANI 路径先相对于来源脚本解析，再查找 PVF 根路径；仅在 PVF 虚拟目录中查找。打开的引用文件读取未保存的文档快照，其次读取已保存草稿，再读取原包。

编辑停顿约 350 ms 后重新解析与渲染。修改未完成时显示解析错误；标签切换或后续修改会使旧异步任务的结果失效。取消预览不打断挂载 PVF 的 FileChannel，以免中断导致原包读取句柄关闭。选择资源、预览与外观参数不会保存或导出配置。

路径 `%d`、`%02d` 等数字占位符由“外观参数”替换，例如 `0,0`。在完整场景里，装备先使用自身 `[variation]`，客户端显式装配参数按已核对的配方覆盖，不共享面板的默认外观值。参数数量不足、负数或不支持的占位符会报错；不同装备的实际可用组合取决于当前 NPK。

## 支持的格式

- NPK `NeoplePack_Bill\0`，20 字节头及每条 264 字节目录记录；支持明文路径与已知 XOR 路径。删除标记（长度为 unsigned -1）跳过并记录提示。
- IMG v1 的交错帧记录与像素，以及 v2 的独立帧目录与像素。
- ARGB1555（14）、ARGB4444（15）、BGRA 字节顺序的 ARGB8888（16）和链接帧（17）。压缩模式 5 为原始像素，6 为 zlib。
- 路径大小写、正反斜线及可选的 `sprite/` 前缀统一匹配。重复 IMG 给出涉及的 NPK 文件名，供用户检查目录中的补丁冲突。
- 按源 NPK 大小和修改时间检查索引是否过期；重新选择目录后重新加载。IMG 元数据最多缓存 32 项，像素缓存最多 32 MiB，单帧像素和解压长度有上限。

格式布局经本地 ACT1 数据验证，另参考上游读取实现：[godof IMG reader](https://github.com/dof-dev/godof/blob/main/npk/img/img.go)。实现独立于该项目，没有引入其代码或依赖。

## 预览边界

单个 ANI 每帧渲染一张 IMG；MAP/TIL 使用独立场景模式合成多个引用，并可叠加 TIL 通行格。0.6.0 按 [客户端函数证据](client-preview-evidence.md) 修正地图坐标、格子比例、ANI 枢轴，并补充隐式多部位装配。0.5.0 还合成地图 NPC、怪物及其装备、被动对象的待机动画，未加入玩家角色和实体运行逻辑。支持普通透明混合与 LINEARDODGE 灯光，场景支持位置、缩放、旋转及 RGBA 帧间插值；独立 ANI 面板仍按关键帧检查。暂不模拟其他特殊混合、阴影、音效或游戏逻辑；不支持的效果有提示。旋转及场景变换的实现通过离线渲染验证，未逐项与原生客户端截图对照，不能视为客户端像素一致性验收。

原包中有重复/跳号的 FRAME 标签、声明数量与实际帧数不符的 ANI。为便于检查，预览保留文件中的帧顺序并显示提示；这些异常配置与原客户端的容错行为尚未逐项对照。`FRAME MAX 0` 显示为空动画。缺少资源、损坏帧、未知版本或超限数据会显示错误，修正配置后自动恢复。

## 验证命令

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
.\gradlew.bat check -PwithIntellij=true :pvf-product:verifyProduct
python -B -m unittest discover -s validation -p 'test_*.py'

# 实际平台界面：播放、逐帧、图标、ANI 引用、未保存覆盖、丢失资源、过期回调、目录持久化。
# 同时执行原有编辑器、差异、PVF 导出、备份与恢复检查。
python -B tools/check_product.py `
  --product pvf-product/build/product/PVFStudio-0.10.0 `
  --archive 'G:\path\Script.pvf' --npk 'G:\path\ImagePacks2'

# 独立只读探针：遍历所有 ANI 进行解析，并渲染代表样本与物品图标。
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 `
  -cp 'pvf-core/build/classes/java/main;pvf-preview/build/classes/java/main' `
  -d build/preview-probe validation/PreviewProbe.java
& "$env:JAVA_HOME\bin\java.exe" '-Djava.awt.headless=true' `
  -cp 'build/preview-probe;pvf-core/build/classes/java/main;pvf-preview/build/classes/java/main' `
  PreviewProbe 'G:\path\Script.pvf' 'G:\path\ImagePacks2' 'build/preview-new-run'
```

探针和产品冒烟输出位于 `build/`；探针的输出目录须为新目录。游戏数据及生成的资源截图不提交源码仓库。合成测试覆盖 v1/v2、XOR、三种颜色、透明度、原始/zlib、链接链及循环、越界、重复与过期目录、路径变量、引用解析、参数错误与 RGBA/裁剪位置。

## PTL 粒子（0.7.0）

打开 `.ptl` 会显示完整粒子预览；配置中的 PTL 引用也可直接选择。地图依据客户端工厂编号及 OBJ 的 string/int data 加载生成器，MAP 坐标覆盖 PTL 的默认创建坐标。森林光束、雨滴、火雨、海底鱼群/球体/光照、天空城远景和暴风雪已用实际资源检查。生成器数量、活动粒子数和对象碰撞数量显示在详情中。

场景时钟采样 PTL 的生成频率、初始进度、最大创建数/时间、寿命、三轴运动、生成区域和透明度；所有粒子使用稳定种子，回退后得到相同结果。PTL 可以引用 ANI、IMG 帧及被动对象的初始视觉定义，支持有限嵌套和循环引用诊断。修改 PTL、OBJ 或引用 ANI 的未保存快照会重新解析。销毁/攻击触发的粒子没有自动提前生成。

随机结果用于可复现预览，与运行中客户端的随机序列不同；复杂振荡落地采用有界求交。每生成器最多同时展示 1024 个粒子，每场景 8192 个、最多嵌套 4 层，超限有提示。原包存在无效参数和旧韩文标签的 PTL，会保留错误/字段诊断。细节与原生函数证据见 [客户端预览证据](client-preview-evidence.md)。
