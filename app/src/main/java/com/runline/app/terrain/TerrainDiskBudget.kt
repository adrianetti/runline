package com.runline.app.terrain

import java.io.File

/** Only this private terrain folder is eligible for eviction; temporary files count too. */
object TerrainDiskBudget {
    const val BYTES = 64 * 1024 * 1024L
    fun used(folder: File) = folder.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
    fun reserve(folder: File, incomingBytes: Long, currentName: String, pinned: Set<String>,
                budget: Long = BYTES, usableBytes: Long = folder.usableSpace) {
        require(incomingBytes in 1..budget)
        // Refuse before eviction if the filesystem itself cannot support a download.
        require(usableBytes >= incomingBytes + 4 * 1024 * 1024L)
        val candidates = folder.listFiles().orEmpty().filter { it.isFile && it.name != currentName }
            .sortedWith(compareBy<File> { it.name in pinned }.thenBy { it.lastModified() })
        var bytes = used(folder)
        for (file in candidates) {
            if (bytes + incomingBytes <= budget) break
            val size = file.length(); if (file.delete()) bytes -= size
        }
        require(bytes + incomingBytes <= budget)
    }
}
