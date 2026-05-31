package com.dfeer.plugin.generator

import com.dfeer.plugin.model.TableInfo

class MapperXmlGenerator {

    fun generate(table: TableInfo, packageName: String): String {
        val className = table.className
        val entityName = "${className}Do"
        val sb = StringBuilder()

        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\"")
        sb.appendLine("        \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">")
        sb.appendLine("<mapper namespace=\"${packageName}.dao.${className}Dao\">")
        sb.appendLine()
        sb.appendLine("</mapper>")
        return sb.toString()
    }
}
