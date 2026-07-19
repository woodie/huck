.PHONY: build test lint format check package run

build:
	./gradlew build -x test

run:
	./gradlew run

# Auto-fixes the mechanical stuff ktlintCheck flags (parameter-list wrapping,
# supertype-on-newline, multiline-expression placement, etc.) -- much safer
# than hand-editing indentation to guess what the formatter wants. Run this
# before `make build`/`make check` whenever lint fails on wrapping-style
# violations.
format:
	./gradlew ktlintFormat

# clean, not just test -- Gradle otherwise marks the test task UP-TO-DATE on
# an unchanged run and skips re-executing it, which also skips the custom
# TestListener reporter's output entirely (it only prints on real
# execution). Matches next-caltrain-kotlin's test.sh (./gradlew clean test).
test:
	./gradlew clean test

lint:
	./gradlew ktlintCheck

check:
	./gradlew clean check

# Packages a native distribution for whatever OS you're running this on
# (.dmg on macOS). Windows-native .msi packaging happens in CI -- see
# .github/workflows/windows-package.yml and docs/COWORK.md.
package:
	./gradlew packageDistributionForCurrentOS
