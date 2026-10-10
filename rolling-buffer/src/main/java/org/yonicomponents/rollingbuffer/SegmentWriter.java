package org.yonicomponents.rollingbuffer;

import java.io.IOException;
import java.util.*;
import java.util.function.LongSupplier;
import static org.yonicomponents.rollingbuffer.Model.*;

/** Packet segmentation without restarting AudioRecord or the encoder. Owned by one encoder thread. */
public final class SegmentWriter {
    private final Repository repository;
    private final String run;
    private final int rate,channels;
    private final LongSupplier nowUs;
    private final List<Packet> pending=new ArrayList<>();
    public SegmentWriter(Repository repository,String run,int rate,int channels,LongSupplier nowUs){
        Aac.rateIndex(rate);this.repository=repository;this.run=run;this.rate=rate;this.channels=channels;this.nowUs=nowUs;
    }
    public void push(long us,byte[] data)throws IOException {
        if(!pending.isEmpty()){
            long last=pending.get(pending.size()-1).us;
            if(us<=last)throw new IOException("ENCODER_TIMESTAMPS_NOT_MONOTONIC");
            if(us-last>Aac.frameUs(rate)+5000 || us-pending.get(0).us>=2_000_000)flush();
        }
        pending.add(new Packet(us,data));
    }
    public void flush()throws IOException {
        if(pending.isEmpty())return;
        Segment s=new Segment(UUID.randomUUID().toString(),run,rate,channels,pending);
        repository.append(s,nowUs.getAsLong());pending.clear();
    }
}

