package com.dfeer.plugin.generator

import com.dfeer.plugin.model.TableInfo

class ServiceImplGenerator {

    fun generate(table: TableInfo, packageName: String): String {
        val className = table.className
        val entityName = "${className}Do"
        val sb = StringBuilder()

        sb.appendLine("package $packageName.service.impl;")
        sb.appendLine()
        sb.appendLine("import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;")
        sb.appendLine("import $packageName.dao.${className}Dao;")
        sb.appendLine("import $packageName.entity.$entityName;")
        sb.appendLine("import $packageName.service.${className}Service;")
        sb.appendLine("import org.springframework.stereotype.Service;")
        sb.appendLine()
        sb.appendLine("@Service")
        sb.appendLine("public class ${className}ServiceImpl extends ServiceImpl<${className}Dao, $entityName> implements ${className}Service {")
        sb.appendLine("}")
        return sb.toString()
    }
}
