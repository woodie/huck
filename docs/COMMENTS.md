# Comments

Rationale, history, and design notes that don't belong as multi-line comments
in the source. Organized by file, then by the type/function each note is
attached to. See `humane`/`humane-ruby`/`humane-swift`/`humane-kotlin`'s own
`docs/COMMENTS.md` for the pattern this follows.

## src/main/kotlin/Main.kt

### `wiringCheck`
Calls `Humane.humanSize(225_935)` and displays it in the window purely to
prove the `humane-kotlin` composite-build dependency (`settings.gradle.kts`'s
`includeBuild("../humane-kotlin")`) actually resolves at build and run time.
Not a real product feature -- see `docs/COWORK.md`'s "Current status" for
what is and isn't ported from `zouk` yet. Remove once a real feature
exercises the dependency instead.

### Expression-body `fun main()`
`fun main() = application { ... }` with the lambda starting on the same line
as `=` failed `ktlint`'s `function-expression-body` rule the first time this
was written (`A multiline expression should start on a new line`) -- an
expression-bodied function whose expression itself spans multiple lines
needs that expression to start on the line after `=`, not trail on the
declaration line. Fixed by putting `application { ... }` on its own indented
line below `fun main() =`.
