"""Optional design tool: python3 -m pip install fonttools brotli.
Subset a locally installed OFL Source Han Serif; no font downloads at runtime.
"""
from pathlib import Path
import argparse
import hashlib
import json
from fontTools import subset
from fontTools.ttLib import TTFont

parser = argparse.ArgumentParser()
parser.add_argument('--source', default='/usr/share/fonts/opentype/source-han/SourceHanSerifCN-Regular.otf')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
source = Path(args.source)
text = ''.join((root / 'public/launcher' / name).read_text() for name in ['index.html', 'launcher.js'])
# All current Chinese copy plus every date/weekday, independent of today's date.
characters = {c for c in text if '\u3400' <= c <= '\u9fff'} | set('0123456789年月日星期一二三四五六天，。？！：、·')
font = TTFont(source)
missing = [c for c in characters if ord(c) not in font.getBestCmap()]
if missing:
    raise ValueError('Missing source glyphs: ' + ''.join(sorted(missing)))
options = subset.Options()
options.flavor = 'woff2'
options.name_IDs = ['*']
options.name_legacy = True
options.name_languages = ['*']
worker = subset.Subsetter(options=options)
worker.populate(text=''.join(sorted(characters)))
worker.subset(font)
# Source is a reserved font name; a subset uses our own derivative name.
for record in font['name'].names:
    values = {1: 'Aperture Song', 2: 'Regular', 3: 'ApertureSong-Regular-Subset', 4: 'Aperture Song Regular', 6: 'ApertureSong-Regular', 16: 'Aperture Song', 17: 'Regular'}
    if record.nameID in values:
        record.string = values[record.nameID].encode(record.getEncoding())
if 'CFF ' in font:
    cff = font['CFF '].cff
    cff.fontNames = ['ApertureSong-Regular']
    for top in cff.topDictIndex:
        top.FamilyName = 'Aperture Song'
        top.FullName = 'Aperture Song Regular'
font.flavor = 'woff2'
output = root / 'public/launcher/fonts/aperture-song.woff2'
font.save(output)
manifest = {'font': 'Aperture Song', 'derivedFrom': 'Source Han Serif CN Regular', 'sourceUrl': 'https://github.com/adobe-fonts/source-han-serif', 'license': 'SIL OFL 1.1', 'licenseFile': 'SourceHanSerif-OFL.txt', 'sourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(), 'outputSha256': hashlib.sha256(output.read_bytes()).hexdigest(), 'characters': ''.join(sorted(characters)), 'bytes': output.stat().st_size}
(output.parent / 'aperture-song.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
print('Bundled', len(characters), 'glyphs,', output.stat().st_size, 'bytes')
