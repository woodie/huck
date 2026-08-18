package com.netpress.huck

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// Guards Main.kt's two label usages (Window title, Help > About item) and AboutWindow.kt's Text
// via the shared constants they're built from. Ports zouk's AppInfoSpec.swift -- unlike that
// spec, this doesn't need any @testable-style indirection to reach the constants under test:
// src/test already sees src/main directly in a single Gradle module/compilation, so
// APP_SHORT_NAME/APP_FULL_NAME (declared in AppInfo.kt, same package as Main.kt) are just
// regular top-level references, no separate testable target required.
class AppInfoSpec :
    DescribeSpec({
        describe("APP_SHORT_NAME") {
            it("matches macOS's real app-menu (\"Hide Huck\"/\"Quit Huck\")") {
                APP_SHORT_NAME shouldBe "Huck"
            }
        }

        describe("APP_FULL_NAME") {
            it("matches the window title and About dialog text") {
                APP_FULL_NAME shouldBe "Huck scan retriever"
            }

            it("is APP_SHORT_NAME plus \"scan retriever\", not an independent literal") {
                APP_FULL_NAME shouldBe "$APP_SHORT_NAME scan retriever"
            }
        }
    })
