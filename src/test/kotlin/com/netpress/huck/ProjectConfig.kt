package com.netpress.huck

import com.netpress.kwick.JustBeforeEachExtension
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.spec.SpecExecutionOrder
import io.kotest.core.test.TestCaseOrder

// Pins spec/test execution order so full-suite output is reproducible -- matches
// humane-kotlin's and next-caltrain-kotlin's ProjectConfig.
object ProjectConfig : AbstractProjectConfig() {
    override val specExecutionOrder = SpecExecutionOrder.Lexicographic
    override val testCaseOrder = TestCaseOrder.Sequential

    // Without this, justBeforeEach (ScanClientSpec.kt) is a silent no-op --
    // see kwick's own README "Setup".
    override fun extensions() = listOf(JustBeforeEachExtension)
}
