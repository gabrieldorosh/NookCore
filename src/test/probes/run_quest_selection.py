from pathlib import Path
import subprocess,shutil,socket,os,json,hashlib
repo=Path(__file__).resolve().parents[3];root=repo.parent;local=Path(__file__).resolve().parent
server=Path(os.environ.get('NN_CHOICE_SERVER',str(root/'staging/quest-selection-probe-20261103')))
with socket.socket() as guard:guard.bind(('127.0.0.1',25615))
server.mkdir(parents=True,exist_ok=True);plugins=server/'plugins';plugins.mkdir(exist_ok=True)
for name in ['cache','libraries','versions','.paper']:
    source=Path(os.environ.get('NN_PAPER_CACHE',str(root/'staging/upgrade-26.3-staff-20261101')))/name
    if source.exists() and not (server/name).exists():shutil.copytree(source,server/name)
for name in ['VaultUnlocked-2.20.3.jar','PlaceholderAPI-2.12.3.jar','ChestShop.jar','worldedit-bukkit-7.4.6.jar','worldguard-bukkit-7.0.20.jar','FancyNpcs-2.12.1.jar']:shutil.copy2(Path(os.environ.get('NN_PLUGIN_CANDIDATES',str(root/'staging/candidates')))/('ChestShop-build-481.jar' if name=='ChestShop.jar' else name),plugins/name)
artifact=repo/'target/nookcore-0.1.0-SNAPSHOT.jar';shutil.copy2(artifact,plugins/'NookCore.jar')
cfg=plugins/'NookCore';cfg.mkdir(exist_ok=True)
for suffix in ['', '-wal', '-shm']:
    p=cfg/('nooks.db'+suffix)
    if p.exists():p.rename(cfg/('probe-before-'+str(__import__('time').time_ns())+'-'+p.name))
npcconfig=(repo/'src/main/resources/spawn-npcs.yml').read_text(encoding='utf-8').replace('enabled: false','enabled: true',1).replace('test-mode: false','test-mode: true',1)+'\ntest-start: 1793181600000\n'
(cfg/'spawn-npcs.yml').write_text(npcconfig,encoding='utf-8')
for name in ['pip-catalogue.yml','quest-rotation.yml']:(cfg/name).write_text((repo/'src/main/resources'/name).read_text(encoding='utf-8'),encoding='utf-8')
(cfg/'quests.yml').write_text((repo/'release/october-21-refresh/quests.yml').read_text(encoding='utf-8').replace('enabled: false','enabled: true',1),encoding='utf-8')
skins=plugins/'FancyNpcs/skins';skins.mkdir(parents=True,exist_ok=True)
for name in ['niko.png','vendor-aviator.png']:shutil.copy2(root/'assets/npc-skins'/name,skins/name)
cache=plugins/'FancyNpcs/.data/skins';cache.mkdir(parents=True,exist_ok=True)
sourcecache=Path(os.environ.get('NN_SKIN_CACHE',str(root/'staging/upgrade-26.3-npc-20261101/plugins/FancyNpcs/.data/skins')))
for name in ['bmlrby5wbmc=.json','dmVuZG9yLWF2aWF0b3IucG5n.json']:shutil.copy2(sourcecache/name,cache/name)
metrics=plugins/'bStats';metrics.mkdir(exist_ok=True);(metrics/'config.yml').write_text('enabled: false\n')
(cfg/'config.yml').write_text('chat:\n  enabled: false\nrental-commands-enabled: false\nshop-gate:\n  enabled: false\n',encoding='utf-8')
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25615\nonline-mode=true\nwhite-list=true\nenforce-whitelist=true\nenable-rcon=false\nenable-query=false\nview-distance=2\nsimulation-distance=2\nlevel-name=interaction-test\nlevel-seed=1\nallow-nether=false\nmax-players=1\n')
(server/'bukkit.yml').write_text('settings:\n  allow-end: false\n')
bin=root/'tools/jdk/jdk-25.0.4.1+1/bin';classes=repo/'target/quest-selection-probe';classes.mkdir(exist_ok=True)
classpath=os.pathsep.join(str(p) for p in [*(root/'tools/m2').rglob('*.jar'),*(root/'staging/candidates').glob('*.jar'),*(server/'libraries').rglob('*.jar'),server/'versions/26.3/paper-26.3.jar'])
args=classes/'recovery-javac-args.txt';args.write_text('-cp\n"'+classpath.replace('\\','/')+'"\n-d\n"'+str(classes).replace('\\','/')+'"\n"'+str(local/'QuestChoiceProbe.java').replace('\\','/')+'"\n')
subprocess.run([str(bin/'javac.exe'),'@'+str(args)],check=True)
(classes/'plugin.yml').write_text('name: QuestChoiceProbe\nversion: 1\nmain: QuestChoiceProbe\napi-version: 26.2\ndepend: [NookCore]\n')
subprocess.run([str(bin/'jar.exe'),'cf',str(plugins/'QuestChoiceProbe.jar'),'-C',str(classes),'.'],check=True)
log=server/'quest-selection-probe.log'
with log.open('w',encoding='utf-8') as out:
    proc=subprocess.Popen([str(bin/'java.exe'),'-Xms512M','-Xmx2G','-jar',str(Path(os.environ.get('NN_PAPER_JAR',root/'staging/candidates/paper-26.3-237.jar'))),'--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True)
    try:proc.wait(timeout=180)
    except subprocess.TimeoutExpired:
        proc.stdin.write('stop\n');proc.stdin.flush()
        try:proc.wait(timeout=30)
        except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=15)
lines=log.read_text(encoding='utf-8',errors='replace').splitlines()
result={'passed':any('CHOICE_COMPLETE' in l for l in lines) and not any('CHOICE_FAILED' in l or 'Could not pass event' in l or 'generated an exception' in l for l in lines),'exit_code':proc.returncode,'jar_sha256':hashlib.sha256((plugins/'NookCore.jar').read_bytes()).hexdigest(),'log':str(log),'checks':[l for l in lines if 'CHOICE_' in l]}
(server/'RESULT.json').write_text(json.dumps(result,indent=2))
if (plugins/'QuestChoiceProbe.jar').exists():(plugins/'QuestChoiceProbe.jar').replace(server/'QuestChoiceProbe.jar.disabled')
print(json.dumps(result,indent=2));raise SystemExit(0 if result['passed'] and proc.returncode==0 else 1)
