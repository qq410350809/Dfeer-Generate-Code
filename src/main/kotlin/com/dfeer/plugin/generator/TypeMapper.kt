package com.dfeer.plugin.generator

object TypeMapper {

    private val knownTypePrefixes = listOf(
        "TINYINT", "SMALLINT", "MEDIUMINT", "INTEGER", "INT", "BIGINT",
        "VARCHAR", "CHAR", "TEXT", "LONGTEXT", "MEDIUMTEXT", "TINYTEXT",
        "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE",
        "DATETIME", "TIMESTAMP", "DATE", "BIT"
    )

    fun toJavaType(dbType: String, columnName: String = "", overrides: Map<String, String> = emptyMap()): String {
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
            if (upper.startsWith(prefix) || prefix.startsWith(upper)) {
                return overrides[prefix]
            }
        }
        return null
    }

    private fun defaultToJavaType(upper: String): String {
        return when {
            upper.startsWith("VARCHAR") || upper.startsWith("CHAR") ||
            upper.startsWith("TEXT") || upper.startsWith("LONGTEXT") ||
            upper.startsWith("MEDIUMTEXT") || upper.startsWith("TINYTEXT") -> "String"
            upper == "TINYINT(1)" || upper == "TINYINT (1)" ||
            upper == "BIT" || (upper.startsWith("TINYINT") && upper.contains("(1)")) -> "Boolean"
            upper.startsWith("TINYINT") -> "Integer"
            upper.startsWith("SMALLINT") || upper.startsWith("MEDIUMINT") ||
            upper.startsWith("INT") || upper == "INTEGER" -> "Integer"
            upper.startsWith("BIGINT") -> "Long"
            upper.startsWith("DECIMAL") || upper.startsWith("NUMERIC") ||
            upper.startsWith("FLOAT") || upper.startsWith("DOUBLE") -> "BigDecimal"
            upper == "DATE" -> "LocalDate"
            upper.startsWith("DATETIME") || upper.startsWith("TIMESTAMP") -> "LocalDateTime"
            else -> "String"
        }
    }

    fun getDefaultMappings(): List<Pair<String, String>> {
        val seen = mutableSetOf<String>()
        return knownTypePrefixes.mapNotNull { prefix ->
            val jt = defaultToJavaType(prefix)
            if (seen.add(jt)) prefix to jt else null
        }
    }

    fun toImportTypes(dbType: String, overrides: Map<String, String> = emptyMap()): String? {
        val javaType = toJavaType(dbType, overrides = overrides)
        return if (javaType == "BigDecimal") "java.math.BigDecimal"
        else if (javaType == "LocalDate") "java.time.LocalDate"
        else if (javaType == "LocalDateTime") "java.time.LocalDateTime"
        else null
    }

    fun toJdbcType(dbType: String): String {
        val upper = dbType.uppercase().trim()
        return when {
            upper.startsWith("VARCHAR") || upper.startsWith("CHAR") ||
            upper.startsWith("TEXT") || upper.startsWith("LONGTEXT") ||
            upper.startsWith("MEDIUMTEXT") || upper.startsWith("TINYTEXT") -> "VARCHAR"
            upper.startsWith("TINYINT") -> "TINYINT"
            upper.startsWith("SMALLINT") -> "SMALLINT"
            upper.startsWith("MEDIUMINT") -> "INTEGER"
            upper.startsWith("INT") || upper == "INTEGER" -> "INTEGER"
            upper.startsWith("BIGINT") -> "BIGINT"
            upper.startsWith("DECIMAL") || upper.startsWith("NUMERIC") -> "DECIMAL"
            upper.startsWith("FLOAT") -> "REAL"
            upper.startsWith("DOUBLE") -> "DOUBLE"
            upper == "DATE" -> "DATE"
            upper.startsWith("DATETIME") || upper.startsWith("TIMESTAMP") -> "TIMESTAMP"
            upper == "BIT" -> "BIT"
            else -> "VARCHAR"
        }
    }
}
