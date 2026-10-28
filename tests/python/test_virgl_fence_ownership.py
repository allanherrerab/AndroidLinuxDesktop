"""Exercise the patched upstream fence-owner check, including the broken baseline."""
import os
from pathlib import Path
import shlex
import subprocess
import tarfile
import tempfile
import unittest

PROJECT = Path(__file__).resolve().parents[2]
SOURCE = PROJECT / 'build/graphics/downloads/virgl.tar.gz'
PATCH = PROJECT / 'tools/build/patches/virgl-fence-ownership.patch'

# No queued fences are needed to reproduce this race: the sync thread has
# already removed a fence from its queue and is waiting on it while the main
# thread destroys an unrelated client. Keep queue/mutex dependencies inert so
# the actual upstream function can be tested deterministically without EGL.
HARNESS = r'''
#include <stdbool.h>
#include <stddef.h>
struct vrend_context { int id; };
struct vrend_fence { struct vrend_context *ctx; };
struct {
    bool sync_thread;
    int fence_mutex, fence_list, fence_wait_list;
    struct vrend_fence *fence_waiting;
} vrend_state;
static void mtx_lock(int *m) { (void)m; }
static void mtx_unlock(int *m) { (void)m; }
static void free_fence_locked(struct vrend_fence *f) { (void)f; }
#define list_for_each_entry_safe(type, item, head, member) \
    for (type *item = NULL; item != NULL; item = NULL)

UPSTREAM_FUNCTION

int main(void) {
    struct vrend_context shared = {0}, victim = {1};
    struct vrend_fence pending = {&shared};
    vrend_state.sync_thread = true;
    vrend_state.fence_waiting = &pending;
    vrend_free_fences_for_context(&victim);
    if (pending.ctx != &shared) return 1; /* unrelated completion was dropped */
    vrend_free_fences_for_context(&shared);
    if (pending.ctx != NULL) return 2; /* own fence must be invalidated */
    vrend_state.fence_waiting = NULL;
    vrend_free_fences_for_context(&victim);
    return 0;
}
'''


def fence_function(source):
    start = source.index('static void vrend_free_fences_for_context(')
    end = source.index('\n}\n', start) + 3
    return source[start:end]


class FenceOwnershipTest(unittest.TestCase):
    def test_context_destruction_preserves_other_contexts_pending_fence(self):
        if not SOURCE.exists():
            self.skipTest('Run prepare-virgl.py to cache the pinned upstream source')
        with tarfile.open(SOURCE) as archive:
            member = next(m for m in archive.getmembers()
                          if m.name.endswith('/src/vrend/vrend_renderer.c'))
            upstream = archive.extractfile(member).read().decode()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'src/vrend/vrend_renderer.c'
            source.parent.mkdir(parents=True)
            source.write_text(upstream)
            subprocess.run(['git', 'apply', str(PATCH)], cwd=root, check=True,
                           capture_output=True)
            patched = source.read_text()
            compiler = shlex.split(os.environ.get('CC', 'cc'))
            for name, content, expected in [('upstream', upstream, 1), ('patched', patched, 0)]:
                test = root / (name + '.c')
                test.write_text(HARNESS.replace('UPSTREAM_FUNCTION', fence_function(content)))
                binary = root / name
                subprocess.run(compiler + ['-std=c11', '-Wall', '-Wextra', '-Werror',
                    str(test), '-o', str(binary)], check=True, capture_output=True)
                result = subprocess.run([str(binary)], timeout=5)
                self.assertEqual(result.returncode, expected, name)
