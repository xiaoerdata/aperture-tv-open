"""Isolated loopback Samba. Packages are extracted in /tmp; no system daemon/config is modified."""
import os
import pwd
import sys
import time
from pathlib import Path
from impacket.ntlm import compute_lmhash, compute_nthash

port, share, runtime, maximum = sys.argv[1:]
base = Path(share).parent / "samba-state"
base.mkdir(mode=0o700)
for name in ["private", "state", "cache", "lock", "run", "ncalrpc", "log"]:
    (base / name).mkdir(mode=0o755)
user = pwd.getpwuid(os.getuid()).pw_name
password_file = base / "smbpasswd"
password_file.write_text(f"{user}:{os.getuid()}:{compute_lmhash('synthetic-nas-only').hex().upper()}:{compute_nthash('synthetic-nas-only').hex().upper()}:[U          ]:LCT-{int(time.time()):08X}:\n")
password_file.chmod(0o600)
config = f"""[global]
server role = standalone server
security = user
workgroup = WORKGROUP
interfaces = 127.0.0.1
bind interfaces only = yes
disable netbios = yes
smb ports = {port}
server min protocol = SMB2_02
server max protocol = {maximum}
server signing = mandatory
map to guest = Never
passdb backend = smbpasswd:{password_file}
load printers = no
disable spoolss = yes
logging = stdout
log level = 0
rpc_server:default = disabled
rpc_server:epmapper = disabled
rpc_server:tcpip = no
private dir = {base}/private
state directory = {base}/state
cache directory = {base}/cache
lock directory = {base}/lock
pid directory = {base}/run
ncalrpc dir = {base}/ncalrpc
log file = {base}/log/log.smbd
[Family]
path = {share}
read only = yes
valid users = {user}
"""
path = base / "smb.conf"
path.write_text(config)
os.environ["LD_LIBRARY_PATH"] = f"{runtime}/usr/lib/x86_64-linux-gnu:{runtime}/usr/lib/x86_64-linux-gnu/samba"
os.execv(f"{runtime}/usr/sbin/smbd", ["smbd", "-F", "--no-process-group", "--debug-stdout", "-s", str(path)])
