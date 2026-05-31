package com.dfeer.plugin.ui

import com.dfeer.plugin.generator.*
import com.dfeer.plugin.model.TableInfo
import com.dfeer.plugin.module.EntityPackageFinder
import com.dfeer.plugin.module.ModuleInfo
import com.dfeer.plugin.module.ModuleScanner
import com.dfeer.plugin.settings.GenerationSettings
import com.dfeer.plugin.writer.FileWriter
import com.intellij.openapi.application.ApplicationManager
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
import javax.swing.table.TableCellRenderer

class GenerationDialog(
    private val project: Project, private val allTables: List<TableInfo>
) : DialogWrapper(true) {

    private val settings = GenerationSettings(project)
    private val cardLayout = CardLayout()
    private val rootPanel = JPanel(cardLayout)

    // Step 1 - Setup
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
        fun refreshAll() {
            fireTableDataChanged()
        }
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

    // Step 2 - Generation / Log
    private val summaryLabel = JLabel()
    private val logArea = JTextArea(15, 60).apply { isEditable = false }

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
        if (step == 1) {
            buttonPanel.add(cancelBtn)
            buttonPanel.add(nextBtn)
        } else {
            buttonPanel.add(backBtn)
            buttonPanel.add(generateButton)
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
        backBtn.addActionListener { showSetupStep() }
        generateButton.addActionListener { startGeneration() }
        buildButtonsForStep()
        autoDetectModule()
        isInitializing = false
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
                } catch (_: Exception) {
                }
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
            } catch (_: Exception) {
            }
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
        tableModel.addTableModelListener {
            updateHeaderState()
        }

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

        // 变更即保存
        val docListener = object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) {
                saveSettings()
            }

            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) {
                saveSettings()
            }

            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {
                saveSettings()
            }
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
            allSelected -> {
                headerCb.isSelected = true; headerCb.isEnabled = true
            }

            anySelected -> {
                headerCb.isSelected = true; headerCb.isEnabled = false
            }

            else -> {
                headerCb.isSelected = false; headerCb.isEnabled = true
            }
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

    override fun createCenterPanel(): JComponent {
        rootPanel.add(createSetupPanel(), "setup")
        rootPanel.add(createGenerationPanel(), "generation")
        cardLayout.show(rootPanel, "setup")
        return rootPanel
    }

    override fun doCancelAction() {
        if (isGenerationInProgress) {
            JOptionPane.showMessageDialog(rootPanel, "请等待生成完成")
            return
        }
        saveSettings()
        super.doCancelAction()
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

    private fun goToNextStep() {
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
        showGenerationStep()
    }

    // ---- Setup Panel ----

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
        col0.preferredWidth = 40
        col0.maxWidth = 40
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

    // ---- Generation Panel ----

    private fun createGenerationPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val c = GridBagConstraints()
        c.fill = GridBagConstraints.BOTH
        c.insets = Insets(4, 8, 4, 8)

        c.gridx = 0; c.gridy = 0; c.weighty = 0.0; c.fill = GridBagConstraints.HORIZONTAL
        panel.add(summaryLabel, c)

        c.gridx = 0; c.gridy = 1; c.weighty = 1.0
        val logScroll = JBScrollPane(logArea)
        logScroll.preferredSize = java.awt.Dimension(580, 300)
        panel.add(logScroll, c)

        return panel
    }

    private fun showSetupStep() {
        step = 1
        cardLayout.show(rootPanel, "setup")
        buildButtonsForStep()
    }

    private fun showGenerationStep() {
        step = 2
        val selected = allTables.filterIndexed { i, _ -> checked[i] }
        summaryLabel.text = "将生成 ${selected.size} 个表的代码: ${selected.joinToString(", ") { it.className }}"
        logArea.text = ""
        cardLayout.show(rootPanel, "generation")
        buildButtonsForStep()
    }

    // ---- Generation ----

    private fun startGeneration() {
        val pkg = packageField.text.trim()
        val selected = allTables.filterIndexed { i, _ -> checked[i] }
        val javaDirPath = javaSourceField.text.trim()
        val resourceDirPath = resourceField.text.trim()

        appendLog("开始生成代码...")
        appendLog("包名: $pkg")
        appendLog("Java目录: $javaDirPath")
        appendLog("Resources目录: $resourceDirPath")
        isGenerationInProgress = true
        generateButton.isEnabled = false
        generateButton.text = "生成中..."

        ApplicationManager.getApplication().runWriteAction {
            val writer = FileWriter(project)

            val javaDir = LocalFileSystem.getInstance().findFileByPath(javaDirPath)
            val resourceDir = LocalFileSystem.getInstance().findFileByPath(resourceDirPath)
            if (javaDir == null) {
                SwingUtilities.invokeLater { finishGeneration() }
                appendLog("❌ Java源目录不存在: $javaDirPath"); return@runWriteAction
            }
            if (resourceDir == null) {
                SwingUtilities.invokeLater { finishGeneration() }
                appendLog("❌ Resources目录不存在: $resourceDirPath"); return@runWriteAction
            }

            try {
                selected.forEach { table ->
                    val entityDir = writer.findOrCreatePackageDir(javaDir, pkg, "entity")
                    val daoDir = writer.findOrCreatePackageDir(javaDir, pkg, "dao")
                    val serviceDir = writer.findOrCreatePackageDir(javaDir, pkg, "service")
                    val mapperDir = writer.findOrCreateDir(resourceDir, "mapper")
                    val entityName = "${table.className}Do"

                    if (genEntityCb.isSelected && entityDir != null) {
                        val gen = EntityGenerator(useLombokCb.isSelected, useTableLogicCb.isSelected, useSwaggerCb.isSelected)
                        val code = gen.generate(table, pkg, entityName)
                        val r = writer.writeJavaFile(entityDir, entityName, code, overwrite = true)
                        appendLog("${if (r.success) "✔" else "❌"} $entityName.java ${r.error ?: ""}")
                    }
                    if (genDaoCb.isSelected && daoDir != null) {
                        val gen = DaoGenerator()
                        val code = gen.generate(table, pkg)
                        val r = writer.writeJavaFile(daoDir, "${table.className}Dao", code, overwrite = false)
                        appendLog("${if (r.success) "✔" else if (r.error?.contains("跳过") == true) "•" else "❌"} ${table.className}Dao.java ${r.error ?: ""}")
                    }
                    if (genServiceCb.isSelected && serviceDir != null) {
                        val gen = ServiceGenerator()
                        val code = gen.generate(table, pkg)
                        val r =
                            writer.writeJavaFile(serviceDir, "${table.className}Service", code, overwrite = false)
                        appendLog("${if (r.success) "✔" else if (r.error?.contains("跳过") == true) "•" else "❌"} ${table.className}Service.java ${r.error ?: ""}")
                        val implDir = writer.findOrCreatePackageDir(javaDir, pkg, "service.impl")
                        if (implDir != null) {
                            val genImpl = ServiceImplGenerator()
                            val codeImpl = genImpl.generate(table, pkg)
                            val r2 = writer.writeJavaFile(
                                implDir, "${table.className}ServiceImpl", codeImpl, overwrite = false
                            )
                            appendLog("${if (r2.success) "✔" else if (r2.error?.contains("跳过") == true) "•" else "❌"} ${table.className}ServiceImpl.java ${r2.error ?: ""}")
                        }
                    }
                    if (genMapperCb.isSelected && mapperDir != null) {
                        val gen = MapperXmlGenerator()
                        val code = gen.generate(table, pkg)
                        val r = writer.writeXmlFile(mapperDir, "${table.className}Mapper", code, overwrite = false)
                        appendLog("${if (r.success) "✔" else if (r.error?.contains("跳过") == true) "•" else "❌"} ${table.className}Mapper.xml ${r.error ?: ""}")
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
