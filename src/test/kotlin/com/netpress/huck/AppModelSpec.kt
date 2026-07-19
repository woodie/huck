package com.netpress.huck

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.net.URI
import java.util.UUID
import java.util.prefs.Preferences

// Ports the connection half of zouk's AppModelTests -- selectedScanID/pendingDelete/save/delete
// aren't covered because AppModel doesn't expose them yet, see docs/COWORK.md. Each example gets
// its own scoped Preferences node (not the shared user default node) so tests don't read or
// write real state on the machine running them. runTest (kotlinx-coroutines-test) gives connect()
// virtual time, so the 2-second minimum-connecting-duration floor doesn't actually slow the
// suite down -- matching how zouk's own tests avoid a real 2-second sleep per example.
class AppModelSpec :
    DescribeSpec({
        fun scopedPreferences(): Preferences = Preferences.userRoot().node("com/netpress/huck/test/${UUID.randomUUID()}")

        describe("AppModel") {
            context("constructed with a previously saved host") {
                it("reads hostInput back out of preferences") {
                    val preferences = scopedPreferences()
                    preferences.put("zouk.lastHost", "scans.example.com")

                    val model = AppModel(preferences = preferences)

                    model.hostInput shouldBe "scans.example.com"
                }
            }

            context("connect() with a blank hostInput") {
                it("fails without attempting a network call") {
                    runTest {
                        val model =
                            AppModel(
                                preferences = scopedPreferences(),
                                clientFactory = { FakeScanFetching { emptyList() } },
                            )

                        model.connect()

                        model.state shouldBe
                            ConnectionState.Failed(
                                "Enter a hostname or IP address, like scans.example.com or 10.0.1.111.",
                            )
                        model.hasEverConnected shouldBe false
                    }
                }
            }

            context("connect() with a valid host and a client that succeeds") {
                it("stores the scans, marks hasEverConnected, and persists the host") {
                    runTest {
                        val preferences = scopedPreferences()
                        val fixtureScans =
                            listOf(ScanEntry(name = "scan.pdf", size = 79_992, time = "2026-07-19T10:00:00Z", path = "/files/scan.pdf"))
                        val model =
                            AppModel(
                                preferences = preferences,
                                clientFactory = { FakeScanFetching { fixtureScans } },
                            )
                        model.hostInput = "scans.example.com"

                        model.connect()

                        model.state shouldBe ConnectionState.Connected
                        model.hasEverConnected shouldBe true
                        model.scans shouldBe fixtureScans
                        preferences.get("zouk.lastHost", null) shouldBe "scans.example.com"
                    }
                }
            }

            context("connect() with a client that throws") {
                it("fails and leaves hasEverConnected false") {
                    runTest {
                        val model =
                            AppModel(
                                preferences = scopedPreferences(),
                                clientFactory = { FakeScanFetching { throw ScanClientError("offline") } },
                            )
                        model.hostInput = "scans.example.com"

                        model.connect()

                        model.state shouldBe ConnectionState.Failed("Check that it's on the same network.")
                        model.hasEverConnected shouldBe false
                    }
                }
            }

            context("changeServer()") {
                it("resets hasEverConnected, state, and scans") {
                    runTest {
                        val fixtureScans =
                            listOf(ScanEntry(name = "scan.pdf", size = 79_992, time = "2026-07-19T10:00:00Z", path = "/files/scan.pdf"))
                        val model =
                            AppModel(
                                preferences = scopedPreferences(),
                                clientFactory = { FakeScanFetching { fixtureScans } },
                            )
                        model.hostInput = "scans.example.com"
                        model.connect()

                        model.changeServer()

                        model.state shouldBe ConnectionState.Idle
                        model.hasEverConnected shouldBe false
                        model.scans shouldBe emptyList()
                    }
                }
            }

            describe("baseUrlFrom") {
                context("a blank string") {
                    it("returns null") {
                        AppModel.baseUrlFrom("   ") shouldBe null
                    }
                }

                context("a host with no scheme") {
                    it("prepends http://") {
                        AppModel.baseUrlFrom("scans.example.com") shouldBe URI("http://scans.example.com")
                    }
                }

                context("a host that already has a scheme") {
                    it("leaves it unchanged") {
                        AppModel.baseUrlFrom("https://scans.example.com") shouldBe URI("https://scans.example.com")
                    }
                }
            }
        }
    })

private class FakeScanFetching(
    private val result: () -> List<ScanEntry>,
) : ScanFetching {
    override suspend fun fetchScans(): List<ScanEntry> = result()
}
