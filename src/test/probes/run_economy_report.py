"""Prepare and test a separate local reporting copy; preserve Travel servers."""
from pathlib import Path
import hashlib,json,os,re,shutil,socket,subprocess
repo=Path(__file__).resolve().parents[3]
root=repo.parent
source=root/'staging/travel-live-matched-20260928'
server=root/'staging/economy-reporting-20260928'
with socket.socket() as guard:guard.bind(('127.0.0.1',25578))
with socket.socket() as guard:guard.bind(('127.0.0.1',25579))
if not server.exists():
    print('Preparing separate reporting server from the stopped isolated integration copy.',flush=True)
    shutil.copytree(source,server,ignore=shutil.ignore_patterns('*.log','*.log.gz','INTEGRATION-RESULT.json','PREPARATION-RESULT.json','SOURCE-BASELINE-RESULT.json','BASELINE-RESULT.json'))
manifest=json.loads((server/'ISOLATION-MANIFEST.json').read_text(encoding='utf-8'))
for name in manifest['disabled_services']:
    if (server/'plugins'/name).exists():raise SystemExit('External service present: '+name)
props=(server/'server.properties').read_text()
props=re.sub(r'(?m)^server-port=.*$','server-port=25579',props)
props=re.sub(r'(?m)^motd=.*$','motd=Nikos Nook - private economy reporting prototype',props)
for expected in ['server-ip=127.0.0.1','online-mode=true','white-list=true','enforce-whitelist=true','enable-rcon=false','enable-query=false']:
    if expected not in props.splitlines():raise SystemExit('Isolation mismatch: '+expected)
(server/'server.properties').write_text(props,encoding='utf-8')
shutil.copy2(repo/'target/nookcore-0.1.0-SNAPSHOT.jar',server/'plugins/NookCore.jar')
sha=hashlib.sha256((server/'plugins/NookCore.jar').read_bytes()).hexdigest()
manifest.update({'source':str(source),'purpose':'Separate read-only economy-reporting development prototype; not the Travel release.','source_nookcore_sha256':manifest.get('candidate_sha256'),'nookcore_sha256':sha,'candidate_sha256':sha})
manifest['properties_overridden']['server-port']='25579'
manifest['properties_overridden']['motd']='Nikos Nook - private economy reporting prototype'
(server/'ISOLATION-MANIFEST.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
bin=root/'tools/jdk/jdk-25.0.4.1+1/bin'
classes=repo/'target/economy-report-probe';classes.mkdir(parents=True,exist_ok=True)
classpath=os.pathsep.join(str(p) for p in (root/'tools/m2').rglob('*.jar'))
subprocess.run([str(bin/'javac.exe'),'-encoding','UTF-8','-cp',classpath,'-d',str(classes),str(Path(__file__).with_name('EconomyReportProbe.java'))],check=True)
(classes/'plugin.yml').write_text('name: EconomyReportProbe\nversion: 1\nmain: EconomyReportProbe\napi-version: 26.2\ndepend: [NookCore]\n',encoding='utf-8')
probe=server/'plugins/EconomyReportProbe.jar'
subprocess.run([str(bin/'jar.exe'),'cf',str(probe),'-C',str(classes),'.'],check=True)
log=server/'economy-report-probe.log'
print('Running read-only staff report checks against real Paper and the copied snapshot.',flush=True)
with log.open('w',encoding='utf-8') as out:
    proc=subprocess.Popen([str(bin/'java.exe'),'-Xms1G','-Xmx4G','-jar','server.jar','--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True)
    forced=False
    try:proc.wait(timeout=180)
    except subprocess.TimeoutExpired:
        proc.stdin.write('stop\n');proc.stdin.flush()
        try:proc.wait(timeout=60)
        except subprocess.TimeoutExpired:proc.kill();proc.wait();forced=True
text=log.read_text(encoding='utf-8',errors='replace')
passed='ECONOMY_REPORT_PROBE_PASS' in text and 'ECONOMY_REPORT_PROBE_FAILED' not in text and proc.returncode==0 and not forced
record={'passed':passed,'exit_code':proc.returncode,'forced_stop':forced,'candidate_sha256':sha,'log':str(log),'checks':[line for line in text.splitlines() if '[EconomyReportProbe]' in line and ('PASS ' in line or 'PROBE_' in line)]}
(server/'REPORT-RESULT.json').write_text(json.dumps(record,indent=2),encoding='utf-8')
if passed:probe.replace(server/'EconomyReportProbe.jar.disabled')
print(json.dumps(record,indent=2),flush=True)
raise SystemExit(0 if passed else 1)