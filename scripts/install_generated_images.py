import json
import sys
from pathlib import Path
from PIL import Image

project = Path(__file__).resolve().parents[1]
products_dir = project / 'frontend' / 'public' / 'products'
manifest_path = project / 'catalog' / 'generated-images.json'
catalog = json.loads((project / 'catalog' / 'fresh-market.json').read_text(encoding='utf-8'))
meal_slugs = {meal['slug'] for meal in catalog['setmeals']}
entries = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
manifest = json.loads(manifest_path.read_text(encoding='utf-8')) if manifest_path.exists() else {}
for entry in entries:
    slug = entry['slug']
    asset_slug = f'meal-{slug}' if slug in meal_slugs else slug
    source = Path(entry['source'])
    if not source.is_file():
        raise FileNotFoundError(source)
    image = Image.open(source).convert('RGB')
    image.thumbnail((720, 720), Image.Resampling.LANCZOS)
    image.save(products_dir / f'{asset_slug}.webp', 'WEBP', quality=88, method=6)
    manifest[slug] = f'/products/{asset_slug}.webp'
manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'Installed {len(entries)} generated images; catalogue now has {len(manifest)} image paths.')
