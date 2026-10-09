"""Isolated Paper check; no production data, network plugins or existing test courses."""
from pathlib import Path
import subprocess,shutil,socket,os,json
repo=Path(__file__).resolve().parents[3]
root=repo.parent
server=root/'staging/shop-fixes-20261009'
with socket.socket() as guard: guard.bind(('127.0.0.1',25586))
server.mkdir(parents=True,exist_ok=True)
plugins=server/'plugins';plugins.mkdir(exist_ok=True)
for name in ['cache','libraries','versions','.paper']:
    source=root/'staging/smoke'/name
    if source.exists() and not (server/name).exists():shutil.copytree(source,server/name)
for name in ['VaultUnlocked-2.20.3.jar','PlaceholderAPI-2.12.3.jar','ChestShop.jar','worldedit-bukkit-7.4.5.jar','worldguard-bukkit-7.0.19.jar']:
    shutil.copy2(root/'staging/candidates'/name,plugins/name)
shutil.copy2(repo/'target/nookcore-0.1.0-SNAPSHOT.jar',plugins/'NookCore.jar')
cfg=plugins/'NookCore';cfg.mkdir(exist_ok=True)
# Deliberately omit help-replies to test an existing installation inheriting defaults.
(cfg/'config.yml').write_text('chat:\n  enabled: true\n  display-titles:\n    "983f1c6c-7f17-4729-ab16-8c0d3407592a": mrs-bee\nrental-commands-enabled: false\nshop-gate:\n  enabled: false\n',encoding='utf-8')
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25586\nonline-mode=true\nwhite-list=true\nenforce-whitelist=true\nenable-rcon=false\nenable-query=false\nview-distance=2\nsimulation-distance=2\nlevel-name=qol-test\nlevel-seed=1\nallow-nether=false\nmax-players=3\n')
(server/'bukkit.yml').write_text('settings:\n  allow-end: false\n')
bin=root/'tools/jdk/jdk-25.0.4.1+1/bin'
classes=repo/'target/chat-qol-probe';classes.mkdir(parents=True,exist_ok=True)
classpath=os.pathsep.join(str(p) for p in (root/'tools/m2').rglob('*.jar'))
subprocess.run([str(bin/'javac.exe'),'-cp',classpath,'-d',str(classes),str(Path(__file__).with_name('ShopReleaseProbe.java')),str(Path(__file__).with_name('ShopFixChecks.java'))],check=True)
(classes/'plugin.yml').write_text('name: ChatQolProbe\nversion: 1\nmain: ShopReleaseProbe\napi-version: 26.2\ndepend: [NookCore, ChestShop]\n')
subprocess.run([str(bin/'jar.exe'),'cf',str(plugins/'ChatQolProbe.jar'),'-C',str(classes),'.'],check=True)
log=server/'chat-qol-probe.log'
with log.open('w',encoding='utf-8') as out:
    proc=subprocess.Popen([str(bin/'java.exe'),'-Xms512M','-Xmx2G','-jar',str(root/'staging/paper-26.2-126.jar'),'--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True)
    try:proc.wait(timeout=180)
    except subprocess.TimeoutExpired:
        proc.stdin.write('stop\n');proc.stdin.flush()
        try:proc.wait(timeout=30)
        except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=15)
lines=log.read_text(encoding='utf-8').splitlines()
result={'passed':any('CHAT_QOL_PASS' in l for l in lines) and not any('CHAT_QOL_FAILED' in l for l in lines),'exit_code':proc.returncode,'log':str(log),'checks':[l for l in lines if 'PASS ' in l or 'CHAT_QOL_' in l]}
(server/'RESULT.json').write_text(json.dumps(result,indent=2))
if result['passed']:(plugins/'ChatQolProbe.jar').replace(server/'ChatQolProbe.jar.disabled')
print(json.dumps(result,indent=2))
raise SystemExit(0 if result['passed'] and proc.returncode==0 else 1)
