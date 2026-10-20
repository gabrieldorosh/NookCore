from pathlib import Path
import subprocess,shutil,socket,os,json,hashlib
repo=Path(__file__).resolve().parents[3];root=repo.parent;local=Path(__file__).resolve().parent
server=root/'staging/october21-refresh-probe-20261020'
with socket.socket() as guard:guard.bind(('127.0.0.1',25598))
server.mkdir(parents=True,exist_ok=True);plugins=server/'plugins';plugins.mkdir(exist_ok=True)
for name in ['cache','libraries','versions','.paper']:
    source=root/'staging/smoke'/name
    if source.exists() and not (server/name).exists():shutil.copytree(source,server/name)
for name in ['VaultUnlocked-2.20.3.jar','PlaceholderAPI-2.12.3.jar','ChestShop.jar','worldedit-bukkit-7.4.5.jar','worldguard-bukkit-7.0.19.jar','FancyNpcs-2.12.1.jar']:shutil.copy2(root/'staging/candidates'/name,plugins/name)
artifact=repo/'target/nookcore-0.1.0-SNAPSHOT.jar';shutil.copy2(artifact,plugins/'NookCore.jar')
cfg=plugins/'NookCore';cfg.mkdir(exist_ok=True)
for suffix in ['', '-wal', '-shm']:
    p=cfg/('nooks.db'+suffix)
    if p.exists():p.rename(cfg/('probe-before-'+str(__import__('time').time_ns())+'-'+p.name))
npcconfig=(repo/'src/main/resources/spawn-npcs.yml').read_text(encoding='utf-8').replace('enabled: false','enabled: true',1).replace('test-mode: false','test-mode: true',1)
(cfg/'spawn-npcs.yml').write_text(npcconfig,encoding='utf-8')
(cfg/'quests.yml').write_text((repo/'release/october-21-refresh/quests.yml').read_text(encoding='utf-8').replace('enabled: false','enabled: true',1),encoding='utf-8')
skins=plugins/'FancyNpcs/skins';skins.mkdir(parents=True,exist_ok=True)
for name in ['niko.png','vendor-aviator.png']:shutil.copy2(root/'assets/npc-skins'/name,skins/name)
(cfg/'config.yml').write_text('chat:\n  enabled: false\nrental-commands-enabled: false\nshop-gate:\n  enabled: false\n',encoding='utf-8')
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25598\nonline-mode=true\nwhite-list=true\nenforce-whitelist=true\nenable-rcon=false\nenable-query=false\nview-distance=2\nsimulation-distance=2\nlevel-name=interaction-test\nlevel-seed=1\nallow-nether=false\nmax-players=1\n')
(server/'bukkit.yml').write_text('settings:\n  allow-end: false\n')
bin=root/'tools/jdk/jdk-25.0.4.1+1/bin';classes=repo/'target/october21-refresh-probe';classes.mkdir(exist_ok=True)
classpath=os.pathsep.join(str(p) for p in [*(root/'tools/m2').rglob('*.jar'),*(root/'staging/candidates').glob('*.jar'),*(server/'libraries').rglob('*.jar'),server/'versions/26.2/paper-26.2.jar'])
args=classes/'recovery-javac-args.txt';args.write_text('-cp\n"'+classpath.replace('\\','/')+'"\n-d\n"'+str(classes).replace('\\','/')+'"\n"'+str(local/'SpawnNpcProbe.java').replace('\\','/')+'"\n')
subprocess.run([str(bin/'javac.exe'),'@'+str(args)],check=True)
(classes/'plugin.yml').write_text('name: SpawnNpcProbe\nversion: 1\nmain: SpawnNpcProbe\napi-version: 26.2\ndepend: [NookCore]\n')
subprocess.run([str(bin/'jar.exe'),'cf',str(plugins/'SpawnNpcProbe.jar'),'-C',str(classes),'.'],check=True)
log=server/'october21-refresh-probe.log'
with log.open('w',encoding='utf-8') as out:
    proc=subprocess.Popen([str(bin/'java.exe'),'-Xms512M','-Xmx2G','-jar',str(root/'staging/paper-26.2-126.jar'),'--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True)
    try:proc.wait(timeout=180)
    except subprocess.TimeoutExpired:
        proc.stdin.write('stop\n');proc.stdin.flush()
        try:proc.wait(timeout=30)
        except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=15)
lines=log.read_text(encoding='utf-8').splitlines()
result={'passed':any('NPC_COMPLETE' in l for l in lines) and not any('NPC_FAILED' in l or 'Could not pass event' in l or 'generated an exception' in l for l in lines),'exit_code':proc.returncode,'jar_sha256':hashlib.sha256((plugins/'NookCore.jar').read_bytes()).hexdigest(),'log':str(log),'checks':[l for l in lines if 'NPC_' in l]}
(server/'RESULT.json').write_text(json.dumps(result,indent=2))
if (plugins/'SpawnNpcProbe.jar').exists():(plugins/'SpawnNpcProbe.jar').replace(server/'SpawnNpcProbe.jar.disabled')
print(json.dumps(result,indent=2));raise SystemExit(0 if result['passed'] and proc.returncode==0 else 1)
