"""Frozen metadata-reviewed additions; run before prepare_data.py. Never selects on predictions."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
selection = json.loads((ROOT / 'datasets/selection.json').read_text())
# Metadata-only pre-evaluation correction: avoid possible school/workshop overlap.
excluded = {381951, 379015, 380664}
selection = [r for r in selection if r['id'] not in excluded]
infos = {s: json.loads((ROOT / f'data/downloads/FSD50K.metadata/{s}_clips_info_FSD50K.json').read_text())
         for s in ('dev', 'eval')}
additions = {
    ('training', 'explosion'): [182429, 182431, 332181, 171971],
    ('training', 'siren_traffic'): [393666],
    ('training', 'background'): [182474, 212182, 264993, 162443, 268015, 275558,
        350724, 380941, 379754, 383062, 76683, 395415, 329760, 199953,
        194842, 401719, 369574, 346154, 104525, 332914, 72723, 328728,
        370714, 170015, 121888, 87315, 221639, 251969, 60689, 354056,
        184833, 402642, 17643, 223521, 256513, 336390, 273320, 100144,
        386226, 143591, 11585, 219551],
    ('development', 'background'): [198496, 151849, 369247, 36902, 36161, 76813, 109733, 160679],
    ('final-test', 'explosion'): [73005, 251759],
    ('final-test', 'siren_traffic'): [377125, 377126, 43806],
    ('final-test', 'scream_distress'): [110139, 110140, 208893, 169628, 367307, 90790, 328003, 345450],
    ('final-test', 'impact_crash'): [367624, 355340, 387177, 372919, 115916, 273647, 260432, 221528],
    ('final-test', 'background'): [26176, 151359, 15547, 199688, 423964, 71602,
        96933, 151589, 316558, 221207, 128042, 257034, 156212, 349401,
        168251, 361551, 253611, 422724, 83479, 198353],
}
rationales = {
    'explosion': 'Uploader documents a recorded explosive detonation; excludes gunshots, synthesized booms and fireworks. 171971 credits authentic 4kg TNT source 165808.',
    'siren_traffic': 'Recorded traffic emergency siren; 393666 is an uploader-edited loop of recorded NYC siren, not a synthesized tone.',
    'scream_distress': 'Recorded human acted/scary/strong scream, an acoustic proxy only; not proof of real distress.',
    'impact_crash': 'Recorded glass breakage; not a vehicle collision label.',
    'background': 'Genuine non-target environmental/household/voice noise; includes thunder, door/hammer knocks, dogs, cheering and fireworks as hard negatives.',
}
existing = {r['id'] for r in selection}
for (split, category), ids in additions.items():
    for sound_id in ids:
        if sound_id in existing:
            continue
        subset = next(s for s in infos if str(sound_id) in infos[s])
        info = infos[subset][str(sound_id)]
        assert '/licenses/by/' in info['license'] or '/publicdomain/zero/' in info['license']
        prior = next((r for r in selection if infos[r['source_split']][str(r['id'])]['uploader'] == info['uploader']), None)
        if prior:
            assert prior['split'] == split, (sound_id, info['uploader'], prior)
        selection.append(dict(id=sound_id, source_split=subset, split=split, category=category,
                              group=prior['group'] if prior else 'uploader:' + info['uploader'],
                              rationale=rationales[category]))
        existing.add(sound_id)
(ROOT / 'datasets/selection.json').write_text(json.dumps(selection, indent=2, ensure_ascii=False) + '\n')
