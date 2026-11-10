# Game Effect Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在用户确认的中央预览布局内组合装备/装扮、称号与有依据的技能特效，消费当前编辑快照，交付可运行便携版。

**Architecture:** 在 pvf-preview 增加只读脚本、本地化、玩家装配和多轨渲染模型，复用既有 NPK/IMG 与 ANI 渲染。桌面共享一个活动 GamePreviewPanel，接入 VisualEditorSurface、当前文档快照和资源索引；代码模式继续使用原预览。按原生证据逐项启用版本配方。

**Tech Stack:** Java 17 预览核心、Java 21 Swing/IntelliJ 桌面、Gradle/JUnit、现有 Python 产品检查；本机 JDK 25。

**Spec:** `docs/superpowers/specs/2026-11-10-game-effect-preview-design.md`

## Global Constraints

- 首个完整验收版本为用户截图对应的 ACT1。
- 预览读取顺序：当前未保存 Document 快照 → 已保存草稿 → 原包。
- 预览职业、技能等级和搭配属于预览设置，不自动写回 EQU/SKL。
- 方案 A 的游戏对照属于验收手段，不是嵌入或自动启动游戏客户端的功能。
- 同一预览必须绑定配套 PVF、NPK 和版本，记录样本摘要。
- 源码不包含原游戏资源；全部验证输出置于 build 或仓库外。
- 在现有隔离分支直接执行、最终一次独立审阅；主目录已有删除保持。

## Review Focus

- 未保存的被引用 LAY/ANI/STR 修改应刷新，旧异步结果不能替换新标签；归属任务 3。
- 多次 include 的图层和 variation 上下文不能串到其他装备；归属任务 1。
- 缺失或循环引用局部诊断并保持 UI 响应；归属任务 1、2。
- 相同技能 ID 的其他版本不得启用 ACT1 配方；归属任务 2。
- 没有实例信息的属性不能显示伪造的强化、随机品级或最终角色值；归属任务 1。

## Task 1: Script, localization and character assembly

**Files:** create `pvf-preview/src/main/java/studio/pvf/preview/GameScript.java`, `GameItemInfo.java`, `CharacterAppearance.java`; tests `GameScriptTest.java`, `CharacterAppearanceTest.java` in the corresponding test package.

**Interfaces:**
- `GameScript.Lookup.read(String path) throws IOException` supplies immutable active snapshots; `GameScript` token events preserve includes and quoted tags.
- `GameItemInfo.read(String path,String text,GameScript.Lookup lookup,int level)` returns name, explanations, attribute rows and diagnostics.
- `CharacterAppearance.load(String path,String text,GameScript.Lookup lookup,Set<String> paths,String job,String action,Map<String,String> outfit)` returns ordered `Part` records (ANI, variants, layer, slot), actions and warnings.

- [ ] Write tests with hand-checked fixtures for two included layers, `%s` resolution, variations, hidden/replaced slots and native relative paths; recursive include rejection; localized `<12::name>` lookup; raw numerical range retained and no runtime-instance fields invented.
- [ ] Run `gradlew.bat :pvf-preview:test --tests '*GameScriptTest' --tests '*CharacterAppearanceTest'` and observe missing-feature failure.
- [ ] Implement bounded event parsing/localization, equipment info and five-job CHR/LAY assembly. Derive defaults from actual scripts/resources and record evidence rather than invent body offsets.
- [ ] Re-run the task tests, then `gradlew.bat check -PwithIntellij=true :pvf-product:test`; expect success.
- [ ] Commit this independently usable parsing/assembly component.

## Task 2: Multi-track rendering and verified effects

**Files:** create `pvf-preview/src/main/java/studio/pvf/preview/GamePreview.java`, `GameEffectRecipes.java`; tests `GamePreviewTest.java`; create `validation/GamePreviewAudit.java` and evidence/support notes.

**Interfaces:**
- consumes task 1 parts and `GameScript.Lookup` plus `NpkResources`.
- `GamePreview.load(path,text,lookup,paths,resources,Options)`; Options carry job/action/outfit/skill level/ACT1 identity/branch.
- `GamePreview.render(long milliseconds,boolean mirrored)` returns prepared draws, bounds, diagnostics and duration; `GamePreview.info()` exposes parsed item information.
- Version recipes emit timed ANI tracks using script parameters and evidence-backed anchors. They never execute battle state.

- [ ] Add failing pixel/timing tests for independent track durations, mirroring, seek reproducibility, delayed start/end, missing part diagnosis, version mismatch and modified delay/range.
- [ ] Implement rendering with existing `AnimationTimeline` and `PreviewRenderer`, bounded prepared-pixel caching, fixed union bounds and finite effect tracks.
- [ ] Audit real ACT1 five jobs, equipment/avatars/titles and locate screenshot items. Resolve actual laser dependencies and a second job skill; investigate Sun Eater definitions and client-backed effects. Unsupported cases remain explicit evidence gaps, never fabricated full effects.
- [ ] Run relevant tests plus whole suite; capture real rendered frames and timings in ignored build output; expect no unreported warnings for supported examples.
- [ ] Commit renderer, proven recipes and audit tool.

## Task 3: Preview-first desktop layout and live editing

**Files:** create `pvf-product/src/main/java/studio/pvf/intellij/GamePreviewPanel.java`; modify `PvfStudioFrame.java`, `VisualEditorSurface.java`, `ResourcePreviewPanel.java`; test `GamePreviewPanelTest.java` and native workbench smoke support.

**Interfaces:**
- panel consumes `ResourcePreviewPanel.Source`, immutable current path set, version identity and shared `NpkResources`; owns its cancellable generation state and playback clock.
- `ResourcePreviewPanel.resources()` exposes its current indexed resources package-locally; resource-change callback refreshes the active game panel.
- EQU/SKL/STK sessions own a stable host inside `VisualEditorSurface`, with default DESIGN renamed “效果预览”; other session types preserve their behavior.

- [ ] Write a failing Swing test for view switching without document recreation, stale source results, pause/seek/level changes and cleanup.
- [ ] Implement central actor/effect canvas; right-side game info/outfit/resources tabs; bottom timeline; controls for job, actions, facing, level, branch, speed and zoom. Use real resources exclusively.
- [ ] Add same-PVF outfit chooser, reset/toggle controls, resource-directory entry and explicit partial/unsupported messages. Remember preview choices per workspace, with no script dirtying.
- [ ] Integrate shared panel lifecycle, current snapshots and dependency refresh into existing session/rename/close/restore paths. Avoid scene catalog work for game sessions.
- [ ] Run Swing/model tests and whole suite; verify both preview and code modes keep existing save/undo/diff shortcuts.
- [ ] Commit desktop integration.

## Task 4: Real workbench, game comparison and portable delivery

**Files:** update `tools/check_product.py` and product smoke routing; add `GamePreviewWorkbenchSmoke.java`; update README/CHANGELOG, `docs/game-effect-preview.md` and acceptance report.

**Interfaces:** use task 3 actual UI and task 2 audit to produce a support matrix and evidence files. Build with existing `tools/build-release.ps1 -ZipOnly` and pinned dependencies.

- [ ] Add/run real workbench checks for EQU/SKL/STK selection, live top-level/dependency edits, quick switching, resource reindex, no preview dirtying, 1024×720 and 1264×811 layouts, cleanup and session restoration.
- [ ] Compare actual actor/title/action and skill stages with same-version native evidence. Record any missing runtime evidence explicitly and continue obtaining it where available.
- [ ] Run full JVM/Python suites and existing desktop smoke regression; expect no failures or unexplained changed behavior.
- [ ] Build a distinct local version and portable ZIP, verify identity/no game assets/checksums; inspect actual screenshots and provide source/build attribution and limitations.
- [ ] Commit verification/docs, obtain one independent whole-change review, fix important findings with regression evidence and report the actual final scope.
