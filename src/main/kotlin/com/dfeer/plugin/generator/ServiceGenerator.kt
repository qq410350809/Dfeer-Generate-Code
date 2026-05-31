package com.dfeer.plugin.generator

import com.dfeer.plugin.model.TableInfo

class ServiceGenerator {

    fun generate(table: TableInfo, packageName: String): String {
        val className = table.className
        val entityName = "${className}Do"
        val sb = StringBuilder()

        sb.appendLine("package $packageName.service;")
        sb.appendLine()
        sb.appendLine("import com.baomidou.mybatisplus.extension.service.IService;")
        sb.appendLine("import $packageName.entity.$entityName;")
        sb.appendLine()
        sb.appendLine("public interface ${className}Service extends IService<$entityName> {")
        sb.appendLine()
        sb.appendLine("}")
        return sb.toString()
    }
}
