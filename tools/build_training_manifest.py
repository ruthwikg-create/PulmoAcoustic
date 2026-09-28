#!/usr/bin/env python3
from pathlib import Path
import csv, json

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'research' / 'datasets' / 'dataset_catalog.json'
DATA_ROOT = ROOT / 'research_data'
OUT = DATA_ROOT / 'training_manifest.csv'

def main():
    DATA_ROOT.mkdir(parents=True, exist_ok=True)
    catalog = {d['id']: d for d in json.loads(CATALOG.read_text(encoding='utf-8'))['datasets']}
    rows = []
    for dataset_id, dataset in catalog.items():
        folder = DATA_ROOT / dataset_id
        if not folder.exists():
            continue
        for p in folder.rglob('*'):
            if p.is_file():
                rows.append({
                    'dataset_id': dataset_id,
                    'dataset_name': dataset['name'],
                    'relative_path': str(p.relative_to(folder)),
                    'size_bytes': p.stat().st_size,
                    'source_url': dataset['url'],
                })
    fields = ['dataset_id','dataset_name','relative_path','size_bytes','source_url']
    with OUT.open('w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)
    print(f'Wrote {len(rows)} entries to {OUT}')

if __name__ == '__main__':
    main()