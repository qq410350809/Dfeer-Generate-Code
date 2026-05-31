package com.dfeer.plugin.action

import com.dfeer.plugin.database.DatabaseReader
import com.dfeer.plugin.ui.GenerationDialog
import com.intellij.database.model.ObjectKind
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbElement
import com.intellij.database.psi.DbTable
import com.intellij.database.view.getSelectedDbElements
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class GenerateCodeAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val hasTables = e.dataContext.getSelectedDbElements(DbTable::class.java).isNotEmpty()
        val hasDataSources = e.dataContext.getSelectedDbElements(DbDataSource::class.java).isNotEmpty()
        val hasContainer = e.dataContext.getSelectedDbElements(DbElement::class.java)
            .any { el -> el.getDasChildren(ObjectKind.TABLE).isNotEmpty() }
        e.presentation.isEnabledAndVisible = hasTables || hasDataSources || hasContainer
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        try {
            val reader = DatabaseReader()
            val tables = mutableListOf<DbTable>()

            // 1. 选中的 DbTable（支持复选）
            tables.addAll(e.dataContext.getSelectedDbElements(DbTable::class.java).toList())

            // 2. 选中的 DbDataSource → 加载该数据源所有表
            e.dataContext.getSelectedDbElements(DbDataSource::class.java).forEach { ds ->
                tables.addAll(reader.listTables(ds))
            }

            // 3. 选中的容器节点（schema/database）→ 加载该节点下的所有表
            e.dataContext.getSelectedDbElements(DbElement::class.java).filter { el ->
                el !is DbTable && el !is DbDataSource &&
                        el.getDasChildren(ObjectKind.TABLE).isNotEmpty()
            }.forEach { container ->
                container.getDasChildren(ObjectKind.TABLE).forEach { obj ->
                    if (obj is DbTable) tables.add(obj)
                }
            }

            if (tables.isEmpty()) {
                Messages.showInfoMessage("选中的元素中没有找到表", "提示")
                return
            }

            val tableInfos = tables.distinctBy { it.name }.map { reader.readTableInfo(it) }
            GenerationDialog(project, tableInfos).show()
        } catch (ex: Exception) {
            Messages.showErrorDialog(project, "操作失败: ${ex.message}", "错误")
        }
    }
}
