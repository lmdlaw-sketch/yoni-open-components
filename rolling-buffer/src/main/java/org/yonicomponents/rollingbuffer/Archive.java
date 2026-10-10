package org.yonicomponents.rollingbuffer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import static org.yonicomponents.rollingbuffer.Model.*;

public final class Archive {
    private Archive() {}
    public static boolean selected(Clip c,Segment s,Packet p){return p.us+Aac.frameUs(s.rate)>c.start&&p.us<c.end;}
    public static String quote(String s){
        StringBuilder b=new StringBuilder("\"");
        for(int i=0;i<s.length();i++){char c=s.charAt(i);switch(c){case '"':b.append("\\\"");break;case '\\':b.append("\\\\");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;default:if(c<32)b.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else b.append(c);}}
        return b.append('"').toString();
    }
    public static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
    private static MessageDigest sha(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static void interrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Operation cancelled");}
    public static String timeline(Repository.Lease lease,String partsJson){
        Clip c=lease.clip;StringBuilder g=new StringBuilder("[");
        for(Gap gap:lease.gaps){if(g.length()>1)g.append(',');g.append("{\"start_us\":").append(Math.max(c.start,gap.start)).append(",\"end_us\":").append(Math.min(c.end,gap.end)).append(",\"missing\":").append(gap.missing).append(",\"reason\":").append(quote(gap.reason)).append('}');}g.append(']');
        return "{\"schema\":1,\"clip_id\":"+quote(c.id)+",\"title\":"+quote(c.title)+",\"kind\":"+quote(c.kind)+",\"status\":"+quote(c.status)+
                ",\"requested_start_us\":"+c.start+",\"requested_end_us\":"+c.end+",\"wall_anchor_ms\":"+(c.wallMs<0?"null":Long.toString(c.wallMs))+",\"monotonic_anchor_us\":"+c.anchorUs+
                ",\"boundary_policy\":\"overlapping_AAC_LC_frames_max_one_frame_at_each_boundary\",\"unknown_source_time\":"+(c.wallMs<0)+
                ",\"playback_notice\":\"discontinuities_split_into_parts; system_silencing_marked_separately; no_call_coverage_guarantee\",\"events\":"+g+",\"parts\":"+partsJson+"}";
    }
    /** Exports a package rather than concealing unknown intervals in one falsely continuous file. */
    public static void exportZip(Repository repository,String id,OutputStream destination)throws IOException {
        try(Repository.Lease lease=repository.lease(id);ZipOutputStream zip=new ZipOutputStream(destination)){
            int part=0,rate=0,channels=0;long start=0,end=0,frames=0;String name="";MessageDigest digest=null;StringBuilder parts=new StringBuilder("[");
            for(Ref ref:lease.refs){interrupted();Segment s=lease.read(ref);
                for(Packet p:s.packets){if(!selected(lease.clip,s,p))continue;
                    if(digest==null || s.rate!=rate || s.channels!=channels || p.us>end+5000 || p.us<end-2){
                        if(digest!=null){zip.closeEntry();if(parts.length()>1)parts.append(',');parts.append(partJson(name,start,end,rate,channels,frames,digest.digest()));}
                        rate=s.rate;channels=s.channels;start=p.us;frames=0;name=String.format(Locale.ROOT,"audio-%03d.aac",++part);
                        zip.putNextEntry(new ZipEntry(name));digest=sha();
                    }
                    byte[] header=Aac.header(p.data.length,s.rate,s.channels);zip.write(header);zip.write(p.data);digest.update(header);digest.update(p.data);
                    end=p.us+Aac.frameUs(rate);frames++;
                }
            }
            if(digest==null)throw new IOException("Recording contains no durable audio");
            zip.closeEntry();if(parts.length()>1)parts.append(',');parts.append(partJson(name,start,end,rate,channels,frames,digest.digest())).append(']');
            zip.putNextEntry(new ZipEntry("timeline.json"));zip.write(timeline(lease,parts.toString()).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("READ-ME-HE.txt"));
            zip.write(("ייצוא מלולאת קול\nהחבילה אינה מוצפנת. כל קובץ AAC הוא מקטע רציף לפי תזמוני המקודד.\nפערי תזמון מפוצלים לקבצים נפרדים; השתקת מיקרופון ואירועי מערכת מתועדים ב-timeline.json.\nאין להסיק מקיום קובץ ששני צדדי שיחה הוקלטו. זמן המקור של ייבוא ידני אינו ידוע.\nגבולות השמירה מעוגלים למסגרות AAC; עד מסגרת אחת נוספת בכל קצה.\nהאזנה בתוך האפליקציה מדלגת על פערי תזמון. אין לחבר קבצים ולהציגם כרצף ללא דוח הזמנים.\nSHA-256 מתאר את בייטי קובץ הייצוא; אינו חתימה דיגיטלית או הבטחת מקור.\n").getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
    }
    private static String partJson(String name,long start,long end,int rate,int channels,long frames,byte[] digest){return "{\"file\":"+quote(name)+",\"start_us\":"+start+",\"end_us\":"+end+",\"rate\":"+rate+",\"channels\":"+channels+",\"frames\":"+frames+",\"sha256\":"+quote(hex(digest))+"}";}

    /** Random access ADTS view. Only one small plaintext segment is cached in memory. */
    public static final class Reader implements AutoCloseable {
        private static final class Block {Ref ref;long offset;int length;Block(Ref r,long o,int n){ref=r;offset=o;length=n;}}
        private final Repository.Lease lease;
        private final List<Block> blocks=new ArrayList<>();
        private long size;
        private int cached=-1;
        private byte[] clear=new byte[0];
        private boolean closed;
        public final boolean hasWarnings;
        public Reader(Repository repository,String id)throws IOException {
            lease=repository.lease(id);boolean warnings=!lease.gaps.isEmpty()||lease.clip.status.equals("INTERRUPTED");long previous=-1;
            try {
                for(Ref ref:lease.refs){interrupted();Segment s=lease.read(ref);int n=0;
                    for(Packet p:s.packets)if(selected(lease.clip,s,p)){if(previous>=0&&p.us>previous+5000)warnings=true;previous=p.us+Aac.frameUs(s.rate);n+=7+p.data.length;}
                    if(n>0){blocks.add(new Block(ref,size,n));size+=n;}
                }
                if(size==0)throw new IOException("Recording contains no durable audio");
            } catch(IOException|RuntimeException e){lease.close();throw e;}
            hasWarnings=warnings;
        }
        public synchronized long size(){return size;}
        public synchronized int readAt(long position,byte[] buffer,int offset,int length)throws IOException {
            if(closed)throw new IOException("Playback closed");
            if(position<0||offset<0||length<0||offset>buffer.length-length)throw new IOException("Invalid read range");
            if(length==0)return 0;if(position>=size)return -1;int written=0;
            while(written<length&&position<size){
                int lo=0,hi=blocks.size()-1;
                while(lo<hi){int mid=(lo+hi+1)>>>1;if(blocks.get(mid).offset<=position)lo=mid;else hi=mid-1;}
                Block b=blocks.get(lo);
                if(cached!=lo){Arrays.fill(clear,(byte)0);ByteArrayOutputStream out=new ByteArrayOutputStream(b.length);Segment s=lease.read(b.ref);for(Packet p:s.packets)if(selected(lease.clip,s,p))Aac.write(out,p,s.rate,s.channels);clear=out.toByteArray();cached=lo;if(clear.length!=b.length)throw new IOException("Immutable archive changed");}
                int at=(int)(position-b.offset),n=Math.min(length-written,clear.length-at);
                System.arraycopy(clear,at,buffer,offset+written,n);position+=n;written+=n;
            }
            return written;
        }
        @Override public synchronized void close(){if(!closed){closed=true;Arrays.fill(clear,(byte)0);clear=new byte[0];lease.close();}}
    }
}

