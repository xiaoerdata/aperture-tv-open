import {networkInterfaces} from 'node:os';
import {createInterface} from 'node:readline/promises';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {access} from 'node:fs/promises';
import {delimiter, join} from 'node:path';
import {lanOptions, lanConfiguration} from './lan-options.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
let activeChild, stopping = false;
for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => {
  stopping = true;
  if (activeChild) activeChild.kill(signal);
});

try {
  const options = lanOptions(process.argv.slice(2), process.env, networkInterfaces());
  if (options.help) {
    console.log('开窗 · 家庭局域网试用\n用法：npm run start:lan [-- --address 192.168.1.50 --port 4318]\n交互选择本机私有 IPv4 网卡；非交互时必须明确 --address。\n--print-config 只输出地址，不启动服务、不创建数据。\n仅限受信任家庭网络 HTTP 内测；不适用于公网或正式 HTTPS 部署。');
  } else {
    let address = options.address;
    if (!address) {
      if (!process.stdin.isTTY || !process.stdout.isTTY) throw new Error('非交互运行请明确 --address；不能自动猜测家庭网卡');
      console.log('选择与电视、手机互通的家庭网卡；Docker / VPN / WSL 地址可能无法访问：');
      options.addresses.forEach((item, index) => console.log(`${index + 1}. ${item.name} — ${item.address}`));
      const prompt = createInterface({input: process.stdin, output: process.stdout});
      try {
        const answer = (await prompt.question('输入网卡序号（直接回车取消）：')).trim();
        if (!/^[1-9][0-9]*$/.test(answer) || !options.addresses[Number(answer) - 1]) throw new Error('未选择有效网卡，未启动服务');
        address = options.addresses[Number(answer) - 1].address;
      } finally { prompt.close(); }
    }
    const config = lanConfiguration(options, address);
    if (options.print) console.log(JSON.stringify(config, null, 2));
    else {
      const env = {...process.env, HOST: config.host, PORT: String(config.port), PUBLIC_ORIGIN: config.origin};
      const venv = join(root, '.venv', 'bin');
      try { await access(join(venv, 'python3')); env.PATH = venv + delimiter + (env.PATH || ''); } catch {}
      console.log('仅在所选网卡监听。HTTP 不加密，请只用合成资料内测；助手跨机仍需可信 HTTPS。');
      const status = await new Promise((resolve, reject) => {
        activeChild = spawn(process.execPath, ['scripts/doctor.mjs'], {cwd: root, env, stdio: 'inherit'});
        activeChild.once('error', reject);
        activeChild.once('exit', code => resolve(code));
      });
      activeChild = undefined;
      if (status !== 0) throw new Error('启动检查未通过，请先完成依赖安装');
      if (!stopping) {
        const {default: QRCode} = await import('qrcode');
        const qr = await QRCode.toString(config.studio, {type: 'terminal', small: true});
        if (!stopping) {
          activeChild = spawn(process.execPath, ['server/main.mjs'], {cwd: root, env, stdio: ['inherit', 'inherit', 'inherit', 'ipc']});
          activeChild.on('message', message => {
            if (message?.type !== 'aperture-ready' || message.origin !== config.origin) return;
            console.log(`\n手机工作台（二维码只含地址，不含口令）：\n${qr}\n${config.studio}\n电视 APK 输入：${config.origin}\n电视兼容检查：${config.diagnostics}\n管理员口令只保存在私有数据目录；请在本机查看，别录入视频。\n保持此窗口运行；Ctrl+C 停止服务。`);
          });
          const result = await new Promise((resolve, reject) => {
            activeChild.once('error', reject);
            activeChild.once('exit', (code, signal) => resolve({code, signal}));
          });
          activeChild = undefined;
          if (!stopping && (result.code !== 0 || result.signal)) throw new Error('服务未正常结束，请检查端口占用、地址变化或上方错误；未改变原有家庭数据');
        }
      }
    }
  }
} catch (error) {
  console.error('开窗：' + error.message);
  process.exitCode = 1;
} finally {
  if (activeChild) activeChild.kill('SIGTERM');
}
