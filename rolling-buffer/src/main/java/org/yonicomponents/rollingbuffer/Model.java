package org.yonicomponents.rollingbuffer;

import java.io.*;
import java.util.*;

public final class Model {
    private Model() {}
    public static final class Packet {
        public final long us;
        public final byte[] data;
        public Packet(long us, byte[] data) {
            if(us<0 || data.length<1 || data.length>8184) throw new IllegalArgumentException("Invalid packet");
            this.us=us; this.data=data.clone();
        }
    }
    public static final class Segment {
        public final String id,run;
        public final int rate,channels;
        public final List<Packet> packets;
        public Segment(String id,String run,int rate,int channels,List<Packet> packets) {
            UUID.fromString(id); UUID.fromString(run); Aac.rateIndex(rate);
            if(channels<1 || channels>2 || packets.isEmpty() || packets.size()>1024) throw new IllegalArgumentException("Invalid segment");
            long prev=-1;
            for(Packet p:packets) { if(p.us<=prev) throw new IllegalArgumentException("Non-monotonic packet"); prev=p.us; }
            this.id=id; this.run=run; this.rate=rate; this.channels=channels;
            this.packets=Collections.unmodifiableList(new ArrayList<>(packets));
        }
        public long start() { return packets.get(0).us; }
        public long end() { return packets.get(packets.size()-1).us+Aac.frameUs(rate); }
        public byte[] encode() throws IOException {
            ByteArrayOutputStream b=new ByteArrayOutputStream(); DataOutputStream d=new DataOutputStream(b);
            d.writeInt(0x52415331); d.writeUTF(id);d.writeUTF(run);d.writeInt(rate);d.writeInt(channels);d.writeInt(packets.size());
            for(Packet p:packets) {d.writeLong(p.us);d.writeInt(p.data.length);d.write(p.data);}d.flush();return b.toByteArray();
        }
        public static Segment decode(byte[] bytes) throws IOException {
            try(DataInputStream d=new DataInputStream(new ByteArrayInputStream(bytes))) {
                if(d.readInt()!=0x52415331) throw new IOException("Unknown segment version");
                String id=d.readUTF(),run=d.readUTF();int rate=d.readInt(),ch=d.readInt(),n=bounded(d.readInt(),1,1024);
                List<Packet> ps=new ArrayList<>();
                for(int i=0;i<n;i++){long t=d.readLong();int l=bounded(d.readInt(),1,8184);byte[] p=new byte[l];d.readFully(p);ps.add(new Packet(t,p));}
                if(d.available()!=0)throw new IOException("Trailing segment bytes");
                return new Segment(id,run,rate,ch,ps);
            } catch(IllegalArgumentException e){throw new IOException("Invalid segment",e);}
        }
    }
    public static final class Ref {
        public final String id,run;
        public final long start,end;
        public Ref(Segment s){this(s.id,s.run,s.start(),s.end());}
        public Ref(String id,String run,long start,long end){this.id=id;this.run=run;this.start=start;this.end=end;}
    }
    public static final class Clip {
        public String id,run,title,kind,status;
        public long start,end,wallMs,anchorUs;
        public Clip copy(){Clip c=new Clip();c.id=id;c.run=run;c.title=title;c.kind=kind;c.status=status;c.start=start;c.end=end;c.wallMs=wallMs;c.anchorUs=anchorUs;return c;}
        public boolean touches(Ref r){return run.equals(r.run)&&r.end>start&&r.start<end;}
    }
    public static final class Gap {
        public String run,reason;
        public long start,end;
        public boolean missing;
        public Gap(String run,long start,long end,String reason,boolean missing){this.run=run;this.start=start;this.end=end;this.reason=reason;this.missing=missing;}
        public Gap copy(){return new Gap(run,start,end,reason,missing);}
    }
    static int bounded(int n,int min,int max)throws IOException{if(n<min||n>max)throw new IOException("Invalid length");return n;}
    public static long covered(List<long[]> ranges,long from,long to){
        List<long[]> sorted=new ArrayList<>();
        for(long[] r:ranges) if(r[1]>from&&r[0]<to)sorted.add(new long[]{Math.max(from,r[0]),Math.min(to,r[1])});
        sorted.sort(Comparator.comparingLong(x->x[0]));long sum=0,a=-1,b=-1;
        for(long[] r:sorted){if(a<0){a=r[0];b=r[1];}else if(r[0]<=b)b=Math.max(b,r[1]);else{sum+=b-a;a=r[0];b=r[1];}}
        return sum+(a<0?0:b-a);
    }
}

