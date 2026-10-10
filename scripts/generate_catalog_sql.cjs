const fs = require('node:fs')
const path = require('node:path')
const catalog = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../catalog/fresh-market.json'), 'utf8'))
const q = value => `'${String(value).replaceAll("'", "''")}'`
const esc = value => value == null ? 'NULL' : q(value)
const slugProduct = Object.fromEntries(catalog.products.map(row => [row.slug, row]))
const categoryByKey = Object.fromEntries(catalog.categories.map(row => [row.key, row]))
const lines = [
  '-- Generated from catalog/fresh-market.json. Safe to re-run; historical orders are retained.',
  'SET NAMES utf8mb4;',
  'SET @has_unit := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = \'dish\' AND column_name = \'unit\');',
  "SET @unit_ddl := IF(@has_unit = 0, 'ALTER TABLE dish ADD COLUMN unit VARCHAR(32) NULL COMMENT ''销售规格'' AFTER name', 'SELECT 1');",
  'PREPARE unit_stmt FROM @unit_ddl;',
  'EXECUTE unit_stmt;',
  'DEALLOCATE PREPARE unit_stmt;',
  '',
  '-- Retire the previous prepared-food menu but keep rows and all linked order history.',
  'UPDATE category SET status = 0 WHERE type IN (1, 2);',
  'UPDATE dish SET status = 0;',
  'UPDATE setmeal SET status = 0;',
  'UPDATE flash_sale_activity SET status = 0;',
  '',
  '-- Create or reactivate the selected catalogue categories.'
]
for (const [sort, category] of catalog.categories.entries()) {
  lines.push(`INSERT INTO category (type, name, sort, status, create_time, update_time) SELECT ${category.type}, ${q(category.name)}, ${sort + 1}, 1, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM category WHERE type = ${category.type} AND name = ${q(category.name)});`)
  lines.push(`UPDATE category SET sort = ${sort + 1}, status = 1, update_time = NOW() WHERE type = ${category.type} AND name = ${q(category.name)};`)
}
lines.push('', '-- Upsert raw ingredients and packaged beverages.', 'START TRANSACTION;')
for (const product of catalog.products) {
  const category = categoryByKey[product.category]
  if (!category) throw new Error(`Missing product category: ${product.slug}`)
  lines.push(`INSERT INTO dish (name, unit, category_id, price, image, description, status, create_time, update_time) SELECT ${q(product.name)}, ${q(product.unit)}, c.id, ${Number(product.price).toFixed(2)}, ${q(`/products/${product.slug}.webp`)}, ${q(product.description)}, 1, NOW(), NOW() FROM category c WHERE c.type = 1 AND c.name = ${q(category.name)} AND NOT EXISTS (SELECT 1 FROM dish old WHERE old.category_id = c.id AND old.name = ${q(product.name)});`)
  lines.push(`UPDATE dish d JOIN category c ON c.id = d.category_id SET d.unit = ${q(product.unit)}, d.price = ${Number(product.price).toFixed(2)}, d.image = ${q(`/products/${product.slug}.webp`)}, d.description = ${q(product.description)}, d.status = 1, d.update_time = NOW() WHERE c.type = 1 AND c.name = ${q(category.name)} AND d.name = ${q(product.name)};`)
}
lines.push('', '-- Add ingredient bundles with server-verified 95% pricing.', '-- Re-running this section updates the defined component recipe for these new bundles.')
const mealCategory = categoryByKey['meal-kits']
for (const [sort, meal] of catalog.setmeals.entries()) {
  lines.push(`INSERT INTO setmeal (category_id, name, price, status, description, image, create_time, update_time) SELECT c.id, ${q(meal.name)}, 0.00, 0, ${q(meal.description)}, ${q(`/products/meal-${meal.slug}.webp`)}, NOW(), NOW() FROM category c WHERE c.type = 2 AND c.name = ${q(mealCategory.name)} AND NOT EXISTS (SELECT 1 FROM setmeal old WHERE old.category_id = c.id AND old.name = ${q(meal.name)});`)
  lines.push(`UPDATE setmeal sm JOIN category c ON c.id = sm.category_id SET sm.description = ${q(meal.description)}, sm.image = ${q(`/products/meal-${meal.slug}.webp`)}, sm.status = 0, sm.update_time = NOW() WHERE c.type = 2 AND c.name = ${q(mealCategory.name)} AND sm.name = ${q(meal.name)};`)
}
const mealNames = catalog.setmeals.map(meal => q(meal.name)).join(', ')
lines.push(`DELETE sd FROM setmeal_dish sd JOIN setmeal sm ON sm.id = sd.setmeal_id WHERE sm.name IN (${mealNames});`)
for (const meal of catalog.setmeals) {
  for (const [slug, copies] of meal.components) {
    const product = slugProduct[slug]
    if (!product) throw new Error(`Missing ingredient ${slug} for ${meal.slug}`)
    lines.push(`INSERT INTO setmeal_dish (setmeal_id, dish_id, name, price, copies) SELECT sm.id, d.id, d.name, d.price, ${Number(copies)} FROM setmeal sm JOIN category sc ON sc.id = sm.category_id JOIN dish d ON d.name = ${q(product.name)} JOIN category dc ON dc.id = d.category_id WHERE sc.type = 2 AND sc.name = ${q(mealCategory.name)} AND sm.name = ${q(meal.name)} AND dc.name = ${q(categoryByKey[product.category].name)} AND dc.type = 1;`)
  }
  lines.push(`UPDATE setmeal sm JOIN category c ON c.id = sm.category_id JOIN (SELECT sd.setmeal_id, ROUND(SUM(d.price * sd.copies) * 0.95, 2) AS bundle_price FROM setmeal_dish sd JOIN dish d ON d.id = sd.dish_id GROUP BY sd.setmeal_id) totals ON totals.setmeal_id = sm.id SET sm.price = totals.bundle_price, sm.status = 1, sm.update_time = NOW() WHERE c.type = 2 AND c.name = ${q(mealCategory.name)} AND sm.name = ${q(meal.name)};`)
}
lines.push('', '-- Seed four active flash sales; preserve consumed stock when the migration is re-run.')
for (const sale of catalog.flashSales) {
  const product = slugProduct[sale.product]
  if (!product) throw new Error(`Missing sale product ${sale.product}`)
  lines.push(`INSERT INTO flash_sale_activity (activity_name, dish_id, sale_price, stock, available_stock, limit_per_user, start_time, end_time, status, create_time, update_time) SELECT ${q(sale.activityName)}, d.id, ${Number(sale.salePrice).toFixed(2)}, ${Number(sale.stock)}, ${Number(sale.stock)}, ${Number(sale.limitPerUser)}, NOW() - INTERVAL 1 HOUR, NOW() + INTERVAL 7 DAY, 1, NOW(), NOW() FROM dish d WHERE d.name = ${q(product.name)} AND NOT EXISTS (SELECT 1 FROM flash_sale_activity old WHERE old.activity_name = ${q(sale.activityName)} AND old.dish_id = d.id);`)
  lines.push(`UPDATE flash_sale_activity f JOIN dish d ON d.id = f.dish_id SET f.sale_price = ${Number(sale.salePrice).toFixed(2)}, f.stock = ${Number(sale.stock)}, f.limit_per_user = ${Number(sale.limitPerUser)}, f.start_time = NOW() - INTERVAL 1 HOUR, f.end_time = NOW() + INTERVAL 7 DAY, f.status = 1, f.update_time = NOW() WHERE f.activity_name = ${q(sale.activityName)} AND d.name = ${q(product.name)};`)
}
lines.push('COMMIT;', '')
const out = path.resolve(__dirname, '../backend/sky-server/src/main/resources/sql/fresh_market_catalog.sql')
fs.writeFileSync(out, lines.join('\n'), 'utf8')
console.log(`Wrote ${out} (${catalog.products.length} products, ${catalog.setmeals.length} bundles, ${catalog.flashSales.length} flash sales).`)
