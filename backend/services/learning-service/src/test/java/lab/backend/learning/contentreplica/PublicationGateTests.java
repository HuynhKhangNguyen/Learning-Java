package lab.backend.learning.contentreplica;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class PublicationGateTests {
  @Test void exclusiveFenceWaitsForExistingSharedAndBlocksNewShared()throws Exception{
    var gate=new PublicationGate();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(3);
    try{Future<String> first=pool.submit(()->gate.contentSensitive(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}return "old";}));assertTrue(entered.await(1,TimeUnit.SECONDS));Future<String> fence=pool.submit(()->gate.exclusive(()->"fenced"));Thread.sleep(50);Future<String> later=pool.submit(()->gate.contentSensitive(()->"new"));assertFalse(fence.isDone());assertFalse(later.isDone());release.countDown();assertEquals("old",first.get(1,TimeUnit.SECONDS));assertEquals("fenced",fence.get(1,TimeUnit.SECONDS));assertEquals("new",later.get(1,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
  }
}
