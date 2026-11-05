"""Compile and run GamesChecks against a fresh, local Paper 26.3 fixture.

`--compile-only` validates the probe without copying a core jar or launching Java.
The normal run never reads a production config, database, world, or player list.
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

PORT = 25619
REPO = Path(__file__).resolve().parents[3]
ROOT = REPO.parent
LOCAL = Path(__file__).resolve().parent
DEFAULT_OUTPUT = Path('D:/Documents/NikosNook/output/overnight-20261103')
DEFAULT_UPGRADE = Path('D:/Documents/NikosNook/output/upgrade-26.3-20261031')
DEPENDENCIES = {
    'VaultUnlocked-2.20.3.jar': 'VaultUnlocked-2.20.3.jar',
    'PlaceholderAPI-2.12.3.jar': 'PlaceholderAPI-2.12.3.jar',
    'ChestShop.jar': 'ChestShop-build-481.jar',
    'worldedit-bukkit-7.4.6.jar': 'worldedit-bukkit-7.4.6.jar',
    'worldguard-bukkit-7.0.20.jar': 'worldguard-bukkit-7.0.20.jar',
    'FancyNpcs-2.12.1.jar': 'FancyNpcs-2.12.1.jar',
}


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def guard_port():
    # Bind only our fixed fixture port; never inspect or stop other server ports.
    with socket.socket() as guard:
        guard.bind(('127.0.0.1', PORT))


def contained(path, parent):
    resolved, boundary = path.resolve(), parent.resolve()
    if resolved == boundary or boundary not in resolved.parents:
        raise ValueError(f'Fixture must be a child of the probe output directory: {resolved}')
    return resolved


def compile_probe(classes, cache, candidates, java_bin):
    classes.mkdir(parents=True, exist_ok=False)
    paper_server = cache / 'versions/26.3/paper-26.3.jar'
    if not paper_server.is_file():
        raise FileNotFoundError(f'Exact cached Paper 26.3 server jar is missing: {paper_server}')
    # Exact server classes first. Everything else comes from the verified local
    # server cache, candidate dependencies and the portable Maven cache.
    jars = [paper_server]
    jars.extend(sorted((cache / 'libraries').rglob('*.jar'), key=lambda path: ('26.3' not in str(path), str(path))))
    jars.extend(candidates / source for source in DEPENDENCIES.values())
    jars.extend(sorted((ROOT / 'tools/m2').rglob('*.jar')))
    seen = set()
    jars = [path for path in jars if not (str(path) in seen or seen.add(str(path)))]
    for path in jars:
        if not path.is_file():
            raise FileNotFoundError(path)
    classpath = os.pathsep.join(str(path) for path in jars)
    javac_args = classes / 'javac-args.txt'
    javac_args.write_text('-proc:none\n-cp\n"' + classpath.replace('\\', '/') + '"\n-d\n"' + str(classes).replace('\\', '/') + '"\n"' + str(LOCAL / 'GamesChecks.java').replace('\\', '/') + '"\n"' + str(REPO / 'release/november-games/PipProductsChecks.java').replace('\\', '/') + '"\n"' + str(REPO / 'release/niko-roaming/NikoRoamingChecks.java').replace('\\', '/') + '"\n', encoding='utf-8')
    subprocess.run([str(java_bin / 'javac.exe'), '@' + str(javac_args)], check=True)
    (classes / 'plugin.yml').write_text('name: GamesChecks\nversion: 1\nmain: GamesChecks\napi-version: 26.2\ndepend: [NookCore]\n', encoding='utf-8')
    artifact = classes.parent / 'GamesChecks.jar'
    subprocess.run([str(java_bin / 'jar.exe'), 'cf', str(artifact), '-C', str(classes), '.'], check=True)
    return artifact


def prepare(server, cache, candidates, core_jar, probe_jar):
    server.mkdir(parents=True, exist_ok=False)
    plugins = server / 'plugins'
    plugins.mkdir()
    for name in ['cache', 'libraries', 'versions', '.paper']:
        source = cache / name
        if source.exists():
            shutil.copytree(source, server / name)
    for destination, source in DEPENDENCIES.items():
        shutil.copy2(candidates / source, plugins / destination)
    shutil.copy2(core_jar, plugins / 'NookCore.jar')
    shutil.copy2(probe_jar, plugins / 'GamesChecks.jar')
    config = plugins / 'NookCore'
    config.mkdir()
    (config / 'config.yml').write_text('''chat:
  enabled: false
joining-bonus-cents: 0
rental-commands-enabled: false
shop-gate:
  enabled: false
advancement-rewards-enabled: true
advancement-rewards: {}
games:
  coinflip:
    enabled: true
    minimum-cents: 100
    maximum-cents: 10000
    invitation-seconds: 120
    cooldown-seconds: 5
    incoming-limit: 5
''', encoding='utf-8')
    (config / 'wordle.yml').write_text('enabled: true\nreward-cents: 100\n', encoding='utf-8')
    # Install candidate public catalogues, never an existing server data folder.
    for name in ['game-advancements.yml', 'advancements.yml', 'quests.yml', 'quest-rotation.yml', 'spawn-npcs.yml']:
        source = REPO / 'src/main/resources' / name
        if source.exists():
            shutil.copy2(source, config / name)
    # Neither NPCs nor weekly quests are part of this isolated command fixture.
    (config / 'spawn-npcs.yml').write_text('enabled: false\n', encoding='utf-8')
    quest_source = (REPO / 'src/main/resources/quests.yml').read_text(encoding='utf-8')
    (config / 'quests.yml').write_text(re.sub(r'(?m)^enabled:.*$', 'enabled: false', quest_source, count=1), encoding='utf-8')
    metrics = plugins / 'bStats'
    metrics.mkdir()
    (metrics / 'config.yml').write_text('enabled: false\n', encoding='utf-8')
    (server / 'eula.txt').write_text('eula=true\n', encoding='utf-8')
    (server / 'server.properties').write_text(f'''server-ip=127.0.0.1
server-port={PORT}
online-mode=true
white-list=true
enforce-whitelist=true
enable-rcon=false
enable-query=false
enable-jmx-monitoring=false
view-distance=2
simulation-distance=2
level-name=games-probe-world
level-seed=1
level-type=minecraft:flat
allow-nether=false
max-players=1
spawn-protection=0
pause-when-empty-seconds=-1
''', encoding='utf-8')
    (server / 'bukkit.yml').write_text('settings:\n  allow-end: false\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--compile-only', action='store_true')
    parser.add_argument('--output', type=Path, default=Path(os.environ.get('NN_GAMES_OUTPUT', DEFAULT_OUTPUT)))
    parser.add_argument('--cache', type=Path, default=Path(os.environ.get('NN_PAPER_CACHE', DEFAULT_UPGRADE / 'graves-paper237-check')))
    parser.add_argument('--candidates', type=Path, default=Path(os.environ.get('NN_PLUGIN_CANDIDATES', DEFAULT_UPGRADE / 'candidates')))
    parser.add_argument('--core-jar', type=Path, default=Path(os.environ.get('NN_CORE_JAR', REPO / 'target/nookcore-0.1.0-SNAPSHOT.jar')))
    parser.add_argument('--paper-jar', type=Path, default=Path(os.environ.get('NN_PAPER_JAR', DEFAULT_UPGRADE / 'candidates/paper-26.3-237.jar')))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d-%H%M%S') + '-' + str(time.time_ns() % 1_000_000)
    run = contained(args.output / ('games-paper237-' + stamp), args.output)
    run.mkdir(exist_ok=False)
    java_bin = ROOT / 'tools/jdk/jdk-25.0.4.1+1/bin'
    probe = compile_probe(run / 'probe-classes', args.cache, args.candidates, java_bin)
    if args.compile_only:
        result = {'compile_only': True, 'passed': True, 'probe_jar': str(probe), 'source_sha256': sha256(LOCAL / 'GamesChecks.java'), 'server_launched': False}
        (run / 'COMPILE_RESULT.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
        print(json.dumps(result, indent=2))
        return 0
    if not args.core_jar.is_file() or not args.paper_jar.is_file():
        raise FileNotFoundError('Build the candidate core jar and provide the exact local Paper jar before running the fixture.')
    guard_port()
    server = contained(run / 'server', run)
    prepare(server, args.cache, args.candidates, args.core_jar, probe)
    guard_port()
    log = server / 'games-checks.log'
    timed_out = False
    with log.open('w', encoding='utf-8') as output:
        proc = subprocess.Popen([str(java_bin / 'java.exe'), '-Xms512M', '-Xmx2G', '-jar', str(args.paper_jar.resolve()), '--nogui'], cwd=server, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT, text=True, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        try:
            proc.wait(timeout=180)
        except subprocess.TimeoutExpired:
            timed_out = True
            # Only stop the child this runner launched. Other ports and processes
            # are deliberately outside this script's scope.
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
    complete = next((line for line in lines if 'GAMES_COMPLETE' in line), '')
    failed = any(token in line for line in lines for token in ['GAMES_FAILED', 'Could not pass event', 'generated an exception'])
    match = re.search(r'checks=(\d+)', complete)
    result = {
        'passed': bool(complete) and not failed and not timed_out and proc.returncode == 0,
        'exit_code': proc.returncode,
        'timed_out': timed_out,
        'checks_count': int(match.group(1)) if match else 0,
        'core_sha256': sha256(server / 'plugins/NookCore.jar'),
        'paper_sha256': sha256(args.paper_jar),
        'probe_sha256': sha256(probe),
        'source_sha256': sha256(LOCAL / 'GamesChecks.java'),
        'fixture_port': PORT,
        'log': str(log),
        'checks': [line for line in lines if 'GAMES_' in line],
    }
    (server / 'RESULT.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    installed_probe = server / 'plugins/GamesChecks.jar'
    if installed_probe.exists():
        installed_probe.replace(server / 'GamesChecks.jar.disabled')
    print(json.dumps(result, indent=2))
    return 0 if result['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())

