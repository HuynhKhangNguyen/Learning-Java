package lab.backend.identity.session;

import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.*;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;

/** Chỉ canonical profile đã verify từ initializer mới có worker/private API.
 * Profile session/transport lịch sử không được auto-enable từ bean này. */
@Configuration(proxyBeanMethods=false)
@Profile("canonical")
public class CanonicalConfiguration {
  @Bean(destroyMethod="shutdownNow") ScheduledExecutorService canonicalWorker(CanonicalRuntime runtime){return runtime.startWorker();}
  @Bean WebServerFactoryCustomizer<TomcatServletWebServerFactory> canonicalErrorEnvelope(){
    return factory->factory.addContextCustomizers(context->{
      if(!(context.getParent() instanceof org.apache.catalina.core.StandardHost host))throw new IllegalStateException("Canonical error boundary unavailable");
      host.setErrorReportValveClass(SessionErrorReportValve.class.getName());
    });
  }
}
