package com.dfeer.plugin.settings

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project

class GenerationSettings(private val project: Project) {

    companion object {
        private const val PREFIX = "GenerateCode."
        private const val PACKAGE = "${PREFIX}package"
        private const val JAVA_DIR = "${PREFIX}javaDir"
        private const val RESOURCE_DIR = "${PREFIX}resourceDir"
        private const val LOMBOK = "${PREFIX}lombok"
        private const val TABLE_LOGIC = "${PREFIX}tableLogic"
        private const val ENTITY = "${PREFIX}entity"
        private const val DAO = "${PREFIX}dao"
        private const val SERVICE = "${PREFIX}service"
        private const val MAPPER = "${PREFIX}mapper"
        private const val SWAGGER = "${PREFIX}swagger"
    }

    private val props: PropertiesComponent = PropertiesComponent.getInstance(project)

    var packageName: String
        get() = props.getValue(PACKAGE, "com.example.demo")
        set(v) = props.setValue(PACKAGE, v)

    var javaSourceDir: String
        get() = props.getValue(JAVA_DIR, "")
        set(v) = props.setValue(JAVA_DIR, v)

    var resourceDir: String
        get() = props.getValue(RESOURCE_DIR, "")
        set(v) = props.setValue(RESOURCE_DIR, v)

    var useLombok: Boolean
        get() = props.getBoolean(LOMBOK, true)
        set(v) = props.setValue(LOMBOK, v)

    var useTableLogic: Boolean
        get() = props.getBoolean(TABLE_LOGIC, false)
        set(v) = props.setValue(TABLE_LOGIC, v)

    var genEntity: Boolean
        get() = props.getBoolean(ENTITY, true)
        set(v) = props.setValue(ENTITY, v)

    var genDao: Boolean
        get() = props.getBoolean(DAO, true)
        set(v) = props.setValue(DAO, v)

    var genService: Boolean
        get() = props.getBoolean(SERVICE, true)
        set(v) = props.setValue(SERVICE, v)

    var genMapper: Boolean
        get() = props.getBoolean(MAPPER, true)
        set(v) = props.setValue(MAPPER, v)

    var useSwagger: Boolean
        get() = props.getBoolean(SWAGGER, false)
        set(v) = props.setValue(SWAGGER, v)
}
