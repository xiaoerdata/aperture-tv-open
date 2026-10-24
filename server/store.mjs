import {mkdir, readFile, writeFile, rename} from 'node:fs/promises';
import {join} from 'node:path';
import {emptyBoard} from '../shared/model.mjs';
export async function createStore(directory) {
  await mkdir(directory, {recursive:true, mode:0o700});
  const filename = join(directory, 'state.json');
  let state;
  try { state = JSON.parse(await readFile(filename, 'utf8')); }
  catch (e) { if (e.code !== 'ENOENT') throw e; state = {board:emptyBoard(), sessions:{}, pairs:{}, receipts:{}}; }
  let queue = Promise.resolve();const subscribers=new Set();
  return {
    read: () => structuredClone(state),
    subscribe(fn){subscribers.add(fn);return ()=>subscribers.delete(fn);},
    transact(operation) {
      const result = queue.then(async () => {
        const next = structuredClone(state);
        const output = await operation(next);
        await writeFile(filename + '.tmp', JSON.stringify(next), {mode:0o600});
        await rename(filename + '.tmp', filename);
        const changed=state.board.revision!==next.board.revision;state = next;
        if(changed)for(const notify of subscribers){try{notify(state.board.revision);}catch{}}
        return structuredClone(output);
      });
      queue = result.catch(() => {}); return result;
    }
  };
}
