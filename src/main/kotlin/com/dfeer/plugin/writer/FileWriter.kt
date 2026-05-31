package com.dfeer.plugin.writer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class FileWriter(private val project: Project) {

    data class WriteResult(
        val filePath: String,
        val success: Boolean,
        val error: String? = null
    )

    fun writeJavaFile(packageDir: VirtualFile, className: String, content: String, overwrite: Boolean = true): WriteResult {
        return writeFile(packageDir, "$className.java", content, overwrite)
    }

    fun writeXmlFile(resourcesDir: VirtualFile, fileName: String, content: String, overwrite: Boolean = true): WriteResult {
        return writeFile(resourcesDir, "$fileName.xml", content, overwrite)
    }

    private fun writeFile(dir: VirtualFile, fileName: String, content: String, overwrite: Boolean): WriteResult {
        val filePath = "${dir.path}/$fileName"
        return try {
            val existing = dir.findChild(fileName)
            if (existing != null) {
                if (!overwrite) return WriteResult(filePath, true, "已存在，跳过")
                existing.delete(this)
            }
            val newFile = dir.createChildData(this, fileName)
            newFile.setBinaryContent(content.toByteArray())
            WriteResult(filePath, true)
        } catch (e: Exception) {
            WriteResult(filePath, false, e.message)
        }
    }

    fun findOrCreateDirByPath(absolutePath: String): VirtualFile? {
        val fs = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
        var dir = fs.findFileByPath(absolutePath)
        if (dir != null) return dir
        val parent = findOrCreateDirByPath(absolutePath.substringBeforeLast("/"))
            ?: return null
        return parent.createChildDirectory(this, absolutePath.substringAfterLast("/"))
    }

    fun findOrCreatePackageDir(baseDir: VirtualFile, packageName: String, subPackage: String): VirtualFile? {
        val fullPath = "$packageName/$subPackage".replace('.', '/')
        var current: VirtualFile? = baseDir
        for (segment in fullPath.split("/")) {
            if (segment.isNotBlank()) {
                current = current?.findChild(segment) ?: current?.createChildDirectory(this, segment)
            }
        }
        return current
    }

    fun findOrCreateDir(baseDir: VirtualFile, subDir: String): VirtualFile? {
        var current: VirtualFile = baseDir
        for (segment in subDir.split("/")) {
            if (segment.isNotBlank()) {
                current = current.findChild(segment) ?: current.createChildDirectory(this, segment)
            }
        }
        return current
    }
}
