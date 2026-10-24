import test from 'node:test';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
test('public preparation rejects traversal, links, duplicate paths and private evidence before extraction',()=>{
 const output=execFileSync('python3',['-c',`
import importlib.util, io, tarfile
spec=importlib.util.spec_from_file_location('prep','scripts/prepare-public-source.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
for names,link in [(['aperture-tv/../outside'],False),(['/aperture-tv/file'],False),(['aperture-tv/file','aperture-tv/file'],False),(['aperture-tv/private-preview/raw'],False),(['aperture-tv/file'],True)]:
 data=io.BytesIO()
 with tarfile.open(fileobj=data,mode='w') as archive:
  for name in names:
   item=tarfile.TarInfo(name);item.size=0
   if link:item.type=tarfile.SYMTYPE;item.linkname='/etc/passwd'
   archive.addfile(item,io.BytesIO())
 data.seek(0)
 with tarfile.open(fileobj=data,mode='r') as archive:
  try:module.safe_members(archive);raise AssertionError('unsafe archive accepted')
  except ValueError:pass
print('5 hostile archives rejected before extraction')
`],{encoding:'utf8'});
 assert.match(output,/5 hostile archives rejected/);
});

test('public documentation links keep code and historical text, but route private downloads to available instructions',()=>{
 const output=execFileSync('python3',['-c',`
import importlib.util, tempfile
from pathlib import Path
spec=importlib.util.spec_from_file_location('prep','scripts/prepare-public-source.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
with tempfile.TemporaryDirectory() as directory:
 root=Path(directory);(root/'docs').mkdir()
 (root/'docs/DOWNLOADS.md').write_text('Download instructions')
 text='History: private-preview remains private. [candidate](../private-preview/README.md)\\n[web](https://example.com/a) [section](#section) [service](/home)\\n'+chr(96)+'[example](missing.md)'+chr(96)+'\\n'+chr(96)*3+'md\\n[code](missing.md)\\n'+chr(96)*3+'\\n[ref]: ../private-preview/ref.md\\n'
 doc=root/'docs/history.md';doc.write_text(text)
 assert len(module.documentation_links(root)['missing'])==2
 changes=module.public_download_links(root)
 assert changes==[{'path':'docs/history.md','links':2}]
 assert 'History: private-preview remains private.' in doc.read_text()
 assert '[candidate](DOWNLOADS.md)' in doc.read_text()
 assert '[ref]: DOWNLOADS.md' in doc.read_text()
 assert '[example](missing.md)' in doc.read_text()
 assert not module.documentation_links(root)['missing']
 doc.write_text(doc.read_text()+'[unknown](unknown.md)\\n[escape](../../outside.md)\\n')
 assert len(module.documentation_links(root)['missing'])==2
 assert not module.public_download_links(root)
print('private links redirected; examples untouched; unknown and escaping paths rejected')
`],{encoding:'utf8'});
 assert.match(output,/unknown and escaping paths rejected/);
});

test('public README links resolve both in the source template and at the repository root',()=>{
 const output=execFileSync('python3',['-c',`
import importlib.util, tempfile
from pathlib import Path
spec=importlib.util.spec_from_file_location('prep','scripts/prepare-public-source.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
with tempfile.TemporaryDirectory() as directory:
 root=Path(directory);(root/'docs/release').mkdir(parents=True)
 (root/'INSTALL.md').write_text('Install')
 (root/'docs/My Guide.md').write_text('Guide')
 (root/'docs/icon.png').write_bytes(b'fixture')
 template=root/'docs/release/README-public.md'
 template.write_text('[install](../../INSTALL.md#start) [guide](<../My Guide.md>) ![icon](../icon.png)\\n[ref]: ../../INSTALL.md\\n')
 assert not module.documentation_links(root)['missing']
 result=module.rebase_readme(template,root/'README.md')
 assert '[install](INSTALL.md#start)' in result
 assert '[guide](<docs/My%20Guide.md>)' in result
 assert '![icon](docs/icon.png)' in result
 assert '[ref]: INSTALL.md' in result
 (root/'README.md').write_text(result)
 assert not module.documentation_links(root)['missing']
print('template, root, image, reference and encoded-space destinations checked')
`],{encoding:'utf8'});
 assert.match(output,/encoded-space destinations checked/);
});
