# Database Code Generation Plugin — Design Spec

## 概述

基于 IntelliJ IDEA Database 工具窗口中已配置的数据源，读取表结构信息，自动生成 Java Entity、Dao、Service、MyBatis-Plus Mapper XML 代码。

## 架构

```
┌─────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  DatabaseReader  │────>│  CodeGenerator   │────>│  FileWriter      │
│  (读取DB Schema)  │     │  (模板生成代码)   │     │  (写入项目文件)   │
└─────────────────┘     └──────────────────┘     └──────────────────┘
         │                        │                        │
         v                        v                        v
   DatabasePsiManager        字符串拼接                PsiFileFactory
```

## 组件设计

### 1. DatabaseReader

- 通过 `com.intellij.database.psi.*` API 获取项目所有已配置的数据源
- 提供方法：列出数据源、列出表、读取表结构（列名、类型、注释、主键）
- 内部 model: `TableInfo`、`ColumnInfo` 封装读取结果
- 支持多表同时选择

### 2. CodeGenerator

根据 `TableInfo` 生成以下代码，使用字符串拼接（无模板引擎依赖）：

#### Entity (Java POJO)
- 类名 = 表名大驼峰（`sys_user` → `SysUser`）
- 字段名 = 列名小驼峰（`user_name` → `userName`）
- 使用 MyBatis-Plus 注解：
  - 类: `@TableName("sys_user")`
  - 主键: `@TableId(value = "id", type = IdType.AUTO)`
  - 普通列: `@TableField("username")`
- **Lombok 开关**（Wizard 中可勾选）：
  - 开启: 类加 `@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@Accessors(chain = true)`，不生成 Getter/Setter
  - 关闭: 生成 private 字段 + Getter/Setter 方法

#### Dao (Java Interface)
- extends MyBatis-Plus `BaseMapper<Entity>`，自动继承 CRUD 方法
- 标记 `@Mapper` 注解

#### Service (Java Class)
- extends MyBatis-Plus `ServiceImpl<Dao, Entity>`
- implements 自定义 Service 接口（可选）
- `@Service` 注解
- 提供基础 CRUD 方法（可直接继承 ServiceImpl 的）

#### Mapper XML（可选生成）
- MyBatis-Plus 支持自动 SQL，XML 不是必须的
- 如果用户勾选生成 XML，则生成含自定义 SQL 的 XML（基础 CRUD 由 MP 内置）

### 3. FileWriter

- 接收基础目标目录和包名
- Entity → `{baseDir}/{包路径}/entity/`
- Dao → `{baseDir}/{包路径}/dao/`
- Service → `{baseDir}/{包路径}/service/`
- Mapper XML → `{resourcesDir}/mapper/`
- 使用 IntelliJ `PsiFileFactory` 创建文件

### 4. GenerationDialog (Wizard UI)

使用 IntelliJ `DialogWrapper` 实现向导对话框：

| 步骤 | 内容 |
|------|------|
| Step 1 | 选择数据源 → 加载该数据源下的所有表，多选列表 |
| Step 2 | 勾选要生成的代码类型（Entity / Dao / Service / Mapper XML）+ 是否使用 Lombok + 表是否有 `is_del` 逻辑删除字段 |
| Step 3 | 输入基础包名（如 `com.example.demo`），选择 Java 源目录和 Resources 目录 |
| Step 4 | 预览生成文件列表，确认生成 |

## MyBatis-Plus 字段策略注解规则

根据字段名自动匹配策略，无需用户手动配置：

| 字段名模式 | 注解 | 说明 |
|-----------|------|------|
| `is_del`, `deleted`, `is_deleted` | `@TableLogic` + `@TableField` | 逻辑删除（可选，用户勾选后才加） |
| `create_time`, `cra_time` | `@TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)` | INSERT/UPDATE 时忽略（数据库自动处理） |
| `update_time`, `up_time` | `@TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)` | INSERT/UPDATE 时忽略（数据库自动处理） |

- `is_del` 相关字段：在 Wizard Step 2 添加复选框"启用逻辑删除（@TableLogic）"，勾选后 Entity 中该字段追加 `@TableLogic`
- 时间字段：自动识别并添加对应策略注解，无需用户干预

## 类型映射 (MySQL → Java)

| MySQL 类型 | Java 类型 |
|-----------|----------|
| `VARCHAR`, `CHAR`, `TEXT`, `LONGTEXT`, `MEDIUMTEXT` | `String` |
| `TINYINT(1)` | `Boolean` |
| `TINYINT` (>1) | `int` |
| `INT`, `INTEGER`, `MEDIUMINT`, `SMALLINT` | `int` |
| `BIGINT` | `long` |
| `DECIMAL`, `NUMERIC`, `FLOAT`, `DOUBLE` | `BigDecimal` |
| `BIT` | `Boolean` |
| `DATE` | `LocalDate` |
| `DATETIME`, `TIMESTAMP` | `LocalDateTime` |

## 包结构

```
{basePackage}/
  ├── entity/
  │   └── SysUser.java
  ├── dao/
  │   └── SysUserDao.java
  └── service/
      └── SysUserService.java

{resources}/mapper/
  └── SysUserMapper.xml
```

## 代码模板示例

### Entity（使用 Lombok）

```java
package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName("sys_user")
public class SysUser {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("username")
    private String username;

    @TableField("email")
    private String email;

    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("is_del")
    private Boolean isDel;
}
```

> 注：`@TableLogic` 仅在用户勾选"启用逻辑删除"时生成；`is_del` 字段类型为 `Boolean`。

### Entity（不使用 Lombok）

同上，去掉 Lombok 注解，添加 Getter/Setter 方法。

### Dao

```java
package com.example.demo.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserDao extends BaseMapper<SysUser> {
}
```

### Service

```java
package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.dao.SysUserDao;
import com.example.demo.entity.SysUser;
import org.springframework.stereotype.Service;

@Service
public class SysUserService extends ServiceImpl<SysUserDao, SysUser> {
}
```

### Mapper XML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.demo.dao.SysUserDao">

    <!-- MyBatis-Plus 基础 CRUD 由 BaseMapper 内置，此 XML 仅用于自定义 SQL -->

    <resultMap id="BaseResultMap" type="com.example.demo.entity.SysUser">
        <id column="id" property="id" jdbcType="BIGINT"/>
        <result column="username" property="username" jdbcType="VARCHAR"/>
        <result column="email" property="email" jdbcType="VARCHAR"/>
        <result column="create_time" property="createTime" jdbcType="TIMESTAMP"/>
    </resultMap>

    <sql id="Base_Column_List">
        id, username, email, create_time
    </sql>

</mapper>
```

## 错误处理

- 数据源未配置或无表 → 提示用户先在 Database 工具窗口配置数据源
- 表没有主键 → 警告但仍可生成
- 目标目录无效 → 提示选择有效目录
- 文件已存在 → 弹窗确认覆盖或跳过

## 技术依赖

- `com.intellij.database` — Database PSI API
- `com.baomidou:mybatis-plus-annotation` — MP 注解（Entity）
- `com.baomidou:mybatis-plus-core` / `mybatis-plus-extension` — BaseMapper / ServiceImpl
- `org.mybatis:mybatis` — `@Mapper` 注解
- Lombok 注解（可选）
- IntelliJ `PsiFileFactory` — 创建和写入文件
- 代码生成方式：字符串拼接（无模板引擎依赖）
