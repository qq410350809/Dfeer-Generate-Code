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
        private const val ENTITY_SUFFIX = "${PREFIX}entitySuffix"
        private const val DAO_SUFFIX = "${PREFIX}daoSuffix"
        private const val SERVICE_SUFFIX = "${PREFIX}serviceSuffix"
        private const val SERVICE_IMPL_SUFFIX = "${PREFIX}serviceImplSuffix"
        private const val MAPPER_SUFFIX = "${PREFIX}mapperSuffix"
        private const val TYPE_MAPPINGS = "${PREFIX}typeMappings"
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

    var entitySuffix: String
        get() = props.getValue(ENTITY_SUFFIX, "Do")
        set(v) = props.setValue(ENTITY_SUFFIX, v)

    var daoSuffix: String
        get() = props.getValue(DAO_SUFFIX, "Dao")
        set(v) = props.setValue(DAO_SUFFIX, v)

    var serviceSuffix: String
        get() = props.getValue(SERVICE_SUFFIX, "Service")
        set(v) = props.setValue(SERVICE_SUFFIX, v)

    var serviceImplSuffix: String
        get() = props.getValue(SERVICE_IMPL_SUFFIX, "ServiceImpl")
        set(v) = props.setValue(SERVICE_IMPL_SUFFIX, v)

    var mapperSuffix: String
        get() = props.getValue(MAPPER_SUFFIX, "Mapper")
        set(v) = props.setValue(MAPPER_SUFFIX, v)

    var typeMappings: Map<String, String>
        get() {
            val raw = props.getValue(TYPE_MAPPINGS, "") ?: ""
            if (raw.isBlank()) return emptyMap()
            return raw.split(",").mapNotNull { entry ->
                val parts = entry.split(":", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()
        }
        set(v) {
            val raw = v.entries.joinToString(",") { "${it.key}:${it.value}" }
            props.setValue(TYPE_MAPPINGS, raw)
        }
}
