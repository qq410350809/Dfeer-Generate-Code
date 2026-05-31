package com.dfeer.plugin.generator

import com.dfeer.plugin.model.TableInfo

class DaoGenerator {

    fun generate(table: TableInfo, packageName: String, entityName: String = "${table.className}Do"): String {
        val className = table.className
        val sb = StringBuilder()

        sb.appendLine("package $packageName.dao;")
        sb.appendLine()
        sb.appendLine("import com.baomidou.mybatisplus.core.mapper.BaseMapper;")
        sb.appendLine("import $packageName.entity.$entityName;")
        sb.appendLine("import org.springframework.stereotype.Repository;")
        sb.appendLine()
        sb.appendLine("@Repository")
        sb.appendLine("public interface ${className}Dao extends BaseMapper<$entityName> {")
        sb.appendLine("}")
        return sb.toString()
    }
}
