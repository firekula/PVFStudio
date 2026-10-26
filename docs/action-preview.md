# 二进制 MAP 与 ACT 实体预览（0.13.2）

86 版 MAP 的二进制 token 流没有文本行边界；编辑器会把同一标签下的多条记录显示在一行。0.13.2 按实体字段拆分这一行，修复只读取第一个怪物和被动对象的问题，已有文本按行编写的配置继续适用。

NPC 支持带或不带 Z 的配置；怪物支持通常的十字段记录和 champion 附加参数；被动对象按编号、X、Y、Z 读取。特殊容器的物品、怪物、任务和地狱派对内容只作为容器配置跳过，不提前生成地图实体。错误或截断数据仍显示诊断。

## ACT 初始视觉

地图实体定义有直接的 waiting motion/basic motion 时，继续使用该 ANI。缺少直接 ANI 时，读取类型对应的 waiting action/basic action（NPC 的 field action），解析 ACT 中第一个 MOTION：

| 字段 | 当前行为 |
| --- | --- |
| BASE ANI | 按 ACT 所在路径解析基础 ANI |
| SUB ANI | 读取 ANI 路径、起始帧和部件层序；起始帧为 0 的部件参与初始视觉 |
| SUB ANI WITH XY / XYZ | 在上述字段后读取相对坐标；偏移跟随实体朝向，Z 投影到屏幕纵向 |

部件按层序排在身体前后，保留各 ANI 的延迟、循环和变换。部件和实体整体仍使用原 MAP 的绘制层、视差与地面深度，怪物/对象在相同坐标的多个实例分别绘制。被动对象粒子的初始视觉使用同一解析路径。

字段含义依据包内 ACT 配置及公开整理的[原始 ACT 示例注释](https://github.com/zjysun/dnf-pvf-parser-ts/blob/main/src/config/scriptLang/scriptTags/act.json)，编排逻辑为本项目独立实现。

预览不执行 TRIGGER、BEHAVIOR、SET ACTION、生成实体、战斗或移动逻辑；不以攻击/销毁动作代替待机。非零子动画起始帧暂未模拟，会跳过该部件并给出诊断。其他有效基础/子部件保留，无法加载的实体继续显示标记及原因。该实现展示初始视觉，不代表执行完整 ACT。

## 验证

真实 `map/fruz_station/12057(3,2)n.map` 包含 7 个怪物和 15 个被动对象。修复前只读到各 1 个，而且因未跟进 ACT 引用而仅有标记；修复后 22 个实体均加载图像，共 65 个场景图层，无资源诊断。实际工作台在 2.146 秒检查每个实体自己的放置坐标及非透明图片，关闭对象标记/碰撞叠加后，与删除实体配置的同一场景比较，超过 30 万像素发生变化。普通 MAP、DGN 相同房间及播放检查均通过。

普鲁兹目录的 16 张 MAP 读取到 92 个怪物、199 个被动对象和 1 个 NPC，共 292 个实体的初始视觉引用解析及资源解码通过，没有实体/资源诊断。审计不覆盖全部 86 地图或运行时动作。

回归夹具另外覆盖同一行的多个 NPC/怪物/被动对象、条件怪物、特殊容器内容隔离、相对路径、部件层序、XY/XYZ 投影及真实颜色像素；原有 ACT1 资源和实体检查继续执行。

```powershell
.\gradlew.bat check :pvf-workspace:classes :pvf-preview:classes
& "$env:JAVA_HOME\bin\java.exe" -Xmx2g `
  -cp 'pvf-core/build/classes/java/main;pvf-workspace/build/classes/java/main;pvf-preview/build/classes/java/main;pvf-preview/build/resources/main' `
  validation/EntityCompatibilityAudit.java 'C:\Game86\Script.pvf' 'C:\Game86\ImagePacks2' 'build/entities86-audit'
python -B tools/check_product.py `
  --product 'pvf-product/build/product/PVFStudio-0.13.2' `
  --archive 'C:\Game86\Script.pvf' --npk 'C:\Game86\ImagePacks2' --entities-86
```

审计要求新的输出目录，可追加第四个参数指定 MAP 前缀。工作台检查使用上述固定样本路径，截图和结果保存在独立的 `build/product-checks/` 子目录；不修改游戏原包或个人工作区。
