package lab.backend.content.publication;

import lab.backend.content.transport.TransportSettings;
import org.springframework.context.annotation.*;

/** Private bundle provider trên publication/canonical topology; coordinator vẫn là explicit CLI. */
@Configuration(proxyBeanMethods=false) @Profile({"publication", "canonical"})
public class PublicationConfiguration {
  @Bean PublicationRuntime publicationRuntime(TransportSettings settings){return new PublicationRuntime(settings);}
}

