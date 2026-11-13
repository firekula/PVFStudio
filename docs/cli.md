# PVF Studio CLI

Windows 安装版和便携版的 `PVFStudioCLI.exe` 与桌面程序放在同一目录，使用随包提供的 Java。每次运行只执行一条命令。下面是 PowerShell 示例，请将两个游戏路径换成实际路径。

```powershell
$cli = '.\PVFStudioCLI.exe'
$pvf = 'C:\Game\Script.pvf'
$npk = 'C:\Game\ImagePacks2'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
& $cli schema
& $cli schema list
& $cli --json info $pvf
```

## 给 AI 的简明指令

```text
用 PVFStudioCLI.exe 操作指定的 Script.pvf。
每条业务命令都以 --json 开头，例如 PVFStudioCLI.exe --json info "C:\Game\Script.pvf"。
不确定参数时执行 schema 命令名，例如 PVFStudioCLI.exe schema cat。
每次读取一条 UTF-8 JSON。只有 ok 为 true 时才继续，失败时按 error.hint 检查参数。
先用 list 或 search 确认真实虚拟路径，再用 cat 读取内容。
编辑时使用独立草稿目录。编辑后用同一个目录回读，核对后再导出到新的 PVF 文件。
预览时先取 data.effects，再将其中的 key 原样传给 --effect。
```

## 查询和读取

虚拟路径是 PVF 内部路径，例如 `skill/gunner/laserrifle.skl`。文件系统路径是磁盘上的 PVF、NPK、草稿或输出路径。路径有空格时在 PowerShell 中用引号包住。

```powershell
& $cli --json list $pvf --prefix 'skill/gunner/' --suffix '.skl' --limit 20
& $cli --json search $pvf '激光炮' --field name --prefix 'skill/gunner/' --limit 10
$entry = 'skill/gunner/laserrifle.skl'
& $cli --json inspect $pvf $entry --level 1
& $cli --json cat $pvf $entry --max-lines 30
```

- `list` 按虚拟路径排序。把返回的 `data.next_offset` 传给 `--offset`，继续使用同一组筛选条件，直到它为 `null`。
- `search` 默认逐行搜索字面文本，`--field name` 搜索物品与技能的本地化名称和英文名。每次扫描的文件数有限，`data.next_cursor` 非空时，即使当前页没有匹配，也可继续用 `--cursor` 查询。
- `inspect` 解析 `.equ`、`.skl` 和 `.stk` 的名称、说明和配置属性。无需 NPK。
- `cat` 返回原始换行。需要下一页时把 `data.next_line` 传给 `--from-line`。完整脚本用 `cat --output 新文件.txt` 写成 UTF-8，原始字节用 `extract` 保存。
- 默认编码与桌面端一致。ACT1 用 GBK，UUID 用 Big5，旧版固定目录用 MS949。样本使用其他编码时，读取、查询和编辑统一传入 `--charset`。

## 编辑、回读和导出

先把脚本保存成新文件。编辑 `laser-edit.txt` 时保持原有脚本结构，然后执行后续命令。将脚本中的 `<12::name_16285>` 这类原始引用和 `inspect` 解析出的显示名称区分开，修改时从 `cat` 返回的原文取值。

```powershell
$draft = '.\pvf-draft'
function Invoke-PvfJson {
    $result = & $cli --json @args | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0 -or -not $result.ok) { throw $result.error.message }
    $result.data
}
$copy = Invoke-PvfJson cat $pvf $entry --output '.\laser-edit.txt'
# 在这里编辑 laser-edit.txt，再执行以下命令。
Invoke-PvfJson stage-text $pvf $draft $entry '.\laser-edit.txt' --expect-sha256 $copy.sha256
Invoke-PvfJson cat $pvf $entry --workspace $draft --max-lines 40
Invoke-PvfJson status $pvf $draft
Invoke-PvfJson workspace-export $pvf $draft '.\Script-edited.pvf'
Invoke-PvfJson cat '.\Script-edited.pvf' $entry --max-lines 40
```

小改动可直接用 `replace-text`。从当前脚本中复制一段唯一的原文作为 `--from`，将新文本传给 `--to`。匹配区分大小写。找不到原文或出现多次时返回 `MATCH_COUNT`。删除该段时使用 `--to=`。

```powershell
& $cli --json replace-text $pvf $draft $entry --from '从当前脚本复制的唯一原文' --to '新文本'
& $cli --json revert $pvf $draft $entry
```

`--expect-sha256` 可用于两种编辑命令。它比较当前条目的原始字节摘要，草稿已变化时返回 `STALE_ENTRY`。读取已有草稿时先用同一个 `--workspace` 再取摘要。工作区绑定源 PVF，同一目录只能由一个进程打开。导出到新文件并保留草稿。对于依赖共享字符串表的二进制脚本，编辑后 `status` 也可能列出 `stringtable.bin`。

## 预览与判定框

预览使用与桌面端相同的 ACT1 技能、装备和物品渲染。先查询分支和时长，再用毫秒取帧。下面的激光炮分支键来自查询结果。

```powershell
& $cli --json preview $pvf $entry --npk $npk
& $cli --json preview $pvf $entry --npk $npk --effect 'laser:charged' --time 1400
& $cli --json preview $pvf $entry --npk $npk --effect 'laser:charged' --time 1400 --output '.\laser.png' --hitboxes
```

只传 `--time` 也会返回 `data.combat_boxes`。`--hitboxes` 把这些框画进 PNG。可用 `--level` 指定等级，`--facing left` 切换朝向，`--workspace` 查看草稿效果。技能时间应小于选定分支的 `data.duration_ms`。返回的盒坐标相对于各盒原点，`origin_screen_y` 是已经投影的纵坐标。

## JSON 与退出码

每次调用输出一个 UTF-8 JSON 对象。固定外层字段如下。

```json
{"schema_version":1,"ok":true,"command":"list","data":{"entries":[],"total":0,"next_offset":null}}
{"schema_version":1,"ok":false,"command":"cat","error":{"code":"ENTRY_NOT_FOUND","message":"Entry not found: skill/example.skl","hint":"Use list to find the exact virtual path inside the archive."}}
```

退出码 `0` 表示成功，`2` 表示参数错误，`3` 表示操作失败。调用方应同时检查退出码和 `ok`。`OUTPUT_EXISTS` 时改用新文件名，`ENTRY_NOT_FOUND` 时重新查找虚拟路径，`TEXT_ENCODING` 时核对编码。各命令的完整参数和示例以 `schema <命令>` 为准。

从源码执行 `gradlew.bat :pvf-cli:installDist` 可生成使用 Java 17+ 的独立发行目录。运行其中的 `bin\pvf-studio.bat` 或 `bin/pvf-studio` 即可使用相同的命令，已有文本输出调用也继续可用。
