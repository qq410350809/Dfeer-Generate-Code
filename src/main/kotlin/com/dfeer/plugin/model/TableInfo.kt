package com.dfeer.plugin.model

data class TableInfo(
    val name: String, val comment: String, val columns: List<ColumnInfo>
) {
    val className: String get() = name.toCamelCase().replaceFirstChar { it.uppercase() }
}
