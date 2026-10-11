"""Metadata-only fresh final selection. No features/predictions are consulted."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
rows = json.loads((ROOT / 'datasets/selection.json').read_text())
old = json.loads((ROOT / 'datasets/manifest.json').read_text())
used = {r['uploader'] for r in old if r['split'] != 'rejection-final'}
infos = {s: json.loads((ROOT / f'data/downloads/FSD50K.metadata/{s}_clips_info_FSD50K.json').read_text()) for s in ('dev', 'eval')}
selection = {
    'scream_distress': [175385, 241591, 235595, 243376, 82418, 400183, 159460, 132828],
    'impact_crash': [336425, 376462, 978, 20133, 141563, 186767, 182896, 189575],
    'siren_traffic': [147933, 93110],
    'background': [364662, 182395, 389254, 264332, 261286, 348332,
        165874, 253763, 155833, 243757, 249950, 34930,
        368841, 234034, 219766, 397317, 125093, 337153, 175382, 183601,
        138465, 262759, 334903, 89872, 201183, 342542, 415826, 241895, 221567, 413854],
}
reasons = {
    'scream_distress': 'Recorded human scream; mostly acted/scary/fear/pain according to descriptions/tags; does not establish actual distress.',
    'impact_crash': 'Recorded glass/bottle/window/light-bulb breakage, not vehicle collision.',
    'siren_traffic': 'Uploader describes recorded passing ambulance (147933) or city street distant siren (93110); original tags corroborate traffic context.',
    'background': 'Recorded dogs, thunder, ordinary/cheerful voice, doors, traffic, hammering, applause or rain; no target label inferred from loudness.',
}
existing = {r['id'] for r in rows}
for category, ids in selection.items():
    for sound_id in ids:
        if sound_id in existing:
            assert next(r for r in rows if r['id'] == sound_id)['split'] == 'rejection-final'
            continue
        subset = next(s for s in infos if str(sound_id) in infos[s])
        info = infos[subset][str(sound_id)]
        assert info['uploader'] not in used
        assert '/licenses/by/' in info['license'] or '/publicdomain/zero/' in info['license']
        rows.append(dict(id=sound_id, source_split=subset, split='rejection-final', category=category,
                         group='uploader:' + info['uploader'], rationale=reasons[category]))
(ROOT / 'datasets/selection.json').write_text(json.dumps(rows, indent=2, ensure_ascii=False) + '\n')
