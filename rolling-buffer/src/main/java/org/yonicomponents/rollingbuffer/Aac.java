package org.yonicomponents.rollingbuffer;

import java.io.IOException;
import java.io.OutputStream;

/** AAC-LC access units, not concatenated MP4 files. */
public final class Aac {
    private Aac() {}
    public static final int[] RATES = {96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000,7350};
    public static int rateIndex(int rate) {
        for (int i=0;i<RATES.length;i++) if (RATES[i]==rate) return i;
        throw new IllegalArgumentException("Unsupported AAC sample rate: " + rate);
    }
    public static byte[] header(int bytes, int rate, int channels) {
        if (bytes < 1 || bytes + 7 > 8191 || channels < 1 || channels > 2)
            throw new IllegalArgumentException("Invalid AAC access unit");
        int n=bytes+7, f=rateIndex(rate);
        return new byte[]{(byte)0xff,(byte)0xf1,(byte)((1<<6)|(f<<2)|(channels>>2)),
                (byte)(((channels&3)<<6)|(n>>11)),(byte)(n>>3),
                (byte)(((n&7)<<5)|0x1f),(byte)0xfc};
    }
    public static void write(OutputStream out, Model.Packet p, int rate, int channels) throws IOException {
        out.write(header(p.data.length,rate,channels)); out.write(p.data);
    }
    public static long frameUs(int rate) { return 1024_000_000L / rate; }
}

