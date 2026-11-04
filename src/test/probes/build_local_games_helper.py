"""Compile the test-only LocalGamesHelper jar against cached Paper 26.3 classes.

This command only builds an artifact. It never launches a service, copies a
production configuration/database/world, or installs the helper into a server.
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import os
import subprocess
import time

from run_games_checks import DEFAULT_OUTPUT, DEFAULT_UPGRADE, DEPENDENCIES, ROOT

LOCAL = Path(__file__).resolve().parent


def compile_helper(classes, cache, candidates, java_bin):
    """Return a standalone helper jar; never start Paper or modify NookCore."""
    classes = Path(classes)
    cache, candidates, java_bin = Path(cache), Path(candidates), Path(java_bin)
    classes.mkdir(parents=True, exist_ok=False)
    paper_server = cache / 'versions/26.3/paper-26.3.jar'
    if not paper_server.is_file():
        raise FileNotFoundError(f'Exact cached Paper 26.3 classes are missing: {paper_server}')
    jars = [paper_server]
    jars.extend(sorted((cache / 'libraries').rglob('*.jar'), key=lambda path: ('26.3' not in str(path), str(path))))
    jars.extend(candidates / source for source in DEPENDENCIES.values())
    jars.extend(sorted((ROOT / 'tools/m2').rglob('*.jar')))
    unique = list(dict.fromkeys(jars))
    for jar in unique:
        if not jar.is_file():
            raise FileNotFoundError(jar)
    classpath = os.pathsep.join(str(path) for path in unique)
    source = LOCAL / 'LocalGamesHelper.java'
    arguments = classes.parent / 'local-games-javac-args.txt'
    arguments.write_text('-proc:none\n-encoding\nUTF-8\n-cp\n"' + classpath.replace('\\', '/') +
                         '"\n-d\n"' + str(classes).replace('\\', '/') + '"\n"' +
                         str(source).replace('\\', '/') + '"\n', encoding='utf-8')
    subprocess.run([str(java_bin / 'javac.exe'), '@' + str(arguments)], check=True)
    (classes / 'plugin.yml').write_text('''name: LocalGamesHelper
version: 1
main: LocalGamesHelper
api-version: '26.2'
depend: [NookCore]
commands:
  testgames:
    description: Disposable local fixture controls
    permission: nookcore.localtest
permissions:
  nookcore.localtest:
    description: Operator-only disposable local fixture controls
    default: op
''', encoding='utf-8')
    artifact = classes.parent / 'LocalGamesHelper.jar'
    subprocess.run([str(java_bin / 'jar.exe'), 'cf', str(artifact), '-C', str(classes), '.'], check=True)
    return artifact


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument('--cache', type=Path, default=DEFAULT_UPGRADE / 'graves-paper237-check')
    parser.add_argument('--candidates', type=Path, default=DEFAULT_UPGRADE / 'candidates')
    parser.add_argument('--java-bin', type=Path, default=ROOT / 'tools/jdk/jdk-25.0.4.1+1/bin')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d-%H%M%S') + '-' + str(time.time_ns() % 1_000_000)
    run = args.output / ('local-games-helper-' + stamp)
    run.mkdir(exist_ok=False)
    artifact = compile_helper(run / 'classes', args.cache, args.candidates, args.java_bin)
    result = {'passed': True, 'server_launched': False, 'helper_jar': str(artifact),
              'helper_sha256': hashlib.sha256(artifact.read_bytes()).hexdigest(),
              'source_sha256': hashlib.sha256((LOCAL / 'LocalGamesHelper.java').read_bytes()).hexdigest()}
    (run / 'COMPILE_RESULT.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
