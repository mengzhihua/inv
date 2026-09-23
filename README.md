# INV 发票管理系统

进销项一体化发票管理与税务合规平台：覆盖开票申请 → 审核 → 开具（号段取号/数电票）→ 交付 → 作废/红冲 的销项全流程，
以及进项发票录入（手工/扫描/导入/API）→ 查重 → 查验 → 勾选抵扣 → 入账的进项全流程，
另含费用报销合规检查、税务属期与增值税申报预填、电子档案与集成开放接口。
与本组织 BMS（~/repos/bms）同构（Spring Boot 2.7 + MyBatis-Plus / Vue 3 + Element Plus）。

## 功能范围

| 模块 | 能力 |
| --- | --- |
| 基础数据 | 纳税主体（纳税人识别号校验、一般/小规模、开票人/收款人/复核人、分票种开票限额）、往来单位（客户/供应商/BOTH）、商品服务（税收分类编码、税率档 0/0.01/0.03/0.05/0.06/0.09/0.13）、发票号段库存 |
| 销项管理 | 开票申请（MANUAL/OMS/BMS/IMPORT，source+extRef 幂等），行级税额计算与 ±0.06 容差校验、含税录入反算、专票必填校验、限额自动拆分、多申请合并、>8 行专票销货清单标记；发票开具（号段取号 / 数电 20 位号）、作废（仅当月纸质票）、红字信息表与部分/全额红冲、EMAIL/SMS 交付、CSV 导出、批量审核/开票/交付 |
| 进项管理 | 进项票录入查重（code+no 唯一）、模拟税局查验（校验码派生 + 抬头比对 ABNORMAL）、勾选抵扣（属期、确认抵扣生成 DeductionBatch）、不抵扣标记、三单匹配（poNo/receiptNo+amount，容差匹配）、入账生成模拟凭证号、CSV 导入、OCR 模拟 /scan |
| 费用发票 | 员工费用票上传，合规检查输出风险项（DUPLICATE / TITLE_MISMATCH / EXPIRED / VERIFY_FAILED / AMOUNT_LIMIT），无风险 COMPLIANT 可报销，可驳回 |
| 税务 | 属期关账（拦截销项作废与进项勾选/抵扣确认）、增值税申报预填（销项分税率净额、进项已抵扣、应纳税额/留抵、税负率）、税负预警与勾选预演 |
| 系统集成 | `/api/open/**` 开放接口（X-Api-Key）：OMS/BMS 推送开票申请（幂等）与状态查询、SRM 推送进项票；集成日志；电子档案（发票 PDF/OFD 模拟归档、按月查询、导出清单） |
| 报表 | 工作台（待审核/待开票/今日开票、本月进销项税额、待查验/异常/待勾选、费用风险数、号段余量预警、12 月趋势）、销项统计（主体/客户/票种/月）、进项统计（供应商/抵扣状态）、红冲作废统计、客户开票排名、CSV 导出 |
| 系统管理 | 用户与角色（ADMIN / FINANCE / OPERATOR / VIEWER）、操作日志 |

## 技术栈与目录

- 后端：Java 8 语法（JDK 17 编译，`maven.compiler.release=8`）、Spring Boot 2.7.18、MyBatis-Plus 3.5.3.1、H2（开发）/ MySQL 8（生产）、Bearer Token 认证
- 前端：Vue 3、Vite、Element Plus、Vue Router、Axios（开发服务器 `/api` 代理到 8080）

```text
backend/
  src/main/java/com/inv/
    basic/        纳税主体（含限额）/ 往来单位 / 商品 / 号段（原子取号）
    sales/        开票申请 + 明细、发票 + 明细 + 事件、红字信息表、交付日志
    purchase/     进项发票 + 明细、抵扣批次
    expense/      费用发票（合规检查/报销）
    tax/          属期、增值税预填、税负预警
    integration/  Open API、集成日志、电子档案
    report/       工作台与统计报表
    system/       用户、角色权限 AccessPolicy、令牌、操作日志
    common/       统一响应 R、异常处理、CSV、编号生成、税额计算、校验码派生
  src/main/resources/schema.sql, data.sql   幂等建表与演示数据
  src/test/java/com/inv/                    JUnit 5 集成测试（H2 内存库）
frontend/                                   Vue 3 + Element Plus 管理界面
scripts/smoke.sh                            端到端冒烟脚本
```

端口：后端 8080，前端开发服务器 5173（`/api` 代理到后端）。

## 快速开始

### 后端

要求 JDK 17 和 Maven：

```bash
cd backend
INV_ADMIN_PASSWORD=admin123 INV_OPEN_API_KEY=dev-open-key mvn spring-boot:run
```

默认 H2 文件库 `backend/data/inv`，启动时自动执行 `schema.sql`（建表）与 `data.sql`（幂等演示数据：
2 个纳税主体及各票种限额、8 个往来单位、12 个商品、各票种号段、演示申请/发票/进项/费用票、当前与上月属期）。
首次启动以 `INV_ADMIN_PASSWORD` 创建 `admin`；未设置时随机生成一次性初始口令并打印到启动日志。
MySQL 通过 `--spring.profiles.active=mysql` 启用（`DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD`）。

主要环境变量：

| 变量 | 说明 |
| --- | --- |
| `INV_AUTH_SECRET` / `INV_TOKEN_TTL` | 登录令牌签名密钥与有效期 |
| `INV_ADMIN_PASSWORD` | 首次启动初始化的管理员密码（为空则随机生成并输出到日志） |
| `INV_OPEN_API_KEY` | `/api/open/**` 开放接口的 `X-Api-Key`（未设置时开放接口全部拒绝） |
| `INV_CORS_ORIGINS` / `INV_H2_CONSOLE` | 跨域来源、是否开启 H2 控制台 |
| `INV_MOCK_TAX_FAIL` | `true` 时含税金额尾数 `.99` 的开票请求模拟税控失败（演示用） |
| `INV_EXPENSE_MAX_DAYS` / `INV_EXPENSE_SINGLE_LIMIT` | 费用票合规：开票距今天数上限（默认 180）/ 单张含税上限（默认 50000） |
| `INV_TAX_BURDEN_WARN` | 税负率预警阈值（默认 0.03） |
| `INV_LOGIN_MAX_FAIL` / `INV_LOGIN_LOCK_MINUTES` | 同一用户名连续登录失败次数 / 锁定分钟数（默认 5 / 15，内存级，重启清零） |

### 前端

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173，/api 代理至后端 8080
npm run build    # 产出 dist/
```

菜单与页面：

| 菜单 | 页面 |
| --- | --- |
| 工作台 | 指标卡（今日开票张数/金额、本月销项/进项税额、异常进项、费用风险）、待办（待审核/待开票/待查验/待勾选）、近 12 月开票趋势、号段余量预警 |
| 销项管理 | 开票申请（筛选、新建/编辑含明细行、含税/不含税切换自动反算、提交/审核/驳回/拆分/合并/批量审核/批量开票）；发票查询（详情抽屉含明细/事件/交付，作废/红冲/交付/导出）；红字信息表（确认/红冲开票） |
| 进项管理 | 进项发票（录入/CSV 导入/OCR 模拟、查验/批量查验、勾选/取消勾选/不抵扣、三单匹配、入账、导出）；抵扣确认（按主体+属期生成抵扣批次） |
| 费用发票 | 上传、合规检查/批量检查（风险项标签）、报销、驳回 |
| 税务管理 | 增值税申报预填（分税率销项、已抵扣进项、应纳税额/留抵、税负率预警、“再勾选 N 元进项税”预演）；属期管理（开账/关账） |
| 系统集成 | 电子档案（按月筛选、导出清单）、集成日志、开放接口说明（curl 示例） |
| 基础数据 | 纳税主体（含分票种限额子表）、往来单位、商品/服务、发票号段 |
| 报表分析 | 销项统计（月/主体/客户/票种）、进项统计、红冲作废统计、客户开票排名 TOP10，CSV 导出 |
| 系统管理 | 用户管理、操作日志 |

前端按登录角色隐藏无权按钮（审核/开票/关账/抵扣等仅 FINANCE 及以上，用户管理仅 ADMIN）。

### 登录与权限

| 角色 | 权限 |
| --- | --- |
| `ADMIN` | 全部操作，含用户管理、操作日志（`/api/system/**` 仅 ADMIN 可访问） |
| `FINANCE` | 全部业务操作（审核/开票/作废/红冲/勾选/关账等），不可维护用户 |
| `OPERATOR` | 读取全部业务数据；录入/申请/上传/撤销草稿；不可审核开票、勾选、关账，不可维护基础数据 |
| `VIEWER` | 只读 |

前端登录后令牌保存在浏览器本地，`INV_AUTH_SECRET` 未设置时每次重启后需重新登录。

### Open API（上游系统推送）

上游 OMS / BMS / SRM 以 `X-Api-Key: $INV_OPEN_API_KEY` 调用：

```text
POST /api/open/invoice-requests                     推送开票申请（source+extRef 幂等，重复返回原 requestNo）
GET  /api/open/invoice-requests/{source}/{extRef}   查询申请状态与发票号
POST /api/open/input-invoices                       推送进项发票
GET  /api/open/archive?month=yyyy-MM&direction=OUT  电子档案查询
```

开票申请请求体示例：

```json
{
  "request": {
    "source": "OMS", "extRef": "SO-2026-001", "taxEntityId": 1,
    "invoiceType": "E_NORMAL", "buyerName": "买方公司", "buyerTaxNo": "91XXXXXXXXXXXXXXXX"
  },
  "lines": [{"goodsName": "软件开发服务", "amount": 10000.00, "taxRate": 0.06}]
}
```

行内 `amount` 为不含税金额；`priceIncludeTax=1` 时 `amount` 视为含税金额自动反算（不含税=round(含税/(1+rate),2)，税额=含税−不含税）。

### 测试与冒烟

```bash
cd backend && mvn test                 # 税额/容差、拆分、必填校验、并发取号、作废、红冲、进项流程、合规、幂等、预填
INV_ADMIN_PASSWORD=admin123 INV_OPEN_API_KEY=dev-open-key \
  scripts/smoke.sh [http://localhost:8080]   # 需要后端已启动，依赖 curl / jq / python3
```

冒烟流程：登录 → 建客户/商品 → 创建申请 → 提交 → 审核 → 开票 → 交付 → 红冲 → 录入进项 →
查验 → 勾选 → 确认抵扣 → 上传费用票 → 合规检查 → 增值税预填 → 报表/档案断言。

## 状态机

```text
开票申请    DRAFT → SUBMITTED → APPROVED → ISSUED
            SUBMITTED → REJECTED →（编辑）DRAFT；DRAFT/SUBMITTED → CANCELLED
发票        ISSUED → CANCELLED（仅纸质票、当月、未红冲、未交付）→ 跨月/电子票走红冲
            ISSUED →（部分红冲）→ RED_FLUSHED（全额红冲）
            红字信息表 DRAFT → CONFIRMED（16 位 redInfoNo）→ USED
进项发票    UNVERIFIED → VERIFIED | FAILED；NORMAL | ABNORMAL | RED_FLUSHED
            PENDING → CHECKED（指定属期）→ DEDUCTED（确认抵扣批次）；或 → NOT_DEDUCT
            UNMATCHED → MATCHED | MISMATCH；UNPOSTED → POSTED
费用发票    UPLOADED → COMPLIANT | RISK → REIMBURSED | REJECTED
属期        OPEN → CLOSED（关账后该期不可再作废销项/勾选进项，红冲不受限）
```

## 主要数据表

`inv_user / inv_op_log / inv_tax_entity (+inv_tax_entity_limit) / inv_partner / inv_goods / inv_invoice_stock`
`inv_invoice_request (+_line) / inv_invoice (+_line) / inv_invoice_event / inv_red_info (+_line) / inv_delivery_log`
`inv_input_invoice (+_line) / inv_deduction_batch / inv_expense_invoice / inv_tax_period`
`inv_integration_log / inv_archive / inv_sequence`

## 规则要点

- 税额 = round(不含税金额 × 税率, 2)（HALF_UP），申请合计与明细合计容差 ±0.06（与税局一致）。
- 专票/电子专票买方税号、地址电话、开户行账号必填；普票买方为企业时税号必填，个人可空。
- 单张含税金额不得超过主体该票种限额，`POST /api/sales/request/{id}/split` 按行自动拆分（单行超限报错）；
  `POST /api/sales/request/merge` 合并同买方、同票种、同主体的多张草稿。
- 开票在事务内取号：号段 `SELECT ... FOR UPDATE` 锁行后 `UPDATE ... WHERE remaining>0` 原子扣减；
  数电票（ALL_ELECTRIC）不用号段，生成 20 位号码。明细超过 8 行的专票自动标记销货清单。
- 进项查重 `(invoice_code, invoice_no)`（数电票仅号码）；查验以校验码后 6 位与
  `sha256(code|no|date|amount)` 派生值比对，抬头税号不属于本企业主体时标 ABNORMAL。
- 电子档案随开票/红冲自动归档（模拟 PDF/OFD URL），可按月查询导出。

## 控制塔对接

开票申请、销项发票和进项发票快照，以及查验、提交、审核，见 [技术方案](docs/技术方案.md)。

这些指令必须带 API Key：`/api/open/ir/snapshots`、`/actions`、`/verify-input`、`/submit-request`、`/approve-request`。控制塔登录模式不调用本系统。上游 OMS / BMS / SRM 推送仍走原来的 `/api/open` 业务口。

## 发布包（开箱即用）

前端生产构建打进 Spring Boot 可执行 JAR。三种用法：

### 1. 服务端（任意已装 JDK 17 的机器）

```bash
java -jar inv-backend-1.0.0.jar --server.port=8089
```

Linux systemd 示例见发布包 `README.txt`。

### 2. 便携包（需本机已装 Java）

```bash
bash scripts/package-release.sh
unzip release/inv-1.0.0.zip
cd inv-1.0.0
```

| 系统 | 怎么用 |
| --- | --- |
| Linux | `./start.sh` |
| macOS | 双击 `start.command`，或 `./start.sh` |
| Windows | 双击 `start.bat` |

### 3. 原生包（捆绑 JRE，不必装 Java）

合并到默认分支且便携包冒烟通过后，GitHub Actions 自动发布 GitHub Release（也可在 Actions 里手动 `workflow_dispatch`）。分别在 Ubuntu / Windows / macOS 生成：

- `inv-1.0.0-linux-x64.zip` → `bin/inv`
- `inv-1.0.0-windows-x64.zip` → 双击 `inv.exe`
- `inv-1.0.0-macos-arm64.zip` → Apple Silicon（M 系列），双击 `inv.app`
- `inv-1.0.0-macos-x64.zip` → Intel Mac，双击 `inv.app`

浏览器访问 `http://127.0.0.1:8089`。默认账号 `admin / admin123`。

十二套系统可同时启动：OMS 8081 / WMS 8082 / TMS 8083 / BMS 8084 / SAP 8085 / OA 8086 / SRM 8087 / BOM 8088 / INV 8089 / IR 8090 / CRM 8091 / DMS 8092。

