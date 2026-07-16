package com.archivenexus.backend.archiveos.runtime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ArchiveOsRuntimeDeliveryScheduler {
    private final ArchiveOsRuntimeDeliveryService deliveries; private final AtomicBoolean lock=new AtomicBoolean();
    public ArchiveOsRuntimeDeliveryScheduler(ArchiveOsRuntimeDeliveryService deliveries){this.deliveries=deliveries;}
    @Scheduled(initialDelayString="${archiveos.runtime-ingest.startup-delay-ms:30000}",fixedDelayString="${archiveos.runtime-ingest.fixed-delay-ms:5000}")
    public void run(){if(!lock.compareAndSet(false,true))return;try{deliveries.publishBatch();}finally{lock.set(false);}}
}
