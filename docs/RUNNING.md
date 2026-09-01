# 运行说明

## 1. 适用范围

本文面向本地开发、联调、测试和打包。默认环境为 Windows PowerShell；Linux/macOS 可将路径和复制命令替换为对应 Shell 语法。

## 2. 环境要求

| 软件/服务 | 要求 |
|---|---|
| Java | JDK 17+ |
| Maven | 3.9+，或可用的 Maven Wrapper |
| PostgreSQL | 14+ |
| pgvector | 安装到目标 PostgreSQL 实例 |
| SiliconFlow | 有效 API Key，可访问 OpenAI 兼容接口 |

检查环境：

```powershell
java -version
mvn -version
psql --version
```

项目配置的 HTTP 端口为 `8082`。启动前可检查端口：

```powershell
Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue
```

## 3. 初始化 PostgreSQL

### 3.1 创建数据库

```powershell
psql -U postgres -c "CREATE DATABASE knowledge_base_rebuild;"
```

如果数据库已存在，PostgreSQL 会报重复错误，可以忽略并继续检查。

### 3.2 启用 pgvector

```powershell
psql -U postgres -d knowledge_base_rebuild -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

验证：

```powershell
psql -U postgres -d knowledge_base_rebuild -c "SELECT extname, extversion FROM pg_extension WHERE extname='vector';"
```

## 4. 配置环境变量

应用会读取启动工作目录中的 `.env`：

```yaml
spring.config.import: optional:file:.env[.properties]
```

复制示例：

```powershell
Copy-Item .env.example .env
```

编辑 `.env`：

```properties
DB_URL=jdbc:postgresql://localhost:5432/knowledge_base_rebuild
DB_USERNAME=postgres
DB_PASSWORD=replace-with-your-database-password
SILICONFLOW_API_KEY=replace-with-your-siliconflow-api-key
```

注意：当前 `.env.example` 可能只包含数据库变量，首次配置时仍需手动加入 `SILICONFLOW_API_KEY`。

安全要求：

- 不要提交 `.env`。
- 不要把真实值复制到 `application.yaml`。
- 不要在问题截图、日志或错误报告中暴露 API Key。
- 密钥泄漏后应立即在服务商后台撤销并重新生成。

确认 `.env` 被忽略：

```powershell
git check-ignore -v .env
git status --short
```

## 5. 配置说明

主要配置位于 `src/main/resources/application.yaml`。

### 5.1 数据库

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/knowledge_base_rebuild}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
```

建议本地也显式填写 `.env`，不要依赖默认密码。

### 5.2 向量存储

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        table-name: vector_store
        schema-name: public
        dimensions: 1024
        distance-type: COSINE_DISTANCE
        index-type: HNSW
        initialize-schema: true
```

`dimensions` 必须和 `BAAI/bge-m3` 的实际输出一致。

### 5.3 模型

```yaml
spring:
  ai:
    openai:
      base-url: https://api.siliconflow.cn/v1
      embedding:
        model: BAAI/bge-m3
      chat:
        model: Qwen/Qwen3-8B
```

### 5.4 业务参数

```yaml
app:
  chunk:
    max-chars: 500
    overlap-chars: 50
  web:
    max-content-bytes: 2097152
  rag:
    top-k: 5
    min-similarity: 0.55
    retry-min-hits: 2
    max-context-chars: 7000
```

## 6. 启动方式

### 6.1 使用本机 Maven

当前项目已使用此方式完成验收：

```powershell
mvn spring-boot:run
```

### 6.2 使用 Maven Wrapper

```powershell
.\mvnw.cmd spring-boot:run
```

如果 Wrapper 无法启动，优先检查 `.mvn/wrapper` 配置和网络，或改用已安装的 Maven。

### 6.3 使用可执行 JAR

先打包：

```powershell
mvn package
```

再运行：

```powershell
java -jar target\knowledge-base-bulid-0.0.1-SNAPSHOT.jar
```

临时修改端口：

```powershell
java -jar target\knowledge-base-bulid-0.0.1-SNAPSHOT.jar --server.port=8083
```

## 7. 启动成功判定

日志应包含类似内容：

```text
HikariPool-1 - Start completed.
Initializing PGVectorStore schema for table: vector_store in schema: public
Tomcat started on port 8082
Started KnowledgeBaseBulidApplication
```

访问：

- 前端：<http://localhost:8082/>
- Swagger：<http://localhost:8082/swagger-ui.html>
- OpenAPI：<http://localhost:8082/v3/api-docs>

## 8. 功能冒烟测试

以下操作会调用真实 Embedding 或 Chat 模型，并在数据库写入测试数据。请在确认 API Key、额度和测试环境后执行。

### 8.1 查询资料列表

```powershell
Invoke-RestMethod http://localhost:8082/api/documents
```

### 8.2 创建笔记

```powershell
$body = @{
    title = '冒烟测试笔记'
    content = '实验室例会安排在每周三下午三点。'
} | ConvertTo-Json

$document = Invoke-RestMethod `
    -Uri http://localhost:8082/api/documents/notes `
    -Method Post `
    -ContentType 'application/json' `
    -Body $body

$document
```

成功响应应满足：

- `status` 为 `READY`。
- `chunkCount` 大于 0。
- 返回非空 `id`。

### 8.3 提问

```powershell
$question = @{
    question = '实验室例会是什么时间？'
} | ConvertTo-Json

Invoke-RestMethod `
    -Uri http://localhost:8082/api/chat `
    -Method Post `
    -ContentType 'application/json' `
    -Body $question
```

检查：

- `answer` 引用资料编号。
- `sources` 至少包含一条记录。
- `retrieval.acceptedCount` 大于 0。
- 如果首次命中不足，`retrieval.retried` 为 `true`。

### 8.4 更新资料

文件更新接口使用 multipart：

```powershell
curl.exe -X PUT `
  -F "file=@C:\path\to\replacement.txt" `
  http://localhost:8082/api/documents/1
```

更新后确认资料保持 `READY`，并且旧向量已被新向量替换。

### 8.5 删除测试资料

删除会同时删除资料元数据和对应向量：

```powershell
Invoke-RestMethod `
    -Uri http://localhost:8082/api/documents/1 `
    -Method Delete
```

将示例中的 `1` 替换为实际测试资料 ID。

## 9. 数据库检查

连接数据库：

```powershell
psql -U postgres -d knowledge_base_rebuild
```

执行：

```sql
SELECT current_database();

SELECT extname, extversion
FROM pg_extension
WHERE extname = 'vector';

SELECT id, name, file_type, status, chunk_count, upload_time
FROM document
ORDER BY upload_time DESC;

SELECT COUNT(*) AS vector_count
FROM vector_store;
```

检查单份资料的向量数量时，可根据 metadata 中的 `documentId` 过滤。具体 JSON 查询语法取决于 Spring AI 当前生成的 metadata 列类型。

## 10. 测试

运行全部测试：

```powershell
mvn test
```

当前测试集包括：

- Spring 上下文启动。
- TXT/Markdown 解析。
- 文本切分。
- 网页 URL 安全限制。
- 两阶段 RAG 和无依据拒答。

RAG 单元测试使用 Mock，不会消耗真实模型额度。

## 11. 打包

```powershell
mvn clean package
```

跳过测试打包：

```powershell
mvn package -DskipTests
```

发布前不建议跳过测试。

确认产物：

```powershell
Get-Item target\knowledge-base-bulid-0.0.1-SNAPSHOT.jar
```

## 12. 常见故障

### 12.1 端口已被占用

错误：

```text
Web server failed to start. Port 8082 was already in use.
```

查找进程：

```powershell
Get-NetTCPConnection -LocalPort 8082 -State Listen
```

关闭已有实例，或临时使用其他端口。

### 12.2 数据库连接失败

检查：

- PostgreSQL 服务是否启动。
- 数据库名称是否为 `knowledge_base_rebuild`。
- `.env` 是否位于启动工作目录。
- URL、用户名和密码是否正确。
- 5432 端口是否可访问。

### 12.3 `vector` 扩展不存在

错误通常包含 `type vector does not exist` 或扩展初始化失败。

执行：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

如果命令失败，需要先为当前 PostgreSQL 版本安装 pgvector。

### 12.4 模型认证失败

检查 `.env` 中的 `SILICONFLOW_API_KEY`。修改 `.env` 后需要重启应用。

应用会将常见 401/API Key 错误转换为 `502 Bad Gateway`，不会把密钥返回给客户端。

### 12.5 向量维度不一致

更换 Embedding 模型但未同步修改 `dimensions` 或重建表时可能出现维度错误。恢复步骤通常包括：

1. 确认新模型维度。
2. 更新配置。
3. 备份并重建向量数据。
4. 对全部资料重新入库。

不要直接修改现有向量列维度后继续混用旧向量。

### 12.6 Maven 无法写入本地仓库

如果看到本地 Maven 仓库“拒绝访问”，检查仓库目录权限、杀毒软件占用以及 Maven `settings.xml` 中的 `localRepository`。

### 12.7 网页无法抓取

确认：

- URL 使用公开的 HTTP/HTTPS。
- 目标不是 localhost、内网 IP 或私有 DNS。
- 页面返回 `text/html` 或 `text/plain`。
- 页面大小未超过限制。
- 目标站点允许服务端抓取。

## 13. 停止应用

在当前终端按 `Ctrl+C`。

如果进程在后台运行，先查找监听端口对应 PID，再停止准确的 Java 进程。不要批量终止所有 Java 进程，以免影响其他应用。

## 14. 生产运行建议

- 使用专用数据库账号，不使用 `postgres` 超级用户。
- 通过部署平台 Secret 管理密钥，不使用磁盘 `.env`。
- 禁用生产环境 Swagger 或增加访问控制。
- 使用 Flyway/Liquibase 管理数据库结构。
- 增加 Actuator、日志采集、指标、告警和调用链追踪。
- 为模型调用配置超时、重试、限流和费用告警。
- 为资料入库增加异步队列、幂等控制与失败补偿。
- 定期备份 `document` 和 `vector_store`。

