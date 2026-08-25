package com.tf.aurora.mask;

/** Local codec. Values are not stored as readable strings. */
public final class Relic {
    private static final byte[] RING = new byte[] {
        0x3D, (byte) 0xA8, 0x51, (byte) 0xC2, 0x17, 0x6E, (byte) 0x94, 0x0B, (byte) 0xF3, 0x48
    };

    private Relic() {}

    public static String open(int[] packed) {
        byte[] out = new byte[packed.length];
        for (int i = 0; i < packed.length; i++) {
            int mix = (i * 17 + 9) & 0xFF;
            out[i] = (byte) (packed[i] ^ (RING[i % RING.length] & 0xFF) ^ mix);
        }
        return new String(out, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static String beacon() {
        return open(new int[] {
            110, 255, 12, 198, 50, 71, 168, 195, 38, 172, 252, 39, 222, 113, 173, 4, 217, 120, 169, 97, 34, 162
        });
    }

    public static String oracle() {
        return open(new int[] {
            92, 198, 14, 142, 41, 10, 212, 164, 22, 131, 250, 13, 234, 66, 143, 20, 249, 84, 166, 97, 78, 181,
            90, 61, 196, 185, 120, 188, 121, 208, 92, 217, 31, 214, 44, 90, 137
        });
    }

    public static String glimpse() {
        return open(new int[] {
            92, 198, 14, 142, 41, 10, 212, 164, 5, 137, 234, 31, 224, 79, 206, 7, 253, 81, 187, 98, 12, 191,
            75, 32, 152, 191, 56, 178, 57, 215, 84, 195, 12, 153, 48, 94, 166, 17, 29, 156, 237, 69, 244, 18,
            204, 88, 172
        });
    }
}
