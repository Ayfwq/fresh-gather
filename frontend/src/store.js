import { reactive, computed } from 'vue'
import { createDemo } from './demo.js'
import { request, upload } from './api.js'
import { cartTotal } from './domain.js'
import { productImageFor } from './productImages.js'

const demo = createDemo(localStorage)
const customerAuthMethod = sessionStorage.getItem('fresh-user-auth')
if (!['phone-password', 'nickname-password', 'wechat'].includes(customerAuthMethod)) {
  sessionStorage.removeItem('fresh-user-token')
  sessionStorage.removeItem('fresh-user-name')
}
const state = reactive({ mode: localStorage.getItem('fresh-mode') || 'live', userToken: sessionStorage.getItem('fresh-user-token') || '', adminToken: sessionStorage.getItem('fresh-admin-token') || '', userName: sessionStorage.getItem('fresh-user-name') || '', categories: [], setmealCategories: [], products: [], setmeals: [], activities: [], cart: [], orders: [], addresses: [], adminOrders: [], adminProducts: [], adminSetmeals: [], adminCategories: [], employees: [], adminActivities: [], metrics: {}, workspace: {}, orderStats: {}, report: {}, shopStatus: 1, busy: false, error: '', messages: [], wsConnected: false, toast: '', favorites: JSON.parse(localStorage.getItem('fresh-favorites') || '[]') })
let toastTimer, socket, retryTimer, disposed = false
const say = text => { state.toast = text; clearTimeout(toastTimer); toastTimer = setTimeout(() => { state.toast = '' }, 4000) }
const call = (path, options = {}) => request(path, { token: options.admin ? state.adminToken : state.userToken, ...options })
const guarded = async operation => { try { return await operation() } catch (error) { say(error.message); throw error } }
const enrich = product => ({ ...product, unit: product.unit || '1份', badge: product.badge || '鲜集精选', image: product.image || productImageFor(product.name), originalPrice: product.originalPrice || product.price, flavors: product.flavors || [] })

async function refreshShop() {
  state.error = ''; state.busy = true
  try {
    if (state.mode === 'demo') {
      state.categories = demo.categories(1).filter(p => p.status === 1); state.setmealCategories = demo.categories(2).filter(p => p.status === 1); state.products = demo.products().filter(p => p.status === 1); state.setmeals = demo.setmeals().filter(p => p.status === 1).map(p => ({ ...p, setmealId: p.id, kind: 'setmeal' })); state.activities = demo.activities(); state.shopStatus = demo.data.shopStatus
      await refreshUser(); return
    }
    if (!state.userToken) { state.categories = []; state.setmealCategories = []; state.products = []; state.setmeals = []; state.activities = []; state.cart = []; state.orders = []; state.addresses = []; return }
    const [categories, setmealCategories, activities, status] = await Promise.all([call('/user/category/list?type=1'), call('/user/category/list?type=2'), call('/user/flashsale/list'), call('/user/shop/status')])
    state.categories = categories || []; state.setmealCategories = setmealCategories || []; state.activities = activities || []; state.shopStatus = status
    const [groups, mealGroups] = await Promise.all([Promise.all(state.categories.map(c => call(`/user/dish/list?categoryId=${c.id}`))), Promise.all(state.setmealCategories.map(c => call(`/user/setmeal/list?categoryId=${c.id}`)))])
    state.products = groups.flat().map(enrich); state.setmeals = mealGroups.flat().map(p => enrich({ ...p, setmealId: p.id, kind: 'setmeal' }))
    await refreshUser()
  } catch (error) {
    state.error = error.message
    if (error.status === 401) {
      state.userToken = ''; state.userName = ''
      sessionStorage.removeItem('fresh-user-token'); sessionStorage.removeItem('fresh-user-name'); sessionStorage.removeItem('fresh-user-auth')
      state.categories = []; state.setmealCategories = []; state.products = []; state.setmeals = []; state.activities = []; state.cart = []; state.orders = []; state.addresses = []
    }
  } finally { state.busy = false }
}
async function refreshUser() {
  if (state.mode === 'demo') { state.cart = demo.cart(); state.orders = demo.orders(); state.addresses = demo.addresses(); return }
  if (!state.userToken) { state.cart = []; state.orders = []; state.addresses = []; return }
  const [cart, orders, addresses] = await Promise.all([call('/user/shoppingCart/list'), call('/user/order/historyOrders?page=1&pageSize=100'), call('/user/addressBook/list')])
  state.cart = cart || []; state.orders = orders?.records || []; state.addresses = addresses || []
}
async function refreshAdmin() {
  if (state.mode === 'demo') {
    state.adminOrders = demo.orders(); state.adminProducts = demo.products(); state.adminSetmeals = demo.setmeals(); state.adminCategories = demo.categories(); state.employees = demo.employees(); state.adminActivities = demo.activities(); state.shopStatus = demo.data.shopStatus
    const completed = state.adminOrders.filter(o => o.status === 5)
    const turnover = completed.reduce((s, o) => s + Number(o.amount || 0), 0)
    state.metrics = { turnover, validOrderCount: completed.length, unitPrice: completed.length ? turnover / completed.length : 0, orderCompletionRate: state.adminOrders.length ? completed.length / state.adminOrders.length : 0, newUsers: 3 }
    state.workspace = { orderOverview: { waitingOrders: state.adminOrders.filter(o => o.status === 2).length, deliveredOrders: state.adminOrders.filter(o => o.status === 4).length, completedOrders: completed.length, cancelledOrders: state.adminOrders.filter(o => o.status === 6).length, allOrders: state.adminOrders.length }, dishOverview: { sold: state.adminProducts.filter(p => p.status === 1).length, discontinued: state.adminProducts.filter(p => p.status === 0).length }, setmealOverview: { sold: state.adminSetmeals.filter(p => p.status === 1).length, discontinued: state.adminSetmeals.filter(p => p.status === 0).length } }
    state.orderStats = { toBeConfirmed: state.adminOrders.filter(o => o.status === 2).length, confirmed: state.adminOrders.filter(o => o.status === 3).length, deliveryInProgress: state.adminOrders.filter(o => o.status === 4).length }
    return
  }
  if (!state.adminToken) return
  const [metrics, orderOverview, dishOverview, setmealOverview, orderStats, status, orders] = await Promise.all([call('/admin/workspace/businessData', { admin: true }), call('/admin/workspace/overviewOrders', { admin: true }), call('/admin/workspace/overviewDishes', { admin: true }), call('/admin/workspace/overviewSetmeals', { admin: true }), call('/admin/order/statistics', { admin: true }), call('/admin/shop/status', { admin: true }), call('/admin/order/conditionSearch?page=1&pageSize=8', { admin: true })])
  state.metrics = metrics || {}; state.workspace = { orderOverview: orderOverview || {}, dishOverview: dishOverview || {}, setmealOverview: setmealOverview || {} }; state.orderStats = orderStats || {}; state.shopStatus = status; state.adminOrders = orders?.records || []
}
function closeSocket() { clearTimeout(retryTimer); if (socket) { socket.onclose = null; socket.close(); socket = null } state.wsConnected = false }
async function adminRequest(path, options = {}) {
  if (state.mode !== 'demo' && !state.adminToken) throw new Error('请先登录商家账号')
  return guarded(() => call(path, { ...options, admin: true }))
}
function connectSocket() {
  closeSocket()
  if (state.mode !== 'live' || !state.adminToken || disposed) return
  socket = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/fresh-${crypto.randomUUID()}`)
  socket.onopen = () => { state.wsConnected = true }
  socket.onmessage = event => { try { const message = JSON.parse(event.data); state.messages.unshift({ ...message, time: new Date().toLocaleTimeString('zh-CN') }); state.messages = state.messages.slice(0, 20); window.dispatchEvent(new CustomEvent('fresh-order-update', { detail: message })); say(message.type === 2 ? '收到一条客户催单' : '有新的订单待处理'); refreshAdmin().catch(error => say(error.message)) } catch { /* Ignore non-order messages. */ } }
  socket.onclose = () => { state.wsConnected = false; if (!disposed) retryTimer = setTimeout(connectSocket, 5000) }
  socket.onerror = () => { state.wsConnected = false }
}
export function useMarket() {
  return {
    state, demo, say, refreshShop, refreshUser, refreshAdmin, adminRequest, total: computed(() => cartTotal(state.cart)), cartCount: computed(() => state.cart.reduce((s, p) => s + p.number, 0)),
    async setMode(mode) { if (mode === state.mode) return; if (mode === 'live' && state.adminToken === 'demo-admin') { state.adminToken = ''; sessionStorage.removeItem('fresh-admin-token') } state.mode = mode; localStorage.setItem('fresh-mode', mode); closeSocket(); state.products = []; state.categories = []; state.setmealCategories = []; state.setmeals = []; state.activities = []; state.cart = []; state.orders = []; state.addresses = []; state.adminOrders = []; state.adminProducts = []; state.adminSetmeals = []; state.adminCategories = []; state.employees = []; state.adminActivities = []; state.metrics = {}; state.workspace = {}; state.orderStats = {}; state.report = {}; state.messages = []; await refreshShop(); if (mode === 'live') connectSocket() },
    async registerCustomer(customer) { return guarded(async () => { const user = await request('/user/user/register', { method: 'POST', body: customer }); state.userToken = user.token; state.userName = user.name || customer.username || customer.name; sessionStorage.setItem('fresh-user-token', user.token); sessionStorage.setItem('fresh-user-name', state.userName); sessionStorage.setItem('fresh-user-auth', 'nickname-password'); await refreshShop(); say('客户账号创建成功'); return user }) },
    async loginCustomer(username, password) { return guarded(async () => { const user = await request('/user/user/nickname-login', { method: 'POST', body: { username, password } }); state.userToken = user.token; state.userName = user.name || username; sessionStorage.setItem('fresh-user-token', user.token); sessionStorage.setItem('fresh-user-name', state.userName); sessionStorage.setItem('fresh-user-auth', 'nickname-password'); await refreshShop(); say('客户登录成功'); return user }) },
    async loginWithWechatCode(code) { return guarded(async () => { if (!code) throw new Error('微信授权未返回登录凭证'); const user = await request('/user/user/login', { method: 'POST', body: { code } }); state.userToken = user.token; state.userName = '微信顾客'; sessionStorage.setItem('fresh-user-token', user.token); sessionStorage.setItem('fresh-user-name', state.userName); sessionStorage.setItem('fresh-user-auth', 'wechat'); await refreshShop(); say('微信登录成功') }) },
    async adminLogin(username, password) { return guarded(async () => { const user = await request('/admin/employee/login', { method: 'POST', body: { username, password } }); state.adminToken = user.token; sessionStorage.setItem('fresh-admin-token', user.token); await refreshAdmin(); connectSocket(); say('商家登录成功') }) },
    async logoutAdmin() { try { if (state.mode === 'live' && state.adminToken) await call('/admin/employee/logout', { method: 'POST', admin: true }) } finally { state.adminToken = ''; sessionStorage.removeItem('fresh-admin-token'); closeSocket() } },
    async addCart(product, flavor = '') { return guarded(async () => { if (state.shopStatus !== 1) throw new Error('门店已打烊，暂时不能下单'); const isSetmeal = Boolean(product.setmealId || product.kind === 'setmeal'); if (state.mode === 'demo') demo.addCart(product, flavor); else await call('/user/shoppingCart/add', { method: 'POST', body: isSetmeal ? { setmealId: product.setmealId || product.id } : { dishId: product.dishId || product.id, dishFlavor: flavor } }); await refreshUser(); say('已加入购物袋') }) },
    async subtract(item) { return guarded(async () => { if (state.mode === 'demo') demo.subtractCart(item); else await call('/user/shoppingCart/sub', { method: 'POST', body: { dishId: item.dishId || null, dishFlavor: item.dishFlavor || '', setmealId: item.setmealId || null } }); await refreshUser() }) },
    async clearCart() { return guarded(async () => { if (state.mode === 'demo') demo.clearCart(); else await call('/user/shoppingCart/clean', { method: 'DELETE' }); await refreshUser(); say('购物袋已清空') }) },
    async address(value) { return guarded(async () => { if (state.mode === 'demo') value.id ? demo.updateAddress(value) : demo.addAddress(value); else await call('/user/addressBook', { method: value.id ? 'PUT' : 'POST', body: { ...value, sex: value.sex || '1', provinceCode: value.provinceCode || '110000', provinceName: value.provinceName || '北京市', cityCode: value.cityCode || '110100', cityName: value.cityName || '北京市', districtCode: value.districtCode || '110108', districtName: value.districtName || '海淀区' } }); await refreshUser(); say(value.id ? '收货地址已更新' : '收货地址已保存') }) },
    async defaultAddress() { if (state.mode === 'demo') return demo.addresses().find(address => Number(address.isDefault) === 1) || null; try { return await call('/user/addressBook/default') } catch (error) { if (error.message?.includes('没有查询到默认地址')) return null; throw error } },
    async setDefaultAddress(address) { return guarded(async () => { if (state.mode === 'demo') demo.setDefaultAddress(address.id); else await call('/user/addressBook/default', { method: 'PUT', body: { id: address.id } }); await refreshUser(); say('默认配送地址已更新') }) },
    async deleteAddress(address) { return guarded(async () => { if (state.mode === 'demo') demo.deleteAddress(address.id); else await call('/user/addressBook?id=' + encodeURIComponent(address.id), { method: 'DELETE' }); await refreshUser(); say('地址已删除') }) },
    async addressDetail(address) { return state.mode === 'demo' ? address : call('/user/addressBook/' + address.id) },
    async submit(addressBookId, remark) { return guarded(async () => { if (state.shopStatus !== 1) throw new Error('门店已打烊'); const result = state.mode === 'demo' ? demo.submit(addressBookId, remark) : await call('/user/order/submit', { method: 'POST', body: { addressBookId, remark, payMethod: 1, estimatedDeliveryTime: null, deliveryStatus: 1, tablewareNumber: 0, tablewareStatus: 0, packAmount: 0, amount: cartTotal(state.cart) } }); await refreshUser(); return result }) },
    async purchase(activity, addressBookId, requestId) { return guarded(async () => { if (state.shopStatus !== 1) throw new Error('门店已打烊'); const result = state.mode === 'demo' ? demo.purchase(activity.id, addressBookId, requestId) : await call('/user/flashsale/purchase', { method: 'POST', body: { activityId: activity.id, addressBookId, requestId } }); if (result.status === 2) throw new Error(result.message); return result }) },
    async activityDetail(activity) { return state.mode === 'demo' ? activity : call('/user/flashsale/' + activity.id) },
    async purchaseResult(requestId) { return state.mode === 'demo' ? demo.data.requests[requestId] : call(`/user/flashsale/result/${encodeURIComponent(requestId)}`) },
    async orderAction(order, action, admin = false, reason = '') { return guarded(async () => { let result; if (state.mode === 'demo') { if (action === 'reminder') say('已提醒商家尽快处理'); else demo.transition(order.id, action === 'wechatPay' ? 'pay' : action); } else {
      const config = { pay: { path: `/user/dev/payment/${order.id}`, method: 'PUT' }, wechatPay: { path: '/user/order/payment', method: 'PUT', body: { orderNumber: order.number || order.orderNumber, payMethod: 1 } }, cancel: admin ? { path: '/admin/order/cancel', method: 'PUT', body: { id: order.id, cancelReason: reason } } : { path: `/user/order/cancel/${order.id}`, method: 'PUT' }, confirm: { path: '/admin/order/confirm', method: 'PUT', body: { id: order.id } }, reject: { path: '/admin/order/rejection', method: 'PUT', body: { id: order.id, rejectionReason: reason } }, delivery: { path: `/admin/order/delivery/${order.id}`, method: 'PUT' }, complete: { path: `/admin/order/complete/${order.id}`, method: 'PUT' }, reminder: { path: `/user/order/reminder/${order.id}`, method: 'GET' } }[action]
      if (!config) throw new Error('暂不支持此订单操作')
      result = await call(config.path, { ...config, admin: admin || ['confirm', 'reject', 'delivery', 'complete'].includes(action) })
    } await refreshUser(); if (admin) await refreshAdmin(); if (action !== 'reminder') say(action === 'pay' ? (state.mode === 'demo' ? '演示付款完成，没有真实扣款' : '模拟支付成功，未产生真实扣款') : action === 'wechatPay' ? '已向微信支付服务发起预支付' : '订单状态已更新'); return result }) },
    async orderDetail(order, admin = false) { if (state.mode === 'demo') return demo.order(order.id) || order; return call((admin ? '/admin/order/details/' : '/user/order/orderDetail/') + order.id, { admin }) },
    async setmealDetail(meal) { if (state.mode === 'demo') return demo.setmeal(meal.id) || meal; const dishes = await call('/user/setmeal/dish/' + meal.id); return { ...meal, setmealDishes: dishes || [] } },
    async repeatOrder(order) { return guarded(async () => { if (state.mode === 'demo') demo.repeatOrder(order.id); else await call('/user/order/repetition/' + order.id, { method: 'POST' }); await refreshUser(); say('原订单商品已放回购物袋') }) },
    async toggleProduct(product) { return guarded(async () => { if (state.mode === 'demo') demo.toggleProduct(product.id); else await call(`/admin/dish/status/${product.status ? 0 : 1}?id=${product.id}`, { method: 'POST', admin: true }); await refreshAdmin(); await refreshShop() }) },
    async toggleActivity(activity) { return guarded(async () => { if (state.mode === 'demo') demo.toggleActivity(activity.id); else await call(`/admin/flashsale/${activity.id}/${activity.status ? 'disable' : 'enable'}`, { method: 'POST', admin: true }); await refreshAdmin(); await refreshShop() }) },
    async preheat(activity) { return guarded(async () => { if (!activity.status) throw new Error('请先开启活动'); if (state.mode === 'live') await call(`/admin/flashsale/${activity.id}/preheat`, { method: 'POST', admin: true }); say(state.mode === 'demo' ? '演示库存已准备' : '活动库存已准备') }) },
    async saveActivity(value) { return guarded(async () => { if (state.mode === 'demo') demo.saveActivity(value); else await call('/admin/flashsale', { method: value.id ? 'PUT' : 'POST', body: value, admin: true }); await refreshAdmin(); say('特卖活动已保存') }) },
    async toggleShop() { return guarded(async () => { if (state.mode === 'demo') demo.toggleShop(); else await call(`/admin/shop/${state.shopStatus ? 0 : 1}`, { method: 'PUT', admin: true }); await refreshAdmin(); await refreshShop() }) },
    async uploadImage(file) { return guarded(() => upload('/admin/common/upload', file, { token: state.adminToken, admin: true })) },
    favorite(id) { const index = state.favorites.indexOf(id); if (index < 0) state.favorites.push(id); else state.favorites.splice(index, 1); localStorage.setItem('fresh-favorites', JSON.stringify(state.favorites)) },
    connectSocket,
    dispose() { disposed = true; closeSocket(); clearTimeout(toastTimer) },
  }
}
