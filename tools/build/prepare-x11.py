#!/usr/bin/env python3
"""Verify and stage the pinned, unmodified Termux:X11 native engine and source."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

PROJECT = Path(__file__).resolve().parents[2]
COMMIT = '7e5f5c5c9226e2cdab392b2efba91bc4bd86463e'
APK_URL = 'https://github.com/termux/termux-x11/releases/download/nightly/termux-x11-universal-debug.apk'
APK_SHA = 'd9eb4b99f6f89384caa3a136f9c2d4d3c3e6ac356a1ee264db9e579ddc848a20'
SOURCE_SHA = '6910977c14e432527e429a625370a5f473d07f41053234b007ebbab45c4b5e73'

def fetch(path, url, expected):
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(url, timeout=90) as source, path.with_suffix('.partial').open('wb') as out:
            shutil.copyfileobj(source, out)
        path.with_suffix('.partial').replace(path)
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if expected and actual != expected:
        raise SystemExit(f'{path}: checksum mismatch. The nightly URL may have moved; restore the pinned cached artifact instead of accepting an unverified update.')
    return actual

def main():
    cache = PROJECT/'build/x11'
    cache.mkdir(parents=True,exist_ok=True)
    artifact = cache/'termux-x11.apk'
    if not artifact.exists() and (PROJECT/'build/integration-sources/termux-x11.apk').exists():
        shutil.copyfile(PROJECT/'build/integration-sources/termux-x11.apk',artifact)
    fetch(artifact, APK_URL, APK_SHA)
    source = cache/f'termux-x11-{COMMIT}.zip'
    source_sha = fetch(source, f'https://codeload.github.com/termux/termux-x11/zip/{COMMIT}', SOURCE_SHA)
    with zipfile.ZipFile(artifact) as archive:
        if not any(COMMIT.encode() in archive.read(n) for n in archive.namelist() if n.endswith('.dex')):
            raise SystemExit('X11 artifact commit does not match the pinned source')
        binary = archive.read('lib/arm64-v8a/libXlorie.so')
    target = PROJECT/'dist/runtime/arm64-v8a/libXlorie.so'
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_bytes(binary)
    (PROJECT/'dist/x11.manifest.json').write_text(json.dumps({
        'project':'Termux:X11', 'commit':COMMIT, 'apk_url':APK_URL, 'apk_sha256':APK_SHA,
        'source_url':f'https://codeload.github.com/termux/termux-x11/zip/{COMMIT}',
        'source_sha256':source_sha, 'binaries':{'libXlorie.so':hashlib.sha256(binary).hexdigest()},
    },indent=2)+'\n')
    # These annotations exist on the Android boot classpath but not in android.jar.
    # Compile-only declarations preserve the upstream registered JNI calling ABI.
    stubs = cache/'annotations/dalvik/annotation/optimization'
    stubs.mkdir(parents=True,exist_ok=True)
    for name in ('CriticalNative','FastNative'):
        (stubs/(name+'.java')).write_text('package dalvik.annotation.optimization;\n'
            'import java.lang.annotation.*;\n@Retention(RetentionPolicy.RUNTIME)\n'
            '@Target(ElementType.METHOD)\npublic @interface '+name+' {}\n')
    classes=cache/'annotation-classes';classes.mkdir(exist_ok=True)
    subprocess.run(['javac','-d',str(classes),*[str(p) for p in stubs.glob('*.java')]],check=True)
    subprocess.run(['jar','cf',str(cache/'annotations.jar'),'-C',str(classes),'.'],check=True)
    license_file=PROJECT/'licenses/Termux-X11-GPL-3.0.txt'
    license_file.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(source) as archive:
        license_file.write_bytes(archive.read(f'termux-x11-{COMMIT}/LICENSE'))
    print('Verified pinned X11 engine, corresponding source, and compile-only JNI annotations')

if __name__=='__main__': main()
