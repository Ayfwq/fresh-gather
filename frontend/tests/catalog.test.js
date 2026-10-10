import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { demoSetmeals } from '../src/demo.js'

const project = resolve(fileURLToPath(new URL('../..', import.meta.url)))
const catalog = JSON.parse(readFileSync(resolve(project, 'catalog/fresh-market.json'), 'utf8'))
const generatedImages = JSON.parse(readFileSync(resolve(project, 'catalog/generated-images.json'), 'utf8'))
const products = new Map(catalog.products.map(product => [product.slug, product]))

test('fresh-market catalogue contains the approved categories, products, bundles, and flash sales', () => {
  assert.equal(catalog.products.length, 102)
  assert.equal(catalog.setmeals.length, 9)
  assert.equal(catalog.flashSales.length, 4)
  assert.equal(new Set(catalog.products.map(product => product.slug)).size, catalog.products.length)
  for (const product of catalog.products) {
    assert.ok(catalog.categories.some(category => category.key === product.category), `${product.name} has a category`)
    assert.ok(product.unit && product.price > 0, `${product.name} has a unit and price`)
  }
})

test('each raw-ingredient bundle totals 95% of its selected single-item prices', () => {
  assert.equal(demoSetmeals.length, catalog.setmeals.length)
  for (const meal of catalog.setmeals) {
    const total = meal.components.reduce((sum, [slug, copies]) => {
      assert.ok(products.has(slug), `${meal.name} includes known product ${slug}`)
      return sum + products.get(slug).price * copies
    }, 0)
    assert.ok(meal.components.length >= 3, `${meal.name} combines several ingredients`)
    const demoMeal = demoSetmeals.find(candidate => candidate.name === meal.name)
    assert.ok(demoMeal, `${meal.name} is represented in the client catalogue`)
    assert.equal(demoMeal.price, Math.round((total * 0.95 + Number.EPSILON) * 100) / 100)
  }
})

test('all four flash sales are mapped to the requested fresh products', () => {
  assert.deepEqual(catalog.flashSales.map(sale => products.get(sale.product)?.name), ['西瓜', '牛腩', '黄骨鱼', '鸡翅中'])
  for (const sale of catalog.flashSales) {
    assert.ok(sale.salePrice > 0 && sale.salePrice < products.get(sale.product).price)
    assert.ok(sale.stock > 0 && sale.limitPerUser > 0)
  }
})

test('every catalogue item and bundle has its own generated local image', () => {
  const allAssets = [...catalog.products, ...catalog.setmeals]
  assert.equal(Object.keys(generatedImages).length, allAssets.length)
  for (const item of allAssets) {
    const slug = item.slug
    const asset = generatedImages[slug]
    assert.equal(asset, `/products/${catalog.products.includes(item) ? slug : `meal-${slug}`}.webp`)
    assert.ok(existsSync(resolve(project, 'frontend/public', asset.slice(1))), `${item.name} image exists`)
  }
})
