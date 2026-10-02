# Status

**Current:** v1.0.0, the first public release (being prepared).

## v1.0.0

- `main` starts with the initial import commit: the source zip contents,
  byte for byte, plus `.gitattributes` (no line-ending conversion) and
  `.gitignore`.
- `feat/lamplighter-development` adds the README (with screenshots rendered
  by the game's own `PlayRunner` test), CHANGELOG, LICENSE (MIT for the
  code; the bundled IM FELL English SC font is OFL 1.1, text in
  `docs/licenses/`), project docs, the Claude Code skills, agent and hooks,
  CI, and the branch protection settings.
- `PlayRunner` must run before `MonkeyKt`: it writes `build/snapshots.txt`,
  which Monkey restarts from. `IconGen` rewrites `res/`; it never runs in CI
  and the source hook blocks it.
- Release asset: the original APK, renamed `Lamplighter-v1.0.0.apk`
  (SHA-256 `aaeea6644804cc109a299d4368bf20a72949ddae5816734543f0830b8233fe82`),
  signed with the original key.

## Next

Nothing planned. Before the first release built from source, create the new
permanent signing key (see [workflow.md](workflow.md#signing-keys)).
