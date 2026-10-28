"""Optional test setup: python3 + OpenCV; writes only artifacts, never product assets.

Big Buck Bunny (c) 2008 Blender Foundation / www.bigbuckbunny.org, CC BY 3.0.
Keeps the original MP4 and first three unmodified Mux HLS segments. The image
is a film frame, not an original theatrical poster. External URLs may change.
"""
from pathlib import Path
from urllib.parse import urljoin
import datetime, hashlib, json, subprocess, zipfile
import cv2

root = Path(__file__).resolve().parents[1]
directory = root / 'artifacts/s66-public-media'
directory.mkdir(parents=True, exist_ok=True)
mp4 = 'https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4.zip'
master = 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8'
variant = 'https://test-streams.mux.dev/x36xhzz/url_2/193039199_mp4_h264_aac_ld_7.m3u8'
receipts = []

def fetch(url, name):
    output = directory / name
    subprocess.run(['curl', '--fail', '--location', '--max-time', '120', '--output', str(output), url], check=True)
    receipts.append({'url': url, 'file': name, 'bytes': output.stat().st_size,
                     'sha256': hashlib.sha256(output.read_bytes()).hexdigest()})
    return output

archive = fetch(mp4, 'bbb.zip')
with zipfile.ZipFile(archive) as source:
    name = next(n for n in source.namelist() if n.endswith('.mp4'))
    (directory / 'bbb.mp4').write_bytes(source.read(name))
fetch(master, 'original-master.m3u8')
playlist = fetch(variant, 'original-variant.m3u8')
segments = [line for line in playlist.read_text().splitlines() if line and not line.startswith('#')][:3]
assert len(segments) == 3
for index, address in enumerate(segments):
    fetch(urljoin(variant, address), f'segment{index}.ts')
(directory / 'sample.m3u8').write_text('#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10\n#EXT-X-MEDIA-SEQUENCE:0\n' + ''.join(f'#EXTINF:10.000,\nsegment{i}.ts\n' for i in range(3)) + '#EXT-X-ENDLIST\n')
video = cv2.VideoCapture(str(directory / 'bbb.mp4'))
video.set(cv2.CAP_PROP_POS_MSEC, 60000)
ok, frame = video.read()
assert ok and cv2.imwrite(str(directory / 'poster.jpg'), frame)
video.release()
# New runs never overwrite the checked-in historical receipt.
(directory / 'download-receipt.json').write_text(json.dumps({
    'at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'artwork': 'MP4 frame at 60 seconds', 'hls': '30s local excerpt, unmodified segments',
    'files': receipts}, indent=2) + '\n')
print('Ready. Run node scripts/public-media-fixture.mjs from the project directory.')

subprocess.run(['python3', str(root / 'scripts/prepare-public-media-faststart.py')], cwd=root, check=True)
