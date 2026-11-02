package lab.backend.content.configuration;

import java.util.Arrays;
import lab.backend.content.delivery.DeliveryRuntime;
import lab.backend.content.identityaccess.AdmissionRuntime;
import lab.backend.content.transport.TransportSettings;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/** Prevents the unfinished bootstrap artifact from becoming a network service. */
public final class BootstrapListenerGuard
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    ConfigurableEnvironment environment = context.getEnvironment();
    boolean admission = active(environment, "admission");
    boolean delivery = active(environment, "delivery");
    boolean canonical = active(environment, "canonical");
    boolean browser = active(environment, "browser");
    boolean publication = active(environment, "publication");
    if (publication || browser || canonical || delivery || admission || active(environment, "transport")) {
      TransportSettings settings = TransportSettings.load(environment);
      context.getBeanFactory().registerSingleton("transportSettings", settings);
      if (delivery) {
        context
            .getBeanFactory()
            .registerSingleton("deliveryRuntime", DeliveryRuntime.load(environment, settings));
      }
      // Canonical/browser chỉ own admission/fence gate; startup không tự
      // provision hoặc migrate DB và không mở delivery LAB_APPLY adapter.
      if (admission || canonical || browser) {
        context
            .getBeanFactory()
            .registerSingleton("admissionRuntime", AdmissionRuntime.load(environment, settings));
      }
      return;
    }
    if (!active(environment, "bootstrap")) {
      throw new IllegalStateException(
          "R01c bootstrap requires only the explicit 'bootstrap' profile; trusted profiles belong to R02b.");
    }
    if (!"127.0.0.1".equals(environment.getProperty("server.address"))) {
      throw new IllegalStateException("R01c bootstrap requires server.address=127.0.0.1.");
    }
    Integer port = environment.getProperty("server.port", Integer.class);
    if (port == null || port < 0 || port > 65535 || (port != 0 && port < 1024)
        || port == 8080 || port == 8443) {
      throw new IllegalStateException(
          "R01c bootstrap requires port 0 or an unprivileged port other than 8080/8443.");
    }
    if (!"servlet".equals(environment.getProperty("spring.main.web-application-type"))
        || environment.getProperty("server.ssl.enabled", Boolean.class, false)
        || environment.containsProperty("management.server.port")) {
      throw new IllegalStateException(
          "R01c bootstrap allows one loopback HTTP servlet listener; TLS/management listeners belong to later tasks.");
    }
  }

  private static boolean active(ConfigurableEnvironment environment, String profile) {
    return Arrays.equals(environment.getActiveProfiles(), new String[] {profile});
  }
}
