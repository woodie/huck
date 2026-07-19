.PHONY: build test lint format check package run

# Runs ktlintFormat first, in its own separate Gradle invocation, so it's fully done
# before build/check runs ktlintCheck against the result -- listing both tasks on one
# gradlew call doesn't guarantee that ordering (Gradle schedules by the task graph, not
# left-to-right), which is why this used to need a manual `make format` before `make
# build` would pass. Two invocations is slower but actually reliable.
build:
	./gradlew ktlintFormat
	./gradlew build -x test

run:
	./gradlew run

# Auto-fixes the mechanical stuff ktlintCheck flags (parameter-list wrapping,
# supertype-on-newline, multiline-expression placement, etc.) -- much safer
# than hand-editing indentation to guess what the formatter wants. build/test/check
# below already run this first; call it directly only if you want formatting without
# also running the rest.
format:
	./gradlew ktlintFormat

# clean, not just test -- Gradle otherwise marks the test task UP-TO-DATE on
# an unchanged run and skips re-executing it, which also skips the custom
# TestListener reporter's output entirely (it only prints on real
# execution). Matches next-caltrain-kotlin's test.sh (./gradlew clean test).
test:
	./gradlew ktlintFormat
	./gradlew clean test

# Check-only, no formatting -- the one target that fails loudly on style
# violations instead of silently fixing them, useful if you want to see what
# ktlintFormat would have changed.
lint:
	./gradlew ktlintCheck

check:
	./gradlew ktlintFormat
	./gradlew clean check

# Packages a native distribution for whatever OS you're running this on
# (.dmg on macOS). Windows-native .msi packaging happens in CI -- see
# .github/workflows/windows-package.yml and docs/COWORK.md.
package:
	./gradlew packageDistributionForCurrentOS
