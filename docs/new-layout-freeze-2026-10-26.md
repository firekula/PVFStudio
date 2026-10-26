# 新布局选择 MAP 卡顿修复：0.14.4

用户报告新建布局后选择房间卡死。直接读取当前 0.14.3 进程的两份线程堆栈，均显示 EDT 停在 `MultiSelectDropdown.filter → DefaultListModel.addElement → WideSelectionListUI.updateLayoutState → CellRendererPane.addImpl → Component.addNotify/updateZOrder`；后台 PVF I/O 与资源预览线程空闲。

根因是勾选任一 MAP 后逐项清空 / 重建全部候选，每个模型事件可能触发布局重算；渲染器还为每个条目反复创建新的 JCheckBox 并累积到 CellRendererPane。此前原生多选测试使用小范围搜索，未覆盖完整 MAP 列表。

修复：已存在的候选勾选只重绘复选框；真实筛选 / 候选变化批量发布；固定行高和宽度以避免全表测量；复用唯一渲染组件，勾选状态使用 Set 查询。未登记但已选中的 ID 仍能展示和移除，提交与文档版本保护保持原行为。

验证：

- 新增失败→通过回归：一万项候选只发布一次新增事件，已知项勾选不重建模型，渲染组件复用，未知编号保留 / 删除正常。
- 全部 282 项 JVM 与 14 项 Python 测试通过，产品身份 / 类加载检查通过。
- 实际用户 PVF / IntelliJ UI 复现通过：新建布局，在未搜索的 1026 项完整候选中连续鼠标勾选三项，创建 (1,1) 房间并验证三个 ID，随后两次撤销精确还原原文；操作无卡顿。首轮证据 `build/product-checks/20261026-231342-0ff8e2a4`，最终便携包解压证据 `build/product-checks/20261026-231548-b95bf761`。
- 代码 / 设计小地图、诊断、混合换行、保存 / 导出及撤销回归同时通过；独立定向审阅没有实质发现。解压版 EXE 检查退出码 0、状态 PASS。
- 未关闭或改写用户正在运行的旧进程；原用户 PVF SHA-256 保持 `5fcd4b94dfb081294701f7288345fff6ca5586d780fcc7130e4614c237eb6ad1`。
- ZIP SHA-256：`d6d39ebbb0b0839259b27943bf56c0da50ced16efa688d34e0a1ee434c6f0b17`。
