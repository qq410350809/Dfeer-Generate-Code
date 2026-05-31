package com.dfeer.plugin.model

data class ColumnInfo(
    val name: String,
    val type: String,
    val comment: String,
    val isPrimaryKey: Boolean,
    val isAutoIncrement: Boolean,
    val nullable: Boolean
) {
    val camelName: String get() = name.toCamelCase()
}

fun String.toCamelCase(): String {
    return split("_").joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
        .replaceFirstChar { it.lowercase() }
}
