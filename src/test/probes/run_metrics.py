from pathlib import Path
import subprocess,shutil,socket,os,json,hashlib
repo=Path(__file__).resolve().parents[3];root=repo.parent
server=root/'staging/economy-metrics-20260930'
with socket.socket() as guard:guard.bind(('127.0.0.1',25582))
server.mkdir(parents=True,exist_ok=True);plugins=server/'plugins';plugins.mkdir(exist_ok=True)
for name in ['cache','libraries','versions','.paper']:
    source=root/'staging/smoke'/name
    if source.exists() and not (server/name).exists():shutil.copytree(source,server/name)
for name in ['VaultUnlocked-2.20.3.jar','worldedit-bukkit-7.4.5.jar','worldguard-bukkit-7.0.19.jar','PlaceholderAPI-2.12.3.jar']:
    shutil.copy2(root/'staging/candidates'/name,plugins/name)
shutil.copy2(repo/'target/nookcore-0.1.0-SNAPSHOT.jar',plugins/'NookCore.jar')
config=plugins/'NookCore';config.mkdir(exist_ok=True)
if not (config/'config.yml').exists():shutil.copy2(repo/'src/main/resources/config.yml',config/'config.yml')
shutil.copy2(root/'staging/paper-26.2-126.jar',server/'server.jar')
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25582\nonline-mode=true\nwhite-list=true\nenforce-whitelist=true\nenable-rcon=false\nenable-query=false\nview-distance=3\nsimulation-distance=3\nlevel-name=metrics-world\nlevel-seed=1\nallow-nether=false\nmax-players=3\n')
(server/'bukkit.yml').write_text('settings:\n  allow-end: false\n')
for name in ['ops.json','whitelist.json']:
    source=root/'staging/travel-live-matched-20260928'/name
    if source.exists():shutil.copy2(source,server/name)
bin=root/'tools/jdk/jdk-25.0.4.1+1/bin';classes=repo/'target/metrics-probe';classes.mkdir(parents=True,exist_ok=True)
classpath=os.pathsep.join(str(p) for p in (root/'tools/m2').rglob('*.jar'))
subprocess.run([str(bin/'javac.exe'),'-encoding','UTF-8','-cp',classpath,'-d',str(classes),str(Path(__file__).with_name('MetricsProbe.java'))],check=True)
(classes/'plugin.yml').write_text('name: MetricsProbe\nversion: 1\nmain: MetricsProbe\napi-version: 26.2\ndepend: [NookCore]\n')
probe=plugins/'MetricsProbe.jar';subprocess.run([str(bin/'jar.exe'),'cf',str(probe),'-C',str(classes),'.'],check=True)
log=server/'metrics-probe.log'
with log.open('w',encoding='utf-8') as out:
    proc=subprocess.Popen([str(bin/'java.exe'),'-Xms512M','-Xmx2G','-jar','server.jar','--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True)
    try:proc.wait(timeout=180)
    except subprocess.TimeoutExpired:
        proc.stdin.write('stop\n');proc.stdin.flush();proc.wait(timeout=60)
text=log.read_text(encoding='utf-8',errors='replace')
passed='METRICS_PROBE_PASS' in text and 'METRICS_PROBE_FAILED' not in text and proc.returncode==0
result={'passed':passed,'exit_code':proc.returncode,'jar_sha256':hashlib.sha256((plugins/'NookCore.jar').read_bytes()).hexdigest(),'checks':[l for l in text.splitlines() if '[MetricsProbe]' in l and ('PASS ' in l or 'PROBE_' in l)]}
(server/'RESULT.json').write_text(json.dumps(result,indent=2))
probe.replace(server/'MetricsProbe.jar.disabled')
(server/'start.ps1').write_text("Set-Location -LiteralPath $PSScriptRoot\n& '"+str(bin/'java.exe')+"' -Xms512M -Xmx2G -jar server.jar --nogui\n")
print(json.dumps(result,indent=2));raise SystemExit(0 if passed else 1)
