"""Render a user service without starting it. --install explicitly enables the generated local unit."""
import argparse, os, platform, plistlib, shutil, subprocess
from pathlib import Path


def systemd_arg(value):
    if any(c in value for c in '\n\r\x00'): raise ValueError('Paths may not contain control characters')
    return '"'+value.replace('\\','\\\\').replace('"','\\"').replace('%','%%')+'"'


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--platform',choices=['systemd','launchd'],required=True)
    parser.add_argument('--env-file',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--node',type=Path)
    parser.add_argument('--install',action='store_true')
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]; config=args.env_file.expanduser().resolve(); output=args.output.expanduser().resolve()
    node=args.node.expanduser().resolve() if args.node else Path(shutil.which('node') or '/missing/node')
    if not node.is_file() or not config.is_file(): parser.error('Node executable and private env file must exist')
    if config.stat().st_mode&0o077: parser.error('Env file must be private: chmod 600')
    if output.exists(): parser.error('Output exists; preserved. Choose a new output file')
    output.parent.mkdir(parents=True,exist_ok=True)
    if args.platform=='systemd':
        template=(root/'deploy/systemd/kaishi.service.in').read_text()
        for key,value in {'ROOT':str(root),'NODE':str(node),'ENV':str(config),'MAIN':str(root/'server/main.mjs')}.items(): template=template.replace('@'+key+'@',systemd_arg(value))
        text=template.encode()
    else:
        log=output.parent/'logs';log.mkdir(exist_ok=True,mode=0o700)
        text=plistlib.dumps({'Label':'org.zhixiaoce.kaishi','ProgramArguments':[str(node),'--env-file='+str(config),str(root/'server/main.mjs')],'WorkingDirectory':str(root),'RunAtLoad':True,'KeepAlive':{'SuccessfulExit':False},'ThrottleInterval':5,'StandardOutPath':str(log/'kaishi.log'),'StandardErrorPath':str(log/'kaishi-error.log')})
    with output.open('xb') as f: os.chmod(output,0o600);f.write(text)
    print('Generated service: '+str(output))
    if not args.install:
        print('Not installed or started. Review this file, then follow docs/DEPLOYMENT.md.');return
    if args.platform=='systemd':
        if platform.system()!='Linux' or output!=Path.home()/'.config/systemd/user/kaishi.service': parser.error('--install requires the Linux user-unit destination')
        subprocess.run(['systemctl','--user','daemon-reload'],check=True);subprocess.run(['systemctl','--user','enable','--now','kaishi.service'],check=True)
    else:
        if platform.system()!='Darwin' or output!=Path.home()/'Library/LaunchAgents/org.zhixiaoce.kaishi.plist': parser.error('--install requires the macOS LaunchAgent destination')
        subprocess.run(['launchctl','bootstrap','gui/'+str(os.getuid()),str(output)],check=True)

if __name__=='__main__':main()
