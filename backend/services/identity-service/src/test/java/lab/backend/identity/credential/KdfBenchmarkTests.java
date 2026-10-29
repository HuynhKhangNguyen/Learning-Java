package lab.backend.identity.credential;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

/** Host measurement, not a latency/SLA assertion. Parameters are never reduced for the test. */
class KdfBenchmarkTests {
  @Test void recordsBaselineSingleAndTwoWayConcurrentDurations() throws Exception {
    var h=new Argon2CredentialHasher(); long singleStart=System.nanoTime();String encoded=h.hash("benchmark credential value");long singleMs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-singleStart);assertThat(h.matches("benchmark credential value",encoded)).isTrue();
    var pool=Executors.newFixedThreadPool(2);var gate=new CyclicBarrier(2);Callable<Long> task=()->{gate.await();long start=System.nanoTime();h.hash("concurrent benchmark value");return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);};var a=pool.submit(task);var b=pool.submit(task);long first=a.get(30,TimeUnit.SECONDS),second=b.get(30,TimeUnit.SECONDS);pool.shutdown();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();
    System.out.printf("R03_KDF_BENCHMARK host=%s processors=%d memoryKiB=%d iterations=%d parallelism=%d singleMs=%d concurrentMs=%d,%d%n",System.getProperty("os.name"),Runtime.getRuntime().availableProcessors(),Argon2CredentialHasher.MEMORY_KIB,Argon2CredentialHasher.ITERATIONS,Argon2CredentialHasher.PARALLELISM,singleMs,first,second);
    assertThat(singleMs).isPositive();assertThat(first).isPositive();assertThat(second).isPositive();
  }
}
