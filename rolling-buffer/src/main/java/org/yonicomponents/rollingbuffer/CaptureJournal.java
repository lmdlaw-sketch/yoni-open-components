package org.yonicomponents.rollingbuffer;

import java.io.*;
import java.nio.file.*;

/** Encrypted crash marker and last interruption, independent of the audio catalog.
 * Arm durably BEFORE opening a microphone. Clear only after the pipeline/catalog stop.
 * A cold start reports uncertainty; it never resumes capture or invents a crash time.
 */
public final class CaptureJournal {
    private static final String NAME="capture-state.bin";
    private final File file;
    private final CryptoBox crypto;
    private volatile Snapshot state;

    public static final class Snapshot {
        public final boolean running;
        public final long startedAtMs, failureObservedAtMs;
        public final String failureCode;
        Snapshot(boolean running,long startedAtMs,long failureObservedAtMs,String failureCode){
            this.running=running;this.startedAtMs=startedAtMs;
            this.failureObservedAtMs=failureObservedAtMs;this.failureCode=failureCode;
        }
    }
    public CaptureJournal(File root,CryptoBox crypto)throws IOException {
        this.crypto=crypto;this.file=new File(root,NAME);
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("JOURNAL_UNAVAILABLE");
        if(file.exists()){
            if(file.length()<29||file.length()>4096)throw new IOException("JOURNAL_CORRUPT");
            try(DataInputStream d=new DataInputStream(new ByteArrayInputStream(crypto.open(Files.readAllBytes(file.toPath()),NAME)))){
                if(d.readInt()!=0x52414a31)throw new IOException("JOURNAL_CORRUPT");
                state=new Snapshot(d.readBoolean(),d.readLong(),d.readLong(),d.readUTF());
                if(state.startedAtMs<0||state.failureObservedAtMs<0||!validCode(state.failureCode)||d.available()!=0)
                    throw new IOException("JOURNAL_CORRUPT");
            }
        } else state=new Snapshot(false,0,0,"");
    }
    public Snapshot snapshot(){return state;}
    public synchronized void arm(long wallMs)throws IOException {
        if(state.running)throw new IOException("CAPTURE_ALREADY_ARMED");
        commit(new Snapshot(true,wallMs,state.failureObservedAtMs,state.failureCode));
    }
    public synchronized void stopped(String reason,long observedAtMs)throws IOException {
        if(reason!=null&&(!validCode(reason)||reason.isEmpty()))throw new IOException("INVALID_FAILURE_CODE");
        commit(new Snapshot(false,state.startedAtMs,reason==null?state.failureObservedAtMs:observedAtMs,
                reason==null?state.failureCode:reason));
    }
    public synchronized void noteFailure(String reason,long observedAtMs)throws IOException {
        if(!validCode(reason)||reason.isEmpty())throw new IOException("INVALID_FAILURE_CODE");
        // Does not claim that the microphone or a blocked writer has already stopped.
        commit(new Snapshot(state.running,state.startedAtMs,observedAtMs,reason));
    }
    public synchronized boolean recover(long observedAtMs)throws IOException {
        if(!state.running)return false;
        stopped("PROCESS_INTERRUPTED_TIME_UNKNOWN",observedAtMs);return true;
    }
    private static boolean validCode(String code){return code.isEmpty()||code.matches("[A-Za-z_]{3,80}");}
    private void commit(Snapshot next)throws IOException {
        if(next.startedAtMs<0||next.failureObservedAtMs<0)throw new IOException("INVALID_WALL_CLOCK");
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        try(DataOutputStream d=new DataOutputStream(b)){
            d.writeInt(0x52414a31);d.writeBoolean(next.running);d.writeLong(next.startedAtMs);
            d.writeLong(next.failureObservedAtMs);d.writeUTF(next.failureCode);
        }
        byte[] data=crypto.seal(b.toByteArray(),NAME);File tmp=new File(file.getParentFile(),NAME+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){out.write(data);out.getFD().sync();}
        Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        state=next;
    }
}

