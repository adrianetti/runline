package com.runline.app.updates

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import java.io.File

 data class AppStorage(val total: Long?, val terrain: Long) {
    companion object {
        /** Worker only. Android dataBytes already includes cacheBytes. */
        fun read(context: Context): AppStorage {
            val total = runCatching {
                context.getSystemService(StorageStatsManager::class.java).queryStatsForPackage(
                    context.applicationInfo.storageUuid, context.packageName, Process.myUserHandle()).let { it.appBytes + it.dataBytes }
            }.getOrNull()
            val terrain = File(context.filesDir, "terrain-maps").listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
            return AppStorage(total, terrain)
        }
    }
}
