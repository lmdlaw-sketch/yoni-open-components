package org.yonicomponents.rollingbuffer;

import java.io.IOException;

/** Immutable sample-time anchor. Scheduling backlog may drain, but samples are NEVER re-dated.
 * Android exposes buffer capacity, not a reliable count of lost client frames after overrun.
 * Beyond that capacity, continuity is unknown: stop explicitly instead of guessing a new anchor.
 */
public final class CaptureClock {
    private final int rate;
    private final long anchorUs,maxLagUs;
    private long frames;
    public CaptureClock(int rate,long anchorUs,long bufferFrames){
        if(rate<=0||anchorUs<0||bufferFrames<1)throw new IllegalArgumentException("Invalid capture clock");
        this.rate=rate;this.anchorUs=anchorUs;
        // Leave one read quantum/headroom; never extend the five-second no-read watchdog.
        maxLagUs=Math.max(0,Math.min(4_500_000L,bufferFrames*1_000_000L/rate-100_000L));
    }
    public long accept(int count,long nowUs)throws IOException {
        if(count<1)throw new IOException("INVALID_PCM_BUFFER");
        long pts=anchorUs+frames*1_000_000L/rate;
        long end=anchorUs+(frames+count)*1_000_000L/rate;
        long lag=nowUs-end;
        if(lag>maxLagUs||lag< -100_000L)throw new IOException("MIC_CLOCK_DISCONTINUITY");
        frames+=count;return pts;
    }
    public long maxLagUs(){return maxLagUs;}
}

