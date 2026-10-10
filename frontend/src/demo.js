import { cartTotal, localTime, canPurchase } from './domain.js'
import catalog from '../../catalog/fresh-market.json' with { type: 'json' }

const categoryId = Object.fromEntries(catalog.categories.map((category, index) => [category.key, index + 1]))
const productId = Object.fromEntries(catalog.products.map((product, index) => [product.slug, 1001 + index]))
export const demoCategories = catalog.categories.map((category, index) => ({ id: index + 1, name: category.name, type: category.type, sort: index + 1, status: 1 }))
export const demoProducts = catalog.products.map((product, index) => ({ id: productId[product.slug], categoryId: categoryId[product.category], name: product.name, description: product.description, unit: product.unit, price: product.price, originalPrice: product.price, badge: '鲜集精选', image: `/products/${product.slug}.webp`, status: 1, flavors: [] }))
const productBySlug = Object.fromEntries(demoProducts.map((product, index) => [catalog.products[index].slug, product]))
export const demoSetmeals = catalog.setmeals.map((meal, index) => {
  const setmealDishes = meal.components.map(([slug, copies]) => ({ dishId: productBySlug[slug].id, name: productBySlug[slug].name, price: productBySlug[slug].price, copies }))
  const total = setmealDishes.reduce((sum, item) => sum + Number(item.price) * Number(item.copies), 0)
  return { id: 2001 + index, categoryId: categoryId['meal-kits'], name: meal.name, description: meal.description, unit: '原料组合', price: Math.round(total * 95) / 100, image: `/products/meal-${meal.slug}.webp`, status: 1, setmealDishes }
})

const KEY = 'fresh-market-demo-v2'
export function createDemo(storage) {
  let data
  try { data = JSON.parse(storage.getItem(KEY)) } catch { /* Start with a clean demo if browser data is invalid. */ }
  const base = Date.now()
  data ||= { cart: [], orders: [], addresses: [], products: structuredClone(demoProducts), activities: catalog.flashSales.map((sale, index) => { const product = productBySlug[sale.product]; return { id: index + 1, dishId: product.id, activityName: sale.activityName, dishName: product.name, dishImage: product.image, originalPrice: product.price, salePrice: sale.salePrice, stock: sale.stock, availableStock: sale.stock, limitPerUser: sale.limitPerUser, startTime: localTime(new Date(base - 3600000)), endTime: localTime(new Date(base + 7 * 86400000)), status: 1 } }), requests: {}, shopStatus: 1 }
  data.categories ||= structuredClone(demoCategories)
  data.setmeals ||= structuredClone(demoSetmeals)
  data.employees ||= [{ id: 1, username: 'manager', name: '林店长', phone: '13800000000', sex: '1', idNumber: '110101********001X', status: 1 }]
  data.customers ||= [
    { id: 1001, name: '周女士', phone: '13800001001', sex: '0', createTime: localTime(new Date(base - 86400000 * 12)) },
    { id: 1002, name: '陈先生', phone: '13800001002', sex: '1', createTime: localTime(new Date(base - 86400000 * 8)) },
    { id: 1003, name: '林同学', phone: '13800001003', sex: '1', createTime: localTime(new Date(base - 86400000 * 3)) },
  ]
  const save = () => storage.setItem(KEY, JSON.stringify(data))
  const copy = value => structuredClone(value)
  const order = (items, addressId, remark = '') => {
    const address = data.addresses.find(a => a.id === Number(addressId))
    if (!address) throw new Error('请先添加收货地址')
    if (!items.length) throw new Error('购物车还是空的')
    const id = Date.now(), row = { id, number: `XJ${id}`, orderTime: localTime(), amount: cartTotal(items), status: 1, payStatus: 0, consignee: address.consignee, address: address.detail, phone: address.phone, remark, orderDetailList: copy(items) }
    data.orders.unshift(row); save(); return row
  }
  return {
    data,
    categories: (type = null) => copy(data.categories.filter(c => type == null || Number(c.type) === Number(type)).sort((a, b) => a.sort - b.sort)),
    products: () => copy(data.products),
    product: id => copy(data.products.find(p => Number(p.id) === Number(id))),
    setmeals: () => copy(data.setmeals),
    setmeal: id => copy(data.setmeals.find(p => Number(p.id) === Number(id))),
    employees: () => copy(data.employees),
    customers: () => copy(data.customers),
    activities: () => copy(data.activities),
    cart: () => copy(data.cart),
    orders: () => copy(data.orders),
    order: id => copy(data.orders.find(o => Number(o.id) === Number(id))),
    addresses: () => copy(data.addresses),
    addAddress(value) { const row = { ...value, id: Date.now(), isDefault: data.addresses.length ? 0 : 1 }; data.addresses.push(row); save(); return copy(row) },
    updateAddress(value) { const row = data.addresses.find(a => Number(a.id) === Number(value.id)); if (!row) throw new Error('地址不存在'); Object.assign(row, value); save() },
    setDefaultAddress(id) { data.addresses.forEach(a => { a.isDefault = Number(a.id) === Number(id) ? 1 : 0 }); save() },
    deleteAddress(id) { data.addresses = data.addresses.filter(a => Number(a.id) !== Number(id)); if (data.addresses.length && !data.addresses.some(a => a.isDefault)) data.addresses[0].isDefault = 1; save() },
    addCart(product, flavor = '') { const isSetmeal = Boolean(product.setmealId || product.kind === 'setmeal'); const id = Number(isSetmeal ? (product.setmealId || product.id) : (product.dishId || product.id)); const row = data.cart.find(c => Number(isSetmeal ? c.setmealId : c.dishId) === id && (c.dishFlavor || '') === flavor); if (row) row.number++; else data.cart.push({ id: Date.now(), dishId: isSetmeal ? null : id, setmealId: isSetmeal ? id : null, kind: isSetmeal ? 'setmeal' : 'dish', name: product.name, image: product.image, amount: Number(product.price), number: 1, dishFlavor: flavor }); save() },
    subtractCart(item) { const index = data.cart.findIndex(c => c.id === item.id); if (index >= 0 && --data.cart[index].number <= 0) data.cart.splice(index, 1); save() },
    clearCart() { data.cart = []; save() },
    submit(address, remark) { const row = order(data.cart, address, remark); data.cart = []; save(); return { ...row, orderNumber: row.number } },
    purchase(activityId, address, requestId) {
      if (data.requests[requestId]) return copy(data.requests[requestId])
      const activity = data.activities.find(a => a.id === activityId)
      if (!activity || !canPurchase(activity)) throw new Error('活动已结束或商品已售罄')
      const count = data.orders.filter(o => o.activityId === activityId && o.status !== 6).length
      if (count >= activity.limitPerUser) throw new Error('已达到本次活动限购数量')
      const product = data.products.find(p => p.id === activity.dishId)
      const row = order([{ ...product, dishId: product.id, amount: activity.salePrice, number: 1 }], address)
      row.activityId = activityId; activity.availableStock--
      const result = { status: 1, orderId: row.id, requestId, message: '抢购成功，请完成支付' }
      data.requests[requestId] = result; save(); return copy(result)
    },
    transition(id, action) {
      const row = data.orders.find(o => o.id === id)
      const expected = { pay: 1, confirm: 2, reject: 2, delivery: 3, complete: 4 }
      if (!row || (action !== 'cancel' && row.status !== expected[action]) || (action === 'cancel' && ![1, 2, 3].includes(row.status))) throw new Error('订单状态已变化，请刷新')
      row.status = { pay: 2, confirm: 3, reject: 6, delivery: 4, complete: 5, cancel: 6 }[action]
      if (action === 'pay') row.payStatus = 1
      if (action === 'cancel' && row.activityId) data.activities.find(a => a.id === row.activityId).availableStock++
      save(); return copy(row)
    },
    repeatOrder(id) { const row = data.orders.find(o => Number(o.id) === Number(id)); if (!row) throw new Error('订单不存在'); for (const item of row.orderDetailList || []) this.addCart(item, item.dishFlavor || '') },
    toggleProduct(id) { const row = data.products.find(p => p.id === id); row.status = row.status ? 0 : 1; save() },
    saveProduct(value) { const row = { ...value, id: value.id || Date.now(), price: Number(value.price), status: value.status ?? 1, image: value.image || demoProducts[0].image, flavors: value.flavors || [] }; const i = data.products.findIndex(p => Number(p.id) === Number(row.id)); if (i < 0) data.products.unshift(row); else data.products[i] = { ...data.products[i], ...row }; save() },
    deleteProduct(id) { if (data.activities.some(a => Number(a.dishId) === Number(id)) || data.orders.some(o => (o.orderDetailList || []).some(d => Number(d.dishId) === Number(id)))) throw new Error('商品已关联特卖或订单，不能删除'); data.products = data.products.filter(p => Number(p.id) !== Number(id)); save() },
    saveSetmeal(value) { const components = (value.setmealDishes || []).map(item => { const product = data.products.find(p => Number(p.id) === Number(item.dishId)); if (!product || product.status !== 1) throw new Error('套餐只能选择在售食材'); const copies = Math.max(1, Number(item.copies || 1)); return { ...item, dishId: product.id, name: product.name, price: Number(product.price), copies } }); if (!components.length) throw new Error('套餐至少要包含一种食材'); const singlesTotal = components.reduce((sum, item) => sum + Number(item.price) * item.copies, 0); const row = { ...value, id: value.id || Date.now(), price: Math.round((singlesTotal * 0.95 + Number.EPSILON) * 100) / 100, status: value.status ?? 1, setmealDishes: components }; const i = data.setmeals.findIndex(p => Number(p.id) === Number(row.id)); if (i < 0) data.setmeals.unshift(row); else data.setmeals[i] = { ...data.setmeals[i], ...row }; save() },
    toggleSetmeal(id) { const row = data.setmeals.find(p => Number(p.id) === Number(id)); if (!row) throw new Error('套餐不存在'); row.status = row.status ? 0 : 1; save() },
    deleteSetmeal(id) { data.setmeals = data.setmeals.filter(p => Number(p.id) !== Number(id)); save() },
    saveCategory(value) { const row = { ...value, id: value.id || Date.now(), type: Number(value.type), sort: Number(value.sort || 0), status: value.status ?? 1 }; const i = data.categories.findIndex(c => Number(c.id) === Number(row.id)); if (i < 0) data.categories.push(row); else data.categories[i] = { ...data.categories[i], ...row }; save() },
    toggleCategory(id) { const row = data.categories.find(c => Number(c.id) === Number(id)); if (!row) throw new Error('分类不存在'); row.status = row.status ? 0 : 1; save() },
    deleteCategory(id) { if (data.products.some(p => Number(p.categoryId) === Number(id)) || data.setmeals.some(p => Number(p.categoryId) === Number(id))) throw new Error('分类下还有商品或套餐，不能删除'); data.categories = data.categories.filter(c => Number(c.id) !== Number(id)); save() },
    saveEmployee(value) { const row = { ...value, id: value.id || Date.now(), status: value.status ?? 1 }; const i = data.employees.findIndex(e => Number(e.id) === Number(row.id)); if (i < 0) data.employees.unshift(row); else data.employees[i] = { ...data.employees[i], ...row }; save() },
    toggleEmployee(id) { const row = data.employees.find(e => Number(e.id) === Number(id)); if (!row) throw new Error('员工不存在'); row.status = row.status ? 0 : 1; save() },
    saveActivity(value) { const product = data.products.find(p => Number(p.id) === Number(value.dishId)); if (!product) throw new Error('请选择商品'); const row = { ...value, id: value.id || Date.now(), dishId: product.id, dishName: product.name, dishImage: product.image, originalPrice: product.price, availableStock: value.stock, status: value.status ?? 0 }; const index = data.activities.findIndex(activity => Number(activity.id) === Number(row.id)); if (index < 0) data.activities.unshift(row); else data.activities[index] = { ...data.activities[index], ...row }; save() },
    toggleActivity(id) { const row = data.activities.find(a => a.id === id); row.status = row.status ? 0 : 1; save() },
    toggleShop() { data.shopStatus = data.shopStatus ? 0 : 1; save(); return data.shopStatus },
  }
}
