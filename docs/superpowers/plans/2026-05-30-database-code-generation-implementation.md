# Database Code Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从 IntelliJ IDEA Database 工具窗口读取数据源表结构，生成 MyBatis-Plus 风格的 Entity/Dao/Service/Mapper XML 代码。

**Architecture:** DatabaseReader 读取 DB Schema 转为内部 model → CodeGenerator 根据 model 拼接代码字符串 → FileWriter 写入项目指定目录。UI 使用 IntelliJ DialogWrapper 实现向导流程。

**Tech Stack:** IntelliJ Platform SDK, com.intellij.database PSI API, MyBatis-Plus 注解, Lombok 注解

**File Structure:**

```
src/main/kotlin/com/dfeer/plugin/
├── model/
│   ├── TableInfo.kt          — 表信息 model
│   └── ColumnInfo.kt         — 列信息 model
├── database/
│   └── DatabaseReader.kt     — 读取 Database 工具窗口的数据源/表/列
├── generator/
│   ├── TypeMapper.kt         — MySQL → Java 类型映射
│   ├── EntityGenerator.kt    — 生成 Entity 代码
│   ├── DaoGenerator.kt       — 生成 Dao 代码
│   ├── ServiceGenerator.kt   — 生成 Service 代码
│   └── MapperXmlGenerator.kt — 生成 Mapper XML
├── writer/
│   └── FileWriter.kt         — 将代码写入项目文件
├── ui/
│   └── GenerationDialog.kt   — 向导对话框 UI
├── MyMessageBundle.kt        — 已有，新增 key
├── MyToolWindowFactory.kt    — 已有，修改为打开向导
```

---

### Task 1: 创建 Model 类 (TableInfo, ColumnInfo)

**Files:**
- Create: `src/main/kotlin/com/dfeer/plugin/model/TableInfo.kt`
- Create: `src/main/kotlin/com/dfeer/plugin/model/ColumnInfo.kt`

- [ ] **Step 1: 创建 ColumnInfo.kt**

```kotlin
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
```

- [ ] **Step 2: 创建 TableInfo.kt**

```kotlin
package com.dfeer.plugin.model

data class TableInfo(
    val name: String,
    val comment: String,
    val columns: List<ColumnInfo>
) {
    val className: String get() = name.toCamelCase().replaceFirstChar { it.uppercase() }
    val primaryKey: ColumnInfo? get() = columns.find { it.isPrimaryKey }
}
```

- [ ] **Step 3: 提交**

---

### Task 2: 实现 DatabaseReader

**Files:**
- Create: `src/main/kotlin/com/dfeer/plugin/database/DatabaseReader.kt`

- [ ] **Step 1: 创建 DatabaseReader.kt**

```kotlin
package com.dfeer.plugin.database

import com.dfeer.plugin.model.ColumnInfo
import com.dfeer.plugin.model.TableInfo
import com.intellij.database.psi.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

class DatabaseReader(private val project: Project) {

    fun listDataSources(): List<DbDataSource> {
        return DatabasePsiManager.getInstance(project).dataSources.toList()
    }

    fun listTables(dataSource: DbDataSource): List<DbTable> {
        return dataSource.schemas.flatMap { schema ->
            if (schema is DbPsiFacade) {
                schema.tables.toList()
            } else emptyList()
        }
    }

    fun readTableInfo(table: DbTable): TableInfo {
        val columns = table.columns.map { col ->
            val (typeName, isAutoInc) = parseColumnType(col)
            ColumnInfo(
                name = col.name,
                type = typeName,
                comment = col.comment ?: "",
                isPrimaryKey = col.isPrimaryKey,
                isAutoIncrement = isAutoInc,
                nullable = col.isNullable
            )
        }
        return TableInfo(
            name = table.name,
            comment = table.comment ?: "",
            columns = columns
        )
    }

    fun readTableInfo(dataSource: DbDataSource, tableName: String): TableInfo? {
        val table = listTables(dataSource).find { it.name.equals(tableName, ignoreCase = true) }
        return table?.let { readTableInfo(it) }
    }

    private fun parseColumnType(col: DbColumn): Pair<String, Boolean> {
        var type = col.dataType?.typeName?.uppercase() ?: "VARCHAR"
        val spec = col.defaultValueSpec
        val isAutoInc = spec?.contains("auto_increment", ignoreCase = true) == true ||
                col.isAutoIncrement
        return type to isAutoInc
    }
}
```

- [ ] **Step 2: 提交**

---

### Task 3: 实现 TypeMapper

**Files:**
- Create: `src/main/kotlin/com/dfeer/plugin/generator/TypeMapper.kt`

- [ ] **Step 1: 创建 TypeMapper.kt**

```kotlin
package com.dfeer.plugin.generator

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

object TypeMapper {

    fun toJavaType(dbType: String, columnName: String = ""): String {
        val upper = dbType.uppercase().trim()

        val baseType = when {
            upper.startsWith("VARCHAR") || upper.startsWith("CHAR") ||
            upper.startsWith("TEXT") || upper.startsWith("LONGTEXT") ||
            upper.startsWith("MEDIUMTEXT") || upper.startsWith("TINYTEXT") -> "String"
            upper == "TINYINT(1)" || upper == "TINYINT (1)" ||
            upper == "BIT" -> "Boolean"
            upper.startsWith("TINYINT") -> "int"
            upper.startsWith("SMALLINT") || upper.startsWith("MEDIUMINT") ||
            upper.startsWith("INT") || upper == "INTEGER" -> "int"
            upper.startsWith("BIGINT") -> "long"
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
```

- [ ] **Step 2: 提交**

---

### Task 4: 实现 EntityGenerator

**Files:**
- Create: `src/main/kotlin/com/dfeer/plugin/generator/EntityGenerator.kt`

- [ ] **Step 1: 创建 EntityGenerator.kt**

```kotlin
package com.dfeer.plugin.generator

import com.dfeer.plugin.model.ColumnInfo
import com.dfeer.plugin.model.TableInfo

class EntityGenerator(
    private val useLombok: Boolean,
    private val useTableLogic: Boolean
) {
    private val autoFillFields = setOf("create_time", "cra_time", "update_time", "up_time")
    private val logicDeleteFields = setOf("is_del", "deleted", "is_deleted")

    fun generate(table: TableInfo, packageName: String): String {
        val sb = StringBuilder()
        val className = table.className

        sb.appendLine("package $packageName.entity;")
        sb.appendLine()

        val imports = mutableListOf<String>()
        imports.add("com.baomidou.mybatisplus.annotation.*")
        if (useLombok) {
            imports.add("lombok.Data")
            imports.add("lombok.NoArgsConstructor")
            imports.add("lombok.AllArgsConstructor")
            imports.add("lombok.experimental.Accessors")
        }

        val typeImports = table.columns.mapNotNull { TypeMapper.toImportTypes(it.type) }.distinct()
        imports.addAll(typeImports)

        if (useTableLogic && table.columns.any { it.name.lowercase() in logicDeleteFields }) {
            imports.add("com.baomidou.mybatisplus.annotation.TableLogic")
        }

        imports.forEach { sb.appendLine("import $it;") }
        sb.appendLine()

        if (useLombok) {
            sb.appendLine("@Data")
            sb.appendLine("@NoArgsConstructor")
            sb.appendLine("@AllArgsConstructor")
            sb.appendLine("@Accessors(chain = true)")
        }
        sb.appendLine("@TableName(\"${table.name}\")")
        sb.appendLine("public class $className {")
        sb.appendLine()

        table.columns.forEach { col ->
            generateField(sb, col, table)
        }

        if (!useLombok) {
            table.columns.forEach { col ->
                generateGetterSetter(sb, col, table.className)
            }
        }

        sb.appendLine("}")
        return sb.toString()
    }

    private fun generateField(sb: StringBuilder, col: ColumnInfo, table: TableInfo) {
        val javaType = TypeMapper.toJavaType(col.type, col.name)
        val fieldName = col.camelName
        if (col.comment.isNotBlank()) {
            sb.appendLine("    /** ${col.comment} */")
        }

        if (col.isPrimaryKey) {
            sb.appendLine("    @TableId(value = \"${col.name}\", type = IdType.AUTO)")
        } else {
            val nameLower = col.name.lowercase()
            val strategyAttrs = if (nameLower in autoFillFields) {
                "insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER"
            } else null
            if (useTableLogic && nameLower in logicDeleteFields) {
                sb.appendLine("    @TableLogic")
            }
            if (strategyAttrs != null) {
                sb.appendLine("    @TableField(value = \"${col.name}\", $strategyAttrs)")
            } else {
                sb.appendLine("    @TableField(\"${col.name}\")")
            }
        }

        sb.appendLine("    private $javaType $fieldName;")
        sb.appendLine()
    }

    private fun generateGetterSetter(sb: StringBuilder, col: ColumnInfo, className: String) {
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
```

- [ ] **Step 2: 提交**

---

### Task 5: 实现 DaoGenerator

**Files:**
- Create: `src/main/kotlin/com/dfeer/plugin/generator/DaoGenerator.kt`

- [ ] **Step 1: 创建 DaoGenerator.kt**

```kotlin
package com.dfeer.plugin.generator

import com.dfeer.plugin.model.TableInfo

class DaoGenerator {

    fun generate(table: TableInfo, packageName: String): String {
        val className = table.className
        val sb = StringBuilder()

        sb.appendLine("package $packageName.dao;")
        sb.appendLine()
        sb.appendLine("import com.baomidou.mybatisplus.core.mapper.BaseMapper;")
        sb.appendLine("import $packageName.entity.$className;")
        sb.appendLine("import org.apache.ibatis.annotations.Mapper;")
        sb.appendLine()
        sb.appendLine("@Mapper")
        sb.appendLine("public interface ${className}Dao extends BaseMapper<$className> {")
        sb.appendLine("}")
        return sb.toString()
    }
}
```

- [ ] **Step 2: 提交**

---

### Task 6: 实现 ServiceGenerator

**Files:**
- Create: `src/main/kotlin/com/dfeer/plugin/generator/ServiceGenerator.kt`

- [ ] **Step 1: 创建 ServiceGenerator.kt**

```kotlin
package com.dfeer.plugin.generator

import com.dfeer.plugin.model.TableInfo

class ServiceGenerator {

    fun generate(table: TableInfo, packageName: String): String {
        val className = table.className
        val sb = StringBuilder()

        sb.appendLine("package $packageName.service;")
        sb.appendLine()
        sb.appendLine("import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;")
        sb.appendLine("import $packageName.dao.${className}Dao;")
        sb.appendLine("import $packageName.entity.$className;")
        sb.appendLine("import org.springframework.stereotype.Service;")
        sb.appendLine()
        sb.appendLine("@Service")
        sb.appendLine("public class ${className}Service extends ServiceImpl<${className}Dao, $className> {")
        sb.appendLine("}")
        return sb.toString()
    }
}
```

- [ ] **Step 2: 提交**

---

### Task 7: 实现 MapperXmlGenerator

**Files:**
- Create: `src/main/kotlin/com/dfeer/plugin/generator/MapperXmlGenerator.kt`

- [ ] **Step 1: 创建 MapperXmlGenerator.kt**

```kotlin
package com.dfeer.plugin.generator

import com.dfeer.plugin.model.TableInfo

class MapperXmlGenerator {

    fun generate(table: TableInfo, packageName: String): String {
        val className = table.className
        val sb = StringBuilder()

        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\"")
        sb.appendLine("        \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">")
        sb.appendLine("<mapper namespace=\"${packageName}.dao.${className}Dao\">")
        sb.appendLine()

        // ResultMap
        sb.appendLine("    <resultMap id=\"BaseResultMap\" type=\"${packageName}.entity.$className\">")
        table.columns.forEach { col ->
            val jdbcType = TypeMapper.toJdbcType(col.type)
            if (col.isPrimaryKey) {
                sb.appendLine("        <id column=\"${col.name}\" property=\"${col.camelName}\" jdbcType=\"$jdbcType\"/>")
            } else {
                sb.appendLine("        <result column=\"${col.name}\" property=\"${col.camelName}\" jdbcType=\"$jdbcType\"/>")
            }
        }
        sb.appendLine("    </resultMap>")
        sb.appendLine()

        // Base_Column_List
        sb.appendLine("    <sql id=\"Base_Column_List\">")
        sb.appendLine("        ${table.columns.joinToString(", ") { it.name }}")
        sb.appendLine("    </sql>")

        sb.appendLine()
        sb.appendLine("</mapper>")
        return sb.toString()
    }
}
```

- [ ] **Step 2: 提交**

---

### Task 8: 实现 FileWriter

**Files:**
- Create: `src/main/kotlin/com/dfeer/plugin/writer/FileWriter.kt`

- [ ] **Step 1: 创建 FileWriter.kt**

```kotlin
package com.dfeer.plugin.writer

import com.dfeer.plugin.model.TableInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.xml.XmlFile
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

class FileWriter(private val project: Project) {

    data class WriteResult(
        val filePath: String,
        val success: Boolean,
        val error: String? = null
    )

    fun writeJavaFile(packageDir: VirtualFile, packageName: String, className: String, content: String): WriteResult {
        return writeFile(packageDir, "$className.java", content)
    }

    fun writeXmlFile(resourcesDir: VirtualFile, fileName: String, content: String): WriteResult {
        return writeFile(resourcesDir, "$fileName.xml", content)
    }

    private fun writeFile(dir: VirtualFile, fileName: String, content: String): WriteResult {
        val filePath = "${dir.path}/$fileName"
        return try {
            val psiFile = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, JavaFileType.INSTANCE, content)
            val existing = dir.findChild(fileName)
            if (existing != null) {
                existing.delete(this)
            }
            val newFile = dir.createChildData(this, fileName)
            newFile.setBinaryContent(content.toByteArray())
            WriteResult(filePath, true)
        } catch (e: Exception) {
            WriteResult(filePath, false, e.message)
        }
    }

    fun findOrCreatePackageDir(baseDir: VirtualFile, packageName: String, subPackage: String): VirtualFile? {
        val pkgPath = packageName.replace('.', '/')
        val fullPath = "$pkgPath/$subPackage"
        var current: VirtualFile? = baseDir
        fullPath.split("/").forEach { segment ->
            current = current?.findChild(segment) ?: current?.createChildDirectory(this, segment)
        }
        return current
    }

    fun findOrCreateDir(baseDir: VirtualFile, subDir: String): VirtualFile? {
        var current = baseDir
        subDir.split("/").forEach { segment ->
            if (segment.isNotBlank()) {
                current = current.findChild(segment) ?: current.createChildDirectory(this, segment)
            }
        }
        return current
    }
}
```

- [ ] **Step 2: 提交**

---

### Task 9: 实现 GenerationDialog UI

**Files:**
- Create: `src/main/kotlin/com/dfeer/plugin/ui/GenerationDialog.kt`

- [ ] **Step 1: 创建 GenerationDialog.kt**

```kotlin
package com.dfeer.plugin.ui

import com.dfeer.plugin.database.DatabaseReader
import com.dfeer.plugin.generator.*
import com.dfeer.plugin.model.TableInfo
import com.dfeer.plugin.writer.FileWriter
import com.intellij.database.psi.DbDataSource
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.table.DefaultTableModel

class GenerationDialog(private val project: Project) : DialogWrapper(true) {

    private val dbReader = DatabaseReader(project)
    private val dataSources: List<DbDataSource> = dbReader.listDataSources()
    private var selectedTables: List<TableInfo> = emptyList()

    // UI components
    private val dataSourceCombo = JComboBox<String>()
    private val tableList = JBTable()
    private val genEntityCb = JCheckBox("Entity", true)
    private val genDaoCb = JCheckBox("Dao", true)
    private val genServiceCb = JCheckBox("Service", true)
    private val genMapperCb = JCheckBox("Mapper XML", true)
    private val useLombokCb = JCheckBox("使用 Lombok", true)
    private val useTableLogicCb = JCheckBox("启用逻辑删除 (@TableLogic)", false)
    private val packageField = JBTextField("com.example.demo")
    private val javaSourceField = JBTextField()
    private val resourceField = JBTextField()

    init {
        title = "代码生成向导"
        init()
        loadDataSources()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())

        // Step 1: 选择数据源和表
        // 这里简化实现，实际可以用 Wizard 或 TabbedPane
        dataSourceCombo.addActionListener { loadTables() }

        val tableModel = DefaultTableModel(arrayOf("表名", "注释"), 0)
        tableList.model = tableModel

        // 组装布局（完整布局使用 GridBagLayout）
        panel.add(JLabel("数据源:"))
        panel.add(dataSourceCombo)
        panel.add(JBScrollPane(tableList))
        panel.add(genEntityCb)
        panel.add(genDaoCb)
        panel.add(genServiceCb)
        panel.add(genMapperCb)
        panel.add(useLombokCb)
        panel.add(useTableLogicCb)
        panel.add(JLabel("基础包名:"))
        panel.add(packageField)
        panel.add(JLabel("Java源目录:"))
        panel.add(javaSourceField)
        panel.add(JLabel("Resources目录:"))
        panel.add(resourceField)

        return panel
    }

    private fun loadDataSources() {
        dataSources.forEach { dataSourceCombo.addItem(it.name) }
        if (dataSources.isNotEmpty()) loadTables()
    }

    private fun loadTables() {
        val idx = dataSourceCombo.selectedIndex
        if (idx < 0) return
        val ds = dataSources[idx]
        val tables = dbReader.listTables(ds)
        val tableModel = tableList.model as DefaultTableModel
        tableModel.setRowCount(0)
        tables.forEach { table ->
            tableModel.addRow(arrayOf(table.name, table.comment ?: ""))
        }
    }

    override fun doOKAction() {
        val idx = dataSourceCombo.selectedIndex
        if (idx < 0) return
        val ds = dataSources[idx]

        val selectedRows = tableList.selectedRows
        if (selectedRows.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "请选择至少一个表")
            return
        }

        val allTables = dbReader.listTables(ds)
        selectedTables = selectedRows.map { row ->
            val tableName = tableList.model.getValueAt(row, 0) as String
            dbReader.readTableInfo(ds, tableName)!!
        }

        // 开始生成
        generateCode()
        super.doOKAction()
    }

    private fun generateCode() {
        val pkg = packageField.text
        val javaDir = findSourceDir(javaSourceField.text) ?: return
        val resourceDir = findSourceDir(resourceField.text) ?: return
        val writer = FileWriter(project)

        selectedTables.forEach { table ->
            val entityDir = writer.findOrCreatePackageDir(javaDir, pkg, "entity") ?: return@forEach
            val daoDir = writer.findOrCreatePackageDir(javaDir, pkg, "dao") ?: return@forEach
            val serviceDir = writer.findOrCreatePackageDir(javaDir, pkg, "service") ?: return@forEach
            val mapperDir = writer.findOrCreateDir(resourceDir, "mapper") ?: return@forEach

            if (genEntityCb.isSelected) {
                val gen = EntityGenerator(useLombokCb.isSelected, useTableLogicCb.isSelected)
                val code = gen.generate(table, pkg)
                writer.writeJavaFile(entityDir, pkg, table.className, code)
            }
            if (genDaoCb.isSelected) {
                val gen = DaoGenerator()
                val code = gen.generate(table, pkg)
                writer.writeJavaFile(daoDir, pkg, "${table.className}Dao", code)
            }
            if (genServiceCb.isSelected) {
                val gen = ServiceGenerator()
                val code = gen.generate(table, pkg)
                writer.writeJavaFile(serviceDir, pkg, "${table.className}Service", code)
            }
            if (genMapperCb.isSelected) {
                val gen = MapperXmlGenerator()
                val code = gen.generate(table, pkg)
                writer.writeXmlFile(mapperDir, "${table.className}Mapper", code)
            }
        }

        JOptionPane.showMessageDialog(panel, "代码生成完成!")
    }

    private fun findSourceDir(path: String): VirtualFile? {
        return com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .findFileByPath(path)
    }
}
```

- [ ] **Step 2: 提交**

---

### Task 10: 更新 MyToolWindowFactory 和 plugin.xml

**Files:**
- Modify: `src/main/kotlin/com/dfeer/plugin/MyToolWindowFactory.kt`
- Modify: `src/main/kotlin/com/dfeer/plugin/MyToolWindowFactory.kt` — 添加打开向导按钮

- [ ] **Step 1: 替换 MyToolWindowFactory.kt 内容**

```kotlin
package com.dfeer.plugin

import com.dfeer.plugin.ui.GenerationDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import javax.swing.JButton

class MyToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JBPanel<JBPanel<*>>().apply {
            val label = JBLabel("Generate Code from Database")
            add(label)
            add(JButton("打开代码生成向导").apply {
                addActionListener {
                    GenerationDialog(project).show()
                }
            })
        }
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }
}
```

- [ ] **Step 2: 确认 plugin.xml 已有正确依赖**

确保 `build.gradle.kts` 和 `plugin.xml` 中已包含 `com.intellij.database` 和 `com.intellij.java` 依赖。

- [ ] **Step 3: 提交**

---

### Task 11: 更新资源文件

**Files:**
- Modify: `src/main/resources/messages/MyMessageBundle.properties`

- [ ] **Step 1: 添加新的国际化 key**

```properties
toolwindow.stripe.MyToolWindow=Generate Code
toolwindow.MyToolWindow.generate.button=打开代码生成向导
generation.dialog.title=代码生成向导
generation.datasource.label=数据源:
generation.table.label=选择表:
generation.entity.checkbox=Entity
generation.dao.checkbox=Dao
generation.service.checkbox=Service
generation.mapper.checkbox=Mapper XML
generation.lombok.checkbox=使用 Lombok
generation.tableLogic.checkbox=启用逻辑删除 (@TableLogic)
generation.package.label=基础包名:
generation.javaDir.label=Java源目录:
generation.resourceDir.label=Resources目录:
generation.success=代码生成完成!
generation.selectTable.warning=请选择至少一个表
```

- [ ] **Step 2: 提交**
