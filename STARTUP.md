# Windows 一键启动

## 首次准备

1. 安装 JDK 17 或更新版本，配置 `JAVA_HOME`（指向 JDK 目录）或将 `java.exe`、`javac.exe` 加入 PATH。
2. 安装并启动 MySQL，将 `mysql.exe` 加入 PATH。脚本不会替你安装或启动 MySQL 服务。
3. 把 `.env.example` 复制成 `.env`，填写数据库账号、密码、管理员 Key 和两家上游平台 Key。已有 `.env` 不要覆盖。
4. 双击 `start.bat`。首次构建需要联网下载 Maven 或项目依赖；使用项目自带的 Maven Wrapper，无需另装 Maven。

`.env` 已在 `.gitignore` 中。每行使用 `NAME=value`，不支持行尾注释和多行值；可使用成对引号包围值。值按文字读取，不执行其中的命令。`.env` 覆盖本次进程继承的同名环境变量，不修改系统环境变量。

当前 SQL 写死数据库名 `nothing_router`，所以 DB_URL 必须使用这个库名；主机和端口可以调整。初始化和应用使用相同的地址、账号、密码。使用远程数据库时请按实际环境配置 JDBC 的 TLS 参数。

## 启动时做什么

1. 检查配置与本地工具，缺失或使用 `change-me` 占位值时停止。
2. 执行 `mvnw.cmd -DskipTests package`。每次都进行打包检查，避免运行旧配置或旧代码；跳过测试不代表已经通过测试。
3. 检查数据库：空库按编号执行 `sql/001` 至 `sql/016`；已有表时只检查项目所需字段及 `duration_ms` 是否允许 NULL，不重复建表，也不运行 `reconcile_wallet.sql`。
4. 前台运行应用。看到 Spring Boot 的 `Started NothingApiRouterApplication` 日志才表示应用启动成功。默认端口8080，可用 `SERVER_PORT` 环境变量修改。

初始化遇到任何错误会停止。MySQL 的建表、改表语句可能已经提交，不能将失败理解为自动回滚了整个初始化过程。若库只有部分结构，先检查已执行的迁移，再补执行缺失 SQL；脚本不会删除已有数据或猜测迁移进度。字段检查不替代完整的数据库迁移版本管理。

## 日常使用

- 双击 `start.bat`，保留窗口，使用 `Ctrl+C` 停止应用。
- 如果 IDEA 已在运行本项目，先停止它，避免两个程序抢占8080端口。
- 启动失败看窗口中 `[FAIL]` 或应用错误日志。数据库连接失败时先检查 MySQL 服务、账号权限和 `.env`。
- 新库没有用户、钱包余额和聊天 Key。启动后通过管理员接口创建用户、签发聊天 Key、初始化钱包并增加测试余额。不要拿上游 Key 或管理员 Key 当聊天 Key 使用。
- 上游模型名称、别名映射和价格仍在 `src/main/resources/application.yaml` 中配置；其中 demo 价格不是官方价目表。

只检查配置和工具、不构建、不连接数据库、不启动应用：

```powershell
.\start.bat -CheckOnly
```

本启动方式依赖 Windows、JDK 和运行中的 MySQL，并非包含所有依赖服务的 Docker Compose 部署。
