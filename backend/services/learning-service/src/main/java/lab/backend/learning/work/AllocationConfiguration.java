package lab.backend.learning.work;

import lab.backend.learning.identityaccess.AdmissionRuntime;
import lab.backend.learning.transport.TransportSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;

/** Explicit allocation config bật domain ports trên canonical local topology đã kiểm. */
@Configuration(proxyBeanMethods = false)
@Profile("canonical")
@ConditionalOnProperty(name = "backendlab.allocation.config")
public class AllocationConfiguration {
  @Bean(destroyMethod = "close")
  AllocationRuntime allocationRuntime(ConfigurableEnvironment environment,
      TransportSettings settings, AdmissionRuntime admission) {
    return AllocationRuntime.load(environment, settings, admission);
  }
}
