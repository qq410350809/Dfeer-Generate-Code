package com.dfeer.plugin.generator

import com.dfeer.plugin.model.ColumnInfo
import com.dfeer.plugin.model.TableInfo

class EntityGenerator(
    private val useLombok: Boolean,
    private val useTableLogic: Boolean,
    private val useSwagger: Boolean = false
) {
    private val autoFillFields = setOf("create_time", "cra_time", "update_time", "up_time")
    private val logicDeleteFields = setOf("is_del", "deleted", "is_deleted")

    fun generate(table: TableInfo, packageName: String, classNameOverride: String? = null): String {
        val sb = StringBuilder()
        val className = classNameOverride ?: table.className

        sb.appendLine("package $packageName.entity;")
        sb.appendLine()

        val imports = mutableListOf<String>()
        imports.add("com.baomidou.mybatisplus.annotation.*")
        if (useLombok) {
            imports.add("lombok.Data")
        }
        imports.add("java.io.Serial")
        imports.add("java.io.Serializable")

        table.columns.mapNotNull { TypeMapper.toImportTypes(it.type) }.distinct()
            .forEach { imports.add(it) }

        if (useTableLogic && table.columns.any { it.name.lowercase() in logicDeleteFields }) {
            imports.add("com.baomidou.mybatisplus.annotation.TableLogic")
        }

        if (useSwagger) {
            imports.add("io.swagger.v3.oas.annotations.media.Schema")
        }

        imports.forEach { sb.appendLine("import $it;") }
        sb.appendLine()

        if (useLombok) {
            sb.appendLine("@Data")
        }
        sb.appendLine("@TableName(\"${table.name}\")")
        sb.appendLine("public class $className implements Serializable {")
        sb.appendLine()
        sb.appendLine("    @Serial")
        sb.appendLine("    private static final long serialVersionUID = 1L;")
        sb.appendLine()

        table.columns.forEach { col ->
            generateField(sb, col)
        }

        if (!useLombok) {
            table.columns.forEach { col ->
                generateGetterSetter(sb, col)
            }
        }

        sb.appendLine("}")
        return sb.toString()
    }

    private fun generateField(sb: StringBuilder, col: ColumnInfo) {
        val javaType = TypeMapper.toJavaType(col.type, col.name)
        val fieldName = col.camelName
        if (col.comment.isNotBlank()) {
            sb.appendLine("    /**")
            sb.appendLine("     * ${col.comment}")
            sb.appendLine("     */")
        }
        if (useSwagger) {
            sb.appendLine("    @Schema(description = \"${col.comment}\")")
        }

        if (col.isPrimaryKey) {
            sb.appendLine("    @TableId(value = \"${col.name}\", type = IdType.AUTO)")
        } else {
            val nameLower = col.name.lowercase()
            if (useTableLogic && nameLower in logicDeleteFields) {
                sb.appendLine("    @TableLogic")
            }
            if (nameLower in autoFillFields) {
                sb.appendLine("    @TableField(value = \"${col.name}\", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)")
            }
        }

        sb.appendLine("    private $javaType $fieldName;")
        sb.appendLine()
    }

    private fun generateGetterSetter(sb: StringBuilder, col: ColumnInfo) {
        val javaType = TypeMapper.toJavaType(col.type, col.name)
        val fieldName = col.camelName
        val capName = fieldName.replaceFirstChar { it.uppercase() }

        if (javaType == "Boolean" || javaType == "boolean") {
            sb.appendLine("    public ${javaType} is${capName}() {")
            sb.appendLine("        return $fieldName;")
            sb.appendLine("    }")
        } else {
            sb.appendLine("    public $javaType get$capName() {")
            sb.appendLine("        return $fieldName;")
            sb.appendLine("    }")
        }
        sb.appendLine()
        sb.appendLine("    public void set$capName($javaType $fieldName) {")
        sb.appendLine("        this.$fieldName = $fieldName;")
        sb.appendLine("    }")
        sb.appendLine()
    }
}
