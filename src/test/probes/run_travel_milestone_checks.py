"""Real Paper 26.3 travel adapter checks on fresh generated loopback-only port 25622.
No production files or global fake-player registration. Runs two clean server processes.
"""
from pathlib import Path
import argparse, datetime, hashlib, json, os, re, shutil, socket, subprocess, time
import run_games_checks as base
PORT=25622
LOCAL=Path(__file__).resolve().parent
REPO=LOCAL.parents[2]
ROOT=REPO.parent
DEFAULT=Path('D:/Documents/NikosNook/output/evening-20261104/travel')

def guard():
    with socket.socket() as s:s.bind(('127.0.0.1',PORT))

def compile_probe(classes,cache,candidates,java):
    classes.mkdir()
    jars=[cache/'versions/26.3/paper-26.3.jar']
    jars+=sorted((cache/'libraries').rglob('*.jar'),key=lambda p:('26.3' not in str(p),str(p)))
    jars += [candidates/p for p in base.DEPENDENCIES.values()]
    jars += list((ROOT/'tools/m2').rglob('*.jar'))
    for p in jars:
        if not p.is_file():raise FileNotFoundError(p)
    args=classes/'javac-args.txt'
    args.write_text('-proc:none\n-encoding\nUTF-8\n-cp\n"'+os.pathsep.join(map(str,jars)).replace('\\','/')+'"\n-d\n"'+str(classes).replace('\\','/')+'"\n"'+str(LOCAL/'TravelMilestoneChecks.java').replace('\\','/')+'"\n',encoding='utf-8')
    subprocess.run([str(java/'javac.exe'),'@'+str(args)],check=True)
    (classes/'plugin.yml').write_text('name: TravelMilestoneChecks\nversion: 1\nmain: TravelMilestoneChecks\napi-version: 26.2\ndepend: [NookCore]\n',encoding='utf-8')
    jar=classes.parent/'TravelMilestoneChecks.jar'
    subprocess.run([str(java/'jar.exe'),'cf',str(jar),'-C',str(classes),'.'],check=True)
    return jar

def launch(server,paper,java,phase):
    guard();log=server/f'travel-{phase}.log';timedout=False
    with log.open('w',encoding='utf-8') as out:
        proc=subprocess.Popen([str(java/'java.exe'),'-Xms512M','-Xmx2G','-jar',str(paper.resolve()),'--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        try:proc.wait(timeout=180)
        except subprocess.TimeoutExpired:
            timedout=True
            try:proc.stdin.write('stop\n');proc.stdin.flush()
            except (BrokenPipeError,OSError):pass
            try:proc.wait(timeout=30)
            except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=15)
    lines=log.read_text(encoding='utf-8',errors='replace').splitlines()
    marker='TRAVEL_PHASE1_COMPLETE' if phase==1 else 'TRAVEL_RESTART_COMPLETE'
    complete=next((line for line in lines if marker in line),'')
    failed=any(token in line for line in lines for token in ['TRAVEL_FAILED','Could not pass event','generated an exception','Final travel milestone save failed','Exception ticking world'])
    return dict(passed=bool(complete) and not failed and not timedout and proc.returncode==0,exit_code=proc.returncode,timed_out=timedout,checks_count=int(re.search(r'checks=(\d+)',complete).group(1)) if complete else 0,log=str(log),checks=[line for line in lines if 'TRAVEL_' in line])

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--compile-only',action='store_true');parser.add_argument('--output',type=Path,default=DEFAULT);args=parser.parse_args()
    cache=base.DEFAULT_UPGRADE/'graves-paper237-check';candidates=base.DEFAULT_UPGRADE/'candidates';paper=candidates/'paper-26.3-237.jar';core=REPO/'target/nookcore-0.1.0-SNAPSHOT.jar';java=ROOT/'tools/jdk/jdk-25.0.4.1+1/bin'
    args.output.mkdir(parents=True,exist_ok=True)
    stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d-%H%M%S')+'-'+str(time.time_ns()%1000000)
    run=base.contained(args.output/('travel-paper237-'+stamp),args.output);run.mkdir()
    probe=compile_probe(run/'probe-classes',cache,candidates,java)
    if args.compile_only:print(json.dumps({'passed':True,'probe_jar':str(probe),'server_launched':False},indent=2));return 0
    guard();base.PORT=PORT;server=run/'server';base.prepare(server,cache,candidates,core,probe)
    (server/'plugins/GamesChecks.jar').rename(server/'plugins/TravelMilestoneChecks.jar')
    config=server/'plugins/NookCore/config.yml'
    with config.open('a',encoding='utf-8') as out:out.write('''travel:
  grounded:
    enabled: false
  lifetime-milestones:
    enabled: true
    road-mount-blocks: 20
    rail-blocks: 20
''')
    shutil.copy2(REPO/'src/main/resources/travel-advancements.yml',server/'plugins/NookCore/travel-advancements.yml')
    properties=server/'server.properties';properties.write_text(properties.read_text().replace('games-probe-world','travel-probe-world'),encoding='utf-8')
    phases=[launch(server,paper,java,1)]
    if phases[0]['passed']:phases.append(launch(server,paper,java,2))
    result=dict(passed=len(phases)==2 and all(p['passed'] for p in phases),checks_count=sum(p['checks_count'] for p in phases),fixture_port=PORT,core_sha256=base.sha256(core),paper_sha256=base.sha256(paper),probe_sha256=base.sha256(probe),source_sha256=base.sha256(LOCAL/'TravelMilestoneChecks.java'),phases=phases,limits='Synthetic server-backed rider; real entity state, controlled positions/clocks; client steering remains manual.')
    (run/'RESULT.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    (server/'plugins/TravelMilestoneChecks.jar').rename(server/'TravelMilestoneChecks.jar.disabled')
    print(json.dumps(result,indent=2));return 0 if result['passed'] else 1
if __name__=='__main__':raise SystemExit(main())
