# River4J Database Layer

River4J数据库层是从Go语言的dbsqlc迁移到Java21+JDBI3的实现。它提供了类型安全、SQL优先的数据库访问模式，完全对应Go版本的功能。

## 特性

- ✅ **类型安全**: 使用Java21 Records和枚举，确保编译时类型检查
- ✅ **SQL优先**: 直接使用原生SQL，不依赖ORM魔法
- ✅ **高性能**: 基于HikariCP连接池和JDBI3优化
- ✅ **PostgreSQL优化**: 支持数组、JSONB、枚举等PostgreSQL特性
- ✅ **事务支持**: 完整的事务管理和回滚机制
- ✅ **接口对齐**: 与Go版本的dbsqlc保持API兼容

## 项目结构

```
io.river4j.internal.database/
├── model/                     # 数据模型 (对应Go的models.go)
│   ├── JobState.java         # 工作状态枚举
│   ├── AttemptError.java     # 错误信息记录
│   ├── RiverJob.java         # 工作任务模型
│   ├── RiverLeader.java      # 领导选举模型
│   └── RiverMigration.java   # 数据库迁移模型
├── dao/                      # 数据访问层 (对应Go的*.sql.go)
│   ├── JobDao.java           # 工作任务DAO
│   ├── LeaderDao.java        # 领导选举DAO
│   └── MigrationDao.java     # 数据库迁移DAO
├── mapper/                   # JDBI映射器
│   ├── *RowMapper.java       # 行映射器
│   ├── *ColumnMapper.java    # 列映射器
│   └── *ArgumentFactory.java # 参数工厂
├── config/                   # 数据库配置
│   └── DatabaseConfig.java  # 数据库配置管理
├── DatabaseManager.java     # 主数据库管理器 (对应Go的Queries)
└── DatabaseTransaction.java # 事务上下文
```

## 核心概念

### 1. 数据模型 (Models)

使用Java21 Records实现不可变数据模型，对应Go的struct：

```java
public record RiverJob(
    long id,
    byte[] args,
    short attempt,
    Instant attemptedAt,
    List<String> attemptedBy,
    Instant createdAt,
    List<AttemptError> errors,
    Instant finalizedAt,
    String kind,
    short maxAttempts,
    byte[] metadata,
    short priority,
    String queue,
    JobState state,
    Instant scheduledAt,
    List<String> tags
) {
    // 构造时验证和业务方法
    public boolean isAvailable() {
        return state == JobState.AVAILABLE && 
               scheduledAt.isBefore(Instant.now());
    }
}
```

### 2. 数据访问对象 (DAOs)

使用JDBI3的SqlObject模式，对应Go的生成代码：

```java
public interface JobDao extends SqlObject {
    @SqlQuery("SELECT * FROM river_job WHERE id = :id")
    Optional<RiverJob> findById(@Bind("id") long id);
    
    @SqlUpdate("UPDATE river_job SET state = :state WHERE id = :id")
    int updateState(@Bind("id") long id, @Bind("state") JobState state);
    
    @SqlBatch("INSERT INTO river_job (...) VALUES (...)")
    int[] insertMany(@BindMethods List<JobInsertParams> jobs);
}
```

### 3. 数据库管理器

统一的数据库访问入口，对应Go的Queries结构体：

```java
DatabaseManager db = new DatabaseManager(jdbcUrl, username, password);

// 基础操作
List<RiverJob> jobs = db.jobs().findByKind("EmailJob");
db.leaders().insertOrUpdate(now, expiresAt, leaderID, name);

// 事务操作
db.inTransaction(tx -> {
    tx.jobs().insert(...);
    tx.leaders().deleteExpired(now);
});
```

## Go vs Java 对应关系

| Go (dbsqlc) | Java (River4J) | 说明 |
|-------------|----------------|------|
| `models.go` | `model/*.java` | 数据模型定义 |
| `*.sql.go` | `dao/*.java` | 查询方法实现 |
| `Queries` struct | `DatabaseManager` | 主访问接口 |
| `DBTX` interface | `DatabaseTransaction` | 事务抽象 |
| `context.Context` | 事务范围 | 上下文管理 |
| PostgreSQL数组 | `List<T>` + 自定义映射器 | 数组类型处理 |
| JSONB | Jackson + `byte[]` | JSON序列化 |

## 功能对比

| 功能 | Go实现 | Java实现 | 状态 |
|------|--------|----------|------|
| 基础CRUD | ✅ sqlc生成 | ✅ JDBI SqlObject | 完成 |
| 批量操作 | ✅ 数组参数 | ✅ @SqlBatch | 完成 |
| 事务支持 | ✅ pgx事务 | ✅ JDBI事务 | 完成 |
| 连接池 | 手动管理 | ✅ HikariCP | 增强 |
| 类型安全 | ✅ 编译检查 | ✅ Records+枚举 | 完成 |
| PostgreSQL数组 | ✅ 原生支持 | ✅ 自定义映射器 | 完成 |
| JSONB处理 | ✅ 原生支持 | ✅ Jackson集成 | 完成 |
| 领导选举 | ✅ 完整实现 | ✅ 完整实现 | 完成 |
| 数据库迁移 | ✅ 版本管理 | ✅ 版本管理 | 完成 |

## 使用指南

### 1. 添加依赖

```xml
<dependencies>
    <dependency>
        <groupId>org.jdbi</groupId>
        <artifactId>jdbi3-core</artifactId>
        <version>3.49.5</version>
    </dependency>
    <dependency>
        <groupId>org.jdbi</groupId>
        <artifactId>jdbi3-sqlobject</artifactId>
        <version>3.49.5</version>
    </dependency>
    <dependency>
        <groupId>org.jdbi</groupId>
        <artifactId>jdbi3-postgres</artifactId>
        <version>3.49.5</version>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <version>42.7.4</version>
    </dependency>
    <dependency>
        <groupId>com.zaxxer</groupId>
        <artifactId>HikariCP</artifactId>
        <version>5.1.0</version>
    </dependency>
</dependencies>
```

### 2. 基础使用

```java
// 初始化
DatabaseManager db = new DatabaseManager(
    "jdbc:postgresql://localhost/river_dev",
    "username",
    "password"
);

// 基础操作
Optional<RiverJob> job = db.jobs().findById(123L);
List<RiverJob> available = db.jobs().getAvailable(
    List.of("default"), 10, Instant.now(), "worker-1"
);

// 事务操作
db.inTransaction(tx -> {
    tx.jobs().updateState(jobId, JobState.COMPLETED);
    tx.jobs().insert(...);
});
```

### 3. 迁移指南

从Go版本迁移的主要步骤：

1. **替换数据访问**: `dbsqlc.Queries` → `DatabaseManager`
2. **更新模型使用**: Go structs → Java Records
3. **适配查询方法**: Go方法名 → Java方法名 (基本一致)
4. **事务处理**: `pgx.Tx` → `DatabaseTransaction`
5. **错误处理**: Go errors → Java exceptions

## 性能优化

1. **连接池配置**: HikariCP优化的PostgreSQL连接池
2. **预编译语句缓存**: 自动缓存和重用SQL语句
3. **批量操作**: 使用`@SqlBatch`进行批量插入/更新
4. **索引友好查询**: 保持与Go版本相同的查询模式
5. **事务优化**: 最小化事务范围，避免长事务

## 测试支持

```java
@Test
void testJobOperations() {
    DatabaseManager db = createTestDatabase();
    
    // 插入测试数据
    int inserted = db.jobs().insert("TestJob", ...);
    assertEquals(1, inserted);
    
    // 验证查询
    List<RiverJob> jobs = db.jobs().findByKind("TestJob");
    assertEquals(1, jobs.size());
    
    // 测试事务
    db.inTransaction(tx -> {
        tx.jobs().updateState(jobs.get(0).id(), JobState.COMPLETED);
    });
}
```

## 迁移完成状态

✅ **已完成**:
- 所有核心模型类 (RiverJob, RiverLeader, RiverMigration)
- 完整的DAO接口 (JobDao, LeaderDao, MigrationDao)
- JDBI配置和映射器
- 事务支持
- 基础测试覆盖

🔄 **下一步**:
- 集成测试 (需要PostgreSQL环境)
- 性能基准测试
- 与river4j核心模块集成
- 生产环境配置优化

这个实现提供了与Go版本功能完全对等的Java版本，同时利用了Java21和JDBI3的现代特性，为River4J项目提供了强大的数据库基础设施。