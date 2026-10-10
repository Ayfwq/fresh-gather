# 鲜集 · 生鲜即时零售与特卖平台

鲜集把社区生鲜零售做成一条完整的即时交易链路：田间鲜品浏览、社区购物袋、地址配送、限时特卖和商家订单履约。特卖设计沿用 Redis Lua 原子扣减、MySQL 条件更新、RabbitMQ 异步建单补偿和 WebSocket 实时订单提醒。

## 项目结构

```text
sky-take-out/
├── backend/
│   ├── sky-server/      Spring Boot 接口服务、MyBatis 数据访问
│   ├── sky-common/      鉴权、微信支付等公共服务
│   ├── sky-pojo/        DTO、实体、接口响应模型
│   ├── sky-agent-py/    Python 商家运营分析 Agent
│   └── pom.xml          Java 多模块 Maven 入口
├── frontend/            Vue 3 + Vite：顾客鲜市与商家工作台
└── start-local.ps1      本地开发启动入口
```

## 打开页面

在项目根目录使用 PowerShell 运行：

```powershell
./start-local.ps1
```

第一次运行会先构建 Java 后端，再启动前后端开发服务。顾客端入口为 `http://localhost:5173/#market`，未登录时先显示独立的手机号登录/注册页，登录并取得 JWT 后才进入商城。商家工作台单独从 `http://localhost:5173/#merchant` 打开，使用员工账号登录；顾客页面不提供商家后台入口。商家工作台可以切换演示数据，演示数据只保存在当前浏览器。

如果本地 Java 后端已经在 `127.0.0.1:8080` 正常运行，启动脚本会直接复用它，不会重启或结束已有进程。

前端通过 Vite 代理连接本地开发后端 `127.0.0.1:8080`。顾客可用手机号和密码注册/登录；注册会自动建立客户档案并签发 JWT，商城数据和个人订单由此 JWT 访问。微信小程序授权登录也保留。商家端使用数据库中的员工账号登录后读取和管理后端数据。真实微信支付仍须经微信小程序完成。

首次启用手机号注册前，先运行 `backend/sky-server/src/main/resources/sql/customer_phone_auth_preflight.sql` 并处理重复手机号，再对 `sky_take_out` 数据库执行 `customer_phone_auth_migration.sql`；迁移会为客户手机号建立唯一索引并新增 BCrypt 密码字段。

启动脚本会在检测到 `uv` 时自动启动 `backend/sky-agent-py`，并让它连接同一端口的 Java 后端；没有 `uv` 时，经营参谋之外的页面功能仍可使用。也可以在该目录运行 `uv run uvicorn app.main:app --host 127.0.0.1 --port 8001` 单独启动它。

本地开发后端使用项目原有的 MySQL `sky_take_out` 和 Redis `localhost:6379` 配置。启动脚本会在 Docker Engine 可用时通过根目录 `docker-compose.yml` 自动启动 Redis 和 RabbitMQ；Redis 是 Java 后端必需项，未启动时脚本会保留前端并跳过后端启动。RabbitMQ 不可用时，后端仍可启动并以同步建单模式运行；可单独运行 `docker compose up -d redis rabbitmq`。RabbitMQ 管理界面仅绑定到本机 `127.0.0.1:15672`。

菜品和套餐图片保存在运行后端的机器磁盘，不使用 OSS，也不把二进制图片塞进 MySQL；商品表只保存 `/uploads/...` 图片路径。默认目录为项目运行目录下的 `.runtime/uploads`，部署到服务器时可通过 `SKY_UPLOAD_DIR` 指向持久化磁盘目录，并确保该目录有写权限。真实微信支付还需要配置 `WECHAT_APP_ID`、`WECHAT_APP_SECRET`、`WECHAT_MCH_ID`、`WECHAT_MCH_SERIAL_NO`、`WECHAT_PRIVATE_KEY_FILE`、`WECHAT_PLATFORM_CERT_FILE`、`WECHAT_API_V3_KEY`、`WECHAT_NOTIFY_URL` 和 `WECHAT_REFUND_NOTIFY_URL`；没有这些配置时，普通手机号注册账号仍可使用本地模拟付款。

如只需打开前端设计稿和演示交互：

```powershell
./start-local.ps1 -FrontendOnly
```

## 前端常用命令

```powershell
cd frontend
npm install
npm run dev
npm run build
```

连接地址统一通过 `frontend/.env.local` 中的 `BACKEND_URL` 配置；默认指向本地开发端口 `http://127.0.0.1:8080`。复制 `frontend/.env.example` 可另行配置 Python Agent 服务地址。

## 演示与后端接口

顾客端已有可点击入口对应手机号注册/登录、微信登录、菜品/套餐浏览与套餐明细、分类筛选、口味选择、购物车增减/清空、地址增删改与默认地址、订单历史/详情/取消/支付/催单/再来一单，以及特卖详情/抢购结果。商家工作台覆盖工作台指标、订单查询/详情/接单/拒单/取消/配送/完成、菜品及口味、套餐组合、分类、员工新增/编辑/启停、客户档案检索、特卖活动/预热、营业状态、报表与导出。WebSocket 使用既有 `/ws/{sid}` 端点接收付款提醒与催单广播；经营参谋经由 Python Agent 查询 `/internal/ops/*` 分析接口。

逐路由的前后端对应关系、系统回调和内部 Agent 接口说明见 [docs/API_COVERAGE.md](docs/API_COVERAGE.md)。交易金额现在由后端按商品当前价格重算；支付/退款以订单真实金额处理，用户只能读取和修改自己名下的地址与订单。微信支付回调在解密前校验微信平台证书签名，再校验商户、订单金额和交易状态。

微信支付成功通知 `/notify/paySuccess` 是微信服务器回调，系统自动接收，不属于用户点击入口。普通浏览器可以使用本地模拟支付；真实微信支付需要在已配置商户和 JSAPI 的微信环境运行。

本地模拟付款只在 `webdev` Spring profile 中注册。该 profile 将服务绑定到本机回环地址，不能在生产环境启用；顾客身份仍须通过手机号密码或微信授权登录，开发环境不提供昵称直登录。
