"""Build a disposable localhost Paper course and exercise real entity adapters."""
from pathlib import Path
import subprocess, shutil, threading, time, json, os, socket
repo=Path(__file__).resolve().parents[3]
root=repo.parent
server=root/'staging'/'travel-local'
with socket.socket() as guard:
 guard.bind(('127.0.0.1',25576))
server.mkdir(parents=True,exist_ok=True)
# Guard against accidentally changing the established owner playtest or live downloads.
assert server.resolve()==(root/'staging'/'travel-local').resolve()
plugins=server/'plugins';plugins.mkdir(exist_ok=True)
for name in ['cache','libraries','versions','.paper']:
 source=root/'staging/smoke'/name
 if source.exists():shutil.copytree(source,server/name,dirs_exist_ok=True)
for name in ['VaultUnlocked-2.20.3.jar','ChestShop.jar','worldedit-bukkit-7.4.5.jar','worldguard-bukkit-7.0.19.jar','PlaceholderAPI-2.12.3.jar']:
 shutil.copy2(root/'staging/candidates'/name,plugins/name)
shutil.copy2(repo/'target/nookcore-0.1.0-SNAPSHOT.jar',plugins/'NookCore.jar')
config=plugins/'NookCore';config.mkdir(exist_ok=True)
text=(repo/'src/main/resources/config.yml').read_text(encoding='utf-8').replace('grounded:\n    enabled: false','grounded:\n    enabled: true')
(config/'config.yml').write_text(text,encoding='utf-8')
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25576\nonline-mode=true\nwhite-list=true\nenforce-whitelist=true\nenable-rcon=false\nview-distance=3\nsimulation-distance=3\nlevel-name=travel-course\nlevel-seed=1\nallow-nether=false\nmax-players=3\n')
(server/'bukkit.yml').write_text('settings:\n  allow-end: false\n')
(server/'spigot.yml').write_text('world-settings:\n  default:\n    entity-activation-range:\n      animals: 0\n')
bin=root/'tools/jdk/jdk-25.0.4.1+1/bin'
classes=repo/'target/travel-probe';classes.mkdir(parents=True,exist_ok=True)
classpath=os.pathsep.join(str(p) for p in (root/'tools/m2').rglob('*.jar'))
subprocess.run([str(bin/'javac.exe'),'-cp',classpath,'-d',str(classes),str(Path(__file__).with_name('TravelProbe.java')),str(Path(__file__).with_name('TravelCourse.java'))],check=True)
(classes/'plugin.yml').write_text('name: TravelProbe\nversion: 1\nmain: TravelProbe\napi-version: 26.2\ndepend: [NookCore]\n')
subprocess.run([str(bin/'jar.exe'),'cf',str(plugins/'TravelProbe.jar'),'-C',str(classes),'.'],check=True)
log=server/'travel-probe.log';lines=[]
proc=subprocess.Popen([str(bin/'java.exe'),'-Xms512M','-Xmx2G','-jar',str(root/'staging/paper-26.2-126.jar'),'--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
def read():
 with log.open('w',encoding='utf-8') as out:
  for line in proc.stdout:lines.append(line);out.write(line);out.flush()
thread=threading.Thread(target=read,daemon=True);thread.start()
try:proc.wait(timeout=180)
except subprocess.TimeoutExpired:
 proc.stdin.write('stop\n');proc.stdin.flush()
 try:proc.wait(timeout=30)
 except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=15)
thread.join(timeout=5)
result={'passed':any('TRAVEL_PROBE_PASS' in l for l in lines) and not any('TRAVEL_PROBE_FAILED' in l for l in lines),'exit_code':proc.returncode,'course_prepared':any('TRAVEL_COURSE_READY' in l for l in lines),'log':str(log),'checks':[l.strip() for l in lines if 'PASS ' in l or 'TRAVEL_PROBE_' in l]}
(server/'RESULT.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
if result['passed']:
 (plugins/'TravelProbe.jar').replace(server/'TravelProbe.jar.disabled')
print(json.dumps(result,indent=2))
raise SystemExit(0 if result['passed'] and proc.returncode==0 else 1)
