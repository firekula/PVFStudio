# 可视化编辑验证记录（2026-10-26）

版本：0.14.0 本地开发版。数据文件和截图保留于隔离工作树的 build/，不随源码或程序发行。

| 样本 | 容器 | 校验条目 | 原生 MAP / DGN 回读 | 深渊修改 |
|---|---|---:|---|---|
| act1 | ACT1 | 48,326 | 通过 | 通过 |
| single | LEGACY | 4,263 | 通过 | 样本无配置 |
| 70s2 | UUID | 369,711 | 通过 | 通过 |
| 86 | UUID | 666,933 | 通过 | 通过 |
| 100 | UUID | 929,093 | 通过 | 通过 |

所有样本均执行原生字段编辑、草稿重开、导出、导出后回读；独立 Python 容器解析核对所有修改及未修改载荷、目录元数据、原有间隙和尾部数据，并核对原包 SHA-256。B0 D0 文件另以独立 token / 字符串表解码与预期源码核对。校验产物位于 `build/visual-audit-{act1,single,70s2,86,100}-20261026/independent-report.json`。

原生窗口证据：

- ACT1 原有编辑 / 标签 / 搜索 / 会话 / 菜单 / 资源 / 保存 / 导出 / 备份流程：`build/product-checks/20261026-183714-bb13f026`。
- 86 MAP 画布拖动、Z 保留、撤销、原生资源添加、保存：`build/product-checks/20261026-183302-2764e430`。
- 100 DGN 普通 / 深渊房实际资源、Boss 独立、1264 / 1024 窗口、克隆整体撤销：`build/product-checks/20261026-183302-2e14c390`。
- ACT1 扩展名重命名、Document / Editor 生命周期、关闭重开后整体撤销 / 重做、重命名来源后返回副本：`build/product-checks/20261026-183525-8fc21419`。
- 86 DGN 同类原生窗口操作：`build/product-checks/20261026-183526-0f1c25b1`。
- 86 既有 MAP 文件 / ANI 切换、相机与像素一致、文件树变更、重命名、重开和导出回归：`build/product-checks/20261026-183526-e229439c`。

这些结果不构成 100 级游戏内加载或战斗验证。现有缩减平台还会记录已知的 application-info / consentMap 诊断噪声；本报告依据明确检查项及 smoke 成功标记，不声称日志完全无错误。

复现入口：`validation/VisualSceneEditAudit.java`、`validation/verify_visual_scene.py`、`tools/check_product.py --visual-map / --visual-dungeon / --visual-scene / --map-files`。输入须是本地真实样本，输出须为新目录。

最终打包：`tools/build-release.ps1 -ZipOnly` 成功，257 项 JVM 测试及 14 项 Python 测试零失败。便携 ZIP 解压到新目录后，实际 `PVFStudio.exe --check` 返回 PASS；解压产物的 86 MAP 原生操作再次通过：`build/product-checks/20261026-184038-438f77d2`。程序含 307 个模块、66 个平台 JAR，无捆绑插件和游戏数据。
