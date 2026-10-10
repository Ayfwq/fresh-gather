export const orderLabels = { 1: '待付款', 2: '待接单', 3: '备货中', 4: '配送中', 5: '已完成', 6: '已取消' }
export const money = value => Number(value || 0).toFixed(2)
export const localTime = (date = new Date()) => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(date)
export const cartTotal = rows => Math.round(rows.reduce((sum, row) => sum + Number(row.amount) * row.number, 0) * 100) / 100
export function canPurchase(activity, now = Date.now()) {
  return activity.status === 1 && activity.availableStock > 0 && now >= new Date(activity.startTime.replace(' ', 'T') + '+08:00').getTime() && now < new Date(activity.endTime.replace(' ', 'T') + '+08:00').getTime()
}
export function filterProducts(products, category, query, sort) {
  const list = products.filter(p => (!category || p.categoryId === category) && (!query || `${p.name} ${p.description || ''}`.toLowerCase().includes(query.toLowerCase())))
  if (sort === 'price') list.sort((a, b) => a.price - b.price)
  if (sort === 'price-desc') list.sort((a, b) => b.price - a.price)
  return list
}
