package com.dfeer.plugin.generator

object TypeMapper {

    private val knownTypeDefs = listOf(
        "VARCHAR" to "String",
        "CHAR" to "String",
        "TEXT" to "String",
        "LONGTEXT" to "String",
        "MEDIUMTEXT" to "String",
        "TINYTEXT" to "String",
        "TINYINT(1)" to "Boolean",
        "BIT" to "Boolean",
        "TINYINT" to "Integer",
        "SMALLINT" to "Integer",
        "MEDIUMINT" to "Integer",
        "INT" to "Integer",
        "INTEGER" to "Integer",
        "BIGINT" to "Long",
        "DECIMAL" to "BigDecimal",
        "NUMERIC" to "BigDecimal",
        "FLOAT" to "BigDecimal",
        "DOUBLE" to "BigDecimal",
        "DATE" to "LocalDate",
        "DATETIME" to "LocalDateTime",
        "TIMESTAMP" to "LocalDateTime"
    )

    fun toJavaType(dbType: String, overrides: Map<String, String> = emptyMap()): String {
        val upper = dbType.uppercase().trim()
        if (overrides.isNotEmpty()) {
            val match = findOverrideMatch(upper, overrides)
            if (match != null) return match
        }
        return defaultToJavaType(upper)
    }

    private fun findOverrideMatch(upper: String, overrides: Map<String, String>): String? {
        val exactMatch = overrides[upper]
        if (exactMatch != null) return exactMatch
        val sorted = overrides.keys.sortedByDescending { it.length }
        for (prefix in sorted) {
            if (upper.startsWith(prefix) || upper.replace(" ", "") == prefix) {
                return overrides[prefix]
            }
        }
        return null
    }

    private fun defaultToJavaType(upper: String): String {
        return when {
            upper.startsWith("VARCHAR") || upper.startsWith("CHAR") || upper.startsWith("TEXT") || upper.startsWith("LONGTEXT") || upper.startsWith(
                "MEDIUMTEXT"
            ) || upper.startsWith("TINYTEXT") -> "String"

            upper == "TINYINT(1)" || upper == "TINYINT (1)" || upper == "BIT" || (upper.startsWith("TINYINT") && upper.contains(
                "(1)"
            )) -> "Boolean"

            upper.startsWith("TINYINT") -> "Integer"
            upper.startsWith("SMALLINT") || upper.startsWith("MEDIUMINT") || upper.startsWith("INT") || upper == "INTEGER" -> "Integer"

            upper.startsWith("BIGINT") -> "Long"
            upper.startsWith("DECIMAL") || upper.startsWith("NUMERIC") || upper.startsWith("FLOAT") || upper.startsWith(
                "DOUBLE"
            ) -> "BigDecimal"

            upper == "DATE" -> "LocalDate"
            upper.startsWith("DATETIME") || upper.startsWith("TIMESTAMP") -> "LocalDateTime"
            else -> "String"
        }
    }

    fun getDefaultMappings(): List<Pair<String, String>> {
        return knownTypeDefs
    }

    fun toImportTypes(dbType: String, overrides: Map<String, String> = emptyMap()): String? {
        val javaType = toJavaType(dbType, overrides = overrides)
        return if (javaType == "BigDecimal") "java.math.BigDecimal"
        else if (javaType == "LocalDate") "java.time.LocalDate"
        else if (javaType == "LocalDateTime") "java.time.LocalDateTime"
        else null
    }

}
