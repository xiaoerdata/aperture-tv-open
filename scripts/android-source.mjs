import {execFileSync} from 'node:child_process';
import {readFile, readdir, realpath} from 'node:fs/promises';
import {join, relative, sep} from 'node:path';
import {createHash} from 'node:crypto';

const sha256 = data => createHash('sha256').update(data).digest('hex');

export async function androidSource(root) {
  const files = [], hash = createHash('sha256');
  async function walk(directory) {
    for (const item of (await readdir(directory, {withFileTypes: true})).sort((a, b) => a.name.localeCompare(b.name))) {
      if (['build', '.gradle', 'local.properties'].includes(item.name)) continue;
      const file = join(directory, item.name);
      if (item.isSymbolicLink()) throw new Error('Android input symlink rejected');
      if (item.isDirectory()) await walk(file);
      else if (item.isFile()) {
        const path = relative(root, file).split(sep).join('/'), data = await readFile(file);
        hash.update(path); hash.update('\0'); hash.update(data);
        files.push({path, sha256: sha256(data)});
      } else throw new Error('Unexpected Android input');
    }
  }
  await walk(join(root, 'android'));
  try { await walk(join(root, 'public', 'launcher')); } catch (error) { if (error.code !== 'ENOENT') throw error; }
  return {sha256: hash.digest('hex'), files};
}

export async function sourceProvenance(root, inputs) {
  const git = args => execFileSync('git', args, {cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe']}).trim();
  let ownCheckout = false;
  try { ownCheckout = await realpath(git(['rev-parse', '--show-toplevel'])) === await realpath(root); } catch {}
  if (ownCheckout) {
    const commit = git(['rev-parse', 'HEAD']);
    return {kind: 'git-checkout', sourceCommit: commit, buildCommit: commit, workingTreeDirty: git(['status', '--porcelain']) !== ''};
  }
  let raw;
  try { raw = await readFile(join(root, 'SOURCE-MANIFEST.json')); }
  catch (error) {
    if (error.code !== 'ENOENT') throw error;
    return {kind: 'unversioned', sourceCommit: null, buildCommit: null, workingTreeDirty: null};
  }
  const manifest = JSON.parse(raw);
  if (!Array.isArray(manifest.files)) throw new Error('Invalid source archive manifest');
  const expected = manifest.files.filter(item => typeof item.path === 'string' && (item.path.startsWith('android/') || item.path.startsWith('public/launcher/')));
  const seen = new Set(expected.map(item => item.path));
  const androidMatchesExport = expected.length === seen.size && expected.length === inputs.files.length && inputs.files.every(item => expected.some(entry => entry.path === item.path && entry.sha256 === item.sha256));
  return {
    kind: 'source-archive', sourceCommit: /^[a-f0-9]{40}$/.test(manifest.source?.commit || '') ? manifest.source.commit : null,
    buildCommit: null, workingTreeDirty: null,
    exportWorkingTreeDirty: typeof manifest.source?.workingTreeDirty === 'boolean' ? manifest.source.workingTreeDirty : null,
    sourceManifestSha256: sha256(raw), androidMatchesExport
  };
}
