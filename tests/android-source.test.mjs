import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, mkdir, writeFile, rm, symlink} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {execFileSync} from 'node:child_process';
import {androidSource, sourceProvenance} from '../scripts/android-source.mjs';

async function fixture(t) {
  const root = await mkdtemp(join(tmpdir(), 'aperture-source-test-'));
  t.after(() => rm(root, {recursive: true, force: true}));
  await mkdir(join(root, 'android'));
  await writeFile(join(root, 'android', 'build.gradle'), '// synthetic Android input\n');
  return root;
}

test('source archives build without Git and report modified Android inputs separately from Git status', async t => {
  const root = await fixture(t), inputs = await androidSource(root);
  const manifest = {source: {commit: 'a'.repeat(40), workingTreeDirty: false}, files: inputs.files};
  await writeFile(join(root, 'SOURCE-MANIFEST.json'), JSON.stringify(manifest));
  const original = await sourceProvenance(root, inputs);
  assert.equal(original.kind, 'source-archive');
  assert.equal(original.sourceCommit, 'a'.repeat(40));
  assert.equal(original.buildCommit, null);
  assert.equal(original.workingTreeDirty, null);
  assert.equal(original.androidMatchesExport, true);
  assert.match(original.sourceManifestSha256, /^[a-f0-9]{64}$/);
  await writeFile(join(root, 'android', 'build.gradle'), '// user customization\n');
  assert.equal((await sourceProvenance(root, await androidSource(root))).androidMatchesExport, false);
  await writeFile(join(root, 'android', 'new.gradle'), '// extra input\n');
  assert.equal((await sourceProvenance(root, await androidSource(root))).androidMatchesExport, false);
});

test('a ZIP extracted inside another repository never inherits the parent commit', async t => {
  const root = await fixture(t), parent = join(root, 'parent'), child = join(parent, 'archive');
  await mkdir(join(child, 'android'), {recursive: true});
  execFileSync('git', ['init', '-q', parent]);
  await writeFile(join(child, 'android', 'build.gradle'), '// independent source\n');
  assert.deepEqual(await sourceProvenance(child, await androidSource(child)), {kind: 'unversioned', sourceCommit: null, buildCommit: null, workingTreeDirty: null});
});

test('a real checkout retains clean/dirty source provenance; ignored Android build output does not change input digest', async t => {
  const root = await fixture(t), git = args => execFileSync('git', args, {cwd: root, stdio: 'pipe'}).toString().trim();
  git(['init', '-q']); git(['add', '.']);
  git(['-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-qm', 'fixture']);
  const inputs = await androidSource(root), provenance = await sourceProvenance(root, inputs);
  assert.equal(provenance.kind, 'git-checkout');
  assert.equal(provenance.workingTreeDirty, false);
  assert.equal(provenance.buildCommit, git(['rev-parse', 'HEAD']));
  await mkdir(join(root, 'android', 'build'));
  await writeFile(join(root, 'android', 'build', 'ignored.dex'), 'generated');
  assert.equal((await androidSource(root)).sha256, inputs.sha256);
  await writeFile(join(root, 'android', 'build.gradle'), '// edited\n');
  assert.equal((await sourceProvenance(root, await androidSource(root))).workingTreeDirty, true);
});

test('old source manifests do not fabricate a commit, while malformed manifests and symlink inputs fail', async t => {
  const root = await fixture(t), inputs = await androidSource(root);
  await writeFile(join(root, 'SOURCE-MANIFEST.json'), JSON.stringify({files: inputs.files}));
  assert.equal((await sourceProvenance(root, inputs)).sourceCommit, null);
  await writeFile(join(root, 'SOURCE-MANIFEST.json'), '{broken');
  await assert.rejects(sourceProvenance(root, inputs));
  await symlink('build.gradle', join(root, 'android', 'link.gradle'));
  await assert.rejects(androidSource(root), /symlink/);
});

test('bundled lobby assets are Android inputs and changes invalidate the source match', async t => {
  const root = await fixture(t);
  await mkdir(join(root, 'public', 'launcher'), {recursive:true});
  await writeFile(join(root, 'public', 'launcher', 'index.html'), 'original lobby');
  const original=await androidSource(root);
  await writeFile(join(root, 'SOURCE-MANIFEST.json'),JSON.stringify({files:original.files}));
  assert.equal((await sourceProvenance(root,original)).androidMatchesExport,true);
  await writeFile(join(root, 'public', 'launcher', 'index.html'),'updated lobby');
  const changed=await androidSource(root);
  assert.notEqual(changed.sha256,original.sha256);
  assert.equal((await sourceProvenance(root,changed)).androidMatchesExport,false);
});
