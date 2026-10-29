package lab.backend.content.publication;

import lab.backend.content.transport.TransportSettings;
import org.springframework.context.annotation.*;

/** Profile R06a riêng; không đăng ký coordinator hoặc canonical activation. */
@Configuration(proxyBeanMethods=false) @Profile("publication")
public class PublicationConfiguration {
  @Bean PublicationRuntime publicationRuntime(TransportSettings settings){return new PublicationRuntime(settings);}
}

