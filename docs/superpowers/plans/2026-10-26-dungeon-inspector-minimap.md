# Dungeon inspector and native minimap implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** A fixed, usable dungeon inspector with multi-selection dropdowns and native minimap rendering.

**Architecture:** A reusable Swing multi-select popup feeds lossless DungeonDocument commands. The inspector uses compact fixed tabs. A small immutable minimap atlas loads from the shared NPK index asynchronously and supplies pure painting data.

**Tech Stack:** Java 21 Swing/IntelliJ, Java 17 preview library, JUnit 5, existing native product smoke runner.

**Spec:** docs/superpowers/specs/2026-10-26-dungeon-inspector-minimap.md

## Global constraints

- Retain source-range edits, automatic pools, multi-role lists and mixed newline provenance.
- No scrollbars on the right panel; scrollable dropdown popups are allowed.
- No NPK/PVF reads on the EDT or paint path; use the existing index and worker.
- Preserve the original PVF and existing running product; package version 0.14.2.

## Review focus

- Multiple/overlapping roles must remain visible independently of the selected cell.
- Asynchronous pool/resource results must not overwrite changed selections or display another dungeon's atlas.
- Large layouts remain reachable via fit-to-panel plus coordinate dropdown.
- Missing IDs/resources remain inspectable without corrupting source.
- At 1024×720 every page's actions remain inside bounds, and provenance/undo smoke checks still run.

### Task 1: Dropdowns and fixed inspector

**Files:** Create MultiSelectDropdown.java and test; modify DungeonEditorPanel.java and DungeonEditorPanelTest.java under pvf-product/src/{main,test}/java/studio/pvf/intellij.
**Interfaces:** MultiSelectDropdown<T>.options(Map<T,String>), selected(List<T>), selected(), onChange(Consumer<List<T>>), onCommit(Consumer<List<T>>), toggle(T). DungeonEditorPanel keeps install/resolvedChoices/preview APIs.

- [x] Write and run failing tests for multi-role dropdown commands, preserved automatic pools, fixed tabs and all-page bounds.
- [x] Implement dropdowns, tabs, coordinate navigation and independent colored role markers.
- [x] Run targeted tests; commit the tested inspector change.

### Task 2: Native minimap atlas

**Files:** Create pvf-preview/.../DungeonMinimap.java and test; modify ResourcePreviewPanel.java and DungeonEditorPanel.java.
**Interfaces:** DungeonMinimap.load(String,ScenePreview.SpriteLookup) returns immutable atlas; tile(mask) returns loaded image. Panel setMinimap(path,atlas,reason) only accepts the current source image. ResourcePreviewPanel requests atlas using its current resource directory and generation.

- [x] Write/run failing atlas and painting tests for mask mapping, invalid dimensions and fallback.
- [x] Implement verified 120-frame format, background loading/cache, generation guards and nearest-neighbor rendering.
- [x] Run targeted tests; commit native rendering.

### Task 3: Native usability, package and independent review

**Files:** DungeonUsabilitySmoke.java, build.gradle.kts, user documentation.

- [x] Update native smoke for dropdowns and no scrolling, preserving candidate apply/undo/redo/export and all newline provenance checks.
- [x] Verify real Client_test2 Lorien and NPK rendering at 1264 and 1024, all tabs, multiple roles and fallback. Run full Gradle check/product verification and Python validation.
- [x] One fresh final independent review; fix material findings with regression tests.
- [x] Package 0.14.2 and verify extracted portable product; document evidence and commit.
