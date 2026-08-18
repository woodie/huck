package com.netpress.huck

// Bumped by hand alongside build.gradle.kts's `version` -- see docs/DELIVERY.md's release
// checklist. Not read from the jar manifest automatically, matching zouk's own manually
// maintained Resources/Info.plist version string rather than a new single-source-of-truth
// mechanism. See docs/COMMENTS.md.
const val APP_VERSION = "0.5.3"

// The app's own name, in the two forms shown across the UI -- mirrors zouk's
// Sources/ZoukKit/AppInfo.swift 1:1. Unlike zouk, these don't need a separate
// testable-vs-executable-target split to be reachable from tests: Main.kt (src/main) is already
// visible to this module's src/test source set directly, same compilation. Kept alongside
// APP_VERSION rather than in Main.kt itself since AboutWindow.kt already imports from this file
// for the same "app identity" reason.

// Matches macOS's real app-menu ("Hide Huck"/"Quit Huck", driven by build.gradle.kts's
// packageName) -- also used for Main.kt's Help > About menu item's own label so it reads the
// same as that real system menu, instead of that one item alone carrying the full name.
const val APP_SHORT_NAME = "Huck"

// The full, descriptive name -- shown in Main.kt's Window title and AboutWindow.kt's Text,
// never in a menu item's own label.
const val APP_FULL_NAME = "$APP_SHORT_NAME scan retriever"
