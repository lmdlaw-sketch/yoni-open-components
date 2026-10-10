package org.yonicomponents.rollingbuffer;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded transient PCM, shared by the production reader/encoder and deterministic stress tests. */
public final class PcmQueue {
    public static final int BLOCK_BYTES=2048;
    private final ArrayBlockingQueue<Frame> queue;
    private final AtomicInteger highWater=new AtomicInteger();
    public static final class Frame {
        public final byte[] data;
        public final long us;
        Frame(byte[] data,long us){this.data=data;this.us=us;}
        public void erase(){Arrays.fill(data,(byte)0);}
    }
    public PcmQueue(int capacity){queue=new ArrayBlockingQueue<>(capacity);}
    public static int capacityForSeconds(int rate,int seconds){
        if(rate<1||rate>192000||seconds<1||seconds>10)throw new IllegalArgumentException("Invalid PCM capacity");
        return (rate*2*seconds+BLOCK_BYTES-1)/BLOCK_BYTES;
    }
    public boolean offer(byte[] bytes,int count,long us){
        if(count<2||count>BLOCK_BYTES||count>bytes.length||(count&1)!=0||us<0)
            throw new IllegalArgumentException("Invalid PCM block");
        Frame f=new Frame(Arrays.copyOf(bytes,count),us);
        if(!queue.offer(f)){f.erase();return false;}
        highWater.accumulateAndGet(queue.size(),Math::max);return true;
    }
    public Frame poll(long timeoutMs)throws InterruptedException{return queue.poll(timeoutMs,TimeUnit.MILLISECONDS);}
    public boolean isEmpty(){return queue.isEmpty();}
    public int size(){return queue.size();}
    public int highWater(){return highWater.get();}
    public void clear(){Frame f;while((f=queue.poll())!=null)f.erase();}
}

