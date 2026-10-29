package lab.backend.learning.configuration;

import java.util.Arrays;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/** Prevents the unfinished bootstrap artifact from becoming a network service. */
public final class BootstrapListenerGuard
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    ConfigurableEnvironment environment = context.getEnvironment();
    boolean admission=Arrays.equals(environment.getActiveProfiles(),new String[]{"admission"});
    boolean delivery=Arrays.equals(environment.getActiveProfiles(),new String[]{"delivery"});
    boolean canonical=Arrays.equals(environment.getActiveProfiles(),new String[]{"canonical"});
    boolean publication=Arrays.equals(environment.getActiveProfiles(),new String[]{"publication"});
    if (publication || canonical || delivery || admission || Arrays.equals(environment.getActiveProfiles(), new String[] {"transport"})) {
      var settings = lab.backend.learning.transport.TransportSettings.load(environment);
      context.getBeanFactory().registerSingleton("transportSettings", settings);
      if(delivery)context.getBeanFactory().registerSingleton("deliveryRuntime",lab.backend.learning.delivery.DeliveryRuntime.load(environment,settings));
      // Canonical chỉ own admission/fence gate, không learner domain endpoint
      // hoặc delivery LAB_APPLY adapter. Startup không provision/migrate own DB.
      if(admission||canonical)context.getBeanFactory().registerSingleton("admissionRuntime",lab.backend.learning.identityaccess.AdmissionRuntime.load(environment,settings));
      return;
    }
    if (!Arrays.equals(environment.getActiveProfiles(), new String[] {"bootstrap"})) {
      throw new IllegalStateException(
          "R01d bootstrap requires only the explicit 'bootstrap' profile; trusted profiles belong to R02b.");
    }
    if (!"127.0.0.1".equals(environment.getProperty("server.address"))) {
      throw new IllegalStateException("R01d bootstrap requires server.address=127.0.0.1.");
    }
    Integer port = environment.getProperty("server.port", Integer.class);
    if (port == null || port < 0 || port > 65535 || (port != 0 && port < 1024)
        || port == 8080 || port == 8443) {
      throw new IllegalStateException(
          "R01d bootstrap requires port 0 or an unprivileged port other than 8080/8443.");
    }
    if (!"servlet".equals(environment.getProperty("spring.main.web-application-type"))
        || environment.getProperty("server.ssl.enabled", Boolean.class, false)
        || environment.containsProperty("management.server.port")) {
      throw new IllegalStateException(
          "R01d bootstrap allows one loopback HTTP servlet listener; TLS/management listeners belong to later tasks.");
    }
  }
}
