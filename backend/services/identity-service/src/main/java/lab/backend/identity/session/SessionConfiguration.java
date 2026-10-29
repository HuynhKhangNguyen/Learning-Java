package lab.backend.identity.session;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods=false)
@Profile("session")
public class SessionConfiguration {
  @Bean SessionHttpHandler sessionHttpHandler(SessionRuntime runtime){return runtime.handler();}
  @Bean org.springframework.boot.web.server.WebServerFactoryCustomizer<org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory> sessionErrorEnvelope(){
    return factory->factory.addContextCustomizers(context->{
      if(!(context.getParent() instanceof org.apache.catalina.core.StandardHost host))throw new IllegalStateException("Session error boundary unavailable");
      host.setErrorReportValveClass(SessionErrorReportValve.class.getName());
    });
  }
  @Bean(destroyMethod="shutdownNow") ScheduledExecutorService sessionExpirySweeper(SessionRuntime runtime){
    var executor=Executors.newSingleThreadScheduledExecutor(r->{var thread=new Thread(r,"identity-expiry");thread.setDaemon(true);return thread;});
    executor.scheduleWithFixedDelay(()->{
      try{runtime.authority().sweepExpired(100);}
      catch(Exception unavailable){/* Requests still verify DB clock/guards; never reopen or complete a pending operation. */}
    },10,10,TimeUnit.SECONDS);
    return executor;
  }
}
