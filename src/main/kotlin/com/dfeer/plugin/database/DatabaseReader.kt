package com.dfeer.plugin.database

import com.dfeer.plugin.model.ColumnInfo
import com.dfeer.plugin.model.TableInfo
import com.intellij.database.model.DasColumn
import com.intellij.database.model.ObjectKind
import com.intellij.database.psi.DbColumn
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.psi.DbTable
import com.intellij.openapi.project.Project

class DatabaseReader(private val project: Project) {

    fun listDataSources(): List<DbDataSource> {
        return DbPsiFacade.getInstance(project).dataSources
    }

    fun listTables(dataSource: DbDataSource): List<DbTable> {
        val tables = mutableListOf<DbTable>()
        dataSource.model.modelRoots.forEach { schema ->
            schema.getDasChildren(ObjectKind.TABLE).forEach { obj ->
                if (obj is DbTable) {
                    tables.add(obj)
                }
            }
            schema.getDasChildren(ObjectKind.VIEW).forEach { obj ->
                if (obj is DbTable) {
                    tables.add(obj)
                }
            }
        }
        return tables
    }

    fun readTableInfo(table: DbTable): TableInfo {
        val columns = mutableListOf<ColumnInfo>()
        table.getDasChildren(ObjectKind.COLUMN).forEach { obj ->
            if (obj is DbColumn) {
                columns.add(readColumnInfo(obj, table))
            }
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

    private fun readColumnInfo(col: DbColumn, table: DbTable): ColumnInfo {
        val attrs = table.getColumnAttrs(col)
        val typeSpec = col.dasType.specification
        return ColumnInfo(
            name = col.name,
            type = typeSpec,
            comment = col.comment ?: "",
            isPrimaryKey = attrs.contains(DasColumn.Attribute.PRIMARY_KEY),
            isAutoIncrement = attrs.contains(DasColumn.Attribute.AUTO_GENERATED),
            nullable = !col.isNotNull
        )
    }
}
