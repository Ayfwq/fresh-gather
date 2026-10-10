import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { hasProductImage, productImageFor, productImageNames } from '../src/productImages.js'
import catalog from '../../catalog/fresh-market.json' with { type: 'json' }

test('every configured catalog product has its own local image', () => {
  assert.ok(productImageNames.length >= catalog.products.length + catalog.setmeals.length)
  const paths = productImageNames.map(productImageFor)
  const freshPaths = [...catalog.products.map(product => productImageFor(product.name)), ...catalog.setmeals.map(meal => productImageFor(meal.name))]
  assert.ok(paths.every(path => path.startsWith('/products/')))
  assert.equal(new Set(freshPaths).size, freshPaths.length)
  assert.ok(paths.every(path => existsSync(resolve('public', path.slice(1)))))
})

test('product image lookup tolerates spaces and falls back by product type', () => {
  assert.equal(productImageFor('  草鱼 2斤 '), productImageFor('草鱼2斤'))
  assert.equal(productImageFor('套餐香锅牛蛙'), productImageFor('香锅牛蛙'))
  assert.equal(hasProductImage('清蒸鲈鱼'), true)
  assert.equal(hasProductImage('未知商品'), false)
  assert.equal(productImageFor('未知商品'), '/fresh-harvest-hero.png')
})
