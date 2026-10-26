import concurrent.futures, hashlib, os, urllib.request, zipfile
from pathlib import Path
base=Path(__file__).resolve().parents[2] / 'build'
downloads=base/'downloads'
downloads.mkdir(parents=True,exist_ok=True)
jobs=[
('https://dl.google.com/android/repository/android-ndk-r27c-linux.zip', 'ndk.zip', '090e8083a715fdb1a3e402d0763c388abb03fb4e', 'sdk/ndk'),
('https://dl.google.com/android/repository/platform-36_r02.zip', 'platform.zip', None, 'sdk/platforms'),
('https://dl.google.com/android/repository/build-tools_r36_linux.zip', 'buildtools.zip', None, 'sdk/build-tools'),
('https://dl.google.com/android/repository/cmake-3.22.1-linux.zip', 'cmake.zip', 'fd0a48b4a758310df8c7aa51f59840ed48fe7ed8', 'sdk/cmake/3.22.1'),
('https://downloads.gradle.org/distributions/gradle-8.11.1-bin.zip', 'gradle.zip', None, 'tools'),
]
def run(job):
 url,name,sha,dest=job
 path=downloads/name
 if not path.exists():
  print('Downloading', name, flush=True)
  with urllib.request.urlopen(url, timeout=90) as r, path.with_suffix('.partial').open('wb') as o:
   import shutil
   shutil.copyfileobj(r,o)
  path.with_suffix('.partial').rename(path)
 if sha and hashlib.sha1(path.read_bytes()).hexdigest()!=sha: raise RuntimeError('checksum '+name)
 target=base/dest;target.mkdir(parents=True,exist_ok=True)
 print('Extracting',name,flush=True)
 with zipfile.ZipFile(path) as z:
  z.extractall(target)
  for info in z.infolist():
   mode=info.external_attr>>16
   if mode: (target/info.filename).chmod(mode)
 print('Ready',name,flush=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=5) as e:
 for x in e.map(run,jobs): pass
sdk=base/'sdk'
for old,new in [('ndk/android-ndk-r27c','ndk/27.2.12479018'),('platforms/android-36','platforms/android-36'),('build-tools/android-16','build-tools/36.0.0')]:
 if old!=new and (sdk/old).exists(): (sdk/old).rename(sdk/new)
(sdk/'licenses').mkdir(exist_ok=True)
# Licenses will be checked through the SDK build, rather than silently accepted.
