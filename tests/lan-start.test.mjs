import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {lanAddresses, lanOptions, lanConfiguration} from '../scripts/lan-options.mjs';

const interfaces = {
  lo: [{address: '127.0.0.1', internal: true}],
  wifi: [{address: '192.168.1.20', internal: false}, {address: 'fe80::1', internal: false}],
  vpn: [{address: '10.7.0.2', internal: false}],
  duplicate: [{address: '192.168.1.20', internal: false}],
  public: [{address: '8.8.8.8', internal: false}, {address: '169.254.2.1', internal: false}],
  lan: [{address: '172.16.0.3', internal: false}, {address: '172.32.0.3', internal: false}]
};

test('LAN launcher offers only actual private IPv4 interfaces and binds exactly the chosen one', () => {
  assert.deepEqual(lanAddresses(interfaces).map(item => item.address), ['192.168.1.20', '10.7.0.2', '172.16.0.3']);
  const options = lanOptions(['--address', '192.168.1.20', '--port', '4400'], {}, interfaces);
  assert.deepEqual(lanConfiguration(options, options.address), {
    host: '192.168.1.20', port: 4400, origin: 'http://192.168.1.20:4400',
    studio: 'http://192.168.1.20:4400/studio', television: 'http://192.168.1.20:4400/tv', diagnostics: 'http://192.168.1.20:4400/diagnostics'
  });
  assert.equal(lanOptions([], {}, interfaces).address, undefined, 'no automatic NIC selection');
});

test('LAN launcher rejects wrong-device, public and loopback addresses, invalid ports and existing deployment config', () => {
  for (const address of ['192.168.1.21', '127.0.0.1', '8.8.8.8', '::1', '192.168.1.20/path', '0.0.0.0']) {
    assert.throws(() => lanOptions(['--address', address], {}, interfaces), /本机网卡/);
  }
  for (const port of ['0', '80', '65536', '4e3', '4318x']) assert.throws(() => lanOptions(['--port', port], {}, interfaces), /端口/);
  for (const env of [{HOST: '0.0.0.0'}, {PUBLIC_ORIGIN: 'https://home.example'}]) assert.throws(() => lanOptions([], env, interfaces), /不会覆盖/);
  assert.throws(() => lanOptions([], {}, {lo: interfaces.lo}), /没有找到/);
  assert.throws(() => lanConfiguration(lanOptions([], {}, interfaces), '0.0.0.0'), /列表/);
  assert.throws(() => lanOptions(['--address'], {}, interfaces), /需要填写/);
  assert.throws(() => lanOptions(['--unexpected'], {}, interfaces), /未知选项/);
});

test('LAN command help and invalid choices exit without creating family credentials', () => {
  const env = {...process.env, HOST: '', PUBLIC_ORIGIN: ''};
  const help = spawnSync(process.execPath, ['scripts/start-lan.mjs', '--help'], {encoding: 'utf8', env});
  assert.equal(help.status, 0);
  assert.match(help.stdout, /不启动服务、不创建数据/);
  const invalid = spawnSync(process.execPath, ['scripts/start-lan.mjs', '--address', '127.0.0.1'], {encoding: 'utf8', env});
  assert.equal(invalid.status, 1);
  assert.match(invalid.stderr, /本机网卡|没有找到/);
  const unattended = spawnSync(process.execPath, ['scripts/start-lan.mjs'], {encoding: 'utf8', env});
  assert.equal(unattended.status, 1);
  assert.match(unattended.stderr, /非交互|没有找到/);
});
