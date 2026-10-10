package org.yonicomponents.rollingbuffer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.yonicomponents.rollingbuffer.CoreTests.*;
import static org.yonicomponents.rollingbuffer.Model.*;

/** Synthetic corruption, transaction faults and controlled backpressure. No real audio. */
final class ReliabilityTests {
    static void run(){
        test("corrupt_orphan_quarantined_valid_clip_survives",()->{
            File d=dir();CryptoBox c=crypto();Repository r=new Repository(d,c);
            String run=r.begin(1_000_000,1),id=finishClip(r,run);
            byte[] expected;try(Repository.Lease l=r.lease(id)){expected=l.read(l.refs.get(0)).encode();}
            String name=UUID.randomUUID()+".seg";byte[] damaged={9,8,7};Files.write(new File(d,name).toPath(),damaged);
            Repository x=new Repository(d,c);eq(x.quarantinedObjects(),1);eq(x.unreadableOrphans(),0);
            ok(!new File(d,name).exists());
            try(java.util.stream.Stream<Path> files=Files.walk(new File(d,"quarantine").toPath())){
                Path saved=files.filter(p->p.getFileName().toString().equals(name)).findFirst().orElseThrow();
                ok(Arrays.equals(damaged,Files.readAllBytes(saved)));
            }
            try(Repository.Lease l=x.lease(id)){ok(Arrays.equals(expected,l.read(l.refs.get(0)).encode()));}
            eq(new Repository(d,c).quarantinedObjects(),1);
        });
        test("orphan_uncertainty_recorded_on_interrupted_clip",()->{
            File d=dir();CryptoBox c=crypto();Repository r=new Repository(d,c);
            String run=r.begin(1_000_000,1),id=r.save(1_000_100);
            r.append(seg(run,1_000_000),1_064_000);
            Files.write(new File(d,UUID.randomUUID()+".seg").toPath(),new byte[]{0});
            Repository x=new Repository(d,c);ok(x.get(id).status.equals("INTERRUPTED"));
            ok(x.gaps(run).stream().anyMatch(g->g.reason.equals("UNREADABLE_ORPHAN_UNKNOWN_CONTENT")));
            eq(x.segmentCount(),1);ok(x.run().isEmpty());
        });
        test("unmovable_orphan_preserved_without_blocking_archive",()->{
            File d=dir();CryptoBox c=crypto();Repository r=new Repository(d,c);
            String id=finishClip(r,r.begin(1_000_000,1));
            Files.write(new File(d,"quarantine").toPath(),new byte[]{1}); // Not a directory: injection.
            File bad=new File(d,UUID.randomUUID()+".seg");Files.write(bad.toPath(),new byte[]{0});
            Repository x=new Repository(d,c);ok(x.unreadableOrphans()>0);ok(bad.exists());
            try(Repository.Lease l=x.lease(id)){ok(l.read(l.refs.get(0)).packets.size()>0);}
        });
        test("invalid_orphan_filename_cannot_disable_archive",()->{
            File d=dir();CryptoBox c=crypto();new Repository(d,c);
            Files.write(new File(d,"not-a-valid-uuid.seg").toPath(),new byte[]{0});
            Repository x=new Repository(d,c);eq(x.quarantinedObjects(),1);eq(x.segmentCount(),0);
        });
        test("referenced_missing_segment_still_fails_closed",()->{
            File d=dir();CryptoBox c=crypto();Repository r=new Repository(d,c);
            String id=finishClip(r,r.begin(1_000_000,1));String object;
            try(Repository.Lease l=r.lease(id)){object=l.refs.get(0).id;}
            byte[] catalog=Files.readAllBytes(new File(d,"catalog.bin").toPath());
            Files.delete(new File(d,object+".seg").toPath());throwsIo(()->new Repository(d,c));
            ok(Arrays.equals(catalog,Files.readAllBytes(new File(d,"catalog.bin").toPath())));
        });
        test("referenced_corruption_not_quarantined_or_silently_exported",()->{
            File d=dir();CryptoBox c=crypto();Repository r=new Repository(d,c);
            String id=finishClip(r,r.begin(1_000_000,1));String object;
            try(Repository.Lease l=r.lease(id)){object=l.refs.get(0).id;}
            File file=new File(d,object+".seg");byte[] b=Files.readAllBytes(file.toPath());b[b.length-1]^=1;Files.write(file.toPath(),b);
            Repository x=new Repository(d,c);eq(x.quarantinedObjects(),0);ok(file.exists());
            throwsIo(()->Archive.exportZip(x,id,new ByteArrayOutputStream()));
            ok(Arrays.equals(b,Files.readAllBytes(file.toPath())));
        });
        test("missing_index_never_resets_quarantine_only_archive",()->{
            File d=dir();CryptoBox c=crypto();new Repository(d,c);
            ok(new File(d,"quarantine").mkdir());Files.delete(new File(d,"catalog.bin").toPath());
            throwsIo(()->new Repository(d,c));ok(!new File(d,"catalog.bin").exists());
        });
        test("append_with_eviction_commits_index_once",()->{
            AtomicInteger commits=new AtomicInteger();Repository r=new Repository(dir(),crypto(),()->commits.incrementAndGet());
            String run=r.begin(1_000_000,1);r.setMinutes(1,1_000_000);r.append(seg(run,1_000_000),1_064_000);
            int before=commits.get();r.append(seg(run,70_000_000),70_064_000);
            eq(commits.get()-before,1);eq(r.segmentCount(),1);eq(r.storageStats().appends,2);
        });
        test("failed_combined_commit_preserves_old_disk_and_snapshot",()->{
            File d=dir();CryptoBox c=crypto();AtomicBoolean broken=new AtomicBoolean();
            Repository r=new Repository(d,c,()->{if(broken.get())throw new IOException("INJECTED_IO");});
            String run=r.begin(1_000_000,1);r.setMinutes(1,1_000_000);
            Segment old=seg(run,1_000_000);r.append(old,1_064_000);
            byte[] before=Files.readAllBytes(new File(d,"catalog.bin").toPath());
            broken.set(true);Segment fresh=seg(run,70_000_000);throwsIo(()->r.append(fresh,70_064_000));
            eq(r.durableEnd(),1_064_000);ok(new File(d,old.id+".seg").exists());ok(new File(d,fresh.id+".seg").exists());
            ok(Arrays.equals(before,Files.readAllBytes(new File(d,"catalog.bin").toPath())));
        });
        test("blocked_commit_does_not_block_snapshot_readers_or_ack_save",()->{
            AtomicBoolean block=new AtomicBoolean();CountDownLatch inside=new CountDownLatch(1),release=new CountDownLatch(1);
            Repository r=new Repository(dir(),crypto(),()->{if(block.get()){inside.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("TEST_TIMEOUT");}catch(InterruptedException e){throw new IOException(e);}}});
            r.begin(1_000_000,1);ExecutorService workers=Executors.newFixedThreadPool(2);
            try{
                block.set(true);Future<String> saving=workers.submit(()->r.save(1_000_100));ok(inside.await(2,TimeUnit.SECONDS));
                Future<Boolean> read=workers.submit(()->r.clips().isEmpty()&&r.active()==null&&r.storageStats()!=null);
                ok(read.get(1,TimeUnit.SECONDS));ok(!saving.isDone());release.countDown();ok(saving.get(2,TimeUnit.SECONDS)!=null);
            }finally{release.countDown();workers.shutdownNow();}
        });
        test("clock_absorbs_scheduling_lag_without_reanchoring",()->{
            CaptureClock c=new CaptureClock(44100,1_000_000,44100*3L);
            eq(c.accept(4410,1_100_000),1_000_000);
            eq(c.accept(4410,3_200_000),1_100_000); // Two seconds late, still within actual buffer budget.
            for(int n=2;n<22;n++)eq(c.accept(4410,3_200_000),1_000_000+n*100_000L);
        });
        test("clock_overrun_is_rejected_not_redated",()->{
            CaptureClock c=new CaptureClock(44100,1_000_000,44100*3L);
            throwsIo(()->c.accept(4410,5_000_000));eq(c.accept(4410,1_100_000),1_000_000);
        });
        test("clock_rejects_samples_far_in_future",()->{
            CaptureClock c=new CaptureClock(44100,1_000_000,44100*3L);throwsIo(()->c.accept(4410,800_000));
        });
        test("clock_uses_device_capacity_not_requested_capacity",()->{
            CaptureClock c=new CaptureClock(44100,1_000_000,22050);eq(c.maxLagUs(),400_000);
            throwsIo(()->c.accept(4410,1_600_000));
        });
        test("pcm_queue_bounds_order_ownership_and_wiping",()->{
            PcmQueue q=new PcmQueue(2);byte[] bytes={1,2};ok(q.offer(bytes,2,10));bytes[0]=9;
            ok(q.offer(bytes,2,20));ok(!q.offer(bytes,2,30));eq(q.highWater(),2);
            PcmQueue.Frame f=q.poll(0);eq(f.us,10);eq(f.data[0],1);f.erase();eq(f.data[0],0);
            eq(q.poll(0).us,20);ok(q.isEmpty());ok(q.offer(bytes,2,40));q.clear();ok(q.isEmpty());
        });
        test("pcm_queue_absorbs_controlled_writer_block_then_drains_in_order",()->{
            int count=200;PcmQueue q=new PcmQueue(PcmQueue.capacityForSeconds(44100,6));
            AtomicBoolean block=new AtomicBoolean();CountDownLatch inside=new CountDownLatch(1),release=new CountDownLatch(1);
            Repository r=new Repository(dir(),crypto(),()->{if(block.compareAndSet(true,false)){inside.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("TEST_TIMEOUT");}catch(InterruptedException e){throw new IOException(e);}}});
            String run=r.begin(1_000_000,1);r.save(1_000_000);block.set(true);
            ExecutorService worker=Executors.newSingleThreadExecutor();
            try{
                Future<?> write=worker.submit(()->{try{r.append(seg(run,1_000_000),1_064_000);for(int n=0;n<count;n++){PcmQueue.Frame f=q.poll(2000);if(f==null||f.us!=n*23219L)throw new AssertionError("PCM order lost");f.erase();}}catch(Exception e){throw new RuntimeException(e);}});
                ok(inside.await(2,TimeUnit.SECONDS));byte[] pcm=new byte[PcmQueue.BLOCK_BYTES];
                for(int n=0;n<count;n++)ok(q.offer(pcm,pcm.length,n*23219L));eq(q.size(),count);eq(r.durableEnd(),0);
                release.countDown();write.get(5,TimeUnit.SECONDS);ok(q.isEmpty());eq(q.highWater(),count);
            }finally{release.countDown();worker.shutdownNow();}
        });
        test("capture_journal_detects_interruption_without_claiming_crash_time",()->{
            File d=dir();CryptoBox c=crypto();CaptureJournal j=new CaptureJournal(d,c);j.arm(1000);
            CaptureJournal recovered=new CaptureJournal(d,c);ok(recovered.recover(9000));
            eq(recovered.snapshot().startedAtMs,1000);eq(recovered.snapshot().failureObservedAtMs,9000);
            ok(recovered.snapshot().failureCode.equals("PROCESS_INTERRUPTED_TIME_UNKNOWN"));ok(!recovered.snapshot().running);
            ok(!new CaptureJournal(d,c).recover(10000));
        });
        test("capture_journal_clean_stop_does_not_invent_failure",()->{
            File d=dir();CryptoBox c=crypto();CaptureJournal j=new CaptureJournal(d,c);j.arm(1000);j.stopped(null,2000);
            CaptureJournal x=new CaptureJournal(d,c);ok(!x.recover(3000));ok(x.snapshot().failureCode.isEmpty());
        });
        test("capture_journal_last_incident_survives_successful_restart",()->{
            File d=dir();CryptoBox c=crypto();CaptureJournal j=new CaptureJournal(d,c);j.arm(1000);j.stopped("MIC_STALL",2000);
            j.arm(3000);j.stopped(null,4000);CaptureJournal x=new CaptureJournal(d,c);
            ok(x.snapshot().failureCode.equals("MIC_STALL"));eq(x.snapshot().failureObservedAtMs,2000);
        });
        test("capture_journal_corruption_is_not_overwritten",()->{
            File d=dir();CryptoBox c=crypto();new CaptureJournal(d,c).arm(1000);
            Path p=new File(d,"capture-state.bin").toPath();byte[] b=Files.readAllBytes(p);b[b.length-1]^=1;Files.write(p,b);
            throwsIo(()->new CaptureJournal(d,c));ok(Arrays.equals(b,Files.readAllBytes(p)));
        });
        test("capture_journal_failed_arm_never_publishes_running",()->{
            File d=dir();CaptureJournal j=new CaptureJournal(d,crypto());ok(new File(d,"capture-state.bin.tmp").mkdir());
            throwsIo(()->j.arm(1000));ok(!j.snapshot().running);
        });
        test("capture_journal_failure_note_does_not_claim_stop_completed",()->{
            File d=dir();CryptoBox c=crypto();CaptureJournal j=new CaptureJournal(d,c);j.arm(1000);j.noteFailure("MIC_STALL",2000);
            CaptureJournal x=new CaptureJournal(d,c);ok(x.snapshot().running);ok(x.snapshot().failureCode.equals("MIC_STALL"));
            x.stopped("MIC_STALL",2000);ok(!x.snapshot().running);
        });
        test("capture_journal_rejects_paths_in_failure_reason",()->{
            CaptureJournal j=new CaptureJournal(dir(),crypto());throwsIo(()->j.stopped("/private/secret.wav",1000));
        });
    }
}

