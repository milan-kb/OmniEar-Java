"""Frozen metadata-only 2026-10-15 evaluation selection. Never consults predictions."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
selection = json.loads((ROOT / 'datasets/selection.json').read_text())
old = json.loads((ROOT / 'datasets/manifest.json').read_text())
used = {r['uploader'] for r in old if r['split'] != 'acoustic-final'}
infos = {s: json.loads((ROOT / f'data/downloads/FSD50K.metadata/{s}_clips_info_FSD50K.json').read_text()) for s in ('eval', 'dev')}
chosen = {
    'scream_distress': [360465, 338049, 104028, 399675, 149825, 257691, 398007, 21323],
    'impact_crash': [315647, 356312, 404923, 209996, 346173, 367448, 422240, 231297, 369070, 391327],
    'background': [34865, 198844, 347964, 380772, 156091, 52286, 31569, 62327,
                   249490, 397034, 128896, 401794, 425662, 131199, 73373, 425904,
                   352421, 398885, 263079, 30216, 113173, 121943, 210314, 217800],
}
reasons = {
    'scream_distress': 'Recorded human scream; fear/pain/horror or acted scream in uploader description/tags. Not proof of emergency intent. No UPF/666moviescreams workshop sources.',
    'impact_crash': 'Recorded glass/ceramic breakage, not vehicle collision. Excludes synthetic/composite and known source remixes.',
    'background': 'Recorded traffic/horns, doors, hammering, speech/laughter, dogs, wind, rain, thunder or chimes; no target annotation. Not a claim of harmlessness.',
}
for category, ids in chosen.items():
    for sound_id in ids:
        if any(r['id'] == sound_id for r in selection):
            assert next(r for r in selection if r['id'] == sound_id)['split'] == 'acoustic-final'
            continue
        subset = next(s for s in infos if str(sound_id) in infos[s])
        info = infos[subset][str(sound_id)]
        assert info['uploader'] not in used, info['uploader']
        used.add(info['uploader'])
        assert '/licenses/by/' in info['license'] or '/publicdomain/zero/' in info['license']
        assert '666moviescream' not in str(info).lower() and 'pompeu' not in str(info).lower()
        selection.append(dict(id=sound_id, source_split=subset, split='acoustic-final', category=category,
                              group='uploader:' + info['uploader'], rationale=reasons[category]))
(ROOT / 'datasets/selection.json').write_text(json.dumps(selection, indent=2, ensure_ascii=False) + '\n')
