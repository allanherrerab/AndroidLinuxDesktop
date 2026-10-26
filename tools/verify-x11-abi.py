#!/usr/bin/env python3
"""Compare compiled Java JNI signatures to the pinned upstream native tables."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import zipfile

project=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--build-type',choices=('debug','release','acceptance'),default='debug')
args=parser.parse_args()
metadata=json.loads((project/'dist/x11.manifest.json').read_text())
source=project/'build/x11'/f'termux-x11-{metadata["commit"]}.zip'
classes=project/f'build/app/intermediates/javac/{args.build_type}/compile{args.build_type.capitalize()}JavaWithJavac/classes'
with zipfile.ZipFile(source) as archive:
    prefix=f'termux-x11-{metadata["commit"]}/lorie/src/main/cpp/lorie/'
    activity=archive.read(prefix+'activity.cpp').decode()
    cmd=archive.read(prefix+'cmdentrypoint.cpp').decode()
    tables={
        'LorieView':activity[activity.index('static JNINativeMethod methods[]'):activity.index('vm->AttachCurrentThread(&env',activity.index('static JNINativeMethod methods[]'))],
        'MainActivity':activity[activity.index('static JNINativeMethod mainActivityMethods[]'):],
        'CmdEntryPoint':cmd[cmd.index('void registerCmdEntryPointNatives'):],
    }
count=0
for name,table in tables.items():
    output=subprocess.check_output(['javap','-p','-s','-classpath',str(classes),'com.termux.x11.'+name],text=True)
    methods={}
    for declaration,descriptor in re.findall(r'([^\n]+\([^\n]*\);)\s+descriptor: ([^\n]+)',output):
        method=re.search(r'([\w]+)\(',declaration).group(1)
        methods.setdefault(method,set()).add(descriptor.strip())
    for method,descriptor in re.findall(r'\{"([\w]+)",\s*"([^"]+)"',table):
        if descriptor not in methods.get(method,set()):raise SystemExit(f'JNI ABI mismatch: {name}.{method}{descriptor}')
        count+=1
print(f'Compiled Java matches all {count} pinned X11 native registration signatures')
