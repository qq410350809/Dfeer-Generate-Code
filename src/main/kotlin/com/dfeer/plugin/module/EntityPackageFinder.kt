package com.dfeer.plugin.module

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

object EntityPackageFinder {

    fun findEntityPackage(startPath: Path): String? {
        if (!Files.isDirectory(startPath)) return null
        return try {
            Files.walk(startPath).use { stream: Stream<Path> ->
                stream
                    .filter { Files.isDirectory(it) }
                    .filter { it.fileName.toString().endsWith("entity") }
                    .filter { entityDir ->
                        Files.list(entityDir).use { files: Stream<Path> ->
                            files.anyMatch { f: Path ->
                                f.fileName.toString().endsWith(".java") || f.fileName.toString().endsWith(".kt")
                            }
                        }
                    }
                    .map { entityDir ->
                        startPath.relativize(entityDir.parent).toString().replace(File.separatorChar, '.')
                    }
                    .findFirst()
                    .orElse(null)
            }
        } catch (_: Exception) {
            null
        }
    }
}
