---
description: Java + Spring Boot 后端编码规范。AI 在编写、修改、审查后端代码时必须加载本规则。
globs: backend/**/*.java
alwaysApply: false
---

# Java 后端编码规范

> 适用范围：`backend/` 目录下所有 Java 代码
> 技术栈：Java 17 + Spring Boot 3.2 + MyBatis-Plus 3.5

---

## 1. 分层架构（强制）

```
Controller  ←  HTTP 入口，仅负责参数校验、调用 Service、返回响应
   ↓
Service     ←  业务逻辑、事务边界、领域规则
   ↓
Mapper      ←  数据访问，纯 SQL 映射
   ↓
Domain      ←  实体（与表 1:1）+ 值对象
```

**红线**：
- ❌ Controller 直接调用 Mapper（必须经 Service）
- ❌ Service 返回 Entity 给 Controller（必须转 DTO/VO）
- ❌ Mapper 写业务逻辑（仅 SQL 映射）
- ❌ Domain 依赖 Spring（保持 POJO）

---

## 2. 包与命名

### 2.1 包结构

```
com.company.booking
├── controller       # XxxController
├── service          # XxxService（接口）/ impl/XxxServiceImpl
├── mapper           # XxxMapper（接口，配 XxxMapper.xml）
├── domain
│   ├── entity       # XxxEntity（与表 1:1）
│   ├── enums        # XxxStatus / XxxType
│   └── vo           # 内部值对象
├── dto
│   ├── request      # XxxRequest
│   └── response     # XxxResponse
├── config           # XxxConfig
├── common
│   ├── exception    # 业务异常
│   ├── result       # 统一响应封装 R<T>
│   ├── interceptor  # 拦截器
│   └── util         # 工具类
└── BookingApplication.java
```

### 2.2 命名约定

| 类型 | 规则 | 示例 |
|---|---|---|
| Controller | `XxxController`，路径用 kebab-case | `BookingController` → `/api/bookings` |
| Service 接口 | `XxxService` | `BookingService` |
| Service 实现 | `XxxServiceImpl` | `BookingServiceImpl` |
| Mapper | `XxxMapper` | `BookingMapper` |
| Entity | `XxxEntity` | `BookingEntity` |
| 请求 DTO | `XxxRequest` | `CreateBookingRequest` |
| 响应 DTO | `XxxResponse` | `BookingDetailResponse` |
| 异常 | `XxxException`，继承 `BizException` | `BookingConflictException` |
| 枚举 | 全大写下划线 | `BookingStatus.CONFIRMED` |
| 常量 | `static final`，全大写 | `MAX_BOOKING_DAYS` |
| 变量/方法 | camelCase | `bookingId` / `createBooking()` |

---

## 3. Controller 规范

```java
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Validated
public class BookingController {

    private final BookingService bookingService;

    /**
     * 创建预订
     * @see docs/prd.md US-02
     */
    @PostMapping
    public R<BookingDetailResponse> create(
            @RequestHeader("X-Request-Id") @NotBlank String requestId,
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody CreateBookingRequest request) {
        return R.ok(bookingService.create(user, requestId, request));
    }
}
```

**约束**：
- 每个公开方法必须有 Javadoc，标注关联的 PRD User Story
- 入参用 `@Valid` + Bean Validation 注解
- 幂等接口必须接收 `X-Request-Id` Header
- 返回统一封装 `R<T>`（成功/失败/code/msg）
- **禁止**在 Controller 处理事务（`@Transactional` 只放 Service）

---

## 4. Service 规范

### 4.1 事务

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final BookingMapper bookingMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final IdempotencyService idempotencyService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingDetailResponse create(CurrentUser user, String requestId, CreateBookingRequest req) {
        // 1. 幂等校验
        return idempotencyService.executeOnce(requestId, user.id(), "POST /bookings",
                () -> doCreate(user, req));
    }

    private BookingDetailResponse doCreate(CurrentUser user, CreateBookingRequest req) {
        // 2. 业务校验（独立方法）
        validateTimeRange(req);
        validateCapacity(req);

        // 3. 写入 booking + time_slots（事务原子）
        BookingEntity booking = buildBooking(user, req);
        bookingMapper.insert(booking);
        insertTimeSlots(booking);  // 唯一约束冲突 → DuplicateKeyException

        // 4. 触发后置事件（通知）
        applicationEventPublisher.publishEvent(new BookingCreatedEvent(booking.getId()));

        return toDetailResponse(booking);
    }
}
```

**约束**：
- `@Transactional` 必须指定 `rollbackFor = Exception.class`
- 事务方法保持精简，IO/远程调用尽量放事务外
- 公开方法做"编排"，私有方法做"细节"
- 异常用业务异常类型（`BookingConflictException`），不要让 `RuntimeException` 透传

### 4.2 异常处理

```java
// 自定义业务异常
public class BookingConflictException extends BizException {
    public BookingConflictException(String message) {
        super("BOOKING_CONFLICT", message);
    }
}

// 全局异常处理器
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        log.warn("biz error: code={}, msg={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public R<Void> handleDuplicate(DuplicateKeyException e) {
        log.warn("duplicate key: {}", e.getMessage());
        return R.fail("BOOKING_CONFLICT", "该时段已被预订");
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleAny(Exception e) {
        log.error("unexpected error", e);
        return R.fail("INTERNAL_ERROR", "系统繁忙");
    }
}
```

---

## 5. Mapper 规范

```java
@Mapper
public interface BookingMapper extends BaseMapper<BookingEntity> {

    /** 查询会议室某时间范围内的有效预订 */
    @Select("""
        SELECT * FROM bookings
        WHERE room_id = #{roomId}
          AND status IN ('PENDING', 'CONFIRMED')
          AND deleted_at IS NULL
          AND start_time < #{end}
          AND end_time > #{start}
        """)
    List<BookingEntity> findOverlapping(@Param("roomId") Long roomId,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);
}
```

**约束**：
- 100% 使用 `#{}` 参数占位，**禁止 `${}` 拼接用户输入**
- 动态字段（如 ORDER BY 列）必须先做白名单校验
- 复杂 SQL 用 XML 文件，避免 Java 注解中拼接字符串
- Mapper 方法名要见名知意（`findOverlapping` 而非 `query1`）

---

## 6. DTO / Entity 规范

### 6.1 请求 DTO（带校验）

```java
@Data
public class CreateBookingRequest {

    @NotNull(message = "会议室 ID 不能为空")
    private Long roomId;

    @NotBlank(message = "会议主题不能为空")
    @Length(max = 255)
    private String subject;

    @NotNull
    @Future(message = "预订时间不能是过去")
    private LocalDateTime startTime;

    @NotNull
    private LocalDateTime endTime;

    @NotNull
    @Min(1)
    private Integer attendeeCount;

    private List<Long> attendeeIds;

    private Visibility visibility = Visibility.PUBLIC;  // 默认全员可见
}
```

### 6.2 Entity（与表 1:1）

```java
@Data
@TableName("bookings")
public class BookingEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private Long bookerId;
    private Long principalId;
    private String subject;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BookingStatus status;
    @TableLogic  // MyBatis-Plus 软删除
    private LocalDateTime deletedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

**约束**：
- 字段名严格匹配 `schema.sql`
- 时间一律 `LocalDateTime`，禁止 `Date`
- 枚举字段配 `MybatisEnumTypeHandler` 自动转换
- **禁止**在 Entity 上加业务方法

---

## 7. 单元测试规范

### 7.1 命名

```
src/test/java/com/company/booking/service/BookingServiceImplTest.java
```

测试类与被测类同包同名 + `Test` 后缀。

### 7.2 测试结构（Given-When-Then）

```java
@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock private BookingMapper bookingMapper;
    @Mock private TimeSlotMapper timeSlotMapper;
    @InjectMocks private BookingServiceImpl service;

    @Test
    @DisplayName("创建预订：时段冲突时抛 BookingConflictException")
    void create_whenSlotConflict_throwsConflict() {
        // Given
        var req = TestFixtures.validCreateRequest();
        when(timeSlotMapper.insertBatch(any())).thenThrow(DuplicateKeyException.class);

        // When & Then
        assertThatThrownBy(() -> service.create(currentUser(), "req-1", req))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("已被预订");
    }
}
```

### 7.3 覆盖要求

| 类型 | 最低覆盖 |
|---|---|
| Service 公开方法 | 100% 行覆盖 + 关键分支覆盖 |
| Controller | 集成测试覆盖 happy path + 主要错误码 |
| Mapper | 用 Testcontainers + 真实 MySQL 验证 SQL |

---

## 8. 日志规范

```java
log.info("booking created: id={}, room={}, user={}", booking.getId(), booking.getRoomId(), user.id());
log.warn("booking conflict: room={}, start={}", req.getRoomId(), req.getStartTime());
log.error("notification send failed", exception);
```

**约束**：
- 用 SLF4J，禁止 `System.out.println`
- 用占位符 `{}`，禁止字符串拼接（性能 + 安全）
- ERROR 必须带异常对象（栈信息）
- 不打敏感信息（密码、Token、完整身份证）

---

## 9. 安全红线

| # | 红线 | 检查工具 |
|---|---|---|
| 1 | SQL 必须参数化（`#{}`） | 人工 review |
| 2 | 接口必须鉴权（`@PreAuthorize` 或拦截器） | Spring Security |
| 3 | 越权检查：本人/上级/管理员逻辑必须在 Service 校验 | 人工 review |
| 4 | 密码必须 BCrypt 加密 | `PasswordEncoder` |
| 5 | 敏感配置走 `application-{env}.yml` + 环境变量 | - |

---

## 10. AI 编码 Checklist（Java 任务专用）

写完代码提交前，AI 自检：

- [ ] 类放在了正确的分层包下吗？
- [ ] Controller 是否只做参数校验和编排？
- [ ] Service 是否标注了正确的 `@Transactional`？
- [ ] Mapper 是否 100% 参数化？
- [ ] 是否有对应的单元测试？
- [ ] 异常是否分类（业务异常 vs 系统异常）？
- [ ] 日志是否用占位符 + 不含敏感信息？
- [ ] 是否新增了未声明的依赖？
- [ ] 是否动了用户没要求改的代码？
