package com.runline.app.updates

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    private fun release(tag: String = "v0.16.0") = JSONObject().put("tag_name", tag).put("draft", false).put("prerelease", false)
        .put("assets", JSONArray().put(JSONObject().put("name", "Runline-${tag.removePrefix("v")}.apk").put("state", "uploaded").put("size", 12345)
            .put("browser_download_url", "https://github.com/adrianetti/runline/releases/download/$tag/Runline-${tag.removePrefix("v")}.apk")))
    @Test fun versionsCompareNumericallyIncludingMajorAndPatch() {
        assertTrue(ReleaseVersion.parse("0.16.0")!! > ReleaseVersion.parse("0.9.9")!!)
        assertTrue(ReleaseVersion.parse("0.100.0")!! > ReleaseVersion.parse("0.16.9")!!)
        assertTrue(ReleaseVersion.parse("1.0.0")!! > ReleaseVersion.parse("0.99.99")!!)
        assertTrue(ReleaseVersion.parse("0.16.1")!! > ReleaseVersion.parse("0.16.0")!!)
        assertEquals(ReleaseVersion.parse("v0.16"), ReleaseVersion.parse("0.16.0"))
    }
    @Test fun malformedPreReleaseAndOversizedVersionsAreNotUpdates() {
        for (tag in listOf("", "1", "v0.16.0-beta", "0.16.0+build", "01.16.0", "0.-1.0", "999999999.1.1", " 0.16.0", "0.16.0/evil"))
            assertNull(tag, ReleaseVersion.parse(tag))
    }
    @Test fun onlyStablePublishedExpectedApkCanAdvertiseAnUpdate() {
        assertEquals("v0.16.0", ReleaseVersion.publishedApk(release()))
        for (flag in listOf("draft", "prerelease")) {
            assertNull(ReleaseVersion.publishedApk(release().put(flag, true)))
            assertNull(ReleaseVersion.publishedApk(release().apply { remove(flag) }))
        }
        assertNull(ReleaseVersion.publishedApk(release().put("assets", JSONArray())))
        assertNull(ReleaseVersion.publishedApk(release("v0.16.0-rc1")))
    }
    @Test fun MissingOrUnexpectedAssetCannotOpenAnArbitraryDownload() {
        for ((key, value) in listOf("name" to "source.zip", "state" to "new", "size" to 0, "browser_download_url" to "https://evil.invalid/run.apk")) {
            val r = release(); r.getJSONArray("assets").getJSONObject(0).put(key, value)
            assertNull(ReleaseVersion.publishedApk(r))
        }
    }
}
