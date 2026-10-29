package lab.backend.learning.contentreplica;

import lab.backend.learning.transport.TransportSettings;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false) @Profile("publication")
public class PublicationConfiguration {
  @Bean(destroyMethod="close") PublicationRuntime publicationRuntime(TransportSettings settings)throws Exception{return new PublicationRuntime(settings);}
}
