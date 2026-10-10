# 前后端 API 覆盖清单

本文按 `backend/sky-server/src/main/java/com/sky/controller` 中的 Controller 路由逐项核对。顾客操作对应鲜集前台，商家操作对应管理工作台。`webdev` 本地开发接口、第三方支付回调和经营参谋内部接口是系统间接口，因此不会伪装成普通用户按钮。

## 顾客前台

| 后端接口 | 前端入口 |
| --- | --- |
| `GET /user/category/list`、`GET /user/dish/list`、`GET /user/setmeal/list`、`GET /user/setmeal/dish/{id}`、`GET /user/shop/status` | 首页商品分类、菜品/套餐目录、套餐详情和门店营业状态；`frontend/src/store.js` |
| `GET /user/flashsale/list`、`GET /user/flashsale/{id}`、`POST /user/flashsale/purchase`、`GET /user/flashsale/result/{requestId}` | 限时特卖卡片、详情、立即抢购和结果轮询；`frontend/src/App.vue`、`frontend/src/store.js` |
| `POST /user/shoppingCart/add`、`GET /user/shoppingCart/list`、`POST /user/shoppingCart/sub`、`DELETE /user/shoppingCart/clean` | 加入购物袋、购物袋数量/列表、减少商品和清空；`frontend/src/App.vue`、`frontend/src/store.js` |
| `GET /user/addressBook/list`、`GET /user/addressBook/default`、`GET /user/addressBook/{id}`、`POST /user/addressBook`、`PUT /user/addressBook`、`PUT /user/addressBook/default`、`DELETE /user/addressBook` | 账户地址列表、默认地址结算、详情、添加/编辑、设默认、删除；`frontend/src/App.vue`、`frontend/src/store.js` |
| `POST /user/order/submit`、`PUT /user/order/payment`、`GET /user/order/historyOrders`、`GET /user/order/orderDetail/{id}`、`PUT /user/order/cancel/{id}`、`POST /user/order/repetition/{id}`、`GET /user/order/reminder/{id}` | 提交订单、发起微信支付、订单列表/详情、取消、再来一单、催单；`frontend/src/App.vue`、`frontend/src/store.js` |
| `POST /user/user/register`、`POST /user/user/phone-login`、`POST /user/user/login` | 顾客使用手机号和密码注册/登录（按用户要求不接短信验证码）；注册成功自动建档并签发用户 JWT。微信容器仍可用授权 code 登录，首次成功登录自动建档；普通浏览器不显示无法使用的微信授权按钮。 |

## 商家工作台

| 后端接口 | 前端入口 |
| --- | --- |
| `POST /admin/employee/login`、`POST /admin/employee/logout`、`POST /admin/employee`、`GET /admin/employee/page`、`GET /admin/employee/{id}`、`PUT /admin/employee`、`POST /admin/employee/status/{status}` | 商家登录/退出、员工分页、新增/编辑和启停；`frontend/src/App.vue`、`frontend/src/components/AdminDashboardFull.vue` |
| `GET /admin/customer/page` | 客户档案按姓名/手机号检索和分页；首次微信登录自动建档；`frontend/src/components/AdminDashboardFull.vue`。客户档案不包含 openid、身份证号等敏感字段。 |
| `POST /admin/category`、`GET /admin/category/page`、`GET /admin/category/list`、`PUT /admin/category`、`DELETE /admin/category`、`POST /admin/category/status/{status}` | 分类页面的新增、筛选/分页、选择、编辑、删除和启停；`frontend/src/components/AdminDashboardFull.vue` |
| `POST /admin/dish`、`GET /admin/dish/page`、`GET /admin/dish/list`、`GET /admin/dish/{id}`、`PUT /admin/dish`、`DELETE /admin/dish`、`POST /admin/dish/status/{status}`、`POST /admin/common/upload` | 菜品分页、用于编辑及套餐/特卖选品的完整列表、详情、增改删、起售/停售、图片上传；`frontend/src/components/AdminDashboardFull.vue`、`frontend/src/store.js` |
| `POST /admin/setmeal`、`GET /admin/setmeal/page`、`GET /admin/setmeal/{id}`、`PUT /admin/setmeal`、`DELETE /admin/setmeal`、`POST /admin/setmeal/status/{status}` | 套餐分页、详情、增改删、启停；`frontend/src/components/AdminDashboardFull.vue` |
| `GET /admin/order/conditionSearch`、`GET /admin/order/statistics`、`GET /admin/order/details/{id}`、`PUT /admin/order/confirm`、`PUT /admin/order/rejection`、`PUT /admin/order/cancel`、`PUT /admin/order/delivery/{id}`、`PUT /admin/order/complete/{id}` | 订单筛选/分页、统计、详情、接单/拒单/取消、配送/完成；`frontend/src/components/AdminDashboardFull.vue` |
| `GET /admin/report/turnoverStatistics`、`GET /admin/report/userStatistics`、`GET /admin/report/ordersStatistics`、`GET /admin/report/top10`、`GET /admin/report/export` | 经营报表图表/排名和 Excel 导出；`frontend/src/components/AdminDashboardFull.vue` |
| `GET /admin/workspace/businessData`、`GET /admin/workspace/overviewOrders`、`GET /admin/workspace/overviewDishes`、`GET /admin/workspace/overviewSetmeals` | 商家首页经营概览、订单/菜品/套餐统计；`frontend/src/store.js`、`frontend/src/components/AdminDashboardFull.vue` |
| `GET /admin/shop/status`、`PUT /admin/shop/{status}` | 门店营业状态展示和暂停/恢复接单；`frontend/src/store.js`、`frontend/src/components/AdminDashboardFull.vue` |
| `POST /admin/flashsale`、`PUT /admin/flashsale`、`GET /admin/flashsale/page`、`GET /admin/flashsale/{id}`、`POST /admin/flashsale/{id}/enable`、`POST /admin/flashsale/{id}/disable`、`POST /admin/flashsale/{id}/preheat` | 创建/编辑、分页/详情、启停和预热活动；`frontend/src/components/AdminDashboardFull.vue`、`frontend/src/store.js` |

## 系统间接口

| 后端接口 | 调用方和说明 |
| --- | --- |
| `POST /notify/paySuccess` | 微信支付平台异步回调；由支付回调调用，支付按钮本身调用 `PUT /user/order/payment` 创建预支付单。 |
| `GET /internal/ops/sales-overview`、`GET /internal/ops/sales-trend`、`GET /internal/ops/dish-sales-ranking`、`GET /internal/ops/category-sales-summary`、`GET /internal/ops/order-status-summary`、`GET /internal/ops/store-status` | `backend/sky-agent-py` 的 `OpsApiClient` 调用；商家在经营参谋入口提问后由 agent 汇总调用这些分析接口。 |
| `POST /user/user/web-login`、`PUT /user/dev/payment/{id}` | 仅本地 `webdev` profile 的浏览器开发登录（首次昵称登录会自动建立开发客户档案）和模拟支付入口；不会代替生产微信登录和真实支付。 |

## 核对出的补齐项

- 默认地址读取现在用于普通结算和特卖结算。
- 手机号客户可注册/登录，密码以 BCrypt 哈希保存；注册和登录签发用户 JWT，其余顾客接口由 JWT 拦截器验证。微信首次登录自动注册也保留。首次启用手机号账号前需运行 `customer_phone_auth_preflight.sql` 检查重复号，再应用 `customer_phone_auth_migration.sql`。
- 套餐和特卖编辑器通过 `/admin/dish/list` 按菜品分类取全量上架菜品，不再用固定 100 条分页结果冒充全量选择项。
- 抢购 requestId、Redis 库存、用户限购次数和 PENDING 结果在同一段 Lua 中原子处理；并发重复请求不会再次扣库存。
- 特卖订单通过显式数据库事务创建；RabbitMQ 建单消息开启持久化、发布确认和未路由检查。消费侧临时错误重试 3 次，重试耗尽进入补偿队列并幂等回补抢购配额。
- 普通订单与特卖订单统一使用 32 位随机订单号，避免按毫秒生成导致并发冲突。

## 验证范围

`mvn -f backend/pom.xml -pl sky-server -am clean package`（含 8 项测试）、`npm run build`、PowerShell 启动脚本语法检查和 `docker compose config` 均通过。API 对应关系按 Controller 路由和前端调用点做了静态核对。手机号注册依赖用户表迁移，需先运行预检脚本、处理重复手机号，再运行迁移脚本。此轮运行验证时 MySQL `3306` 可连接，但 Redis `6379` 和 RabbitMQ `5672` 均未启动，Docker Engine 也不可用；由于开发 profile 的启动初始化需要 Redis，本轮无法完成后端在线路由验证，也没有把真实消息发布/消费、死信重试和超时队列冒充成已做过的端到端验证。使用 `start-local.ps1` 时，脚本会尝试通过 Docker Compose 启动 RabbitMQ；没有可用 broker 时会明确以同步建单模式启动。启动后端前还需先启动本机 Redis。
