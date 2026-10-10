package org.yonicomponents.rollingbuffer;

import javax.crypto.KeyGenerator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;
import static org.yonicomponents.rollingbuffer.Model.*;

/** Dependency-free behavioral suite; also emits genuine per-scenario JUnit XML in CI. */
public final class CoreTests {
    interface Test {void run()throws Exception;}
    static int passed,failed;static final StringBuilder xml=new StringBuilder();
    static final List<Path> temp=new ArrayList<>();
    static CryptoBox crypto()throws Exception{KeyGenerator k=KeyGenerator.getInstance("AES");k.init(256);return new CryptoBox(k.generateKey());}
    static File dir()throws Exception{Path p=Files.createTempDirectory("rolling-buffer-test-");temp.add(p);return p.toFile();}
    static void eq(long a,long b){if(a!=b)throw new AssertionError(a+" != "+b);}
    static void ok(boolean b){if(!b)throw new AssertionError("Condition not satisfied");}
    static void throwsIo(Test t)throws Exception{try{t.run();throw new AssertionError("Expected failure");}catch(IOException expected){}}
    static void throwsAny(Test t)throws Exception{try{t.run();throw new AssertionError("Expected failure");}catch(IOException|IllegalArgumentException expected){}}
    static Segment seg(String run,long at){return new Segment(UUID.randomUUID().toString(),run,32000,1,Arrays.asList(new Packet(at,new byte[]{1,2,3}),new Packet(at+32000,new byte[]{4,5})));}
    static String finishClip(Repository r,String run)throws Exception{r.append(seg(run,1000000),1064000);String id=r.save(1064000);r.finish(1064000);r.end(1064000,null);return id;}
    static void test(String name,Test t){long start=System.nanoTime();try{t.run();passed++;System.out.println("PASS "+name);xml.append("<testcase name=\"").append(name).append("\" time=\"").append((System.nanoTime()-start)/1e9).append("\"/>");}catch(Throwable e){failed++;e.printStackTrace();xml.append("<testcase name=\"").append(name).append("\"><failure message=\"").append(e.toString().replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;")).append("\"/></testcase>");}}
    public static void main(String[] args)throws Exception {
        test("aes_gcm_roundtrip",()->{CryptoBox c=crypto();byte[] b="private audio".getBytes(StandardCharsets.UTF_8);ok(Arrays.equals(b,c.open(c.seal(b,"a"),"a")));});
        test("ciphertext_authentication",()->{CryptoBox c=crypto();byte[] b=c.seal(new byte[]{1,2},"a");b[b.length-1]^=1;throwsIo(()->c.open(b,"a"));});
        test("object_name_authentication",()->{CryptoBox c=crypto();byte[] b=c.seal(new byte[]{1},"a");throwsIo(()->c.open(b,"b"));});
        test("fresh_nonce_each_write",()->{CryptoBox c=crypto();ok(!Arrays.equals(c.seal(new byte[]{1},"a"),c.seal(new byte[]{1},"a")));});
        test("malformed_envelope_rejected",()->{CryptoBox c=crypto();throwsIo(()->c.open(new byte[0],"a"));});
        test("segment_roundtrip",()->{Segment s=seg(UUID.randomUUID().toString(),100);Segment x=Segment.decode(s.encode());eq(x.start(),100);eq(x.end(),64100);ok(x.id.equals(s.id));});
        test("segment_order_validated",()->{throwsAny(()->new Segment(UUID.randomUUID().toString(),UUID.randomUUID().toString(),32000,1,Arrays.asList(new Packet(100,new byte[]{1}),new Packet(99,new byte[]{2}))));});
        test("adts_length_and_rate",()->{byte[] h=Aac.header(100,44100,1);eq(((h[3]&3)<<11)|((h[4]&255)<<3)|((h[5]&224)>>5),107);eq((h[2]>>2)&15,4);});
        test("adts_invalid_payload",()->throwsAny(()->Aac.header(8192,44100,1)));
        test("covered_union_not_double_counted",()->eq(Model.covered(Arrays.asList(new long[]{0,10},new long[]{5,20}),0,30),20));
        test("short_warmup_does_not_invent_history",()->{Repository r=new Repository(dir(),crypto());String run=r.begin(1000000,1);r.append(seg(run,1000000),1064000);String id=r.save(1064000);eq(r.get(id).start,1000000);});
        test("double_save_is_idempotent",()->{Repository r=new Repository(dir(),crypto());r.begin(1000000,1);ok(r.save(1000100).equals(r.save(1000200)));eq(r.clips().size(),1);});
        test("save_requires_capture",()->{Repository r=new Repository(dir(),crypto());throwsIo(()->r.save(100));});
        test("window_is_elapsed_time_not_audio_count",()->{Repository r=new Repository(dir(),crypto());String run=r.begin(1000000,1);r.setMinutes(1,1000000);r.append(seg(run,1000000),1000000);r.append(seg(run,70000000),70064000);String id=r.save(70064000);eq(r.get(id).start,10064000);eq(r.segmentCount(),1);eq(r.availableUs(70064000),64000);});
        test("protected_segments_survive_pruning",()->{Repository r=new Repository(dir(),crypto());String run=r.begin(1000000,1);r.append(seg(run,1000000),1064000);String id=r.save(1064000);r.setMinutes(1,80000000);r.append(seg(run,80000000),80064000);eq(r.segmentCount(),2);r.finish(80064000);r.end(80064000,null);try(Repository.Lease l=r.lease(id)){eq(l.refs.size(),2);}});
        test("increasing_window_does_not_restore_deleted_history",()->{Repository r=new Repository(dir(),crypto());String run=r.begin(1000000,1);r.setMinutes(1,1000000);r.append(seg(run,1000000),1064000);r.clearUnsaved(80000000);eq(r.segmentCount(),0);r.setMinutes(60,80000000);eq(r.availableUs(80000000),0);});
        test("finish_waits_for_encoder_watermark",()->{Repository r=new Repository(dir(),crypto());String run=r.begin(1000000,1);String id=r.save(1010000);r.finish(1050000);ok(r.get(id).status.equals("FINALIZING"));r.append(seg(run,1000000),1064000);ok(r.get(id).status.equals("COMPLETE"));});
        test("later_segment_cannot_hide_missing_requested_tail",()->{
            Repository r=new Repository(dir(),crypto());String run=r.begin(1000000,1);String id=r.save(1000000);
            r.append(seg(run,1000000),1064000);r.finish(2000000);ok(r.get(id).status.equals("FINALIZING"));
            r.append(seg(run,3000000),3064000);ok(r.get(id).status.equals("FINALIZING"));
            r.end(3064000,null);ok(r.get(id).status.equals("COMPLETE"));
            ok(r.gaps(run).stream().anyMatch(g->g.reason.equals("UNWRITTEN_TAIL")&&g.missing&&g.start==1064000&&g.end==2000000));
        });
        test("cold_start_marks_interrupted_and_never_restarts",()->{File d=dir();CryptoBox c=crypto();Repository r=new Repository(d,c);String run=r.begin(1000000,1);r.append(seg(run,1000000),1064000);String id=r.save(1064000);Repository recovered=new Repository(d,c);ok(recovered.run().isEmpty());ok(recovered.get(id).status.equals("INTERRUPTED"));eq(recovered.segmentCount(),1);});
        test("cold_start_purges_unsaved_loop_only",()->{File d=dir();CryptoBox c=crypto();Repository r=new Repository(d,c);String run=r.begin(1000000,1);r.append(seg(run,1000000),1064000);Repository x=new Repository(d,c);eq(x.segmentCount(),0);});
        test("metadata_failure_does_not_publish_success",()->{File d=dir();CryptoBox c=crypto();AtomicBoolean fail=new AtomicBoolean();Repository r=new Repository(d,c,()->{if(fail.get())throw new IOException("Injected disk failure");});r.begin(1000000,1);fail.set(true);throwsIo(()->r.save(1064000));eq(r.clips().size(),0);});
        test("durable_orphan_segment_is_recovered",()->{File d=dir();CryptoBox c=crypto();AtomicBoolean fail=new AtomicBoolean();Repository r=new Repository(d,c,()->{if(fail.get())throw new IOException("Injected index failure");});String run=r.begin(1000000,1);String id=r.save(1010000);fail.set(true);throwsIo(()->r.append(seg(run,1000000),1064000));Repository x=new Repository(d,c);eq(x.segmentCount(),1);ok(x.get(id).status.equals("INTERRUPTED"));});
        test("corrupt_catalog_is_fail_closed",()->{File d=dir();CryptoBox c=crypto();new Repository(d,c);Path p=new File(d,"catalog.bin").toPath();byte[] b=Files.readAllBytes(p);b[b.length-1]^=1;Files.write(p,b);throwsIo(()->new Repository(d,c));ok(Files.exists(p));});
        test("deleted_key_cannot_silently_reset_archive",()->{File d=dir();new Repository(d,crypto());throwsIo(()->new Repository(d,crypto()));});
        test("lease_blocks_delete",()->{Repository r=new Repository(dir(),crypto());String run=r.begin(1000000,1);String id=finishClip(r,run);try(Repository.Lease lease=r.lease(id)){throwsIo(()->r.delete(id,2000000));}r.delete(id,2000000);eq(r.segmentCount(),0);});
        test("mutable_lease_metadata_cannot_release_another_clip",()->{
            Repository r=new Repository(dir(),crypto());
            String a=finishClip(r,r.begin(1_000_000,1));
            String b=finishClip(r,r.begin(1_000_000,2));
            try(Repository.Lease leaseA=r.lease(a);Repository.Lease leaseB=r.lease(b)){
                leaseA.clip.id=b;
                leaseA.close();
                throwsIo(()->r.delete(b,2_000_000));
                r.delete(a,2_000_000); // Closing A must release its original identity.
                eq(r.clips().size(),1);
                ok(!leaseB.read(leaseB.refs.get(0)).packets.isEmpty());
                leaseA.close(); // Idempotent close must not release B either.
                throwsIo(()->r.delete(b,2_000_000));
            }
            r.delete(b,2_000_000);
            eq(r.segmentCount(),0);
        });
        test("silenced_ranges_reduce_available_audio",()->{Repository r=new Repository(dir(),crypto());String run=r.begin(1000000,1);r.append(seg(run,1000000),1064000);r.openGap(1010000,"SYSTEM_SILENCED");r.closeGap(1030000,"SYSTEM_SILENCED");eq(r.availableUs(1064000),44000);});
        test("quiet_audio_is_not_automatically_a_gap",()->{Repository r=new Repository(dir(),crypto());String run=r.begin(1000000,1);r.append(seg(run,1000000),1064000);eq(r.gaps(run).size(),0);});
        test("invalid_window_rejected",()->{Repository r=new Repository(dir(),crypto());throwsAny(()->r.setMinutes(0,0));throwsAny(()->r.setMinutes(61,0));});
        test("imports_have_unknown_wall_time",()->{Repository r=new Repository(dir(),crypto());String id=r.createImport("manual");Clip c=r.get(id);eq(c.wallMs,-1);r.append(seg(c.run,0),0);r.completeImport(id,true);ok(r.get(id).status.equals("COMPLETE"));});
        test("import_does_not_contend_with_live_microphone",()->{Repository r=new Repository(dir(),crypto());r.begin(1000000,1);throwsIo(()->r.createImport("manual"));});
        test("export_keeps_discontinuities_separate",()->{Repository r=new Repository(dir(),crypto());String run=r.begin(1000000,1);r.append(seg(run,1000000),1064000);String id=r.save(1064000);r.append(seg(run,3000000),3064000);r.finish(3064000);r.end(3064000,null);ByteArrayOutputStream b=new ByteArrayOutputStream();Archive.exportZip(r,id,b);Set<String> names=new HashSet<>();try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(b.toByteArray()))){for(ZipEntry e;(e=z.getNextEntry())!=null;)names.add(e.getName());}ok(names.contains("audio-001.aac")&&names.contains("audio-002.aac")&&names.contains("timeline.json"));});
        test("random_access_playback_does_not_write_plaintext",()->{File d=dir();Repository r=new Repository(d,crypto());String run=r.begin(1000000,1);String id=finishClip(r,run);try(Archive.Reader reader=new Archive.Reader(r,id)){byte[] all=new byte[(int)reader.size()];eq(reader.readAt(0,all,0,all.length),all.length);byte[] slice=new byte[4];eq(reader.readAt(2,slice,0,4),4);ok(Arrays.equals(slice,Arrays.copyOfRange(all,2,6)));eq(reader.readAt(reader.size(),slice,0,1),-1);}for(File f:Objects.requireNonNull(d.listFiles()))ok(f.getName().endsWith(".bin")||f.getName().endsWith(".seg"));});
        test("duplicate_segment_rejected",()->{Repository r=new Repository(dir(),crypto());String run=r.begin(1000000,1);Segment s=seg(run,1000000);r.append(s,1064000);throwsIo(()->r.append(s,1064000));});
        test("concurrent_save_prune_commands_are_serialized",()->{Repository r=new Repository(dir(),crypto());String run=r.begin(1000000,1);r.append(seg(run,1000000),1064000);ExecutorService pool=Executors.newFixedThreadPool(4);try{List<Future<?>> fs=new ArrayList<>();for(int i=0;i<100;i++)fs.add(pool.submit(()->{try{r.save(1064000);r.clearUnsaved(1064000);}catch(IOException e){throw new RuntimeException(e);}}));for(Future<?> f:fs)f.get();eq(r.clips().size(),1);eq(r.segmentCount(),1);}finally{pool.shutdownNow();}});
        test("json_escaping_is_valid_for_control_characters",()->ok(Archive.quote("a\"\\\n").equals("\"a\\\"\\\\\\n\"")));
        ReliabilityTests.run();
        String report="<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite name=\"RollingBufferCore\" tests=\""+(passed+failed)+"\" failures=\""+failed+"\">"+xml+"</testsuite>";
        Files.writeString(Path.of("core-tests.xml"),report,StandardCharsets.UTF_8);
        System.out.println("RESULT "+passed+" passed, "+failed+" failed");
        for(Path p:temp)try(java.util.stream.Stream<Path> paths=Files.walk(p)){paths.sorted(Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(IOException ignored){}});}
        if(failed>0)throw new AssertionError(failed+" failed tests");
    }
}

