.PHONY: build test lint check package

build:
	./gradlew build -x test

test:
	./gradlew test

lint:
	./gradlew ktlintCheck

check:
	./gradlew check

# Packages a native distribution for whatever OS you're running this on
# (.dmg on macOS). Windows-native .msi packaging happens in CI -- see
# .github/workflows/windows-package.yml and docs/COWORK.md.
package:
	./gradlew packageDistributionForCurrentOS
