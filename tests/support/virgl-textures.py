#!/usr/bin/env python3
"""Guest GLES pixel regression, run by smoke-device-virgl-textures.py.

Uses an EGL pbuffer and generated blocks; no user windows, files or GL overrides.
"""
import ctypes as C
import json
import struct


def bind(lib, name, result, *args):
    fn = getattr(lib, name)
    fn.restype, fn.argtypes = result, args
    return fn


I, U, P, F = C.c_int, C.c_uint, C.c_void_p, C.c_float
egl = C.CDLL('libEGL.so.1')
gl = C.CDLL('libGLESv2.so.2')
get_display = bind(egl, 'eglGetDisplay', P, P)
initialize = bind(egl, 'eglInitialize', U, P, C.POINTER(I), C.POINTER(I))
choose = bind(egl, 'eglChooseConfig', U, P, C.POINTER(I), C.POINTER(P), I, C.POINTER(I))
create_surface = bind(egl, 'eglCreatePbufferSurface', P, P, P, C.POINTER(I))
create_context = bind(egl, 'eglCreateContext', P, P, P, P, C.POINTER(I))
make_current = bind(egl, 'eglMakeCurrent', U, P, P, P, P)
get_string = bind(gl, 'glGetString', C.c_char_p, U)
get_error = bind(gl, 'glGetError', U)
shader = bind(gl, 'glCreateShader', U, U)
shader_source = bind(gl, 'glShaderSource', None, U, I, C.POINTER(C.c_char_p), P)
compile_shader = bind(gl, 'glCompileShader', None, U)
shader_iv = bind(gl, 'glGetShaderiv', None, U, U, C.POINTER(I))
shader_log = bind(gl, 'glGetShaderInfoLog', None, U, I, P, P)
program = bind(gl, 'glCreateProgram', U)
attach = bind(gl, 'glAttachShader', None, U, U)
link = bind(gl, 'glLinkProgram', None, U)
program_iv = bind(gl, 'glGetProgramiv', None, U, U, C.POINTER(I))
use = bind(gl, 'glUseProgram', None, U)
uniform = bind(gl, 'glGetUniformLocation', I, U, C.c_char_p)
uniform1f = bind(gl, 'glUniform1f', None, I, F)
gen_tex = bind(gl, 'glGenTextures', None, I, C.POINTER(U))
bind_tex = bind(gl, 'glBindTexture', None, U, U)
param = bind(gl, 'glTexParameteri', None, U, U, I)
storage = bind(gl, 'glTexStorage2D', None, U, I, U, I, I)
compressed_image = bind(gl, 'glCompressedTexImage2D', None, U, I, U, I, I, I, I, P)
compressed_sub = bind(gl, 'glCompressedTexSubImage2D', None, U, I, I, I, I, I, U, I, P)
image = bind(gl, 'glTexImage2D', None, U, I, I, I, I, I, U, U, P)
viewport = bind(gl, 'glViewport', None, I, I, I, I)
draw = bind(gl, 'glDrawArrays', None, U, I, I)
read = bind(gl, 'glReadPixels', None, I, I, I, I, U, U, P)
delete = bind(gl, 'glDeleteTextures', None, I, C.POINTER(U))


def check(stage):
    error = get_error()
    if error:
        raise RuntimeError('%s: GL error %#x' % (stage, error))


def compiled(kind, source):
    obj = shader(kind)
    shader_source(obj, 1, C.byref(C.c_char_p(source)), None)
    compile_shader(obj)
    ok = I()
    shader_iv(obj, 0x8B81, C.byref(ok))
    if not ok.value:
        log = C.create_string_buffer(4096)
        shader_log(obj, len(log), None, log)
        raise RuntimeError(log.value.decode())
    return obj


def dxt_block(fmt, color):
    color_block = struct.pack('<HHI', color, 0, 0)
    if fmt in (0x83F2, 0x8C4E):  # DXT3, opaque alpha
        return b'\xff' * 8 + color_block
    if fmt in (0x83F3, 0x8C4F):  # DXT5, opaque alpha
        return b'\xff\x00' + b'\x00' * 6 + color_block
    return color_block


def pixel(x, y):
    rgba = (C.c_ubyte * 4)()
    read(x, y, 1, 1, 0x1908, 0x1401, rgba)
    check('read pixel')
    return list(rgba)


def expect(actual, expected):
    if any(abs(a - b) > 3 for a, b in zip(actual, expected)):
        raise RuntimeError('pixel %s, expected %s' % (actual, expected))


display = get_display(None)
assert display and initialize(display, None, None), 'EGL initialization failed'
config, count = P(), I()
attrs = (I * 13)(0x3033, 1, 0x3040, 0x40, 0x3024, 8, 0x3023, 8, 0x3022, 8, 0x3021, 8, 0x3038)
assert choose(display, attrs, C.byref(config), 1, C.byref(count)) and count.value
surface = create_surface(display, config, (I * 5)(0x3057, 8, 0x3056, 8, 0x3038))
context = create_context(display, config, None, (I * 3)(0x3098, 3, 0x3038))
assert surface and context and make_current(display, surface, surface, context), 'EGL context failed'
renderer = get_string(0x1F01).decode()
assert renderer.startswith('virgl') and not any(
    name in renderer.lower() for name in ('llvmpipe', 'softpipe', 'swiftshader')
), 'Expected GPU VirGL, got ' + renderer
extensions = get_string(0x1F03).decode().split()
print(json.dumps({'renderer': renderer, 'version': get_string(0x1F02).decode()}), flush=True)
p = program()
attach(p, compiled(0x8B31, b'''#version 300 es
out vec2 uv;
void main() {
 vec2 pos = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
 uv = pos; gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
}'''))
attach(p, compiled(0x8B30, b'''#version 300 es
precision highp float;
in vec2 uv; uniform sampler2D tex; uniform float lod;
out vec4 color;
void main() { color = textureLod(tex, uv, lod); }
'''))
link(p)
ok = I()
program_iv(p, 0x8B82, C.byref(ok))
assert ok.value, 'Program link failed'
use(p)
lod = uniform(p, b'lod')
viewport(0, 0, 8, 8)
check('setup')
results = []
for name, fmt in [('rgba-control', 0x8058), ('dxt1', 0x83F1), ('dxt3', 0x83F2), ('dxt5', 0x83F3),
                  ('srgb-dxt1', 0x8C4D), ('srgb-dxt3', 0x8C4E), ('srgb-dxt5', 0x8C4F)]:
    # Unsupported formats must not be advertised by the host. Mesa may still
    # expose software decoding; test whatever the guest actually advertises.
    if fmt != 0x8058 and 'GL_EXT_texture_compression_s3tc' not in extensions:
        results.append({'case': name, 'skipped': 'S3TC not advertised'})
        continue
    if name.startswith('srgb') and 'GL_EXT_texture_compression_s3tc_srgb' not in extensions:
        results.append({'case': name, 'skipped': 'sRGB S3TC not advertised'})
        continue
    for immutable in (False, True):
        tex = U()
        gen_tex(1, C.byref(tex))
        bind_tex(0x0DE1, tex)
        param(0x0DE1, 0x2801, 0x2700)  # NEAREST_MIPMAP_NEAREST
        param(0x0DE1, 0x2800, 0x2600)
        param(0x0DE1, 0x813D, 1)  # MAX_LEVEL
        if immutable:
            storage(0x0DE1, 2, fmt, 8, 8)
        for level, size, color in [(0, 8, 0xF800), (1, 4, 0x001F)]:
            if fmt == 0x8058:
                rgba = bytes([255, 0, 0, 255] if level == 0 else [0, 0, 255, 255]) * (size * size)
                if immutable:
                    sub = bind(gl, 'glTexSubImage2D', None, U, I, I, I, I, I, U, U, P)
                    sub(0x0DE1, level, 0, 0, size, size, 0x1908, 0x1401, rgba)
                else:
                    image(0x0DE1, level, fmt, size, size, 0, 0x1908, 0x1401, rgba)
            else:
                data = dxt_block(fmt, color) * ((size // 4) ** 2)
                if immutable:
                    compressed_sub(0x0DE1, level, 0, 0, size, size, fmt, len(data), data)
                else:
                    compressed_image(0x0DE1, level, fmt, size, size, 0, len(data), data)
        check(name + ' upload')
        uniform1f(lod, 0)
        draw(4, 0, 3)
        expect(pixel(1, 1), [255, 0, 0, 255])
        if fmt != 0x8058:
            data = dxt_block(fmt, 0x07E0)
            compressed_sub(0x0DE1, 0, 4, 4, 4, 4, fmt, len(data), data)
            draw(4, 0, 3)
            expect(pixel(6, 6), [0, 255, 0, 255])
            expect(pixel(1, 1), [255, 0, 0, 255])
        uniform1f(lod, 1)
        draw(4, 0, 3)
        expect(pixel(4, 4), [0, 0, 255, 255])
        if fmt != 0x8058:
            data = dxt_block(fmt, 0x8410) * 4  # RGB565 (132, 130, 132)
            compressed_sub(0x0DE1, 0, 0, 0, 8, 8, fmt, len(data), data)
            uniform1f(lod, 0)
            draw(4, 0, 3)
            expected = [59, 57, 59, 255] if name.startswith('srgb') else [132, 130, 132, 255]
            expect(pixel(4, 4), expected)
            if fmt in (0x83F2, 0x8C4E, 0x83F3, 0x8C4F):
                alpha = b'\x88' * 8 if fmt in (0x83F2, 0x8C4E) else b'\x80\x00' + b'\x00' * 6
                data = (alpha + struct.pack('<HHI', 0xF800, 0, 0)) * 4
                compressed_sub(0x0DE1, 0, 0, 0, 8, 8, fmt, len(data), data)
                draw(4, 0, 3)
                expect(pixel(4, 4), [255, 0, 0, 136 if fmt in (0x83F2, 0x8C4E) else 128])
        delete(1, C.byref(tex))
        results.append({'case': name, 'immutable': immutable, 'passed': True})
        print(json.dumps(results[-1]), flush=True)

# Animated meshes address floating-point bone textures using integer vertex
# attributes and a per-instance offset. Constant texel addresses miss this bug.
sub = bind(gl, 'glTexSubImage2D', None, U, I, I, I, I, I, U, U, P)
p2 = program()
attach(p2, compiled(0x8B31, b'''#version 300 es
precision highp float;
uniform highp sampler2D tex;
layout(location=6) in ivec4 i_joint;
layout(location=7) in vec4 i_weight;
layout(location=12) in ivec2 i_misc_data;
out vec4 sampled;
void main() {
 vec2 pos = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
 sampled = texelFetch(tex, ivec2(0,i_joint.x+i_misc_data.x), 0) * i_weight.x * 0.25 + 0.5;
 gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
}'''))
attach(p2, compiled(0x8B30, b'''#version 300 es
precision highp float;
in vec4 sampled; out vec4 color;
void main() {color=sampled;}
'''))
link(p2)
program_iv(p2, 0x8B82, C.byref(ok))
assert ok.value, 'Skinning program link failed'
use(p2)
tex = U()
gen_tex(1, C.byref(tex))
bind_tex(0x0DE1, tex)
param(0x0DE1, 0x2801, 0x2600)
param(0x0DE1, 0x2800, 0x2600)
image(0x0DE1, 0, 0x8814, 4, 1024, 0, 0x1908, 0x1406, None)
data = (F * 16)(*([-1.0, 0.0, 1.0, 2.0] * 4))
sub(0x0DE1, 0, 0, 17, 4, 1, 0x1908, 0x1406, data)
gen_buf = bind(gl, 'glGenBuffers', None, I, C.POINTER(U))
bind_buf = bind(gl, 'glBindBuffer', None, U, U)
buf_data = bind(gl, 'glBufferData', None, U, C.c_ssize_t, P, U)
enable = bind(gl, 'glEnableVertexAttribArray', None, U)
iptr = bind(gl, 'glVertexAttribIPointer', None, U, I, U, I, P)
fptr = bind(gl, 'glVertexAttribPointer', None, U, I, U, U, I, P)
divisor = bind(gl, 'glVertexAttribDivisor', None, U, U)
draw_instanced = bind(gl, 'glDrawArraysInstanced', None, U, I, I, I)
buf = U()
gen_buf(1, C.byref(buf))
bind_buf(0x8892, buf)
vertices = struct.pack('<4h4e', 0, 0, 0, 0, 1, 0, 0, 0) * 3
buf_data(0x8892, len(vertices), vertices, 0x88E8)
enable(6)
iptr(6, 4, 0x1402, 16, None)
enable(7)
fptr(7, 4, 0x140B, 0, 16, P(8))
ins = U()
gen_buf(1, C.byref(ins))
bind_buf(0x8892, ins)
instance = bytes(40) + struct.pack('<2h', 17, 0)
buf_data(0x8892, len(instance), instance, 0x88E8)
enable(12)
iptr(12, 2, 0x1402, 44, P(40))
divisor(12, 1)
check('skinning setup')
draw_instanced(4, 0, 3, 1)
expect(pixel(4, 4), [64, 128, 191, 255])
results.append({'case': 'float-vertex-instanced-index', 'passed': True})
print(json.dumps(results[-1]), flush=True)
# A float weight input must work alongside integer joints and instance offsets.
# Repeat with a signed joint index and compensate the instance offset; both
# addresses must select row 17.
vertices = struct.pack('<4h4e', -1, 0, 0, 0, 1, 0, 0, 0) * 3
bind_buf(0x8892, buf)
buf_data(0x8892, len(vertices), vertices, 0x88E8)
instance = bytes(40) + struct.pack('<2h', 18, 0)
bind_buf(0x8892, ins)
buf_data(0x8892, len(instance), instance, 0x88E8)
draw_instanced(4, 0, 3, 1)
expect(pixel(4, 4), [64, 128, 191, 255])
results.append({'case': 'float-vertex-signed-index', 'passed': True})
print(json.dumps(results[-1]), flush=True)

print(json.dumps({'passed': True, 'cases': sum(r.get('passed', False) for r in results),
                  'skipped': sum('skipped' in r for r in results)}))
