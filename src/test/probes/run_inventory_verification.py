from pathlib import Path
import subprocess,shutil,socket,os,json,sys
repo=Path(__file__).resolve().parents[3]
root=repo.parent
local=Path(__file__).resolve().parent
candidate='--candidate' in sys.argv
server=root/('staging/inventory-fixed-20261010' if candidate else 'staging/inventory-repro-20261010')
with socket.socket() as guard:guard.bind(('127.0.0.1',25588))
server.mkdir(parents=True,exist_ok=True)
plugins=server/'plugins';plugins.mkdir(exist_ok=True)
for name in ['cache','libraries','versions','.paper']:
    source=root/'staging/smoke'/name
    if source.exists() and not (server/name).exists():shutil.copytree(source,server/name)
for name in ['VaultUnlocked-2.20.3.jar','PlaceholderAPI-2.12.3.jar','ChestShop.jar','worldedit-bukkit-7.4.5.jar','worldguard-bukkit-7.0.19.jar']:shutil.copy2(root/'staging/candidates'/name,plugins/name)
artifact=repo/'target/nookcore-0.1.0-SNAPSHOT.jar' if candidate else root/'staging/local-update-20261009-shops/NookCore.jar'
shutil.copy2(artifact,plugins/'NookCore.jar')
cfg=plugins/'NookCore';cfg.mkdir(exist_ok=True)
(cfg/'config.yml').write_text('chat:\n  enabled: false\nrental-commands-enabled: false\nshop-gate:\n  enabled: false\n',encoding='utf-8')
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25588\nonline-mode=true\nwhite-list=true\nenforce-whitelist=true\nenable-rcon=false\nenable-query=false\nview-distance=2\nsimulation-distance=2\nlevel-name=repro-test\nlevel-seed=1\nallow-nether=false\nmax-players=1\n')
(server/'bukkit.yml').write_text('settings:\n  allow-end: false\n')
bin=root/'tools/jdk/jdk-25.0.4.1+1/bin';classes=repo/'target/inventory-probe';classes.mkdir(exist_ok=True)
jars=list((root/'tools/m2').rglob('*.jar'))+list((server/'libraries').rglob('*.jar'))+[server/'versions/26.2/paper-26.2.jar']
classpath=os.pathsep.join(map(str,jars))
compile_args=classes/'javac-args.txt'
compile_args.write_text('-cp\n"'+classpath.replace('\\','/')+'"\n-d\n"'+str(classes).replace('\\','/')+'"\n"'+str(local/'InventoryReproProbe.java').replace('\\','/')+'"\n')
subprocess.run([str(bin/'javac.exe'),'@'+str(compile_args)],check=True)
(classes/'plugin.yml').write_text('name: InventoryReproProbe\nversion: 1\nmain: InventoryReproProbe\napi-version: 26.2\ndepend: [NookCore]\n')
subprocess.run([str(bin/'jar.exe'),'cf',str(plugins/'InventoryReproProbe.jar'),'-C',str(classes),'.'],check=True)
log=server/'inventory-repro.log'
with log.open('w',encoding='utf-8') as out:
    proc=subprocess.Popen([str(bin/'java.exe'),'-Xms512M','-Xmx2G','-jar',str(root/'staging/paper-26.2-126.jar'),'--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True)
    try:proc.wait(timeout=180)
    except subprocess.TimeoutExpired:
        proc.stdin.write('stop\n');proc.stdin.flush()
        try:proc.wait(timeout=30)
        except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=15)
lines=log.read_text(encoding='utf-8').splitlines()
result={'exit_code':proc.returncode,'log':str(log),'checks':[l for l in lines if 'REPRO_' in l]}
(server/'RESULT.json').write_text(json.dumps(result,indent=2))
if (plugins/'InventoryReproProbe.jar').exists():(plugins/'InventoryReproProbe.jar').replace(server/'InventoryReproProbe.jar.disabled')
print(json.dumps(result,indent=2))

passed=proc.returncode==0 and any("REPRO_COMPLETE" in x and "semantic_differences=0" in x for x in result["checks"]) and not any("REPRO_FAILED" in x for x in result["checks"])
if candidate:passed=passed and any("REPRO_CUSTODY_PASS" in x for x in result["checks"])
raise SystemExit(0 if passed else 1)
