# MAP candidate filtering and wheel repair

User follow-up: popup wheel must scroll; ordinary choices must exclude maps with configured Boss monsters; Boss choices must include only maps with configured Boss monsters. Continue the existing authorized inline implementation and final review workflow.

Design: classify the native monster / conditional-monster records using the shared record boundary parser, independently of MAP type. Cache immutable original results per workspace/charset; rescan open or drafted MAP text so unsaved edits, saves, undo and rename remain current. Never do PVF reads on the EDT. Ambiguous/unsupported records are unknown and excluded from new choices. Explicit existing mismatches remain visible as labeled, removable bindings, without rewriting source. Automatic pool display is intersected with eligible options; preview association semantics stay intact.

1. [x] Reproduce popup wheel routing in the real product; add a failing wheel regression and handle events over rows, search and scrollbar at useful increments without closing the popup.
2. [x] Write failing classification/cache/field tests; implement Boss-content metadata and background filtered catalogs. Preserve prior selection-performance, source/version, and binding guards.
3. [x] Verify actual ACT1 choices and unsaved MAP updates, physical wheel operation, popup performance and old regressions. Full tests, one targeted independent review, package 0.14.6 and test extracted artifact.

Review focus: Boss flags after champion traits / NPC fields / conditional sections; incomplete records must not masquerade as no-Boss; old bindings remain removable; caches survive undo correctly; stale workers cannot overwrite current filter; wheel routing scrolls exactly once.
