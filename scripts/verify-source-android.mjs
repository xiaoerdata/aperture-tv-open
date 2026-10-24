// Targeted native verification of an exported source archive. No install or publication.
import assert from 'node:assert/strict';
import {mkdtemp, mkdir, readFile, writeFile, rm, access, cp} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';

const archive = resolve(process.argv[2] || 'artifacts/aperture-tv-v0.2.0-alpha.1-source.tar.gz');
const directory = await mkdtemp(join(tmpdir(), 'aperture-source-android-')), output = resolve('artifacts/source-android');
const run = (command, args, cwd) => new Promise((done, fail) => {
  const child = spawn(command, args, {cwd, env: process.env, stdio: ['ignore', 'pipe', 'pipe']});
  let log = '';
  child.stdout.on('data', data => log += data); child.stderr.on('data', data => log += data);
  child.once('error', fail); child.once('exit', code => done({code, log}));
});
try {
  const extracted = await run('tar', ['-xzf', archive, '-C', directory], directory);
  assert.equal(extracted.code, 0, extracted.log);
  const root = join(directory, 'aperture-tv'), source = JSON.parse(await readFile(join(root, 'SOURCE-MANIFEST.json')));
  await assert.rejects(access(join(root, '.git')));
  assert.ok(!source.files.some(file => file.path.startsWith('private-preview/') || file.path.startsWith('integrations/') || /(^|\/)(\.data|\.env|\.android-local|node_modules)(\/|$)/.test(file.path)));
  for (const file of source.files) {
    assert.ok(!file.path.startsWith('/') && !file.path.split('/').includes('..'));
    const data = await readFile(join(root, file.path));
    assert.equal(createHash('sha256').update(data).digest('hex'), file.sha256, file.path);
    if (file.path.endsWith('.md')) assert.ok(!data.toString().includes('recent account payments have failed'), 'raw private CI evidence must not be exported');
  }
  const built = await run(process.execPath, ['scripts/build-android-local.mjs'], root);
  await mkdir(output, {recursive: true});
  await writeFile(join(output, 'build.log'), built.log);
  assert.equal(built.code, 0, 'Archive build failed; see artifacts/source-android/build.log');
  const candidate = join(root, 'artifacts', 'android-local'), manifest = JSON.parse(await readFile(join(candidate, 'manifest.json')));
  assert.equal(manifest.sourceProvenance.kind, 'source-archive');
  assert.equal(manifest.sourceProvenance.androidMatchesExport, true);
  assert.equal(manifest.workingTreeDirty, null);
  assert.equal(manifest.buildCommit, null);
  assert.equal(manifest.sourceCommit, source.source.commit);
  assert.equal(manifest.signatureVerified, true);
  assert.equal(createHash('sha256').update(await readFile(join(candidate, manifest.apk))).digest('hex'), manifest.sha256);
  await cp(candidate, join(output, 'candidate'), {recursive: true});
  const report = {
    sourceArchiveSha256: createHash('sha256').update(await readFile(archive)).digest('hex'),
    sourceCommit: source.source.commit, sourceExportWorkingTreeDirty: source.source.workingTreeDirty,
    sourceFileHashesVerified: source.files.length, gitDirectoryPresent: false,
    knownPrivateCITextAbsent: true, privatePreviewAndRuntimeExcluded: true,
    androidBuilt: true, apkSha256: manifest.sha256, sourceProvenance: manifest.sourceProvenance,
    hardwareTested: false, published: false
  };
  await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
} finally { await rm(directory, {recursive: true, force: true}); }
