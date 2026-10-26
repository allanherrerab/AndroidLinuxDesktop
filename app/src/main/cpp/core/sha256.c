#include "core.h"
#include <string.h>

/* First-party SHA-256 implementation. Unsigned arithmetic is modulo 2^32;
 * the stream length is encoded modulo 2^64, as required by SHA-256. */
static uint32_t rotate(uint32_t x, unsigned n) { return (x >> n) | (x << (32 - n)); }
static uint32_t load32(const unsigned char *p)
{
    return (uint32_t)p[0] << 24 | (uint32_t)p[1] << 16 | (uint32_t)p[2] << 8 | p[3];
}
static void compress(core_sha256 *hash)
{
    static const uint32_t k[64] = {
        0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
        0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
        0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
        0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
    };
    uint32_t w[64];
    for (unsigned i = 0; i < 16; i++) w[i] = load32(hash->block + i * 4);
    for (unsigned i = 16; i < 64; i++) {
        uint32_t a = w[i-15], b = w[i-2];
        w[i] = w[i-16] + (rotate(a,7) ^ rotate(a,18) ^ (a >> 3))
             + w[i-7] + (rotate(b,17) ^ rotate(b,19) ^ (b >> 10));
    }
    uint32_t a=hash->state[0],b=hash->state[1],c=hash->state[2],d=hash->state[3];
    uint32_t e=hash->state[4],f=hash->state[5],g=hash->state[6],h=hash->state[7];
    for (unsigned i = 0; i < 64; i++) {
        uint32_t t1=h+(rotate(e,6)^rotate(e,11)^rotate(e,25))+((e&f)^(~e&g))+k[i]+w[i];
        uint32_t t2=(rotate(a,2)^rotate(a,13)^rotate(a,22))+((a&b)^(a&c)^(b&c));
        h=g;g=f;f=e;e=d+t1;d=c;c=b;b=a;a=t1+t2;
    }
    hash->state[0]+=a;hash->state[1]+=b;hash->state[2]+=c;hash->state[3]+=d;
    hash->state[4]+=e;hash->state[5]+=f;hash->state[6]+=g;hash->state[7]+=h;
}

void core_sha256_init(core_sha256 *hash)
{
    *hash = (core_sha256){ .state = {0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,
                                   0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19} };
}
void core_sha256_update(core_sha256 *hash, const unsigned char *data, size_t length)
{
    hash->bytes += length;
    while (length) {
        size_t size = 64 - hash->used;
        if (size > length) size = length;
        memcpy(hash->block + hash->used, data, size);
        hash->used += size; data += size; length -= size;
        if (hash->used == 64) { compress(hash); hash->used = 0; }
    }
}
void core_sha256_final(core_sha256 *hash, char hex[65])
{
    uint64_t bits = hash->bytes * 8;
    hash->block[hash->used++] = 0x80;
    if (hash->used > 56) {
        memset(hash->block + hash->used, 0, 64 - hash->used);
        compress(hash); hash->used = 0;
    }
    memset(hash->block + hash->used, 0, 56 - hash->used);
    for (unsigned i = 0; i < 8; i++) hash->block[63-i] = (unsigned char)(bits >> (8*i));
    compress(hash);
    static const char digits[] = "0123456789abcdef";
    for (unsigned i = 0; i < 32; i++) {
        unsigned byte = (hash->state[i/4] >> (24 - (i%4)*8)) & 255;
        hex[i*2] = digits[byte >> 4]; hex[i*2+1] = digits[byte & 15];
    }
    hex[64] = 0;
}
