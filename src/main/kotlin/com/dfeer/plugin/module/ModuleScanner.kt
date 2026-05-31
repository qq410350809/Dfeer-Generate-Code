package com.dfeer.plugin.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import java.nio.file.Paths

data class ModuleInfo(
    val name: String,
    val sourceDir: String,
    val resourceDir: String,
    val basePackage: String
)

class ModuleScanner(private val project: Project) {

    fun getModules(): List<ModuleInfo> {
        return ModuleManager.getInstance(project).modules
            .mapNotNull { module -> scanModule(module) }
            .sortedBy { it.name }
    }

    private fun scanModule(module: Module): ModuleInfo? {
        val modulePath = module.moduleFilePath
        val moduleDir = modulePath.substring(0, modulePath.lastIndexOf("/"))

        val javaSrc = Paths.get(moduleDir, "src/main/java")
        val kotlinSrc = Paths.get(moduleDir, "src/main/kotlin")

        val pkg = EntityPackageFinder.findEntityPackage(javaSrc)
            ?: EntityPackageFinder.findEntityPackage(kotlinSrc)
            ?: return null

        return ModuleInfo(
            name = module.name,
            sourceDir = "$moduleDir/src/main/java",
            resourceDir = "$moduleDir/src/main/resources",
            basePackage = pkg
        )
    }
}
