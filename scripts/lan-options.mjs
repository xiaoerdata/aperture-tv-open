import {isIPv4} from 'node:net';

function privateIPv4(address) {
  if (!isIPv4(address)) return false;
  const [a, b] = address.split('.').map(Number);
  return a === 10 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168);
}

export function lanAddresses(interfaces) {
  const seen = new Set(), entries = [];
  for (const [name, addresses] of Object.entries(interfaces)) {
    for (const item of addresses || []) {
      if (item.internal || !privateIPv4(item.address) || seen.has(item.address)) continue;
      seen.add(item.address);
      entries.push({name, address: item.address});
    }
  }
  return entries;
}

export function lanOptions(args, env, interfaces) {
  const options = {address: undefined, port: env.PORT || '4318', print: false, help: false};
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === '--help') options.help = true;
    else if (arg === '--print-config') options.print = true;
    else if (arg === '--address' || arg === '--port') {
      const value = args[++i];
      if (!value || value.startsWith('--')) throw new Error(`${arg} 后需要填写值`);
      options[arg === '--address' ? 'address' : 'port'] = value;
    } else throw new Error(`未知选项：${arg}；用 --help 查看用法`);
  }
  if (options.help) return options;
  if (env.HOST || env.PUBLIC_ORIGIN) throw new Error('已有 HOST / PUBLIC_ORIGIN 配置，请沿用 npm run start:checked；局域网向导不会覆盖它们');
  if (!/^[0-9]+$/.test(options.port) || Number(options.port) < 1024 || Number(options.port) > 65535) throw new Error('端口应为 1024–65535 的整数');
  options.port = Number(options.port);
  options.addresses = lanAddresses(interfaces);
  if (!options.addresses.length) throw new Error('没有找到本机私有 IPv4 地址。请连接家庭网络；WSL / Docker 需核对宿主机网络，或按 DEPLOYMENT 手动配置');
  if (options.address && !options.addresses.some(item => item.address === options.address)) throw new Error('--address 必须是本机网卡现有的私有 IPv4 地址，不能填写其他设备、回环或公网地址');
  return options;
}

export function lanConfiguration(options, address) {
  if (!options.addresses.some(item => item.address === address)) throw new Error('请选择列表中的网卡地址');
  const origin = `http://${address}:${options.port}`;
  return {host: address, port: options.port, origin, studio: origin + '/studio', television: origin + '/tv', diagnostics: origin + '/diagnostics'};
}
