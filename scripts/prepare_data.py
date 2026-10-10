#!/usr/bin/env python3
"""One-time FSD50K subset acquisition. Python stdlib + ffmpeg; application stays JDK-only.

Fetches ZIP members with HTTP ranges, never the 25 GB archive. Frozen selection
is reviewed from FSD50K labels AND uploader descriptions, not model predictions.
"""
import argparse
import concurrent.futures
import csv
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import time
import urllib.request
import wave
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / 'data/downloads'
BASE = 'https://zenodo.org/records/4060432/files/'
CLASSES = ('scream_distress', 'explosion', 'impact_crash', 'siren_traffic', 'background')


def fetch(name, start=None, length=None):
    headers = {'User-Agent': 'OmniEar-reproducible-subset/1.0'}
    if start is not None:
        headers['Range'] = f'bytes={start}-{start + length - 1}'
    for attempt in range(6):
        try:
            with urllib.request.urlopen(urllib.request.Request(BASE + name + '?download=1', headers=headers), timeout=90) as response:
                if start is not None:
                    if response.status != 206 or not response.headers.get('Content-Range', '').startswith(f'bytes {start}-'):
                        raise ValueError('Server did not honor range; refusing full archive download')
                data = response.read()
                if length is not None and len(data) != length:
                    raise ValueError('Truncated range response')
                return data
        except Exception:
            if attempt == 5:
                raise
            time.sleep(5 * (attempt + 1))


def metadata():
    CACHE.mkdir(parents=True, exist_ok=True)
    for name in ('metadata', 'ground_truth', 'doc'):
        archive = CACHE / f'FSD50K.{name}.zip'
        if not archive.exists():
            archive.write_bytes(fetch(archive.name))
        expected = {'metadata': 'b9ea0c829a411c1d42adb9da539ed237',
                    'ground_truth': 'ca27382c195e37d2269c4c866dd73485', 'doc': '3516162b82dc2945d3e7feba0904e800'}
        assert hashlib.md5(archive.read_bytes()).hexdigest() == expected[name], f'Published archive checksum mismatch: {archive}'
        with zipfile.ZipFile(archive) as archive_file:
            for entry in archive_file.infolist():
                target = (CACHE / entry.filename).resolve()
                if not target.is_relative_to(CACHE.resolve()):
                    raise ValueError('Unsafe archive member')
            archive_file.extractall(CACHE)


class RemoteZip:
    """Read the published split ZIP central directory and CRC-check each member."""
    def __init__(self, subset):
        self.prefix = f'FSD50K.{subset}_audio'
        self.last_disk, self.last_size = (5, 2306663327) if subset == 'dev' else (1, 3037675767)
        index = CACHE / f'{subset}-central.bin'
        if not index.exists():
            tail = fetch(self.prefix + '.zip', self.last_size - 22, 22)
            end = struct.unpack('<4s4H2LH', tail)
            assert end[0] == b'PK\x05\x06' and end[1] == end[2] == self.last_disk
            index.write_bytes(fetch(self.prefix + '.zip', end[6], end[5]))
        data, pos = index.read_bytes(), 0
        self.entries = {}
        while pos < len(data):
            h = struct.unpack_from('<4s6H3L5H2L', data, pos)
            assert h[0] == b'PK\x01\x02'
            name = data[pos + 46:pos + 46 + h[10]].decode()
            self.entries[name] = h
            pos += 46 + h[10] + h[11] + h[12]

    def span(self, disk, offset, length):
        result = b''
        while length:
            size = self.last_size if disk == self.last_disk else 3221225472
            take = min(length, size - offset)
            name = self.prefix + ('.zip' if disk == self.last_disk else f'.z{disk + 1:02}')
            result += fetch(name, offset, take)
            length -= take
            disk, offset = disk + 1, 0
        return result

    def read(self, sound_id):
        h = self.entries[f'{self.prefix}/{sound_id}.wav']
        header = self.span(h[13], h[16], 30)
        local = struct.unpack('<4s5H3L2H', header)
        assert local[0] == b'PK\x03\x04' and not h[3] & 1
        offset = h[16] + 30 + local[9] + local[10]
        disk = h[13]
        if offset >= 3221225472:
            disk, offset = disk + 1, offset - 3221225472
        compressed = self.span(disk, offset, h[8])
        data = zlib.decompress(compressed, -15) if h[4] == 8 else compressed
        assert h[4] in (0, 8) and len(data) == h[9] and zlib.crc32(data) == h[7]
        return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify-only', action='store_true')
    args = parser.parse_args()
    selection = json.loads((ROOT / 'datasets/selection.json').read_text())
    assert len({r['id'] for r in selection}) == len(selection), 'Repeated source recording'
    existing_manifest = ROOT / 'datasets/manifest.json'
    if existing_manifest.exists():
        for row in json.loads(existing_manifest.read_text()):
            path = ROOT / row['path']
            if path.exists():
                assert hashlib.sha256(path.read_bytes()).hexdigest() == row['sha256'], f'Locally modified asset; refusing to overwrite/re-bless: {path}'
    if args.verify_only:
        records = json.loads((ROOT / 'datasets/manifest.json').read_text())
    else:
        metadata()
        infos, labels, archives = {}, {}, {}
        for subset in ('dev', 'eval'):
            infos[subset] = json.loads((CACHE / f'FSD50K.metadata/{subset}_clips_info_FSD50K.json').read_text())
            labels[subset] = {r['fname']: r for r in csv.DictReader((CACHE / f'FSD50K.ground_truth/{subset}.csv').open())}
            archives[subset] = RemoteZip(subset)

        def prepare(row):
            subset, sound_id = row['source_split'], str(row['id'])
            info = infos[subset][sound_id]
            assert '/licenses/by/' in info['license'] or '/publicdomain/zero/' in info['license']
            original = CACHE / f'{sound_id}.wav'
            if not original.exists():
                original.write_bytes(archives[subset].read(sound_id))
            relative = f"datasets/{row['split']}/{row['category']}/fsd-{sound_id}.wav"
            target = ROOT / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            # Full recording, no cropping/normalization/augmentation, deterministic PCM16.
            if not target.exists():
                subprocess.run(['ffmpeg', '-v', 'error', '-nostdin', '-i', str(original), '-map_metadata', '-1',
                                '-ac', '1', '-ar', '16000', '-c:a', 'pcm_s16le', '-bitexact', str(target)], check=True)
            with wave.open(str(target)) as wav:
                duration = wav.getnframes() / wav.getframerate()
                pcm = wav.readframes(wav.getnframes())
            print(relative, flush=True)
            return {**row, 'path': relative, 'title': info['title'], 'uploader': info['uploader'],
                    'description': info['description'], 'license': info['license'],
                    'url': f'https://freesound.org/s/{sound_id}/', 'dataset': 'https://doi.org/10.5281/zenodo.4060432',
                    'original_labels': labels[subset][sound_id]['labels'], 'seconds': duration,
                    'source_sha256': hashlib.sha256(original.read_bytes()).hexdigest(),
                    'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                    'pcm_sha256': hashlib.sha256(pcm).hexdigest()}

        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            records = list(pool.map(prepare, selection))
        (ROOT / 'datasets/manifest.json').write_text(json.dumps(records, indent=2, ensure_ascii=False) + '\n')
        (ROOT / 'datasets/groups.tsv').write_text(''.join(f"{r['path']}\t{r['category']}\t{r['group']}\n" for r in records))
        license_text = (CACHE / 'FSD50K.doc/LICENSE-DATASET').read_text()
        (ROOT / 'datasets/LICENSE-DATASET').write_text('\n'.join(line.rstrip() for line in license_text.strip().splitlines()) + '\n')
        credit = ['# Audio attribution', '',
                  'FSD50K v1.0: Eduardo Fonseca, Xavier Favory, Jordi Pons, Frederic Font and Xavier Serra.',
                  'Collection: https://doi.org/10.5281/zenodo.4060432 — CC BY 4.0 (LICENSE-DATASET).',
                  'Each recording retains the individual license linked below. No endorsement is implied.',
                  'Changes by OmniEar: complete recordings converted to mono 16 kHz PCM16 WAV with ffmpeg; no gain normalization or augmentation.',
                  'Full original descriptions, label rationale, groups and hashes: manifest.json.', '',
                  '| Split / class | Source recording / title | Author / uploader | License |', '|---|---|---|---|']
        for r in records:
            title = r['title'].replace('|', '\\|').replace('<', '&lt;').replace('>', '&gt;').replace('\n', ' ')
            author = 'Adam Wayne Gistarb (deleted_user_4401185)' if r['uploader'] == 'deleted_user_4401185' else r['uploader']
            license_name = 'CC0 1.0' if '/zero/' in r['license'] else 'CC BY 3.0'
            credit.append(f"| {r['split']} / {r['category']} | [{r['id']}]({r['url']}) {title} | {author} | [{license_name}]({r['license']}) |")
        (ROOT / 'datasets/ATTRIBUTION.md').write_text('\n'.join(credit) + '\n')
    seen, users, groups = set(), {}, {}
    assert [{k: r[k] for k in selection[0]} for r in records] == selection, 'Selection/manifest mismatch; rerun preparation'
    actual = {str(p.relative_to(ROOT)) for s in ('training', 'held-out') for p in (ROOT / 'datasets' / s).rglob('*.wav')}
    assert actual == {r['path'] for r in records}, 'Missing or unlisted WAVs in curated directories'
    for row in records:
        path = ROOT / row['path']
        assert hashlib.sha256(path.read_bytes()).hexdigest() == row['sha256'], path
        assert row['pcm_sha256'] not in seen, f'Duplicate decoded audio: {path}'
        seen.add(row['pcm_sha256'])
        for values, key in ((users, row['uploader']), (groups, row['group'])):
            assert values.setdefault(key, row['split']) == row['split'], f'Split leakage: {key}'
    for category in CLASSES:
        print(category, {s: sum(r['category'] == category and r['split'] == s for r in records)
                         for s in ('training', 'held-out')})
    print('PASS: hashes, decoded-audio uniqueness, uploader and session/source-group separation')


if __name__ == '__main__':
    main()
