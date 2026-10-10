package org.yonicomponents.rollingbuffer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.yonicomponents.rollingbuffer.Model.*;

/**
 * One serialized authority for retention and durable metadata. A mutation is published
 * only after its encrypted index has been atomically replaced. Encoders never depend
 * on a UI object. Completed segment data is immutable.
 */
public final class Repository {
    public interface FaultHook { void beforeIndexCommit() throws IOException; }
    private final File root;
    private final CryptoBox crypto;
    private final FaultHook hook;
    private final Map<String,Integer> leases=new HashMap<>();
    private volatile State state;
    private static final String INDEX="catalog.bin";
    private static final int MAX_INDEX=32*1024*1024;
    private volatile int quarantinedObjects, unreadableOrphans;

    /** Process-local measurements; no audio, names or paths. Getters never wait for disk. */
    public static final class StorageStats {
        public final long indexCommits, indexBytes, lastCommitUs, maxCommitUs;
        public final long appends, lastAppendUs, maxAppendUs;
        StorageStats(long commits,long bytes,long last,long max,long appends,long appendLast,long appendMax){
            this.indexCommits=commits;this.indexBytes=bytes;this.lastCommitUs=last;this.maxCommitUs=max;
            this.appends=appends;this.lastAppendUs=appendLast;this.maxAppendUs=appendMax;
        }
    }
    private volatile StorageStats stats=new StorageStats(0,0,0,0,0,0,0);
    public StorageStats storageStats(){return stats;}
    public int quarantinedObjects(){return quarantinedObjects;}
    public int unreadableOrphans(){return unreadableOrphans;}


    private static final class State {
        int minutes=10;
        String run="";
        long runStart,wallMs;
        final List<Ref> refs=new ArrayList<>();
        final List<Clip> clips=new ArrayList<>();
        final List<Gap> gaps=new ArrayList<>();
        State copy(){State s=new State();s.minutes=minutes;s.run=run;s.runStart=runStart;s.wallMs=wallMs;s.refs.addAll(refs);for(Clip c:clips)s.clips.add(c.copy());for(Gap g:gaps)s.gaps.add(g.copy());return s;}
    }

    public Repository(File root,CryptoBox crypto) throws IOException { this(root,crypto,()->{}); }
    public Repository(File root,CryptoBox crypto,FaultHook hook) throws IOException {
        this.root=root;this.crypto=crypto;this.hook=hook;
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Cannot create private archive");
        File index=new File(root,INDEX);
        if(index.isFile())state=decode(crypto.open(readBounded(index,MAX_INDEX),INDEX));
        else {
            File[] existing=root.listFiles((d,n)->n.endsWith(".seg"));
            if(existing==null)throw new IOException("Cannot list archive");
            if(existing.length>0||new File(root,"quarantine").exists())throw new IOException("Index missing; encrypted recordings preserved for recovery");
            state=new State();commit(state);
        }
        recover();
    }

    private void recover()throws IOException {
        State s=state.copy(); Set<String> known=new HashSet<>();for(Ref r:s.refs)known.add(r.id);
        File[] all=root.listFiles();if(all==null)throw new IOException("Cannot list archive");
        Set<String> damaged=new HashSet<>();
        // A segment can have reached disk just before a crash prevented index commit.
        for(File f:all){
            if(!f.getName().endsWith(".seg"))continue;
            String id=f.getName().substring(0,f.getName().length()-4);
            if(known.contains(id))continue;
            try {
                Segment seg=readSegmentFile(id);
                Ref r=new Ref(seg);
                if(isProtected(s,r)){s.refs.add(r);known.add(id);}
            } catch(IOException e) {
                // No authenticated metadata => cannot know whether this was protected audio.
                // Preserve its exact ciphertext and name; never replace/reset the catalog.
                damaged.add(f.getName());
                if(!quarantine(f))unreadableOrphans++;
            }
        }
        quarantinedObjects=countQuarantinedObjects();
        boolean uncertain=quarantinedObjects>0||!damaged.isEmpty();
        s.refs.sort(Comparator.comparing((Ref r)->r.run).thenComparingLong(r->r.start));
        for(Clip c:s.clips){
            if(c.status.equals("ACTIVE")||c.status.equals("FINALIZING")||c.status.equals("IMPORTING")){
                long durable=lastEnd(s,c.run);
                if(c.end==Long.MAX_VALUE)c.end=Math.max(c.start,durable);
                c.status="INTERRUPTED";
                s.gaps.add(new Gap(c.run,c.end,c.end,"PROCESS_INTERRUPTED_UNKNOWN_TAIL",false));
                if(uncertain)s.gaps.add(new Gap(c.run,c.end,c.end,"UNREADABLE_ORPHAN_UNKNOWN_CONTENT",false));
            }
        }
        for(Gap g:s.gaps)if(g.end==Long.MAX_VALUE)g.end=Math.max(g.start,lastEnd(s,g.run));
        for(Ref r:s.refs)if(!file(r.id).isFile())throw new IOException("Referenced segment missing; archive not modified");
        s.run="";commitAndPrune(s,0);
        // Only after successful recovery may stale transaction files be discarded.
        for(File f:all)if(f.getName().endsWith(".tmp"))f.delete();
        Set<String> retained=new HashSet<>();for(Ref r:state.refs)retained.add(r.id);
        for(File f:all)if(f.getName().endsWith(".seg")){
            String id=f.getName().substring(0,f.getName().length()-4);
            if(!retained.contains(id)&&!damaged.contains(f.getName()))f.delete();
        }
    }

    /** Unique subdirectory preserves the original authenticated name without overwriting evidence. */
    private boolean quarantine(File source){
        File directory=new File(new File(root,"quarantine"),UUID.randomUUID().toString());
        try {
            if(!directory.mkdirs())return false;
            Files.move(source.toPath(),new File(directory,source.getName()).toPath(),StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch(IOException|SecurityException e){
            // An unmovable orphan must not disable unrelated clips. The original is excluded from cleanup.
            directory.delete();return false;
        }
    }
    private int countQuarantinedObjects()throws IOException {
        File q=new File(root,"quarantine");if(!q.exists())return 0;
        File[] dirs=q.listFiles();if(dirs==null){unreadableOrphans++;return 0;}
        int count=0;
        for(File d:dirs){File[] files=d.listFiles();if(files==null){count++;continue;}count+=files.length;}
        return count;
    }

    public synchronized String begin(long nowUs,long wallMs)throws IOException {
        if(!state.run.isEmpty())return state.run;
        if(nowUs<0)throw new IllegalArgumentException("Negative clock");
        State s=state.copy();s.run=UUID.randomUUID().toString();s.runStart=nowUs;s.wallMs=wallMs;commit(s);return s.run;
    }
    public String run(){return state.run;}
    public int minutes(){return state.minutes;}
    public long runStart(){return state.runStart;}
    public synchronized void setMinutes(int minutes,long nowUs)throws IOException {
        if(minutes<1||minutes>60)throw new IllegalArgumentException("Window must be 1-60 minutes");
        State s=state.copy();s.minutes=minutes;commitAndPrune(s,nowUs);
    }
    public synchronized String save(long nowUs)throws IOException {
        requireRunning();
        for(Clip c:state.clips)if(c.run.equals(state.run)&&c.status.equals("ACTIVE"))return c.id;
        State s=state.copy(); Clip c=new Clip();c.id=UUID.randomUUID().toString();c.run=s.run;
        c.kind="MICROPHONE";c.title="הקלטה";c.status="ACTIVE";
        c.start=Math.max(s.runStart,nowUs-s.minutes*60_000_000L);c.end=Long.MAX_VALUE;c.wallMs=s.wallMs;c.anchorUs=s.runStart;
        s.clips.add(c);commit(s);return c.id;
    }
    public synchronized void finish(long nowUs)throws IOException {
        State s=state.copy();boolean changed=false;
        for(Clip c:s.clips)if(c.run.equals(s.run)&&c.status.equals("ACTIVE")){
            c.end=Math.max(c.start,nowUs);c.status="FINALIZING";changed=true;
        }
        if(changed){finishReady(s);commit(s);}
    }
    public synchronized void end(long nowUs,String reason)throws IOException {
        if(state.run.isEmpty())return;
        State s=state.copy();
        for(Clip c:s.clips)if(c.run.equals(s.run)&&(c.status.equals("ACTIVE")||c.status.equals("FINALIZING"))){
            if(c.end==Long.MAX_VALUE)c.end=Math.max(c.start,nowUs);
            c.status=reason==null?"COMPLETE":"INTERRUPTED";
            if(!coversRequestedEnd(s,c))s.gaps.add(new Gap(s.run,tailStart(s,c),c.end,"UNWRITTEN_TAIL",true));
        }
        if(reason!=null)s.gaps.add(new Gap(s.run,nowUs,nowUs,reason,false));
        for(Gap g:s.gaps)if(g.run.equals(s.run)&&g.end==Long.MAX_VALUE)g.end=Math.max(g.start,nowUs);
        s.run="";commitAndPrune(s,nowUs);
    }

    public synchronized void append(Segment segment,long nowUs)throws IOException {
        boolean allowed=segment.run.equals(state.run)&&!state.run.isEmpty();
        for(Clip c:state.clips)if(c.run.equals(segment.run)&&c.status.equals("IMPORTING"))allowed=true;
        if(!allowed)throw new IOException("Capture session is no longer open");
        for(Ref r:state.refs)if(r.id.equals(segment.id))throw new IOException("Duplicate segment ID");
        long last=lastEnd(state,segment.run);
        if(last>0&&segment.start()<last-2)throw new IOException("Overlapping encoded segment");
        if(root.getUsableSpace()<16L*1024*1024)throw new IOException("LOW_STORAGE");
        long started=System.nanoTime();
        atomic(file(segment.id),crypto.seal(segment.encode(),segment.id+".seg"));
        State s=state.copy();s.refs.add(new Ref(segment));finishReady(s);
        // Publish addition and retention in ONE index transaction, then delete only unreferenced files.
        commitAndPrune(s,nowUs);
        long elapsed=(System.nanoTime()-started)/1000;StorageStats m=stats;
        stats=new StorageStats(m.indexCommits,m.indexBytes,m.lastCommitUs,m.maxCommitUs,
                m.appends+1,elapsed,Math.max(m.maxAppendUs,elapsed));
    }

    /** Missing intervals and route/mode events are distinct. Silence alone is not a fault. */
    public synchronized void openGap(long nowUs,String reason)throws IOException {
        if(state.run.isEmpty())return;
        for(Gap g:state.gaps)if(g.run.equals(state.run)&&g.end==Long.MAX_VALUE&&g.reason.equals(reason))return;
        State s=state.copy();s.gaps.add(new Gap(s.run,nowUs,Long.MAX_VALUE,reason,true));commit(s);
    }
    public synchronized void closeGap(long nowUs,String reason)throws IOException {
        State s=state.copy();boolean changed=false;
        for(Gap g:s.gaps)if(g.run.equals(s.run)&&g.end==Long.MAX_VALUE&&g.reason.equals(reason)){g.end=Math.max(g.start,nowUs);changed=true;}
        if(changed)commit(s);
    }
    public synchronized void event(long nowUs,String reason)throws IOException {
        if(state.run.isEmpty())return;
        State s=state.copy();s.gaps.add(new Gap(s.run,nowUs,nowUs,reason,false));commit(s);
    }

    public List<Clip> clips(){List<Clip> r=new ArrayList<>();for(Clip c:state.clips)r.add(c.copy());Collections.reverse(r);return r;}
    public Clip active(){State s=state;for(Clip c:s.clips)if(c.status.equals("ACTIVE")&&c.run.equals(s.run))return c.copy();return null;}
    public long availableUs(long nowUs){
        State snapshot=state;
        if(snapshot.run.isEmpty())return 0;
        long from=Math.max(snapshot.runStart,nowUs-snapshot.minutes*60_000_000L);List<long[]> audible=new ArrayList<>();
        for(Ref r:snapshot.refs)if(r.run.equals(snapshot.run)){
            List<long[]> exclusions=new ArrayList<>();
            long a=Math.max(from,r.start),b=Math.min(nowUs,r.end);if(a>=b)continue;
            for(Gap g:snapshot.gaps)if(g.missing&&g.run.equals(r.run))exclusions.add(new long[]{g.start,g.end});
            long missing=Model.covered(exclusions,a,b);audible.add(new long[]{0,Math.max(0,b-a-missing)});
        }
        long sum=0;for(long[] r:audible)sum+=r[1];return Math.min(sum,Math.max(0,nowUs-from));
    }
    public long durableEnd(){State s=state;return s.run.isEmpty()?0:lastEnd(s,s.run);}
    public long writtenEnd(String run){return lastEnd(state,run);}
    public int segmentCount(){return state.refs.size();}
    public List<Gap> gaps(String run){List<Gap> r=new ArrayList<>();for(Gap g:state.gaps)if(g.run.equals(run))r.add(g.copy());return r;}

    public synchronized void rename(String id,String title)throws IOException {
        title=title.trim();if(title.isEmpty()||title.length()>120)throw new IllegalArgumentException("Title must have 1-120 characters");
        State s=state.copy();find(s,id).title=title;commit(s);
    }
    public synchronized void delete(String id,long nowUs)throws IOException {
        Clip c=find(state,id);
        if(c.status.equals("ACTIVE")||c.status.equals("FINALIZING")||c.status.equals("IMPORTING"))throw new IOException("Recording is not closed");
        if(leases.getOrDefault(id,0)>0)throw new IOException("Recording is being played or exported");
        State s=state.copy();s.clips.removeIf(x->x.id.equals(id));commitAndPrune(s,nowUs);
    }
    public synchronized void clearUnsaved(long nowUs)throws IOException {prune(nowUs);}
    public synchronized String createImport(String title)throws IOException {
        if(!state.run.isEmpty())throw new IOException("Stop microphone capture before importing");
        State s=state.copy();Clip c=new Clip();c.id=UUID.randomUUID().toString();c.run=UUID.randomUUID().toString();
        c.kind="IMPORTED";c.title=title.length()>120?title.substring(0,120):title;c.status="IMPORTING";
        c.start=0;c.end=Long.MAX_VALUE;c.wallMs=-1;c.anchorUs=0;s.clips.add(c);commit(s);return c.id;
    }
    public Clip get(String id){return find(state,id).copy();}
    public synchronized void completeImport(String id,boolean success)throws IOException {
        State s=state.copy();Clip c=find(s,id);c.end=lastEnd(s,c.run);c.status=success?"COMPLETE":"INTERRUPTED";
        if(!success)s.gaps.add(new Gap(c.run,c.end,c.end,"IMPORT_INTERRUPTED",false));commitAndPrune(s,0);
    }

    public final class Lease implements AutoCloseable {
        public final Clip clip;
        public final List<Ref> refs;
        public final List<Gap> gaps;
        private final String originalClipId;
        private boolean closed;
        private Lease(Clip c,List<Ref> refs,List<Gap> gaps){this.originalClipId=c.id;this.clip=c;this.refs=Collections.unmodifiableList(refs);this.gaps=Collections.unmodifiableList(gaps);}
        public Segment read(Ref r)throws IOException {synchronized(Repository.this){if(closed)throw new IOException("Closed archive lease");return readSegmentFile(r.id);}}
        @Override public void close(){synchronized(Repository.this){if(!closed){int n=leases.getOrDefault(originalClipId,1)-1;if(n<=0)leases.remove(originalClipId);else leases.put(originalClipId,n);closed=true;}}}
    }
    public synchronized Lease lease(String id)throws IOException {
        Clip c=find(state,id).copy();
        if(c.status.equals("ACTIVE")||c.status.equals("FINALIZING")||c.status.equals("IMPORTING"))throw new IOException("Recording is still being finalized");
        List<Ref> refs=new ArrayList<>();for(Ref r:state.refs)if(c.touches(r))refs.add(r);
        refs.sort(Comparator.comparingLong(x->x.start));
        List<Gap> gaps=new ArrayList<>();for(Gap g:state.gaps)if(g.run.equals(c.run)&&g.end>=c.start&&g.start<=c.end)gaps.add(g.copy());
        leases.put(id,leases.getOrDefault(id,0)+1);return new Lease(c,refs,gaps);
    }

    private void prune(long nowUs)throws IOException {
        State s=state.copy();List<Ref> removed=preparePrune(s,nowUs);
        if(!removed.isEmpty()||s.gaps.size()!=state.gaps.size())commit(s);
        deleteUnreferenced(removed);
    }
    private void commitAndPrune(State s,long nowUs)throws IOException {
        List<Ref> removed=preparePrune(s,nowUs);
        commit(s); // A failure must leave every previously committed segment physically present.
        deleteUnreferenced(removed);
    }
    private static List<Ref> preparePrune(State s,long nowUs){
        List<Ref> removed=new ArrayList<>();
        long cutoff=nowUs-s.minutes*60_000_000L;
        Iterator<Ref> it=s.refs.iterator();while(it.hasNext()){
            Ref r=it.next();boolean inRing=!s.run.isEmpty()&&r.run.equals(s.run)&&r.end>cutoff;
            if(!inRing&&!isProtected(s,r)){removed.add(r);it.remove();}
        }
        // Keep only events needed by a recording or the currently retained wall-time window.
        s.gaps.removeIf(g->{
            if(!s.run.isEmpty()&&g.run.equals(s.run)&&g.end>=cutoff)return false;
            for(Clip c:s.clips)if(c.run.equals(g.run)&&g.end>=c.start&&g.start<=c.end)return false;
            return true;
        });
        return removed;
    }
    private void deleteUnreferenced(List<Ref> removed)throws IOException {
        for(Ref r:removed)file(r.id).delete(); // Failed deletion is retried during cold recovery.
    }
    private static boolean isProtected(State s,Ref r){for(Clip c:s.clips)if(c.touches(r))return true;return false;}
    private static long lastEnd(State s,String run){long n=0;for(Ref r:s.refs)if(r.run.equals(run))n=Math.max(n,r.end);return n;}
    private static boolean coversRequestedEnd(State s,Clip c){
        if(c.end<=c.start)return true;
        for(Ref r:s.refs)if(r.run.equals(c.run)&&r.start<c.end&&r.end>=c.end)return true;
        return false;
    }
    private static long tailStart(State s,Clip c){
        long n=c.start;
        for(Ref r:s.refs)if(r.run.equals(c.run)&&r.start<c.end)n=Math.max(n,Math.min(c.end,r.end));
        return Math.min(n,c.end);
    }
    private static void finishReady(State s){for(Clip c:s.clips)if(c.status.equals("FINALIZING")&&coversRequestedEnd(s,c))c.status="COMPLETE";}
    private void requireRunning()throws IOException {if(state.run.isEmpty())throw new IOException("Microphone capture has not started");}
    private static Clip find(State s,String id){for(Clip c:s.clips)if(c.id.equals(id))return c;throw new IllegalArgumentException("Unknown recording");}
    private File file(String id)throws IOException {try{UUID.fromString(id);}catch(IllegalArgumentException e){throw new IOException("Invalid object ID",e);}return new File(root,id+".seg");}
    private Segment readSegmentFile(String id)throws IOException {Segment s=Segment.decode(crypto.open(readBounded(file(id),16*1024*1024),id+".seg"));if(!s.id.equals(id))throw new IOException("Segment identity mismatch");return s;}
    private void commit(State next)throws IOException {
        long started=System.nanoTime();byte[] bytes=encode(next);hook.beforeIndexCommit();
        atomic(new File(root,INDEX),crypto.seal(bytes,INDEX));state=next;
        long elapsed=(System.nanoTime()-started)/1000;StorageStats m=stats;
        stats=new StorageStats(m.indexCommits+1,m.indexBytes+bytes.length,elapsed,
                Math.max(m.maxCommitUs,elapsed),m.appends,m.lastAppendUs,m.maxAppendUs);
    }
    private static byte[] readBounded(File f,int max)throws IOException {long n=f.length();if(n<1||n>max)throw new IOException("Invalid object size");byte[] b=new byte[(int)n];try(DataInputStream d=new DataInputStream(new FileInputStream(f))){d.readFully(b);if(d.read()!=-1)throw new IOException("Object changed during read");}return b;}
    private static void atomic(File target,byte[] bytes)throws IOException {
        File tmp=new File(target.getParentFile(),target.getName()+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){out.write(bytes);out.getFD().sync();}
        // Fail closed if the filesystem cannot supply atomic replacement. Never delete the old index first.
        Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    private static byte[] encode(State s)throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);
        d.writeInt(0x52414331);d.writeInt(s.minutes);d.writeUTF(s.run);d.writeLong(s.runStart);d.writeLong(s.wallMs);
        d.writeInt(s.refs.size());for(Ref r:s.refs){d.writeUTF(r.id);d.writeUTF(r.run);d.writeLong(r.start);d.writeLong(r.end);}
        d.writeInt(s.clips.size());for(Clip c:s.clips){d.writeUTF(c.id);d.writeUTF(c.run);d.writeUTF(c.title);d.writeUTF(c.kind);d.writeUTF(c.status);d.writeLong(c.start);d.writeLong(c.end);d.writeLong(c.wallMs);d.writeLong(c.anchorUs);}
        d.writeInt(s.gaps.size());for(Gap g:s.gaps){d.writeUTF(g.run);d.writeUTF(g.reason);d.writeLong(g.start);d.writeLong(g.end);d.writeBoolean(g.missing);}
        d.flush();if(b.size()>MAX_INDEX-1024)throw new IOException("Archive index capacity reached");return b.toByteArray();
    }
    private static State decode(byte[] b)throws IOException {
        try(DataInputStream d=new DataInputStream(new ByteArrayInputStream(b))){
            if(d.readInt()!=0x52414331)throw new IOException("Unknown archive version");State s=new State();
            s.minutes=bounded(d.readInt(),1,60);s.run=d.readUTF();if(!s.run.isEmpty())UUID.fromString(s.run);s.runStart=d.readLong();s.wallMs=d.readLong();
            int n=bounded(d.readInt(),0,200000);Set<String> ids=new HashSet<>();
            for(int i=0;i<n;i++){String id=d.readUTF(),run=d.readUTF();UUID.fromString(id);UUID.fromString(run);long a=d.readLong(),e=d.readLong();if(a<0||e<=a||!ids.add(id))throw new IOException("Invalid segment reference");s.refs.add(new Ref(id,run,a,e));}
            n=bounded(d.readInt(),0,20000);ids.clear();
            for(int i=0;i<n;i++){Clip c=new Clip();c.id=d.readUTF();c.run=d.readUTF();UUID.fromString(c.id);UUID.fromString(c.run);c.title=d.readUTF();c.kind=d.readUTF();c.status=d.readUTF();c.start=d.readLong();c.end=d.readLong();c.wallMs=d.readLong();c.anchorUs=d.readLong();if(c.start<0||c.end<c.start||!ids.add(c.id))throw new IOException("Invalid recording");s.clips.add(c);}
            n=bounded(d.readInt(),0,200000);for(int i=0;i<n;i++){String run=d.readUTF(),reason=d.readUTF();long a=d.readLong(),e=d.readLong();boolean missing=d.readBoolean();if(a<0||e<a)throw new IOException("Invalid timeline interval");s.gaps.add(new Gap(run,a,e,reason,missing));}
            if(d.available()!=0)throw new IOException("Trailing index bytes");return s;
        }catch(IllegalArgumentException e){throw new IOException("Invalid archive metadata",e);}
    }
}

