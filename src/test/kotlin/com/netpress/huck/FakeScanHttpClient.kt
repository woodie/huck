package com.netpress.huck

import java.net.URI

// Matches zouk's FakeHTTPClient -- each handler is unset by default and errors loudly if called
// unexpectedly, the same tripwire role zouk's own "throw URLError(.unknown) if no handler" plays
// (see ScanClientSpec's "already cached" context, which sets downloadHandler to fail the test if
// cachedFile() ever calls it).
class FakeScanHttpClient(
    var getHandler: ((URI) -> HttpResult)? = null,
    var downloadHandler: ((URI) -> DownloadResult)? = null,
    var deleteHandler: ((URI) -> HttpResult)? = null,
) : ScanHttpClient {
    override fun get(url: URI): HttpResult = getHandler?.invoke(url) ?: error("FakeScanHttpClient.get: no getHandler set")

    override fun download(url: URI): DownloadResult =
        downloadHandler?.invoke(url) ?: error("FakeScanHttpClient.download: no downloadHandler set")

    override fun delete(url: URI): HttpResult = deleteHandler?.invoke(url) ?: error("FakeScanHttpClient.delete: no deleteHandler set")
}
