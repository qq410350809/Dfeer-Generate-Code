package com.dfeer.plugin.generator

object TypeMapper {

    fun toJavaType(dbType: String, columnName: String = ""): String {
        val upper = dbType.uppercase().trim()
        val baseType = when {
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
        return baseType
    }

    fun toImportTypes(dbType: String): String? {
        val upper = dbType.uppercase().trim()
        return when {
            upper.startsWith("DECIMAL") || upper.startsWith("NUMERIC") ||
            upper.startsWith("FLOAT") || upper.startsWith("DOUBLE") ->
                "java.math.BigDecimal"
            upper == "DATE" -> "java.time.LocalDate"
            upper.startsWith("DATETIME") || upper.startsWith("TIMESTAMP") ->
                "java.time.LocalDateTime"
            else -> null
        }
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
