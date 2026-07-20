package com.netpress.huck

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.prefs.Preferences

// Ports zouk's AppModelSpec.swift -- save/download/thumbnail flows aren't covered because
// they aren't ported yet (see docs/COWORK.md); selection and delete() now are. Each example gets
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

            context("with a connected model showing one scan") {
                // Matches zouk's AppModelSpec.swift setup -- a real connect() through a fake
                // client rather than assigning model.scans directly, since scans is private-set
                // here (public var in zouk, but nothing outside AppModel needs to replace the
                // whole list directly in this port).
                suspend fun connectedModel(onDelete: suspend (ScanEntry) -> Unit = {}): Pair<AppModel, ScanEntry> {
                    val scan =
                        ScanEntry(
                            name = "1782420815.pdf",
                            size = 7,
                            time = "2026-06-25T17:30:00Z",
                            path = "/download/1782420815.pdf",
                        )
                    val model =
                        AppModel(
                            preferences = scopedPreferences(),
                            clientFactory = { FakeScanFetching(onDelete = onDelete) { listOf(scan) } },
                        )
                    model.hostInput = "scans.example.com"
                    model.connect()
                    return model to scan
                }

                describe("toggle()") {
                    context("when toggled once") {
                        it("selects the scan") {
                            runTest {
                                val (model, scan) = connectedModel()

                                model.toggle(scan)

                                model.selectedScanID shouldBe scan.id
                                model.selectedScan shouldBe scan
                            }
                        }

                        it("deselects the scan when toggled again") {
                            runTest {
                                val (model, scan) = connectedModel()
                                model.toggle(scan)

                                model.toggle(scan)

                                model.selectedScanID shouldBe null
                                model.selectedScan shouldBe null
                            }
                        }
                    }
                }

                context("changeServer() with a scan selected") {
                    it("clears the selection along with the scan list") {
                        runTest {
                            val (model, scan) = connectedModel()
                            model.toggle(scan)

                            model.changeServer()

                            model.selectedScanID shouldBe null
                            model.scans shouldBe emptyList()
                        }
                    }
                }

                describe("requestDelete()") {
                    // Only the footer trash button calls this; a real right-click "Move to
                    // Trash" (zouk's context menu) isn't ported yet, so nothing skips this.
                    it("selects the scan and arms pendingDelete for it") {
                        runTest {
                            val (model, scan) = connectedModel()

                            model.requestDelete(scan)

                            model.selectedScanID shouldBe scan.id
                            model.pendingDelete shouldBe scan
                        }
                    }
                }

                describe("delete()") {
                    context("when the server confirms the delete") {
                        it("removes the scan from scans and clears the selection") {
                            runTest {
                                val (model, scan) = connectedModel()
                                model.selectedScanID = scan.id

                                model.delete(scan)

                                model.scans shouldBe emptyList()
                                model.selectedScanID shouldBe null
                            }
                        }
                    }

                    context("when the server rejects the delete") {
                        it("leaves scans untouched and clears the failure flash afterward") {
                            runTest {
                                val (model, scan) =
                                    connectedModel(onDelete = { throw ScanClientError("offline") })

                                model.delete(scan)

                                model.scans shouldBe listOf(scan)
                                model.savingMessage shouldBe null
                            }
                        }
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

// onDelete comes first (with a default) so trailing-lambda call sites like
// FakeScanFetching { fixtureScans } keep binding to result, the last parameter, unchanged.
private class FakeScanFetching(
    private val onDelete: suspend (ScanEntry) -> Unit = {},
    private val result: () -> List<ScanEntry>,
) : ScanFetching {
    override suspend fun fetchScans(): List<ScanEntry> = result()

    override suspend fun cachedFile(
        scan: ScanEntry,
        cacheDirectory: File,
    ): File = throw NotImplementedError("FakeScanFetching.cachedFile isn't exercised by these specs yet")

    override suspend fun save(
        scan: ScanEntry,
        destination: File,
        cacheDirectory: File,
    ): File = throw NotImplementedError("FakeScanFetching.save isn't exercised by these specs yet")

    override suspend fun delete(scan: ScanEntry) = onDelete(scan)
}
