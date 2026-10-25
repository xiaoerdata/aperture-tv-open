"""Re-fetch the S48 licensed assets without editing pixels. Uses fixed audited URLs/checksums.
Existing matching files are reused. Stops on HTTP/checksum failures; does not loop on rate limits.
"""
from pathlib import Path
import hashlib
import json
import urllib.request

root = Path(__file__).resolve().parent.parent
manifest = json.loads((root / 'docs/verification/s48-wallpapers.json').read_text())

def fetch(url, destination, expected):
    if destination.exists() and hashlib.sha256(destination.read_bytes()).hexdigest() == expected:
        print('cached', destination.name)
        return
    request = urllib.request.Request(url, headers={'User-Agent': 'Kaishi-TV-development/0.2 (licensed wallpaper acquisition)'})
    with urllib.request.urlopen(request, timeout=40) as response:
        content = response.read(16 * 1024 * 1024 + 1)
    if len(content) > 16 * 1024 * 1024 or hashlib.sha256(content).hexdigest() != expected:
        raise ValueError('Unexpected content for ' + destination.name)
    destination.write_bytes(content)
    print('downloaded', destination.name)

for item in manifest:
    if item['license'] != 'CC0-1.0':
        raise ValueError('Unaudited license')
    fetch(item['download'], root / item['file'], item['sha256'])
    if 'thumbnail' in item:
        thumb = item['thumbnail']
        fetch(thumb['url'], root / ('public/launcher/' + item['id'] + '-thumb.jpg'), thumb['sha256'])
