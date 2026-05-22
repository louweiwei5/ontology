# HIK_OSP — Ontology Semantic Platform (本体语义平台)

海康威视本体语义平台，提供本体建模、关系映射、DSL 语义查询、OWL 导入/导出及数据库表映射等核心功能。

## 技术栈

| 层级 | 技术 |
|------|------|
| **后端框架** | Spring Boot 3.5.14 + Java 21 |
| **持久化** | Spring Data JPA + Hibernate (MySQL) |
| **语义引擎** | Apache Jena 5.3.0 (OWL/RDF/SPARQL) |
| **OWL解析** | OWL API 5.5.0 （支持 .owx 格式） |
| **前端** | Vue 3 + TypeScript + Vite 5 |
| **可视化** | D3.js（本体图） |
| **构建** | Maven + npm |

## 架构概览

```
┌────────────────────────────────────────────────────┐
│                     Frontend (Vue 3)                │
│  Ontology CRUD / Graph View / Query Engine /       │
│  DB Connections / Table Import                     │
└───────────────┬────────────────────────────┐───────┘
                │ HTTP (proxy /api → :8080)  │
┌───────────────┴────────────────────────────┴───────┐
│              REST Controllers                       │
│  OntologyController / ClassController /             │
│  PropertyController / DbConnectionController /      │
│  DslController / HomeController                     │
└───────────────┬────────────────────────────┴───────┘
                │
┌───────────────┴────────────────────────────┴───────┐
│                 Services                            │
│  OntologyService  本体 CRUD / 导出/导入/图/TBox     │
│  ClassService     类的 CRUD                         │
│  PropertyService  属性 CRUD                         │
│  DbConnectionService  数据库连接管理与表导入         │
│  DslQueryService  DSL 语义查询引擎                  │
│  OwlParser        OWL 文件解析                      │
└───────────────┬────────────────────────────┴───────┘
                │
┌───────────────┴────────────────────────────┴───────┐
│              Data Layer                             │
│  Repository (JPA) → MySQL 数据库                    │
│  Apache Jena → OWL 文件生成                        │
└───────────────┬────────────────────────────┴───────┘
```

## 数据模型

### 核心实体

- **Ontology** (ontologies) — 本体定义，包含命名空间、版本、描述
- **Class** (classes) — 概念/类定义，支持层级继承（parent-child）
- **Property** (properties) — 属性定义，分为 **Data Property**（数据类型属性）和 **Object Property**（对象关系属性），关联关系支持 one-to-one / one-to-many / many-to-one / many-to-many
- **DbConnection** (db_connections) — 外部数据库连接配置
- **TableImport** (table_imports) — 数据库表导入映射配置（JSON格式存储映射关系）

## 功能说明

### 1. 本体管理

- **CRUD** 创建、查看、编辑、删除本体
- **导出** JSON 格式导出本体（含所有类与属性）
- **导入**
  - JSON 格式导入
  - OWL 文件导入（自动检测 RDF/XML、Turtle、N3、JSON-LD、OWL/XML 格式）
- **OWL 导出** 生成标准 OWL 文件（RDF/XML 或 Turtle 格式）
- **TBox 视图** 三种格式：
  - Markdown 表格（可读性高）
  - Manchester 语法（Protégé 兼容）
  - JSON 结构化数据

### 2. 类与属性管理

- 类的创建、编辑、删除，支持父子层级
- Data Property 创建（支持 string / integer / float / boolean / date / datetime / text）
- Object Property 创建，配置关系类型和映射规则
- 属性映射规则可配置（domain_property ↔ range_property）

### 3. 本体图可视化

基于 D3.js 的本体图展示，显示类层级结构（hierarchy edges）和对象属性关系（object property edges），以及每个类关联的数据属性。

### 4. 数据库表导入

- 管理外部数据库连接（MySQL）
- 表结构自省（浏览表、列、主键、注释）
- 表→类、列→属性的映射配置
- 应用映射：将数据库表结构映射为本体中的类与数据属性，自动生成 IRI

### 5. DSL 语义查询引擎

通过 JSON DSL 对已映射的数据库表执行语义查询，支持：

- 实体关联路径的 JOIN 自动推导（INNER JOIN）
- 多层嵌套查询（支持子关系聚合为嵌套 JSON 结果）
- 丰富的过滤条件：EQ / NEQ / GT / GTE / LT / LTE / IN / NOT_IN / BETWEEN / LIKE / CONTAINS / IS_NULL / IS_NOT_NULL
- 过滤条件支持 AND/OR 逻辑组合
- 分页查询（limit/offset 或 page/size）
- Many-to-Many 关联查询（junction table 支持）

## 快速开始

### 前置条件

- JDK 21+
- MySQL 8.0+
- Node.js 22+
- Maven（内置 `mvnw`）

### 1. 创建数据库

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ontology DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### 2. 配置数据库连接

编辑 `src/main/resources/application.yml`，设置数据库密码：

```yaml
spring:
  datasource:
    username: root
    password: your_password
```

### 3. 启动后端

```bash
./mvnw spring-boot:run
```

后端启动在 `http://localhost:8080`

### 4. 启动前端

```bash
cd frontend && npm install && npm run dev
```

前端启动在 `http://localhost:5173`（`/api` 自动代理到后端）

## API 概览

### 本体

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ontologies` | 创建本体 |
| GET | `/api/ontologies` | 列表（含类/属性计数） |
| GET | `/api/ontologies/{id}` | 详情 |
| PUT | `/api/ontologies/{id}` | 更新 |
| DELETE | `/api/ontologies/{id}` | 删除 |
| GET | `/api/ontologies/{id}/graph` | 本体图数据 |
| GET | `/api/ontologies/{id}/tbox` | TBox Markdown |
| GET | `/api/ontologies/{id}/tbox/manchester` | TBox Manchester |
| GET | `/api/ontologies/{id}/tbox/json` | TBox JSON |
| GET | `/api/ontologies/{id}/export` | JSON 导出 |
| POST | `/api/ontologies/import` | JSON 导入 |
| POST | `/api/ontologies/import/owl` | OWL 文件导入 |
| GET | `/api/ontologies/{id}/export-owl` | OWL 导出 |

### 类

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ontologies/{id}/classes` | 创建类 |
| GET | `/api/ontologies/{id}/classes` | 类的列表 |
| GET | `/api/ontologies/{id}/classes/{classId}` | 类详情 |
| PUT | `/api/ontologies/{id}/classes/{classId}` | 更新类 |
| DELETE | `/api/ontologies/{id}/classes/{classId}` | 删除类 |

### 属性

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ontologies/{id}/properties` | 创建属性 |
| GET | `/api/ontologies/{id}/properties` | 属性列表 |
| GET | `/api/ontologies/{id}/properties/{propId}` | 属性详情 |
| PUT | `/api/ontologies/{id}/properties/{propId}` | 更新属性 |
| DELETE | `/api/ontologies/{id}/properties/{propId}` | 删除属性 |

### 数据库连接与表导入

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/db-connections` | 创建连接 |
| GET | `/api/db-connections` | 连接列表 |
| POST | `/api/db-connections/{id}/test` | 测试连接 |
| GET | `/api/db-connections/{id}/tables` | 浏览表 |
| GET | `/api/db-connections/{id}/tables/{table}` | 表详情 |
| POST | `/api/db-connections/{id}/imports` | 创建导入 |
| GET | `/api/db-connections/imports` | 导入列表 |
| PUT | `/api/db-connections/imports/{id}/mapping` | 更新映射 |
| POST | `/api/db-connections/imports/{id}/apply` | 应用映射 |

### DSL 语义查询

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/query/dsl` | 执行 DSL 查询 |
