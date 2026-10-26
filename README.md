# Nothing API Router

Nothing API Router 是一个基于 Java、Spring Boot 和 MyBatis 实现的大模型 API 中转站。

调用方使用中转站签发的 API Key，通过统一接口访问 DeepSeek 和火山方舟。中转站负责鉴权、渠道选择、请求转发、用量归一化、费用计算和资金记录，并提供会话绑定、前缀历史和显式缓存功能。

## 1. 功能说明

| 模块         | 当前实现                                           |
| ------------ | -------------------------------------------------- |
| 统一接口     | 提供 `/v1/chat/completions`，支持文本多轮对话      |
| 多平台接入   | 接入 DeepSeek 和火山方舟                           |
| 非流式调用   | 返回完整的聊天响应                                 |
| SSE 流式调用 | 接收上游事件后向客户端逐步转发，并提取最终用量     |
| API Key 管理 | 管理员创建用户、签发和吊销中转站 Key               |
| 渠道配置     | 配置上游地址、模型、模型别名、输出限制和价格       |
| 用量归一化   | 统一输入、缓存输入和输出 token 数量                |
| 费用计算     | 使用整数金额，根据实际用量和价格快照计算费用       |
| 资金控制     | 调用前预留预算，调用后结算，记录资金流水           |
| 会话绑定     | 使用 `X-Session-Id` 优先复用之前成功的渠道         |
| 前缀历史     | 根据稳定前缀及成功调用记录选择渠道                 |
| 显式缓存     | 通过火山方舟创建缓存，并在非流式聊天中复用         |
| 缓存统计     | 按 API Key、渠道和模型别名统计缓存命中与费用       |
| 对账         | 提供 SQL 对账脚本                                  |
| 异常账单处理 | 支持查询未完成账单、重试结算和人工释放预留资金     |
| 滞留资金巡检 | 提供可配置的定时巡检和有限的自动结算重试，默认关闭 |

## 2. 项目结构

```text
Nothing API Router
├── src/main/java/com/lingrui/router
│   ├── controller      接收 HTTP 请求、鉴权入口和返回响应
│   ├── service         业务接口及实现
│   ├── client          DeepSeek、火山方舟上游调用
│   ├── config          配置绑定
│   ├── dto             接收请求参数
│   ├── vo              响应对象及上游用量对象
│   ├── entity          数据库实体
│   ├── mapper          数据库操作接口
│   ├── task            定时巡检任务
│   ├── exception       异常处理
│   └── util            工具类
├── src/main/resources
│   ├── application.yaml
│   └── mapper          MyBatis SQL 映射文件
├── src/test            测试代码
├── sql                 初始化、迁移及对账脚本
├── .env.example        环境变量示例
├── start.bat           Windows 启动入口
├── start.ps1           检查、构建、初始化和启动逻辑
└── STARTUP.md          启动说明
```

普通聊天的主要调用流程：

```text
调用方
  → ChatCompletionController
  → API Key 鉴权
  → RouterServiceImpl 选择渠道
  → 预留预算、创建调用记录
  → DeepseekClientImpl / ArkClientImpl 请求上游
  → 用量归一化
  → 按价格快照结算、记录流水
  → 更新会话绑定和前缀历史
```

### 3.1 环境准备

1. 安装 JDK 17 或更新版本。
2. 配置 `JAVA_HOME`，或将 `java` 和 `javac` 加入 PATH。
3. 安装并启动 MySQL，将 `mysql.exe` 加入 PATH。
4. 准备 DeepSeek 和火山方舟 API Key。
5. 确认上游账号具有所配置模型的调用权限。使用显式缓存时，还需具备相应缓存功能权限。

启动脚本不会自动安装 JDK、MySQL，也不会启动 MySQL 服务。

### 3.2 配置环境变量

将 `.env.example` 复制为 `.env`，填写：

```dotenv
DB_URL=jdbc:mysql://localhost:3306/nothing_router?characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=你的数据库密码

ROUTER_ADMIN_KEY=自行设置的管理员密钥

DEEPSEEK_API_KEY=你的DeepSeek密钥
ARK_API_KEY=你的火山方舟密钥
```

### 3.3 一键启动

在 Windows 下双击 `start.bat`，或执行：

```powershell
.\start.bat
```

脚本会：

1. 检查必要配置和工具。
2. 使用 Maven Wrapper 打包项目。
3. 在空库中按顺序执行编号为 `001` 至 `016` 的 SQL。
4. 对已有数据库进行必要的结构检查。
5. 启动 Spring Boot 应用。

默认地址：

```text
http://localhost:8080
```

看到 `Started NothingApiRouterApplication` 日志后，才表示应用已启动成功。

仅检查配置和工具：

```powershell
.\start.bat -CheckOnly
```

已有数据库不会自动重复执行全部迁移。如果数据库仅初始化了一部分，需要核对结构并补执行缺失的 SQL。

## 4. 平台、模型和渠道配置

当前配置：

| 平台     | 渠道标识   | 调用方使用的模型别名 | 配置中的上游模型               |
| -------- | ---------- | -------------------- | ------------------------------ |
| DeepSeek | `deepseek` | `study-chat`         | `deepseek-flash`               |
| 火山方舟 | `ark`      | `doubao-chat`        | `doubao-seed-2-1-turbo-260628` |

## 5. 首次使用：创建用户、钱包和 Key

以下请求中的 `{userId}` 应替换为创建用户后返回的实际编号。

管理接口统一添加：

```http
Authorization: Bearer <ROUTER_ADMIN_KEY>
Content-Type: application/json
```

### 5.1 创建用户

```http
POST /api/admin/users
{
  "username": "demo-user"
}
```

### 5.2 初始化钱包

```http
PUT /api/admin/users/{userId}/wallet
```

不需要请求体。已有钱包重复初始化不会清空余额。

### 5.3 增加测试余额

```http
POST /api/admin/users/{userId}/wallet/recharges
{
  "businessId": "demo-recharge-001",
  "amountUnits": 10000000
}
```

金额单位为微元。

### 5.4 签发聊天 Key

```http
POST /api/admin/users/{userId}/api-keys
{
  "keyName": "demo-chat-key"
}
```

保存响应中的 `apiKey`。后续聊天使用：

```http
Authorization: Bearer <中转站API_KEY>
```

数据库保存密钥哈希，完整密钥在创建时返回。

### 5.5 吊销 Key

```http
POST /api/admin/users/{userId}/api-keys/{apiKeyId}/revoke
```

成功返回 HTTP 204。

## 6. 聊天接口

### 6.1 非流式调用

```http
POST /v1/chat/completions
Authorization: Bearer <中转站API_KEY>
Content-Type: application/json
X-Session-Id: session-demo-001
{
  "model": "study-chat",
  "messages": [
    {
      "role": "system",
      "content": "你是一名Java教学助手，请使用简体中文回答。"
    },
    {
      "role": "user",
      "content": "请简要解释接口和抽象类的区别。"
    }
  ],
  "stream": false,
  "max_tokens": 256
}
```

### 6.2 流式调用

在普通聊天请求中设置：

```json
{
  "model": "doubao-chat",
  "messages": [
    {
      "role": "user",
      "content": "请简要解释Java中的多态。"
    }
  ],
  "stream": true,
  "max_tokens": 256
}
```

响应采用 `text/event-stream`，以 SSE 事件逐步返回，正常结束时转发：

```text
data: [DONE]
```

中间事件的 `usage` 可以为空，最终用量由上游的用量事件提供。

流式响应开始后，不能再通过修改 HTTP 状态码表达后续错误。项目会按当前响应状态处理错误，不会在已经开始的流中切换到其他渠道拼接回答。

## 7. 三种缓存相关机制

### 7.1 会话绑定

通过以下请求头标识会话：

```http
X-Session-Id: session-demo-001
```

绑定关系包含：

```text
API Key 编号 + 模型别名 + 会话编号 → 渠道
```

同一会话后续调用优先选择之前成功的渠道，有助于保持上游缓存复用机会。

成功调用后更新绑定及有效期。会话绑定不存储聊天正文，也不代表上游一定命中缓存。

### 7.2 前缀历史

从请求开头连续的 `system` 消息提取稳定前缀，计算哈希，并记录该前缀之前在哪个渠道调用成功。

当前默认要求前缀达到 256 个 Unicode 码点。前缀不足、没有有效历史或历史过期时，使用其他路由规则。

普通请求的渠道优先顺序：

```text
有效会话绑定
  → 有效前缀历史
  → 配置中的第一个候选渠道
```

前缀历史按 API Key 和模型别名隔离，记录的是成功调用线索，并非上游缓存命中的保证。

### 7.3 显式缓存

显式缓存通过上游接口提前缓存固定前缀，再使用缓存编号发起后续调用。

MySQL 保存的是缓存编号、归属、渠道、上游引用和有效期等信息；实际模型缓存由火山方舟维护。

创建缓存：

```http
POST /api/explicit-caches
Authorization: Bearer <中转站API_KEY>
Content-Type: application/json
{
  "model": "doubao-chat",
  "ttl_seconds": 600,
  "messages": [
    {
      "role": "system",
      "content": "你是一名Java教学助手。回答使用简体中文，先解释概念，再给出简短例子。接口用于声明行为约定，抽象类适合表达共同状态和部分实现。请结合这些规则回答后续问题。"
    }
  ]
}
```

示例用于说明请求结构。实际缓存命中验证应使用足够长、稳定的资料，并满足上游限制。

创建成功返回 HTTP 201，包含：

```json
{
  "cache_id": "服务端生成的缓存编号",
  "expires_at": 1792512087,
  "model": "doubao-chat",
  "status": "READY"
}
```

`expires_at` 为 Unix 秒级时间戳。示例中的编号和时间不能直接用于后续调用。

使用创建响应中的新编号发起聊天：

```http
POST /v1/chat/completions
Authorization: Bearer <创建缓存时使用的中转站API_KEY>
Content-Type: application/json
{
  "model": "doubao-chat",
  "cache_id": "替换为实际返回的cache_id",
  "messages": [
    {
      "role": "user",
      "content": "用三句话解释接口和抽象类的区别。"
    }
  ],
  "stream": false,
  "max_tokens": 256
}
```

复用时：

- 使用创建缓存时相同的中转站 Key 和模型别名。
- 只提交新增消息，不重复发送缓存的 `system` 前缀。
- 指定 `cache_id` 后使用缓存绑定的渠道，不再按普通会话和前缀历史选渠道。
- 当前仅支持非流式显式缓存复用。
- 缓存过期后需要重新创建，并使用新的 `cache_id`。
- 无效缓存不会静默退回普通聊天。
- 每次引用的是原来的固定前缀，不会自动累积前一次回答。

查询缓存记录：

```http
GET /api/explicit-caches/{cacheId}
Authorization: Bearer <创建缓存时使用的中转站API_KEY>
```

当前配置的默认有效期为 600 秒，最大允许有效期为 3600 秒。

## 8. 用量归一化与计费

### 8.1 用量归一化

内部统一使用：

- 输入 token 数。
- 缓存命中的输入 token 数。
- 输出 token 数。

适配 DeepSeek 的缓存命中、未命中字段以及嵌套缓存用量字段，并将火山方舟 Responses 接口的用量转换为统一结构。

缓存命中 token 属于输入 token 的一部分：

```text
普通输入 token = 总输入 token - 缓存输入 token
```

缺失用量与零用量不同，不能直接把未知用量当成零费用结算。

### 8.2 价目表

当前配置为演示价格，不代表上游官方价格：

| 计费类型      | 价格版本               |  普通输入 |  缓存输入 |      输出 |
| ------------- | ---------------------- | --------: | --------: | --------: |
| DeepSeek 聊天 | `demo-v1`              | 1,000,000 | 1,000,000 | 1,000,000 |
| 火山方舟聊天  | `demo-v1`              | 1,000,000 | 1,000,000 | 1,000,000 |
| 显式缓存创建  | `cache-create-demo-v1` | 1,000,000 | 1,000,000 | 1,000,000 |

表中单价单位为“微元 / 百万 token”。

每次请求保存实际使用的价格版本和单价快照，后续修改配置不会改变历史账单的计价依据。

### 8.3 费用计算

```text
费用（微元）=
向上取整(
    (
        普通输入 token × 普通输入单价
        + 缓存输入 token × 缓存输入单价
        + 输出 token × 输出单价
    ) / 1,000,000
)
```

金额使用整数处理，计算中使用大整数避免中间乘法溢出，不使用浮点数记账。

当前三种单价相同，因此缓存命中不会在演示价下产生费用折扣。缓存命中率不能直接作为费用节省比例。

### 8.4 预留与结算

调用上游前先预留预算：

```text
可用余额减少预留金额
冻结金额增加预留金额
```

获得完整用量后结算：

```text
冻结金额减少预留金额
可用余额增加“预留金额 - 实际费用”
```

当前普通聊天每次预留 10,000 微元，显式缓存创建每次预留 100,000 微元，均可配置。

实际费用小于预留金额时退回差额；实际费用较大时尝试补扣。余额不足、用量不完整或结算异常时进入待处理流程，不强行扣成负数。

预留金额是固定配置，并非对每次请求实际费用的精确预测。

### 8.5 并发资金控制

使用数据库事务、行锁、条件更新和流水唯一约束控制资金变动。

钱包、调用记录和对应资金流水在相关事务中保持一致。结算会检查请求状态，防止同一账单重复扣费。

网络调用不放在持有资金行锁的长事务中，避免等待上游响应时长期锁住钱包。

充值业务编号和账单结算的重复处理保护，不等同于聊天请求幂等。当前未实现基于 `X-Request-Id` 的聊天请求去重。

## 9. 数据结构

| 数据表                      | 用途                                     |
| --------------------------- | ---------------------------------------- |
| `router_user`               | 用户及用户状态                           |
| `router_api_key`            | API Key 哈希、归属和状态                 |
| `router_wallet`             | 用户可用余额及冻结金额                   |
| `router_wallet_transaction` | 充值、预留、结算和释放流水               |
| `chat_call_log`             | 请求状态、用量、费用、价格快照和结算状态 |
| `router_session_binding`    | 会话与渠道的绑定                         |
| `router_prefix_history`     | 稳定前缀的成功渠道历史                   |
| `router_explicit_cache`     | 显式缓存归属、上游引用、状态及有效期     |

表之间主要通过逻辑编号关联，由业务层校验关联关系，并通过对账脚本检查资金数据一致性。

## 10. 查询、统计与对账

以下接口使用管理员 Key：

```http
Authorization: Bearer <ROUTER_ADMIN_KEY>
```

| 方法 | 路径                                          | 用途                             |
| ---- | --------------------------------------------- | -------------------------------- |
| GET  | `/api/admin/users/{userId}/wallet`            | 查询钱包                         |
| GET  | `/api/call-logs`                              | 查询最近 20 条调用记录           |
| GET  | `/api/call-logs/stats`                        | 查询调用数量统计                 |
| GET  | `/api/call-logs/cache-stats`                  | 查询缓存和费用统计               |
| GET  | `/api/call-logs/billing/unfinished?afterId=0` | 分页查询未完成账单               |
| POST | `/api/call-logs/{requestId}/billing/retry`    | 使用已保存用量重试结算           |
| POST | `/api/call-logs/{requestId}/billing/release`  | 人工核对后释放符合条件的预留资金 |

重试结算不会重新调用上游模型。

释放请求体：

```json
{
  "userId": 1,
  "reason": "已人工核对失败请求，确认释放预留资金"
}
```

释放接口有账单状态和归属检查，不能用于随意撤销已结算账单。

在数据库客户端执行：

```text
sql/reconcile_wallet.sql
```

脚本检查：

1. 钱包金额与流水累计是否一致。
2. 流水是否存在对应钱包。
3. 冻结金额是否与未结束的预留一致。
4. 已结算调用费用是否与结算流水一致。
5. 费用是否符合用量和价格快照。
6. 同一业务是否同时存在结算和释放。

正常情况下，各项异常查询应不返回记录。

## 11. 异常处理与巡检

无法确认真实费用的请求保留待处理状态，通过管理接口核对和处理。

可配置的巡检功能默认关闭：

```yaml
router:
  recovery:
    enabled: false
    interval-ms: 300000
    stale-seconds: 300
    auto-retry-enabled: false
    max-retry-age-seconds: 86400
    batch-size: 50
```

巡检用于发现滞留账单；开启自动重试后，对满足条件、用量完整的账单尝试重新结算。

该功能不等于完整的故障恢复系统。没有可靠用量、上游结果未知或进程异常中断的请求，仍可能需要人工核对，不会仅凭超时就自动退款。

## 12. 验证记录

### 12.1 已观察到的手工验证结果

| 场景                | 结果                                           |
| ------------------- | ---------------------------------------------- |
| DeepSeek 非流式调用 | 返回聊天内容，调用记录可见成功及已结算状态     |
| 会话绑定            | 成功生成绑定记录，后续请求刷新更新时间和有效期 |
| 前缀历史            | 修改固定前缀后，产生不同的前缀哈希记录         |
| 显式缓存创建        | 返回 HTTP 201 和 `READY` 状态                  |
| 显式缓存复用        | 返回 HTTP 200，并返回缓存命中 token            |
| 火山方舟流式调用    | 已观察到 SSE 分段响应                          |
| 鉴权单元测试        | 最近一次执行 4 项测试通过                      |
| 启动脚本检查和构建  | 配置工具检查与 Maven 构建通过                  |

### 12.2 显式缓存复用样本

一次实际复用响应中的用量：

| 指标               |      数值 |
| ------------------ | --------: |
| 输入 token         |       427 |
| 缓存命中输入 token |       412 |
| 普通输入 token     |        15 |
| 输出 token         |        98 |
| 总 token           |       525 |
| 输入缓存命中率     | 约 96.49% |

计算方式：

```text
412 / 427 × 100% ≈ 96.49%
```

在当前演示价格下，该次聊天按公式应计费 525 微元，不包含此前创建缓存的费用。

该样本证明本次调用返回了缓存命中用量，不代表所有调用均能达到相同命中率，也不构成相同请求在有缓存与无缓存情况下的费用对照实验。