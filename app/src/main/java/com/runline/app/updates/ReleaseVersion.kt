package com.runline.app.updates

import org.json.JSONObject

data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion) = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    companion object {
        fun parse(tag: String): ReleaseVersion? {
            val parts = Regex("^v?(0|[1-9][0-9]{0,7})\\.(0|[1-9][0-9]{0,7})(?:\\.(0|[1-9][0-9]{0,7}))?$").matchEntire(tag)?.groupValues ?: return null
            return ReleaseVersion(parts[1].toInt(), parts[2].toInt(), parts[3].ifBlank { "0" }.toInt())
        }
        fun publishedApk(json: JSONObject): String? {
            if (json.optBoolean("draft", true) || json.optBoolean("prerelease", true)) return null
            val tag = json.optString("tag_name"); if (parse(tag) == null) return null
            val name = "Runline-${tag.removePrefix("v")}.apk"; val assets = json.optJSONArray("assets") ?: return null
            return tag.takeIf { (0 until assets.length()).any { i -> assets.optJSONObject(i)?.let {
                it.optString("name") == name && it.optString("state") == "uploaded" && it.optLong("size") > 0 &&
                    it.optString("browser_download_url") == "https://github.com/adrianetti/runline/releases/download/$tag/$name"
            } == true } }
        }
    }
}
