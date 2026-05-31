package com.dfeer.plugin.ui

import com.dfeer.plugin.generator.*
import com.dfeer.plugin.model.TableInfo
import com.dfeer.plugin.module.EntityPackageFinder
import com.dfeer.plugin.module.ModuleInfo
import com.dfeer.plugin.module.ModuleScanner
import com.dfeer.plugin.settings.GenerationSettings
import com.dfeer.plugin.writer.FileWriter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Paths
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class GenerationDialog(
    private val project: Project, private val allTables: List<TableInfo>
) : DialogWrapper(true) {

    private val settings = GenerationSettings(project)
    private val cardLayout = CardLayout()
    private val rootPanel = JPanel(cardLayout)

    // ── Step 1 : 表选择 ──
    private val checked = BooleanArray(allTables.size) { true }

    private val tableModel = object : AbstractTableModel() {
        override fun getRowCount() = allTables.size
        override fun getColumnCount() = 3
        override fun getColumnName(col: Int) = when (col) {
            0 -> ""
            1 -> "表名"
            else -> "注释"
        }

        override fun getColumnClass(col: Int) = if (col == 0) Boolean::class.java else String::class.java
        override fun setValueAt(value: Any?, row: Int, col: Int) {
            if (col == 0) checked[row] = value as Boolean
        }

        override fun getValueAt(row: Int, col: Int) = when (col) {
            0 -> checked[row]
            1 -> allTables[row].name
            else -> allTables[row].comment
        }

        override fun isCellEditable(row: Int, col: Int) = col == 0
        fun refreshAll() { fireTableDataChanged() }
    }

    private val tableList = JBTable(tableModel).apply {
        rowSelectionAllowed = false
        columnSelectionAllowed = false
        rowHeight = 28
        putClientProperty("JTable.autoStartsEdit", false)
    }

    private val headerCb = JCheckBox().apply {
        horizontalAlignment = SwingConstants.CENTER
        border = BorderFactory.createEmptyBorder()
    }

    private val genEntityCb = JCheckBox("Entity", settings.genEntity)
    private val genDaoCb = JCheckBox("Dao", settings.genDao)
    private val genServiceCb = JCheckBox("Service", settings.genService)
    private val genMapperCb = JCheckBox("Mapper XML", settings.genMapper)
    private val useLombokCb = JCheckBox("使用 Lombok", settings.useLombok)
    private val useTableLogicCb = JCheckBox("启用逻辑删除 (@TableLogic)", settings.useTableLogic)
    private val useSwaggerCb = JCheckBox("Swagger 注解", settings.useSwagger)
    private val moduleScanner = ModuleScanner(project)
    private val modules = moduleScanner.getModules().toMutableList().apply {
        if (isEmpty()) {
            val baseDir = project.basePath ?: ""
            add(
                ModuleInfo(
                    name = project.name,
                    sourceDir = "$baseDir/src/main/java",
                    resourceDir = "$baseDir/src/main/resources",
                    basePackage = detectFallbackPackage(baseDir)
                )
            )
        }
    }
    private val moduleCombo = ComboBox<String>().apply {
        modules.forEach { addItem("${it.name}  [${it.basePackage}]") }
        if (itemCount > 0) selectedIndex = 0
    }
    private var selectedModule: ModuleInfo? = null
    private val packageField = JBTextField(settings.packageName)
    private val javaSourceField = TextFieldWithBrowseButton().apply { text = settings.javaSourceDir }
    private val resourceField = TextFieldWithBrowseButton().apply { text = settings.resourceDir }

    // ── Step 2 : 文件配置 ──
    private val fileTypeDefs = listOf(
        FileTypeDef("Entity", "entity", settings.entitySuffix),
        FileTypeDef("Dao", "dao", settings.daoSuffix),
        FileTypeDef("Service", "service", settings.serviceSuffix),
        FileTypeDef("ServiceImpl", "service/impl", settings.serviceImplSuffix),
        FileTypeDef("Mapper XML", "mapper", settings.mapperSuffix)
    )

    private data class FileTypeDef(val label: String, val subPackage: String, var suffix: String)

    private fun computeFileDirs(): List<String> {
        val pkg = packageField.text.trim().replace('.', '/')
        val javaDir = javaSourceField.text.trim()
        val resourceDir = resourceField.text.trim()
        return fileTypeDefs.map { def ->
            if (def.label == "Mapper XML") "$resourceDir/${def.subPackage}"
            else "$javaDir/$pkg/${def.subPackage}"
        }
    }

    private var fileDirs: MutableList<String> = mutableListOf()
    private var fileSuffixes: MutableList<String> = mutableListOf()

    private val fileConfigModel = object : AbstractTableModel() {
        override fun getRowCount() = fileTypeDefs.size
        override fun getColumnCount() = 3
        override fun getColumnName(col: Int) = when (col) {
            0 -> "文件类型"
            1 -> "目录"
            else -> "后缀"
        }
        override fun getValueAt(row: Int, col: Int): Any = when (col) {
            0 -> fileTypeDefs[row].label
            1 -> fileDirs.getOrElse(row) { "" }
            else -> fileSuffixes.getOrElse(row) { "" }
        }
        override fun isCellEditable(row: Int, col: Int) = col > 0
        override fun setValueAt(value: Any?, row: Int, col: Int) {
            if (col == 1) fileDirs[row] = value as String
            else if (col == 2) {
                fileSuffixes[row] = value as String
                fileTypeDefs[row].suffix = value as String
                saveSuffixSettings()
            }
        }
    }

    private val fileConfigTable = JBTable(fileConfigModel).apply {
        rowHeight = 28
        columnModel.getColumn(0).preferredWidth = 100
        columnModel.getColumn(0).maxWidth = 120
        columnModel.getColumn(1).preferredWidth = 350
        columnModel.getColumn(2).preferredWidth = 80
    }

    // ── Step 3 : 类型映射 + 生成 ──
    private val allJavaTypes = listOf(
        "String", "Integer", "Long", "BigDecimal", "Boolean",
        "LocalDate", "LocalDateTime", "Double", "Float", "Short", "Byte", "Byte[]", "Object"
    )

    private var typeMappingRows: MutableList<TypeMappingRow> = mutableListOf()

    private data class TypeMappingRow(val dbType: String, var javaType: String)

    private val typeMappingModel = object : AbstractTableModel() {
        override fun getRowCount() = typeMappingRows.size
        override fun getColumnCount() = 2
        override fun getColumnName(col: Int) = when (col) {
            0 -> "数据库类型"
            else -> "Java 类型"
        }
        override fun getValueAt(row: Int, col: Int): Any = when (col) {
            0 -> typeMappingRows[row].dbType
            else -> typeMappingRows[row].javaType
        }
        override fun isCellEditable(row: Int, col: Int) = col == 1
    }

    private val typeMappingTable = JBTable(typeMappingModel).apply {
        rowHeight = 28
        columnModel.getColumn(0).preferredWidth = 150
        columnModel.getColumn(1).preferredWidth = 150
        columnModel.getColumn(1).cellEditor = DefaultCellEditor(JComboBox(allJavaTypes.toTypedArray()))
    }

    // ── Step 3 : 日志 ──
    private val summaryLabel = JLabel()
    private val logArea = JTextArea(15, 60).apply { isEditable = false }

    // ── 导航 ──
    private var step = 1
    private var isGenerationInProgress = false
    private var isInitializing = true

    private val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 4))
    private val nextBtn = JButton("下一步")
    private val cancelBtn = JButton("取消")
    private val backBtn = JButton("上一步")
    private val generateButton = JButton("开始生成")

    private fun buildButtonsForStep() {
        buttonPanel.removeAll()
        when (step) {
            1 -> {
                buttonPanel.add(cancelBtn)
                buttonPanel.add(nextBtn)
            }
            2, 3 -> {
                buttonPanel.add(backBtn)
                buttonPanel.add(nextBtn)
            }
            4 -> {
                buttonPanel.add(backBtn)
                buttonPanel.add(generateButton)
            }
        }
        buttonPanel.revalidate()
        buttonPanel.repaint()
    }

    override fun createSouthPanel(): JComponent = buttonPanel

    init {
        title = "代码生成向导"
        init()
        setupListeners()
        setupTableColumns()
        cancelBtn.addActionListener { doCancelAction() }
        nextBtn.addActionListener { goToNextStep() }
        backBtn.addActionListener { goPrevStep() }
        generateButton.addActionListener { startGeneration() }
        buildButtonsForStep()
        autoDetectModule()
        refreshFileDirs()
        refreshTypeMappings()
        isInitializing = false
    }

    // ── 文件后缀编辑时实时持久化 ──
    private fun saveSuffixSettings() {
        settings.entitySuffix = fileTypeDefs[0].suffix
        settings.daoSuffix = fileTypeDefs[1].suffix
        settings.serviceSuffix = fileTypeDefs[2].suffix
        settings.serviceImplSuffix = fileTypeDefs[3].suffix
        settings.mapperSuffix = fileTypeDefs[4].suffix
    }

    private fun refreshFileDirs() {
        fileDirs = computeFileDirs().toMutableList()
        fileSuffixes = fileTypeDefs.map { it.suffix }.toMutableList()
        fileConfigModel.fireTableDataChanged()
    }

    private fun refreshTypeMappings() {
        val saved = settings.typeMappings
        typeMappingRows = TypeMapper.getDefaultMappings().map { (dbType, javaType) ->
            TypeMappingRow(dbType, saved[dbType] ?: javaType)
        }.toMutableList()
        typeMappingModel.fireTableDataChanged()
    }

    private fun saveTypeMappings() {
        settings.typeMappings = typeMappingRows.associate { it.dbType to it.javaType }
    }

    private fun detectFallbackPackage(baseDir: String): String {
        val srcDir = LocalFileSystem.getInstance().findFileByPath("$baseDir/src/main/java") ?: return project.name
        val files = FilenameIndex.getAllFilesByExt(project, "java", GlobalSearchScope.projectScope(project)).toList()
        if (files.isNotEmpty()) {
            for (file in files) {
                try {
                    val text = String(file.contentsToByteArray(), Charsets.UTF_8)
                    val pkgLine = text.lineSequence().firstOrNull { it.trimStart().startsWith("package ") }
                    if (pkgLine != null) {
                        return cleanBasePackage(pkgLine.trimStart().removePrefix("package ").removeSuffix(";").trim())
                    }
                } catch (_: Exception) { }
            }
        }
        val ktFiles = FilenameIndex.getAllFilesByExt(project, "kt", GlobalSearchScope.projectScope(project)).toList()
        for (file in ktFiles) {
            try {
                val text = String(file.contentsToByteArray(), Charsets.UTF_8)
                val pkgLine = text.lineSequence().firstOrNull { it.trimStart().startsWith("package ") }
                if (pkgLine != null) {
                    return cleanBasePackage(pkgLine.trimStart().removePrefix("package ").removeSuffix(";").trim())
                }
            } catch (_: Exception) { }
        }
        return project.name
    }

    private fun autoDetectModule() {
        if (modules.isEmpty()) return
        for (i in modules.indices) {
            val info = modules[i]
            val pkg = EntityPackageFinder.findEntityPackage(Paths.get(info.sourceDir))
            if (pkg != null) {
                moduleCombo.selectedIndex = i
                packageField.text = pkg
                javaSourceField.text = info.sourceDir
                resourceField.text = info.resourceDir
                return
            }
        }
    }

    private fun cleanBasePackage(pkg: String): String {
        var p = pkg.trim()
        val suffixes = listOf(".entity", ".dao", ".mapper", ".service", ".controller", ".impl", ".do", ".DO")
        var changed = true
        while (changed) {
            changed = false
            for (suffix in suffixes) {
                if (p.endsWith(suffix, ignoreCase = true)) {
                    p = p.substring(0, p.length - suffix.length)
                    changed = true
                }
            }
        }
        return p
    }

    private fun setupListeners() {
        val descriptor = FileChooserDescriptor(true, true, false, false, false, false)
        javaSourceField.addBrowseFolderListener(project, descriptor)
        resourceField.addBrowseFolderListener(project, descriptor)
        tableModel.addTableModelListener { updateHeaderState() }

        moduleCombo.addActionListener {
            val idx = moduleCombo.selectedIndex
            if (idx in modules.indices) {
                val info = modules[idx]
                selectedModule = info
                if (!isInitializing) {
                    packageField.text = info.basePackage
                    javaSourceField.text = info.sourceDir
                    resourceField.text = info.resourceDir
                }
            }
        }

        val docListener = object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { saveSettings() }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { saveSettings() }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) { saveSettings() }
        }
        packageField.document.addDocumentListener(docListener)
        genEntityCb.addActionListener { saveSettings() }
        genDaoCb.addActionListener { saveSettings() }
        genServiceCb.addActionListener { saveSettings() }
        genMapperCb.addActionListener { saveSettings() }
        useLombokCb.addActionListener { saveSettings() }
        useTableLogicCb.addActionListener { saveSettings() }
        useSwaggerCb.addActionListener { saveSettings() }
        javaSourceField.textField.document.addDocumentListener(docListener)
        resourceField.textField.document.addDocumentListener(docListener)
    }

    private val allSelected: Boolean get() = checked.all { it }
    private val anySelected: Boolean get() = checked.any { it }

    private fun updateHeaderState() {
        when {
            allSelected -> { headerCb.isSelected = true; headerCb.isEnabled = true }
            anySelected -> { headerCb.isSelected = true; headerCb.isEnabled = false }
            else -> { headerCb.isSelected = false; headerCb.isEnabled = true }
        }
        tableList.tableHeader.repaint()
    }

    private fun setupTableColumns() {
        val col0 = tableList.columnModel.getColumn(0)
        headerCb.horizontalAlignment = SwingConstants.CENTER
        tableList.tableHeader.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val col = tableList.columnModel.getColumnIndexAtX(e.x)
                if (col == 0) {
                    val sel = !allSelected
                    for (i in checked.indices) checked[i] = sel
                    updateHeaderState()
                    tableModel.refreshAll()
                }
            }
        })
        col0.headerRenderer = object : TableCellRenderer {
            override fun getTableCellRendererComponent(
                table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, col: Int
            ): Component {
                updateHeaderState()
                return headerCb
            }
        }
        col0.cellRenderer = object : JCheckBox(), TableCellRenderer {
            init {
                horizontalAlignment = SwingConstants.CENTER
                border = BorderFactory.createEmptyBorder()
            }
            override fun getTableCellRendererComponent(
                table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, col: Int
            ): Component {
                this.isSelected = value as? Boolean == true
                return this
            }
        }
        tableList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = tableList.rowAtPoint(e.point)
                val col = tableList.columnAtPoint(e.point)
                if (col == 0 && row >= 0) {
                    checked[row] = !checked[row]
                    updateHeaderState()
                    tableModel.fireTableCellUpdated(row, 0)
                }
            }
        })
    }

    private fun saveSettings() {
        settings.packageName = packageField.text.trim()
        settings.javaSourceDir = javaSourceField.text.trim()
        settings.resourceDir = resourceField.text.trim()
        settings.useLombok = useLombokCb.isSelected
        settings.useTableLogic = useTableLogicCb.isSelected
        settings.useSwagger = useSwaggerCb.isSelected
        settings.genEntity = genEntityCb.isSelected
        settings.genDao = genDaoCb.isSelected
        settings.genService = genServiceCb.isSelected
        settings.genMapper = genMapperCb.isSelected
    }

    override fun doCancelAction() {
        if (isGenerationInProgress) {
            JOptionPane.showMessageDialog(rootPanel, "请等待生成完成")
            return
        }
        saveSettings()
        super.doCancelAction()
    }

    private fun goToNextStep() {
        when (step) {
            1 -> {
                val selected = allTables.filterIndexed { i, _ -> checked[i] }
                if (selected.isEmpty()) {
                    JOptionPane.showMessageDialog(rootPanel, "请选择至少一个表")
                    return
                }
                if (javaSourceField.text.trim().isEmpty() || resourceField.text.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(rootPanel, "请选择 Java 源目录和 Resources 目录")
                    return
                }
                saveSettings()
                refreshFileDirs()
                showStep(2)
            }
            2 -> {
                saveSuffixSettings()
                saveSettings()
                refreshTypeMappings()
                showStep(3)
            }
            3 -> {
                saveTypeMappings()
                showGenerationStep()
            }
        }
    }

    private fun showGenerationStep() {
        val selected = allTables.filterIndexed { i, _ -> checked[i] }
        summaryLabel.text = "将生成 ${selected.size} 个表的代码: ${selected.joinToString(", ") { it.className }}"
        logArea.text = ""
        showStep(4)
    }

    private fun goPrevStep() {
        when (step) {
            2 -> showStep(1)
            3 -> showStep(2)
            4 -> showStep(3)
        }
    }

    // ── Panels ──

    override fun createCenterPanel(): JComponent {
        rootPanel.add(createSetupPanel(), "setup")
        rootPanel.add(createFileConfigPanel(), "fileconfig")
        rootPanel.add(createTypeMappingPanel(), "typemapping")
        rootPanel.add(createGenerationPanel(), "generation")
        cardLayout.show(rootPanel, "setup")
        return rootPanel
    }

    private fun showStep(s: Int) {
        step = s
        val cardName = when (s) {
            1 -> "setup"
            2 -> "fileconfig"
            3 -> "typemapping"
            else -> "generation"
        }
        cardLayout.show(rootPanel, cardName)
        buildButtonsForStep()
    }

    private fun createSetupPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val c = GridBagConstraints()
        c.fill = GridBagConstraints.HORIZONTAL
        c.insets = Insets(4, 8, 4, 8)

        c.gridx = 0; c.gridy = 0; c.gridwidth = 3; c.weighty = 0.0
        panel.add(JLabel("选择要生成的表:"), c)

        c.gridx = 0; c.gridy = 1; c.gridwidth = 3; c.weighty = 1.0
        c.fill = GridBagConstraints.BOTH
        val col0 = tableList.columnModel.getColumn(0)
        col0.preferredWidth = 40; col0.maxWidth = 40
        tableList.columnModel.getColumn(1).preferredWidth = 200
        val scrollPane = JBScrollPane(tableList)
        scrollPane.preferredSize = java.awt.Dimension(550, 200)
        panel.add(scrollPane, c)

        c.fill = GridBagConstraints.HORIZONTAL; c.weighty = 0.0
        c.gridx = 0; c.gridy = 2; c.gridwidth = 3
        val optionsPanel = JPanel()
        optionsPanel.add(genEntityCb); optionsPanel.add(genDaoCb)
        optionsPanel.add(genServiceCb); optionsPanel.add(genMapperCb)
        optionsPanel.add(useLombokCb); optionsPanel.add(useTableLogicCb); optionsPanel.add(useSwaggerCb)
        panel.add(optionsPanel, c)

        c.gridx = 0; c.gridy = 3; c.gridwidth = 1; c.weightx = 0.0
        panel.add(JLabel("模块选择:"), c)
        c.gridx = 1; c.gridwidth = 2; c.weightx = 1.0
        panel.add(moduleCombo, c)

        c.gridx = 0; c.gridy = 4; c.gridwidth = 1; c.weightx = 0.0
        panel.add(JLabel("基础包名:"), c)
        c.gridx = 1; c.gridwidth = 2; c.weightx = 1.0
        panel.add(packageField, c)

        c.gridx = 0; c.gridy = 5; c.gridwidth = 1; c.weightx = 0.0
        panel.add(JLabel("Java源目录:"), c)
        c.gridx = 1; c.gridwidth = 2; c.weightx = 1.0
        panel.add(javaSourceField, c)

        c.gridx = 0; c.gridy = 6; c.gridwidth = 1; c.weightx = 0.0
        panel.add(JLabel("Resources目录:"), c)
        c.gridx = 1; c.gridwidth = 2; c.weightx = 1.0
        panel.add(resourceField, c)

        return panel
    }

    private fun createFileConfigPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val label = JLabel("配置各文件类型的目标目录和后缀名（目录修改不保存，后缀自动保存）:")
        label.border = BorderFactory.createEmptyBorder(8, 8, 4, 8)
        panel.add(label, BorderLayout.NORTH)

        val dirColumn = fileConfigTable.columnModel.getColumn(1)
        dirColumn.cellEditor = object : AbstractCellEditor(), TableCellEditor {
            private val field = TextFieldWithBrowseButton()

            init {
                val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
                field.addBrowseFolderListener(project, descriptor)
            }

            override fun getTableCellEditorComponent(
                table: JTable, value: Any?, isSelected: Boolean, row: Int, col: Int
            ): Component {
                field.text = value as? String ?: ""
                return field
            }

            override fun getCellEditorValue() = field.text
        }

        val scrollPane = JBScrollPane(fileConfigTable)
        scrollPane.border = BorderFactory.createEmptyBorder(0, 8, 8, 8)
        panel.add(scrollPane, BorderLayout.CENTER)
        return panel
    }

    private fun createTypeMappingPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val label = JLabel("数据库类型 → Java 类型映射（修改后自动保存）:")
        label.border = BorderFactory.createEmptyBorder(8, 8, 4, 8)
        panel.add(label, BorderLayout.NORTH)
        val typeScroll = JBScrollPane(typeMappingTable)
        typeScroll.border = BorderFactory.createEmptyBorder(0, 8, 8, 8)
        panel.add(typeScroll, BorderLayout.CENTER)
        return panel
    }

    private fun createGenerationPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val c = GridBagConstraints()
        c.insets = Insets(4, 8, 4, 8)

        c.gridx = 0; c.gridy = 0; c.weighty = 0.0; c.fill = GridBagConstraints.HORIZONTAL
        panel.add(summaryLabel, c)

        c.gridx = 0; c.gridy = 1; c.weighty = 1.0; c.fill = GridBagConstraints.BOTH
        val logScroll = JBScrollPane(logArea)
        logScroll.preferredSize = java.awt.Dimension(580, 300)
        panel.add(logScroll, c)

        return panel
    }

    // ── Generation ──

    private fun startGeneration() {
        // 保存类型映射
        for (i in typeMappingRows.indices) {
            val editor = typeMappingTable.columnModel.getColumn(1).cellEditor
            if (editor != null && editor.isCellEditable(null)) editor.stopCellEditing()
        }
        saveTypeMappings()
        saveSuffixSettings()

        val pkg = packageField.text.trim()
        val selected = allTables.filterIndexed { i, _ -> checked[i] }
        val javaDir = LocalFileSystem.getInstance().findFileByPath(javaSourceField.text.trim())
        val resourceDir = LocalFileSystem.getInstance().findFileByPath(resourceField.text.trim())
        val typeOverrides = settings.typeMappings

        appendLog("开始生成代码...")
        appendLog("包名: $pkg")
        appendLog("Java目录: ${javaSourceField.text.trim()}")
        appendLog("Resources目录: ${resourceField.text.trim()}")
        isGenerationInProgress = true
        generateButton.isEnabled = false
        generateButton.text = "生成中..."

        ApplicationManager.getApplication().runWriteAction {
            val writer = FileWriter(project)

            if (javaDir == null) {
                SwingUtilities.invokeLater { finishGeneration() }
                appendLog("❌ Java源目录不存在: ${javaSourceField.text.trim()}"); return@runWriteAction
            }
            if (resourceDir == null) {
                SwingUtilities.invokeLater { finishGeneration() }
                appendLog("❌ Resources目录不存在: ${resourceField.text.trim()}"); return@runWriteAction
            }

            try {
                selected.forEach { table ->
                    val entitySuffix = fileSuffixes[0]
                    val entityName = "${table.className}$entitySuffix"
                    for (i in fileTypeDefs.indices) {
                        val def = fileTypeDefs[i]
                        val fileDirStr = fileDirs[i]
                        val suffix = fileSuffixes[i]

                        when (def.label) {
                            "Entity" -> {
                                if (!genEntityCb.isSelected) continue
                                val dir = writer.findOrCreateDirByPath(fileDirStr)
                                if (dir != null) {
                                    val gen = EntityGenerator(useLombokCb.isSelected, useTableLogicCb.isSelected, useSwaggerCb.isSelected)
                                    val fileName = "${table.className}$suffix"
                                    val code = gen.generate(table, pkg, entityName)
                                    val r = writer.writeJavaFile(dir, fileName, code, overwrite = true)
                                    appendLog("${if (r.success) "✔" else "❌"} $fileName.java ${r.error ?: ""}")
                                }
                            }
                            "Dao" -> {
                                if (!genDaoCb.isSelected) continue
                                val dir = writer.findOrCreateDirByPath(fileDirStr)
                                if (dir != null) {
                                    val gen = DaoGenerator()
                                    val fileName = "${table.className}$suffix"
                                    val code = gen.generate(table, pkg, entityName)
                                    val r = writer.writeJavaFile(dir, fileName, code, overwrite = false)
                                    appendLog("${if (r.success) "✔" else if (r.error?.contains("跳过") == true) "•" else "❌"} $fileName.java ${r.error ?: ""}")
                                }
                            }
                            "Service" -> {
                                if (!genServiceCb.isSelected) continue
                                val dir = writer.findOrCreateDirByPath(fileDirStr)
                                if (dir != null) {
                                    val gen = ServiceGenerator()
                                    val fileName = "${table.className}$suffix"
                                    val code = gen.generate(table, pkg, entityName)
                                    val r = writer.writeJavaFile(dir, fileName, code, overwrite = false)
                                    appendLog("${if (r.success) "✔" else if (r.error?.contains("跳过") == true) "•" else "❌"} $fileName.java ${r.error ?: ""}")
                                }
                            }
                            "ServiceImpl" -> {
                                if (!genServiceCb.isSelected) continue
                                val dir = writer.findOrCreateDirByPath(fileDirStr)
                                if (dir != null) {
                                    val gen = ServiceImplGenerator()
                                    val fileName = "${table.className}$suffix"
                                    val code = gen.generate(table, pkg, entityName)
                                    val r = writer.writeJavaFile(dir, fileName, code, overwrite = false)
                                    appendLog("${if (r.success) "✔" else if (r.error?.contains("跳过") == true) "•" else "❌"} $fileName.java ${r.error ?: ""}")
                                }
                            }
                            "Mapper XML" -> {
                                if (!genMapperCb.isSelected) continue
                                val dir = writer.findOrCreateDirByPath(fileDirStr)
                                if (dir != null) {
                                    val gen = MapperXmlGenerator()
                                    val fileName = "${table.className}$suffix"
                                    val code = gen.generate(table, pkg, entityName)
                                    val r = writer.writeXmlFile(dir, fileName, code, overwrite = false)
                                    appendLog("${if (r.success) "✔" else if (r.error?.contains("跳过") == true) "•" else "❌"} $fileName.xml ${r.error ?: ""}")
                                }
                            }
                        }
                    }
                }
                appendLog("")
                appendLog("✅ 代码生成完成!")
            } catch (ex: Exception) {
                appendLog("❌ 错误: ${ex.message}")
            }
        }
        finishGeneration()
    }

    private fun finishGeneration() {
        isGenerationInProgress = false
        generateButton.isEnabled = true
        generateButton.text = "开始生成"
    }

    private fun appendLog(msg: String) {
        SwingUtilities.invokeLater {
            logArea.append("$msg\n")
            logArea.caretPosition = logArea.text.length
        }
    }
}
