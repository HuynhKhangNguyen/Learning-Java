package lab.backend.identity.delivery;

import org.springframework.context.annotation.*;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;

/** Container parser/upload errors remain bounded redacted JSON in the lab profile. */
@Configuration(proxyBeanMethods=false) @Profile("delivery")
public class DeliveryConfiguration {
  @Bean WebServerFactoryCustomizer<TomcatServletWebServerFactory> deliveryErrorEnvelope(){
    return factory->factory.addContextCustomizers(context->{
      if(!(context.getParent() instanceof org.apache.catalina.core.StandardHost host))throw new IllegalStateException("Delivery error boundary unavailable");
      host.setErrorReportValveClass(DeliveryErrorReportValve.class.getName());
    });
  }
}

