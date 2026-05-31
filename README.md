# Dfeer-Generate-Code

IntelliJ IDEA 插件，从数据库表自动生成 MyBatis-Plus 代码（Entity / Dao / Service / ServiceImpl / Mapper XML）。

## 功能

- 在数据库工具窗口右键菜单一键生成代码
- 支持多表批量生成
- 自动读取表结构、列信息、主键、自增等元数据
- 支持 Lombok、Swagger 注解、MyBatis-Plus 逻辑删除
- 自定义生成后缀名、包路径、输出目录
- 数据库类型 → Java 类型映射可配置

## 使用方式

1. 在 Database 工具窗口选中表、Schema 或数据源
2. 右键 → **Generate Code**
3. 向导中选择表、配置生成选项
4. 点击"开始生成"

## 生成代码示例

```
Entity:       UserDo.java          @TableName, @TableId, @TableField
Dao:          UserDao.java         extends BaseMapper<UserDo>
Service:      UserService.java     extends IService<UserDo>
ServiceImpl:  UserServiceImpl.java extends ServiceImpl<UserDao, UserDo>
Mapper XML:   UserMapper.xml       namespace + 基础映射
```

## 开发

```bash
./gradlew buildPlugin    # 构建插件
./gradlew runIde          # 调试运行
./gradlew publishPlugin   # 发布到 Marketplace
```
