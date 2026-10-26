# MAP / DGN Visual Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. User chose native execution by the primary agent, with a fresh whole-branch reviewer at the end.

**Goal:** Deliver editable MAP / DGN visual surfaces with shared Code / Split / Design documents, native room and scene commands, and version-aware hell-room configuration.

**Architecture:** Add a pure Java `pvf-scene-edit` module that parses lossless source ranges and produces guarded text edits; reuse `pvf-preview` rendering and `pvf-workspace` codecs/transactions. `pvf-product` adapts these edits to one existing IntelliJ Document per file and hosts visual, code and split surfaces inside the same editor tab. File cloning and index registration use an atomic workspace transaction and a unified undo action.

**Tech Stack:** Java 17 model modules; Java 21 IntelliJ/Swing desktop; existing Gradle 9.5.0 wrapper; JUnit 5.11.4; bundled IntelliJ Community 2025.2.6.3; local JDK 25; existing Python validation and product smoke infrastructure.

**Spec:** `docs/superpowers/specs/2026-10-26-map-dungeon-visual-editor-design.md` (user approved 2026-10-26).

## Global Constraints

- Default mode is 可视化; switching 代码 / 分屏 / 可视化 never saves, regenerates source, disposes the Editor, or resets undo.
- Source PVFs remain read-only. Ctrl+S writes drafts; export creates a validated new PVF with the existing backup rules.
- One virtual path has one Session/Document. DGN → MAP navigation opens/selects the MAP's own tab.
- Preserve unknown fields/tokens, comments, section spelling/language, line endings and record order outside the edited ranges.
- Text, B0 D0 projections, Korean aliases and nested ani info require explicit support; ambiguous structures remain locally read-only.
- New graph connections are reciprocal and cardinal. Preserve existing inconsistent configurations as diagnostics until the user edits them.
- Maze limit 256; layout dimensions 50×50; retain multiple start/Boss/MAP candidates and native no-Boss `-1 -1`.
- MAP object projection remains `y-z+120`; tile width 224; basic pass cells 16×12; extended row height 120. Never replace the existing renderer with mockup geometry.
- Hell ability, per-maze replacement coordinates/MAP and escape-hell exclusion are distinct. Keep ACT1 direct-monster and newer hellparty payload formats.
- ACT1 hit-count length 4 and sampled newer length 5 remain intact. Unverified probability units remain raw values.
- No AI/combat simulation, invented direct MAP/PTL tag, collision painting, WDM editor or automatic public release.
- Deployment, uploads and original game-data replacement are outside this plan; package a local reviewable Windows build.

## Review Focus

1. Multiple identical entities on one binary-projected line must remain separately selectable/editable (Tasks 1/2/7).
2. A code edit arriving during a drag or async parse must defeat the stale graphical write (Tasks 5/7).
3. Closing/renaming/searching a tab whose component is now a surface container must still target the correct Session (Task 5).
4. A cloned MAP registration failing after string-table compilation must leave no new path, list reference or partial draft (Task 4).
5. A DGN with several maze blocks, quest fields, an excluded layout and distinct normal/Boss/hell rooms must not leak settings between layouts (Tasks 3/6).

## Execution Environment and Tracking

- Repository: `G:/Game/DNF_ACT1/PVFStudio`; the chat's original cwd is a different repository, DFLegacy. Pass this repository explicitly to every command.
- Baseline commit `a928298`, branch `codex/pvf-core`, initially clean. Do not include DFLegacy's unrelated local changes.
- Execution method is already selected: native. Do not dispatch task implementers; request one final independent reviewer under the review skill.
- Do not create an unrequested worktree in the chat's different repository. Inspect isolation and applicable preferences before execution; stay in the explicit PVFStudio checkout if no separate-worktree consent exists.
- Use the installed fixed SDK/runtime under `build/sdk/`; avoid downloading a new platform or changing library versions for this feature.
- Progress boxes are updated after their command evidence exists. Commit completed logical tasks, without pushing a release.

## Files and Responsibilities

- New `pvf-scene-edit/build.gradle.kts` and `src/main/java/studio/pvf/sceneedit/`: source token/range model, guarded edits, map/dungeon commands, catalogs and diagnostics. No IntelliJ or filesystem dependency in domain command classes.
- Modify `settings.gradle.kts`, root `build.gradle.kts`, `pvf-product/build.gradle.kts`: include/test/package the new module, Java targets unchanged.
- New focused `pvf-product/.../SceneDocumentBridge.java`, `VisualEditorSurface.java`, `DungeonEditorPanel.java`, `MapEditorPanel.java`, `SceneResourceBrowser.java`, `MapCloneCommand.java`: document binding, surfaces, domain-specific controls and transaction adapter.
- Modify `PvfStudioFrame.java`, `EditorSessionState.java`: Session container identity, navigation, snapshots, actions, restore and disposal.
- Modify/extract `ResourcePreviewPanel.Canvas` into `SceneCanvas.java` without changing painting, camera or playback results; add selection/gesture hooks and renderer source identities as needed.
- Modify `DungeonScript.java` / `DungeonPreview.java` only where necessary for per-maze hell preview and stable room semantics; retain preview-only clients' existing behavior.
- Modify `PvfWorkspace.java`: atomic text/structural batch publication and snapshots for reversible clone operations.
- Add focused model/Swing tests, real-sample audit/smoke support, `docs/visual-map-editor.md`, README and CHANGELOG updates.

## Task 1: Lossless source ranges and guarded edits

**Files:** new `pvf-scene-edit` module, `SourceDocument.java`, `ScriptEdit.java`, `SourceDocumentTest.java`, `ScriptEditTest.java`; modify Gradle settings/root/product packaging; expose existing preview keyword canonicalization through `ScriptDialect` only if needed.

**Interfaces:**
- `SourceDocument.parse(String text)` returns an immutable source with ordered `Token`, `Section`, `Range` and diagnostics. Tokens retain raw spelling, decoded value and exclusive UTF-16 offsets.
- `ScriptEdit(String label, String baseline, List<Replacement> replacements)`; `Replacement(int start, int end, String expected, String value)`; `String applyTo(String current)` rejects a mismatched baseline/expected slice or overlapping ranges.
- `EntryId(String section, int sectionOccurrence, int recordOrdinal)` identifies a record within a particular parsed snapshot; it is never silently reused across a changed document.

- [ ] Write failing tests `quotedAndCommentedTagsAreNotSections`, `keepsSeparateFlatRecordsAndOpaqueTokens`, `preservesCrLfAndKoreanTagSpelling`, `rejectsStaleOrOverlappingEdits`. Assert untouched substrings exactly equal the input; a coordinate edit changes only `100` to `125` inside the selected record.
- [ ] Run `$env:JAVA_HOME='C:\Users\Firekula\.jdks\ms-25.0.4'; .\gradlew.bat :pvf-scene-edit:test --console=plain`; confirm the tests fail for missing implementation, not tooling.
- [ ] Implement the lexer/ranges and descending-offset replacements; add the module to `check` and desktop jar assembly. Unterminated quoted/comment structures produce an explicit unsafe region.
- [ ] Re-run the focused tests and `:pvf-preview:test :pvf-workspace:test`; require zero failures.
- [ ] Commit `feat: add lossless scene source edits`.

## Task 2: MAP commands, native records and resource catalogs

**Files:** new `MapDocument.java`, `MapEntry.java`, `SceneCatalog.java`, `MapDocumentTest.java`, `SceneCatalogTest.java` in pvf-scene-edit; reuse SourceDocument/ScriptEdit from Task 1.

**Interfaces:**
- `MapDocument.parse(String path, String text)`, `entries()`, `tiles(boolean extended)`, `move(EntryId,double,double,double)`, `copy(EntryId)`, `remove(EntryId)`, `setField(EntryId,String,String)` all return/read immutable models; mutation methods produce `ScriptEdit`.
- `addEntity(EntityScript.Kind,int,double,double,double)`, `addAnimation(String,String,boolean,boolean,double,double,double)`, `replaceTiles(boolean,List<String>)`, `setSpecialContents(EntryId,List<SpecialContent>)` produce guarded edits; typed special contents preserve direct-monster vs hellparty formats.
- `SceneCatalog.build(Set<String> paths, SceneCatalog.TextLookup lookup)` supplies local descendant TIL/ANI references and global map/NPC/monster/passive IDs; nested `@FunctionalInterface TextLookup { String read(String path) throws IOException; }`. Names/previews load lazily outside the domain model.

- [ ] Write failing tests for two same-ID entities on a single line, NPC optional Z, champion/conditional/special payload boundaries, auxiliary monster-AI alignment, nested-background preservation, relative path resolution, sparse/commented/duplicate IDs and Korean aliases. Assert moving the second of two identical records leaves the first byte-for-byte unchanged.
- [ ] Run `:pvf-scene-edit:test --tests '*MapDocumentTest' --tests '*SceneCatalogTest'`; confirm expected red failures.
- [ ] Implement native record schemas and minimum defaults, preserving unsupported tails locally. Make tile sequence edits preserve basic/extended ownership and reject newly incomplete rows; update known AI companion records together. Do not add per-instance fields absent from the format.
- [ ] Re-run the focused tests plus `:pvf-preview:test`; require zero failures, including `unsafeAuxiliaryAiDisablesStructuralMonsterEditsButAllowsKnownCoordinates` and `addingFactoryObjectDoesNotInventPtlTag`.
- [ ] Commit `feat: edit map placements and tile sequences`.

## Task 3: DGN layout and version-aware hell commands

**Files:** new `DungeonDocument.java`, `DungeonDocumentTest.java` in pvf-scene-edit; targeted changes/tests in `DungeonScript.java`, `DungeonPreview.java` and `DungeonPreviewTest.java` for hell-aware preview.

**Interfaces:**
- `DungeonDocument.parse(String path,String text)` exposes ordered `Maze` models with explicit room membership, candidate lists and source scopes.
- `createRoom(int maze,Position,List<Integer>)`, `removeRoom(int,Position)`, `connect(int,Position,Direction,boolean)`, `setCandidates(int,Position,boolean boss,List<Integer>)`, `setStarts(int,List<Position>)`, `setBosses(int,List<Position>)`, `resize(int,int,int,boolean crop)`, `copyMaze(int)`, `addMaze(int,int)` return ScriptEdit.
- `hell(int maze)` returns `HellSettings`; `setHell(int,HellSettings)` updates only native-scoped fields; `diagnostics()` reports structural/reference problems without rewriting the source.
- Preview gets an explicit ordinary/hell selection parameter or overload; existing callers retain ordinary behavior by default.

- [ ] Write failing tests `connectUpdatesReciprocalBits`, `deleteRoomRemovesItsReferencesWithoutDeletingMapFiles`, `resizeRefusesImplicitCropping`, `retainsMultipleCandidatesAndNoBoss`, `keepsQuestFieldsAndOtherMazesUntouched`, `hellReplacementAndEscapeAreScopedPerMaze`, `preservesFourAndFiveHitCounts`, `keepsExistingExplicitMapMismatchAsDiagnostic`, and `pendingAaRoomIsNotReportedPlayable`.
- [ ] Run new dungeon tests and existing `:pvf-preview:test --tests '*DungeonPreviewTest'`; confirm meaningful red failures before implementing.
- [ ] Implement graph commands using native right/up/left/down bits 1/2/4/8, reciprocal edits and 50×50/256 limits. Track AA membership through explicit room specifications/roles and diagnose disconnected unfinished rooms. Never enable a new-version-only tag in an old dialect without evidence.
- [ ] Run the focused suites; verify normal Boss (4,1), hell (4,0), MAP 60067 remain independent in the representative fixture. Add reachability checks across all candidate positions, not just the displayed arrangement.
- [ ] Commit `feat: edit dungeon layouts and hell room settings`.

## Task 4: Atomic MAP clone, registration and undo snapshots

**Files:** modify `pvf-workspace/PvfWorkspace.java`, extend `WorkspaceTest.java` / `BinaryScriptCodecTest.java`; new domain `MapRegistration.java`; desktop `MapCloneCommand.java` and tests.

**Interfaces:**
- Add `PvfWorkspace.applyTextBatch(Map<String,byte[]> createdFiles,List<PvfWorkspace.TextChange> changes)` returning `PvfWorkspace.BatchSnapshot`; nested `TextChange(String path,String text,Charset charset,byte[] expectedBytes)` guards existing paths, while newly created paths require a null before value and an explicit entry in createdFiles. Compile all affected binary scripts against one evolving string table and publish one manifest transaction.
- `restoreBatch(BatchSnapshot snapshot,boolean toAfter)` applies the immutable before/after state only if expected current paths/content match; snapshots retain absent-path vs empty-file distinctions, structural origins and string-table state. New-path collision and integer ID allocation are checked before publication. Unrelated drafts must survive restore unchanged.
- `MapRegistration.prepare(...)` consumes source MAP, map.lst, DGN snapshots and requested same-directory destination; returns new ID, source edits and clone bytes. `MapCloneCommand` binds it to IntelliJ document/undo infrastructure after Task 5.

- [ ] Write failing tests for clone+map.lst+DGN all succeeding, duplicate destination/ID, stale unsaved list snapshot, encoding failure, manifest failure, string-table append and undo/redo. Assert `paths`, `dirtyPaths`, before bytes and references remain identical after every pre-commit failure.
- [ ] Run `:pvf-workspace:test --tests '*WorkspaceTest'` and the registration tests; verify expected red failures.
- [ ] Implement prepare-before-publish and atomic structural/text batch state; keep original PVF read-only. During the short cross-layer commit, freeze affected editor actions, verify all Document versions before publication, apply document changes as one global undo action, and roll the workspace back if the document phase fails. Do not perform long resource loads on the EDT. Default clone is same directory; cross-directory destinations require verified relative-reference rewriting or explicit rejection.
- [ ] Re-run workspace/model tests and independent Python archive tests. Test a delete-room command never deletes a shared MAP or catalog entry.
- [ ] Commit `feat: clone and register maps atomically`.

## Task 5: One Document with Code / Split / Design surfaces

**Files:** new `SceneDocumentBridge.java`, `VisualEditorSurface.java` and focused tests; modify `PvfStudioFrame.java`, `EditorSessionState.java`, `EditorSessionStateTest.java`; extend editor/tab/search/close smoke coverage.

**Interfaces:**
- `SceneDocumentBridge` adapts one Document to immutable parse snapshots, guarded `ScriptEdit` writes, diagnostics and disposal; accepts an Executor and callbacks so core race logic is testable without platform startup.
- `VisualEditorSurface` is the Session tab component, wrapping the existing Editor component and a visual child; exposes `CODE`, `SPLIT`, `DESIGN`, view state capture/restore and source-range reveal callbacks.
- Session gets a stable `component()` accessor. Every selection, rename, close, search jump, tab decoration and restore operation uses this container rather than `editor.getComponent()` identity.

- [ ] Write failing tests for mode switching preserving Document identity/content/caret/undo, old session metadata compatibility, duplicate-path navigation, full Session component routing, stale parse rejection and invalid-source write blocking. Run the focused model/Swing tests and verify red.
- [ ] Implement the bridge via IntelliJ write commands and one undo unit per action. Register Save/Undo/Redo/Find actions on both code and visual surfaces; select/create exactly one MAP tab for navigation.
- [ ] Add backward-compatible view metadata and disposal without recreating the Editor; preserve existing newline/encoding save and export paths.
- [ ] Run `:pvf-product:test -PwithIntellij=true`, `:pvf-product:verifyProduct` and existing tab/search/editor-session/window-close smoke. Require zero failures and no changed document after mode-only switching.
- [ ] Commit `feat: add shared visual and code editor surfaces`.

## Task 6: DGN grid, room properties and hell UI

**Files:** new `DungeonEditorPanel.java`, `DungeonEditorPanelTest.java`, related dialog helpers only as needed; integrate with VisualEditorSurface, SceneCatalog and document navigation; extend dungeon workbench smoke.

**Interfaces:** panel consumes a parsed DungeonDocument snapshot, catalog, guarded edit callback and `openMap(path,originRoom)` callback. It emits source EntryId/range selection; it does not save drafts or own a second Document.

- [ ] Write Swing tests for clicking an empty grid cell/create, reciprocal connection toggles, MAP candidate selection, start/Boss/hell independence, layout switch/clone/new, crop preview, error recovery and MAP navigation. Include a multi-maze quest fixture with escape-hell exclusion.
- [ ] Implement the room grid and property controls using Task 3 edits; use native graph diagnostics and keep ordinary/hell preview separate from editing state. Show raw advanced parameters and original field count.
- [ ] Connect existing DungeonPreview/ScenePreview and original gate coordinates; opening the room MAP selects its actual file tab, with a return-to-origin action.
- [ ] Run focused Swing tests and real ACT1/86/100 DGN smoke in isolated draft directories. Check 1024×720 and 1264×811 layouts, disabled unsupported rows and valid source navigation.
- [ ] Commit `feat: add visual dungeon layout editor`.

## Task 7: MAP canvas gestures, properties and resource placement

**Files:** extract `SceneCanvas.java` from `ResourcePreviewPanel.java`; new `MapEditorPanel.java`, `SceneResourceBrowser.java`, hit/gesture adapters and tests; targeted renderer metadata changes; extend actual resource workbench smoke.

**Interfaces:** canvas retains existing paint/camera results and exposes reversible world/view transforms and selection overlays. MapEditorPanel consumes MapDocument entries and ScenePreview rendering; placement/drag/inspector actions emit ScriptEdit through Task 5. SceneResourceBrowser consumes Task 2 catalogs and background thumbnail lookup.

- [ ] Write failing tests for hit identity across duplicate/multipart/hidden/logic entities, anchor and actual frame hits, inverse projection at zoom/pan/viewport/parallax, XY drag preserving Z, Escape cancel, and code edit during drag preventing stale commit.
- [ ] Extract/reuse painting without visual drift; preserve current camera/particle sorting and normal-layer Y rules. Add select/place tools, middle/space pan, overlapping-object cycling, source reveal, clone/delete and exact properties.
- [ ] Add descendant TIL/ANI browsing, global NPC/monster/passive search and lazy real thumbnails; support base/extended sequences, native entity properties, background order and special/hellparty contents. Global-definition-only fields navigate to their file instead of generating unsupported instance fields.
- [ ] Run model/Swing tests plus actual NPK screenshot comparisons before/after extraction. Exercise adding TIL, monster, passive, ANI and a recognized particle factory, then code/split switches and undo.
- [ ] Commit `feat: add interactive map scene editor`.

## Task 8: End-to-end compatibility, local Windows build and independent review

**Files:** new `VisualSceneEditorSmoke.java`, real-sample `validation/VisualSceneEditAudit.java`; modify product starter/smoke dispatch and `tools/check_product.py`; new `docs/visual-map-editor.md`; update README/CHANGELOG and agreed local product version.

**Interfaces:** audit takes explicit PVF/resource/output paths and always creates a fresh isolated work directory. Smoke has deterministic named UI controls and writes results/screenshots; no fixture game data is committed.

- [ ] Add end-to-end tests proving edit → save → reopen → export → independent readback for MAP, DGN and hell settings; verify unchanged scripts and original archive SHA-256. Cover all five sample formats where the feature exists.
- [ ] Run `gradlew.bat check -PwithIntellij=true :pvf-product:verifyProduct` and `python -B -m unittest discover -s validation -p 'test_*.py'`, then new visual editor smoke and the relevant existing regression smokes.
- [ ] Build a local Windows product/portable package using `tools/build-release.ps1 -ZipOnly` once tests pass; verify packaged module closure, launch and UI flows. Do not publish GitHub assets without authorization.
- [ ] Write accurate user instructions, compatibility limits and validation evidence. Keep unsupported 100-level game loading distinct from script read/write evidence; do not claim in-game tests that were not run.
- [ ] Request a fresh independent whole-branch code review under the review skill; fix actionable findings, repeat affected checks, then finish integration under the branch-finishing skill. Deliver the local runnable build and evidence summary, preserving the ability to continue from the source branch.

## Self-review and Acceptance Mapping

- Spec §§4/10: Task 5 (shared surfaces/session lifecycle), Task 7 (gesture race), Task 8 (desktop recovery).
- Spec §5: Tasks 3/6 (graph, candidates, size, roles and multi-layout behavior).
- Spec §6: Tasks 2/7 (native MAP record edits and resources; no invented features).
- Spec §7: Tasks 2/3/6/8 (both payload styles, per-maze hell, raw parameters, actual sample evidence).
- Spec §§8/9: Tasks 1/2/4/5 (lossless ranges, guard, encoding, atomic clone and undo).
- Spec §11: Every task has a focused red/green gate; Task 8 validates the whole installed product.
- All five Review Focus items have explicit tests in their owning tasks. Class names in later tasks consume the interfaces declared above; helper class names may be chosen by the implementer without changing the contracts.

## Current Status

- [x] Design approved; native execution selected.
- [x] Plan written and self-reviewed.
- [x] User plan review checkpoint: approved 2026-10-26; user explicitly requested a separate isolated worktree.
- [x] Baseline verified: `gradlew.bat check -PwithIntellij=true --console=plain` successful (173 cached JVM tests, zero failures/errors/skips); independent Python validation 14/14 passed on 2026-10-26.
- [x] Execution workspace selected and product implementation begun: `.worktrees/map-dungeon-editor`, branch `codex/visual-map-dungeon-editor`; isolated baseline successful.
- [x] Tasks 1–8 implemented with command evidence; final independent whole-branch review is the remaining branch gate.
