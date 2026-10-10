import catalog from '../../catalog/fresh-market.json' with { type: 'json' }

const imageByName = Object.freeze({
  '王老吉': '/products/herbal-tea.webp',
  '北冰洋': '/products/orange-soda.webp',
  '雪花啤酒': '/products/lager-beer.webp',
  '米饭': '/products/rice.webp',
  '馒头': '/products/mantou.webp',
  '老坛酸菜鱼': '/products/pickled-mustard-fish.webp',
  '经典酸菜鮰鱼': '/products/classic-pickled-catfish.webp',
  '蜀味水煮草鱼': '/products/sichuan-boiled-grass-carp.webp',
  '清炒小油菜': '/products/baby-bok-choy.webp',
  '蒜蓉娃娃菜': '/products/garlic-baby-cabbage.webp',
  '清炒西兰花': '/products/garlic-broccoli.webp',
  '炝炒圆白菜': '/products/stir-fried-cabbage.webp',
  '清蒸鲈鱼': '/products/steamed-sea-bass.webp',
  '东坡肘子': '/products/dongpo-pork-hock.webp',
  '梅菜扣肉': '/products/braised-pork-preserved-mustard.webp',
  '剁椒鱼头': '/products/chopped-chili-fish-head.webp',
  '金汤酸菜牛蛙': '/products/golden-soup-bullfrog.webp',
  '香锅牛蛙': '/products/spicy-dry-pot-bullfrog.webp',
  '馋嘴牛蛙': '/products/spicy-bullfrog.webp',
  '草鱼2斤': '/products/fresh-grass-carp.webp',
  '江团鱼2斤': '/products/fresh-river-catfish.webp',
  '鮰鱼2斤': '/products/fresh-catfish.webp',
  '鸡蛋汤': '/products/egg-drop-soup.webp',
  '平菇豆腐汤': '/products/oyster-mushroom-tofu-soup.webp',
})
const normalize = name => String(name || '').trim().replace(/\s+/g, '')
const freshProductImages = new Map(catalog.products.map(product => [normalize(product.name), `/products/${product.slug}.webp`]))
const freshSetmealImages = new Map(catalog.setmeals.map(meal => [normalize(meal.name), `/products/meal-${meal.slug}.webp`]))

export function productImageFor(name = '') {
  const title = normalize(name)
  const historicalName = title.replace(/^套餐/, '')
  return freshProductImages.get(title) || freshSetmealImages.get(title) || imageByName[title] || imageByName[historicalName] || '/fresh-harvest-hero.png'
}

export function setmealImageFor(name = '') {
  const title = normalize(name)
  return freshSetmealImages.get(title) || productImageFor(title)
}

export function hasProductImage(name = '') {
  const title = normalize(name)
  const historicalName = title.replace(/^套餐/, '')
  return freshProductImages.has(title) || freshSetmealImages.has(title) || Object.hasOwn(imageByName, title) || Object.hasOwn(imageByName, historicalName)
}

export const productImageNames = Object.freeze([...freshProductImages.keys(), ...freshSetmealImages.keys(), ...Object.keys(imageByName)])
