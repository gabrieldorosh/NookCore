"""Combined release checks on a disposable localhost server; never copies live data."""
from pathlib import Path
import subprocess,shutil,socket,os,json,hashlib,time,sys
repo=Path(__file__).resolve().parents[3];root=repo.parent;local=Path(__file__).resolve().parent
server=root/'staging/maintenance-20261010'
with socket.socket() as guard:guard.bind(('127.0.0.1',25592))
server.mkdir(exist_ok=True);plugins=server/'plugins';plugins.mkdir(exist_ok=True)
for name in ['cache','libraries','versions','.paper']:
 source=root/'staging/smoke'/name
 if source.exists() and not (server/name).exists():shutil.copytree(source,server/name)
for name in ['VaultUnlocked-2.20.3.jar','PlaceholderAPI-2.12.3.jar','ChestShop.jar','worldedit-bukkit-7.4.5.jar','worldguard-bukkit-7.0.19.jar']:shutil.copy2(root/'staging/candidates'/name,plugins/name)
artifact=repo/'target/nookcore-0.1.0-SNAPSHOT.jar';shutil.copy2(artifact,plugins/'NookCore.jar');sha=hashlib.sha256(artifact.read_bytes()).hexdigest()
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25592\nonline-mode=true\nwhite-list=true\nenforce-whitelist=true\nenable-rcon=false\nenable-query=false\nview-distance=2\nsimulation-distance=2\nlevel-name=maintenance-test\nlevel-seed=1\nallow-nether=false\nmax-players=1\n')
(server/'bukkit.yml').write_text('settings:\n  allow-end: false\n')
cfg=plugins/'NookCore';cfg.mkdir(exist_ok=True)
resume='--resume' in sys.argv
assert resume or not (cfg/'activity.db').exists(),'Use a fresh directory or --resume; never delete existing data.'
base='chat:\n  enabled: true\n  display-titles:\n    "983f1c6c-7f17-4729-ab16-8c0d3407592a": mrs-bee\nrental-commands-enabled: false\nshop-gate:\n  enabled: false\n'
bin=root/'tools/jdk/jdk-25.0.4.1+1/bin';classes=repo/'target/maintenance-probes';classes.mkdir(exist_ok=True)
classpath=os.pathsep.join(str(p) for p in [*(root/'tools/m2').rglob('*.jar'),*(root/'staging/candidates').glob('*.jar'),*(server/'libraries').rglob('*.jar'),server/'versions/26.2/paper-26.2.jar'])
sources=['MetricsProbe','AfkProbe','EconomyReportProbe','StockRecoveryProbe','ShopInteractionProbe','QuickStockProbe','MaintenanceRestartProbe']
args=classes/'javac.args';args.write_text('-encoding\nUTF-8\n-cp\n"'+classpath.replace('\\','/')+'"\n-d\n"'+str(classes).replace('\\','/')+'"\n'+''.join('"'+str(local/(name+'.java')).replace('\\','/')+'"\n' for name in sources),encoding='utf-8')
subprocess.run([str(bin/'javac.exe'),'@'+str(args)],check=True)
phases=[('metrics','MetricsProbe','METRICS_PROBE_PASS'),('restart','MaintenanceRestartProbe','MAINTENANCE_COMPLETE'),('disabled','MaintenanceRestartProbe','MAINTENANCE_COMPLETE'),('afk','AfkProbe','AFK_PASS'),('reports','EconomyReportProbe','ECONOMY_REPORT_PROBE_PASS'),('recovery','StockRecoveryProbe','RECOVERY_COMPLETE'),('interactions','ShopInteractionProbe','INTERACTION_COMPLETE'),('quick-stock','QuickStockProbe','QUICK_COMPLETE')]
results=[]
if resume:
 previous=json.loads((server/'RESULT.json').read_text(encoding='utf-8'))
 assert previous['jar_sha256']==sha,'Cannot resume against a different build'
 for result in previous['phases']:
  if not result['passed']:break
  assert result['phase']==phases[len(results)][0]
  results.append(result)
for name,main,success in phases[len(results):]:
 config=base
 if name=='disabled':config=base.replace('  enabled: true','  enabled: true\n  afk:\n    enabled: false')+'economy-metrics:\n  enabled: false\nmaintenance-probe-disabled: true\n'
 (cfg/'config.yml').write_text(config,encoding='utf-8')
 (classes/'plugin.yml').write_text('name: MaintenanceProbe\nversion: 1\nmain: '+main+'\napi-version: 26.2\ndepend: [NookCore, ChestShop, PlaceholderAPI, WorldGuard]\n')
 probe=plugins/'MaintenanceProbe.jar';subprocess.run([str(bin/'jar.exe'),'cf',str(probe),'-C',str(classes),'.'],check=True)
 log=server/(name+'.log')
 if log.exists():shutil.copy2(log,server/(name+'-previous.log'))
 with log.open('w',encoding='utf-8') as out:
  proc=subprocess.Popen([str(bin/'java.exe'),'-Xms512M','-Xmx2G','-jar',str(root/'staging/paper-26.2-126.jar'),'--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True)
  try:proc.wait(timeout=180)
  except subprocess.TimeoutExpired:
   proc.stdin.write('stop\n');proc.stdin.flush()
   try:proc.wait(timeout=30)
   except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=15)
 lines=log.read_text(encoding='utf-8',errors='replace').splitlines()
 passed=proc.returncode==0 and any(success in l for l in lines) and not any('_FAILED' in l or 'Could not pass event' in l or 'generated an exception' in l for l in lines)
 result={'phase':name,'passed':passed,'exit_code':proc.returncode,'jar_sha256':sha,'log':str(log),'checks':[l for l in lines if '[MaintenanceProbe]' in l and ('PASS' in l or 'COMPLETE' in l or 'FAILED' in l)]};results.append(result)
 (server/'RESULT.json').write_text(json.dumps({'passed':all(r['passed'] for r in results),'finished':len(results)==len(phases),'jar_sha256':sha,'phases':results},indent=2),encoding='utf-8')
 probe.replace(server/(name+'-probe.jar.disabled'))
 print(json.dumps(result),flush=True)
 if not passed:raise SystemExit(1)
print('MAINTENANCE_SUITE_COMPLETE',flush=True)
