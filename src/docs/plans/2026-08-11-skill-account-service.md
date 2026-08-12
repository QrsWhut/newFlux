# Skill 账号服务实施方案

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为小艺 Skill 提供一个 HTTPS JSON-RPC 2.0 账号服务入口，在单一 URL 中实现账号授权、业务 Token 刷新和账号解绑。

**Architecture:** 在现有 Spring WebFlux 应用中新增 `POST /api/skill/account/callback`。Controller 只接收和校验 JSON-RPC 基本结构，并将 `method` 分发给 `SkillAccountService`；Service 负责授权码换取华为用户信息、业务账号绑定、业务 Token 签发/刷新/撤销。华为 OAuth 调用以独立 Client 封装，业务账号及 Token 的持久化以 DAL 接口隔离，避免将外部协议、业务规则和数据库细节耦合。

**Tech Stack:** Java、Spring Boot WebFlux、FastJSON、Lombok、WebClient、JUnit 5、Mockito；华为 OAuth 授权码模式。

---

## 实施范围与前置确认

本期只实现平台回调协议和三个业务方法，暂不实现 AKSK/Header/Query 鉴权的真实验签；但 Controller 必须预留一个统一的 `GatewayRequestAuthenticator` 扩展点。上线前必须启用平台选定的鉴权方式，不能以“暂不验签”的实现上线。

开始编码前，由业务方确认以下事项：

1. 真实 `clientId`、华为 OAuth `clientSecret`、华为 OAuth Token URL、用户信息 URL、申请完成的手机号权限。
2. 业务用户唯一标识优先级：建议 `unionId` 为主键，`openId` 为应用内补充标识；手机号只作为用户资料，不作为唯一键。
3. 现有业务系统中用于创建/查询用户和持久化 Token 的服务或数据库表；当前源码目录未发现 DAL/Mapper，因此不可用内存 Map 替代生产持久化。
4. `loginToken` 的形式（随机不透明 Token 或 JWT）、有效期（文档建议 7 天）、`loginRefreshToken` 有效期（文档建议半年）、解绑后的数据保留策略。
5. 小艺对失败响应的最终联调约定。当前文档展示 `result` 与 `error` 同时存在，和标准 JSON-RPC 2.0 不一致；实现前需以联调样例确认响应结构。

## 协议约束

- 对外地址：`POST /api/skill/account/callback`。实际公网 HTTPS URL 在小艺开放平台的“服务地址”中配置。
- Content-Type：`application/json`。
- 请求体：JSON-RPC 2.0。只允许 `authorize`、`refreshToken`、`deauthorize` 三种 `method`。
- 成功响应必须回显请求 `id`；未知 method、请求格式错误、业务失败均返回平台约定的 JSON-RPC 错误结构。
- `authorize` 接收 `authCode` 和 `clientId`；`refreshToken`、`deauthorize` 接收 `loginRefreshToken`。
- 不记录授权码、华为 accessToken、业务 Token、手机号明文或 clientSecret；日志仅记录脱敏后的请求 ID、clientId 和业务用户 ID。

### Task 1: 确认契约与配置边界

**Files:**
- Create: `main/java/com/example/chat/config/SkillAccountProperties.java`
- Modify: `main/resources/application.yml`
- Verify: 在应用启动时可成功绑定配置；敏感字段仅通过环境变量注入。

**Risk Level:** Standard lightweight verification
**Why:** 此任务只建立配置边界，不包含业务状态变更。

**Step 1: Set verification path**

- Skip strict TDD; use lightweight verification.

**Step 2: Prepare verification**

- 确认项目启动类已启用 `@ConfigurationPropertiesScan`；若没有，在 `main/java/com/example/chat/ChatApplication.java` 添加该注解。

**Step 3: Write minimal implementation**

- 新建 `SkillAccountProperties`，前缀为 `skill.account`，包含：`clientId`、`huaweiTokenUrl`、`huaweiUserInfoUrl`、`loginTokenTtl`、`loginRefreshTokenTtl`、`gatewayAuthenticationMode`。
- `clientSecret` 不写入 `application.yml`；通过 `${SKILL_ACCOUNT_HUAWEI_CLIENT_SECRET}` 注入生产环境。
- 在 `application.yml` 增加仅包含非敏感默认项的 `skill.account` 节点，并使用环境变量占位符引用密钥。

**Step 4: Run chosen verification**

Run: `mvn test`

Expected: PASS；应用上下文可加载配置绑定类。

**Step 5: Record a local checkpoint**

- 记录新增配置项及生产环境必须提供的环境变量名称。

### Task 2: 建立 JSON-RPC 请求与响应对象

**Files:**
- Create: `main/java/com/example/chat/web/vo/account/JsonRpcRequestVO.java`
- Create: `main/java/com/example/chat/web/vo/account/JsonRpcMessageVO.java`
- Create: `main/java/com/example/chat/web/vo/account/JsonRpcDataVO.java`
- Create: `main/java/com/example/chat/web/vo/account/JsonRpcResponseVO.java`
- Create: `main/java/com/example/chat/web/vo/account/JsonRpcErrorVO.java`
- Create: `main/java/com/example/chat/common/enums/SkillAccountMethodEnum.java`
- Verify: `test/java/com/example/chat/web/vo/account/JsonRpcRequestVOTest.java`

**Risk Level:** High-risk TDD
**Why:** 外部协议字段层级固定，字段解析错误会导致平台无法调用全部功能。

**Step 1: Set verification path**

- 先编写反序列化与校验测试，覆盖三个合法 method、未知 method、缺少 `jsonrpc`、缺少 `id`、空 `parts`、`parts[0].kind` 非 `data`。

**Step 2: Prepare verification**

- 使用 FastJSON 将文档中的 `authorize`、`refreshToken`、`deauthorize` 样例反序列化。
- 断言 `authorize` 能提取 `authCode` 与 `clientId`，其余两个 method 能提取 `loginRefreshToken`。

**Step 3: Write minimal implementation**

- 所有 JSON 字段使用 lowerCamelCase，并在 VO 中使用包装类型。
- `SkillAccountMethodEnum` 只定义 `AUTHORIZE`、`REFRESH_TOKEN`、`DEAUTHORIZE` 三项；每个枚举字段使用 `private final`，并写中文 Javadoc。
- 在 `JsonRpcRequestVO` 提供受保护的提取方法，统一验证 `params.message.parts`；禁止 Controller 直接遍历多层集合。

**Step 4: Run chosen verification**

Run: `mvn -Dtest=JsonRpcRequestVOTest test`

Expected: 先 FAIL（对象未实现），实现后 PASS。

**Step 5: Record a local checkpoint**

- 记录已锁定的外部 JSON 字段和错误码映射草案。

### Task 3: 实现单一回调 Controller 与 method 分发

**Files:**
- Create: `main/java/com/example/chat/web/controller/SkillAccountController.java`
- Create: `main/java/com/example/chat/service/interf/SkillAccountService.java`
- Create: `main/java/com/example/chat/service/interf/GatewayRequestAuthenticator.java`
- Create: `test/java/com/example/chat/web/controller/SkillAccountControllerTest.java`
- Verify: `test/java/com/example/chat/web/controller/SkillAccountControllerTest.java`

**Risk Level:** High-risk TDD
**Why:** 这是平台调用入口，必须确保所有 method 的分发、响应 ID 和错误分支稳定。

**Step 1: Set verification path**

- 先写 Controller 单元测试，Mock `GatewayRequestAuthenticator` 与 `SkillAccountService`。

**Step 2: Prepare verification**

- 覆盖：三个合法 method 各调用一次对应 Service 方法；未知 method 返回“方法不支持”；鉴权失败时不调用 Service；响应 `id` 与请求一致。

**Step 3: Write minimal implementation**

- 声明 `@RestController` 和 `@RequestMapping("/api/skill/account")`，以 `@PostMapping("/callback")` 提供唯一入口。
- Controller 的执行顺序必须为：HTTP Header 鉴权扩展点 → JSON-RPC 基本字段校验 → `method` 枚举转换 → Service 分发 → 响应组装。
- `GatewayRequestAuthenticator` 本期只提供明确的开发环境实现；生产 Profile 未配置真实鉴权实现时必须拒绝启动或拒绝请求，禁止静默放行。
- Controller 不得调用 DAO、不得执行华为 OAuth 请求、不得生成 Token。

**Step 4: Run chosen verification**

Run: `mvn -Dtest=SkillAccountControllerTest test`

Expected: 先 FAIL，完成分发后 PASS。

**Step 5: Record a local checkpoint**

- 记录平台服务地址为 `/api/skill/account/callback`，后续反向代理只转发该路径。

### Task 4: 封装华为 OAuth 授权码换取用户身份

**Files:**
- Create: `main/java/com/example/chat/integration/client/HuaweiAccountClient.java`
- Create: `main/java/com/example/chat/integration/client/WebClientHuaweiAccountClient.java`
- Create: `main/java/com/example/chat/common/dto/account/HuaweiTokenDTO.java`
- Create: `main/java/com/example/chat/common/dto/account/HuaweiUserInfoDTO.java`
- Create: `main/java/com/example/chat/common/exception/HuaweiAccountException.java`
- Create: `test/java/com/example/chat/integration/client/WebClientHuaweiAccountClientTest.java`
- Verify: `test/java/com/example/chat/integration/client/WebClientHuaweiAccountClientTest.java`

**Risk Level:** High-risk TDD
**Why:** 该调用决定 authCode 是否有效，且涉及 secret、手机号权限和外部 HTTP 错误处理。

**Step 1: Set verification path**

- 先通过 `ExchangeFunction` Mock WebClient，构造 Token 接口成功、授权码无效、超时、返回缺失 accessToken 四种场景。

**Step 2: Prepare verification**

- 断言 Token 请求为 `application/x-www-form-urlencoded`，包含 `grant_type=authorization_code`、`code`、`client_id`、`client_secret`。
- 断言用户信息请求只在成功取得华为 accessToken 后发送，并将手机号、openId、unionId 映射到 DTO。

**Step 3: Write minimal implementation**

- 用 WebClient 调用华为 Token 接口；不得把 clientSecret 放入 URL、日志或异常消息。
- 成功换取 Token 后调用华为用户信息接口；没有 `unionId/openId` 或没有手机号权限时，抛出可识别的 `HuaweiAccountException`。
- 为每个远程调用设置连接与响应超时；只捕获具体的 WebClient/超时异常并转换为领域异常。

**Step 4: Run chosen verification**

Run: `mvn -Dtest=WebClientHuaweiAccountClientTest test`

Expected: 先 FAIL，外部请求构造及错误转换完成后 PASS。

**Step 5: Record a local checkpoint**

- 记录华为侧错误码、HTTP 状态和对外 JSON-RPC 错误码的映射。

### Task 5: 接入业务账号绑定与 Token 持久化

**Files:**
- Create: `main/java/com/example/chat/common/dto/account/SkillAccountBindingDTO.java`
- Create: `main/java/com/example/chat/common/dto/account/IssuedLoginTokenDTO.java`
- Create: `main/java/com/example/chat/service/interf/SkillAccountBindingService.java`
- Create: `main/java/com/example/chat/service/implement/SkillAccountBindingServiceImpl.java`
- Create: `main/java/com/example/chat/dal/dao/SkillAccountBindingDAO.java`
- Create: `main/java/com/example/chat/dal/model/SkillAccountBindingDO.java`
- Create: `main/java/com/example/chat/dal/model/SkillAccountTokenDO.java`
- Create: `test/java/com/example/chat/service/implement/SkillAccountBindingServiceImplTest.java`
- Verify: `test/java/com/example/chat/service/implement/SkillAccountBindingServiceImplTest.java`

**Risk Level:** High-risk TDD
**Why:** 用户映射、Token 生命周期和解绑是安全状态机，错误会造成串号或凭证仍然有效。

**Step 1: Set verification path**

- 先写 Service 测试，Mock DAO 和既有用户服务；没有既有用户服务时，先由业务方确定并新增对应 Service 接口，不能由 Controller 直接访问数据库。

**Step 2: Prepare verification**

- 覆盖：首次绑定、已绑定用户再次授权、同一 unionId 绑定不同手机号、无效 refreshToken、过期 refreshToken、重复解绑、解绑后刷新失败。

**Step 3: Write minimal implementation**

- 使用 `unionId` 作为华为用户主关联键，`clientId + unionId` 建立唯一绑定；手机号字段加密或脱敏存储。
- 生成高熵、不可预测的随机 `loginToken` 和 `loginRefreshToken`；数据库只保存 Token 的 SHA-256 摘要及过期时间，不保存明文。
- `authorize`：创建或更新绑定，撤销旧有效 Token，签发一对新 Token。
- `refreshToken`：校验摘要、状态和过期时间，仅签发新 `loginToken`；除非小艺书面确认支持，否则不轮换 refreshToken。
- `deauthorize`：在事务中停用绑定并撤销全部关联 Token；重复调用返回成功，确保幂等。
- 涉及绑定与 Token 多表更新的方法使用 `@Transactional`；纯查询不可加事务。

**Step 4: Run chosen verification**

Run: `mvn -Dtest=SkillAccountBindingServiceImplTest test`

Expected: 先 FAIL，状态转换实现后 PASS。

**Step 5: Record a local checkpoint**

- 记录唯一索引、Token 摘要算法、有效期和解绑后的状态定义。

### Task 6: 实现三个 JSON-RPC 业务方法与统一错误响应

**Files:**
- Create: `main/java/com/example/chat/service/implement/SkillAccountServiceImpl.java`
- Create: `main/java/com/example/chat/common/enums/SkillAccountErrorCodeEnum.java`
- Create: `test/java/com/example/chat/service/implement/SkillAccountServiceImplTest.java`
- Modify: `main/java/com/example/chat/web/vo/account/JsonRpcResponseVO.java`
- Verify: `test/java/com/example/chat/service/implement/SkillAccountServiceImplTest.java`

**Risk Level:** High-risk TDD
**Why:** 这里组合外部身份、内部绑定和平台响应，是全部业务分支的集中点。

**Step 1: Set verification path**

- 先写 Service 测试，Mock Huawei Client 和 Binding Service。

**Step 2: Prepare verification**

- 覆盖：授权成功、华为 authCode 无效、手机号权限缺失、刷新成功、刷新 Token 无效/过期、解绑成功、未知错误不泄露内部异常信息。

**Step 3: Write minimal implementation**

- `authorize` 依次执行：读取 `authCode/clientId` → 校验 clientId → 调用华为换 Token 和取用户信息 → 调用绑定服务签发业务 Token → 构造文档要求的结果。
- `refreshToken` 与 `deauthorize` 只调用绑定服务，不得重复调用华为 OAuth。
- 每一条错误码配置明确、稳定、无敏感信息的 `message`；对外错误提示与内部日志内容分离。

**Step 4: Run chosen verification**

Run: `mvn -Dtest=SkillAccountServiceImplTest test`

Expected: 先 FAIL，业务分支和错误转换完成后 PASS。

**Step 5: Record a local checkpoint**

- 输出三种 method 的请求与响应样例，供小艺联调使用。

### Task 7: 建立端到端协议回归与上线前配置检查

**Files:**
- Create: `test/java/com/example/chat/web/controller/SkillAccountContractTest.java`
- Create: `docs/skill-account-service-api.md`
- Modify: `main/resources/application.yml`
- Verify: `test/java/com/example/chat/web/controller/SkillAccountContractTest.java`

**Risk Level:** High-risk TDD
**Why:** 外部平台回调最容易因字段层级、Content-Type、响应 ID 或配置遗漏导致联调失败。

**Step 1: Set verification path**

- 用 Mock Web Server 或 MockWebServer 模拟华为 Token/用户信息接口，以真实 HTTP 请求调用本地 Controller。

**Step 2: Prepare verification**

- 将文档中的三个 JSON-RPC 请求样例作为固定测试夹具；断言返回 `jsonrpc=2.0`、原始 `id`、约定结果字段、无敏感数据泄露。

**Step 3: Write minimal implementation**

- 在 `docs/skill-account-service-api.md` 写明：公网 URL、请求 Header、三个 method 样例、错误码、环境变量、联调检查项。
- 在配置中提供开发环境模拟实现开关，但生产环境启动时必须校验 clientId、secret、OAuth URL、持久化实现和 Gateway 鉴权实现完整存在。

**Step 4: Run chosen verification**

Run: `mvn test`

Expected: PASS；全部现有测试与新增协议测试通过。

**Step 5: Record a local checkpoint**

- 记录小艺开放平台需要配置的服务地址、clientId、鉴权模式和隐私说明。

## 上线验收清单

1. 小艺开放平台已配置唯一 HTTPS 服务地址，并关联正确的 clientId。
2. 华为侧已批准手机号权限；测试 authCode 可以换取 accessToken，并返回用户身份信息。
3. `authorize` 成功返回一对业务 Token，且 Token 不出现在服务日志和数据库明文字段。
4. 在 loginToken 距离过期两天以内，`refreshToken` 可返回新 loginToken。
5. `deauthorize` 后，旧 loginToken 与 loginRefreshToken 均不可再使用；重复解绑不报错。
6. 未配置或验签失败的 Gateway 请求不能执行任何业务方法。
7. 联调使用平台实际请求验证 JSON-RPC 响应格式，特别确认解绑成功响应以及 result/error 的最终结构。
