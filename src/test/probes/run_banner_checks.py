"""Event and two-process persistence proof on a fresh loopback Paper 26.3 fixture.

Uses only public candidate jars and cache files; never copies server configuration,
player lists, world data, or databases. Stops only its own child process.
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import os
import re
import shutil
import socket
import subprocess
import time
import run_games_checks as common

PORT = 25621
REPO = Path(__file__).resolve().parents[3]
LOCAL = Path(__file__).resolve().parent
OUTPUT = Path('D:/Documents/NikosNook/output/evening-20261104/banner')
UPGRADE = Path('D:/Documents/NikosNook/output/upgrade-26.3-20261031')


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def compile_probe(classes, cache, candidates, java_bin):
    classes.mkdir(exist_ok=False)
    jars = [cache / 'versions/26.3/paper-26.3.jar']
    jars += sorted((cache / 'libraries').rglob('*.jar'), key=lambda path: ('26.3' not in str(path), str(path)))
    jars += [candidates / source for source in common.DEPENDENCIES.values()]
    jars += sorted((REPO.parent / 'tools/m2').rglob('*.jar'))
    for jar in jars:
        if not jar.is_file():
            raise FileNotFoundError(jar)
    args = ['-proc:none', '-cp', os.pathsep.join(map(str, jars)), '-d', str(classes), str(LOCAL / 'BannerChecks.java'), str(REPO / 'release/november-games/PipProductsChecks.java')]
    argfile = classes / 'javac-args.txt'
    argfile.write_text('\n'.join('"' + arg.replace('\\', '/') + '"' for arg in args), encoding='utf-8')
    subprocess.run([str(java_bin / 'javac.exe'), '@' + str(argfile)], check=True)
    (classes / 'plugin.yml').write_text('name: BannerChecks\nversion: 1\nmain: BannerChecks\napi-version: 26.2\ndepend: [NookCore, WorldGuard]\n', encoding='utf-8')
    artifact = classes.parent / 'BannerChecks.jar'
    subprocess.run([str(java_bin / 'jar.exe'), 'cf', str(artifact), '-C', str(classes), '.'], check=True)
    return artifact


def launch(server, paper, java_bin, phase):
    common.guard_port()
    log = server / f'banner-phase{phase}.log'
    timed_out = False
    with log.open('w', encoding='utf-8') as output:
        proc = subprocess.Popen([str(java_bin / 'java.exe'), '-Xms512M', '-Xmx2G', '-jar', str(paper.resolve()), '--nogui'], cwd=server, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT, text=True, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        try:
            proc.wait(timeout=180)
        except subprocess.TimeoutExpired:
            timed_out = True
            try:
                proc.stdin.write('stop\n')
                proc.stdin.flush()
            except (BrokenPipeError, OSError):
                pass
            try:
                proc.wait(timeout=30)
            except subprocess.TimeoutExpired:
                proc.terminate()
                proc.wait(timeout=15)
    lines = log.read_text(encoding='utf-8', errors='replace').splitlines()
    token = 'BANNER_PHASE1_COMPLETE' if phase == 1 else 'BANNER_COMPLETE'
    complete = next((line for line in lines if token in line), '')
    failures = [line for line in lines if any(token in line for token in ['BANNER_FAILED', 'Could not pass event', 'generated an exception'])]
    count = re.search(r'checks=(\d+)', complete)
    return {'passed': bool(complete) and not failures and not timed_out and proc.returncode == 0, 'checks_count': int(count.group(1)) if count else 0, 'exit_code': proc.returncode, 'timed_out': timed_out, 'log': str(log), 'checks': [line for line in lines if 'BANNER_' in line or 'PRODUCT_COMPLETE' in line], 'failures': failures}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--compile-only', action='store_true')
    parser.add_argument('--core-jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=OUTPUT)
    parser.add_argument('--cache', type=Path, default=UPGRADE / 'graves-paper237-check')
    parser.add_argument('--candidates', type=Path, default=UPGRADE / 'candidates')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    run = common.contained(args.output / ('banner-paper237-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d-%H%M%S') + '-' + str(time.time_ns() % 1_000_000)), args.output)
    run.mkdir(exist_ok=False)
    java_bin = REPO.parent / 'tools/jdk/jdk-25.0.4.1+1/bin'
    probe = compile_probe(run / 'probe-classes', args.cache, args.candidates, java_bin)
    result = {'passed': True, 'compile_only': args.compile_only, 'source_sha256': sha256(LOCAL / 'BannerChecks.java'), 'runner_sha256': sha256(Path(__file__)), 'probe_sha256': sha256(probe), 'fixture_port': PORT, 'phases': []}
    if not args.compile_only:
        common.PORT = PORT
        common.guard_port()
        server = common.contained(run / 'server', run)
        paper = args.candidates / 'paper-26.3-237.jar'
        common.prepare(server, args.cache, args.candidates, args.core_jar, probe)
        (server / 'plugins/GamesChecks.jar').rename(server / 'plugins/BannerChecks.jar')
        result.update({'core_sha256': sha256(args.core_jar), 'installed_core_sha256': sha256(server / 'plugins/NookCore.jar'), 'paper_sha256': sha256(paper), 'server': str(server)})
        for phase in [1, 2]:
            outcome = launch(server, paper, java_bin, phase)
            result['phases'].append(outcome)
            if not outcome['passed']:
                result['passed'] = False
                break
        result['checks_count'] = sum(phase['checks_count'] for phase in result['phases'])
        installed = server / 'plugins/BannerChecks.jar'
        if installed.exists():
            installed.rename(server / 'BannerChecks.jar.disabled')
        common.guard_port()
    (run / 'RESULT.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result, indent=2))
    return 0 if result['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
