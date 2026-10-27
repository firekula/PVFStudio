# PVF export selection and source verification repair

Continue inline with final independent review, as previously authorized. The source remains read-only.

Root evidence: user workspace records SHA-256 `5fcd4b94…`; the current source has exactly that digest, but file metadata changed after the running session opened it. Strict equality against initial identity/mtime blocks every destination before export. The export chooser also lacks the PVF filter already used by Open.

1. [x] RED/GREEN tests for metadata-only changes and identical file replacement. Verify both original channel and current pathname bytes, reject real changes to either, and require stable size/type/identity/mtime across each verification. Keep strict opening-time validation and read-only-source/output publication rules.
2. [x] PVF-only chooser, `.pvf` completion for names without suffix, rejection of other suffixes; keep the chooser open on invalid input.
3. [ ] Native product tests on a private copy: new destination, existing destination with recoverable backup, metadata touch and identical replacement; true source changes must block publication without losing drafts. Full tests, one final review, package 0.14.7 and verify extracted product.

Review focus: source handle versus path replacement, same-size/restored-mtime modifications, source aliases, content changes during verification, existing target backup/no-clobber behavior, and actual chooser approval.
