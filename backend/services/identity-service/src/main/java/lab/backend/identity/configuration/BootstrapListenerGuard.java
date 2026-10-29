package lab.backend.identity.configuration;

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
    boolean delivery=Arrays.equals(environment.getActiveProfiles(),new String[]{"delivery"});
    boolean canonical=Arrays.equals(environment.getActiveProfiles(),new String[]{"canonical"});
    if (canonical || delivery || Arrays.equals(environment.getActiveProfiles(), new String[] {"transport"})
        || Arrays.equals(environment.getActiveProfiles(), new String[] {"session"})) {
      var settings = lab.backend.identity.transport.TransportSettings.load(environment);
      context.getBeanFactory().registerSingleton("transportSettings", settings);
      if(canonical){
        // Verify own canonical composition trước listener; context close luôn
        // đóng outbound clients, kể cả startup bean/listener thất bại sau đây.
        var runtime=lab.backend.identity.session.CanonicalRuntime.load(environment,settings);
        context.getBeanFactory().registerSingleton("canonicalRuntime",runtime);
        context.addApplicationListener(event->{if(event instanceof org.springframework.context.event.ContextClosedEvent)runtime.close();});
      }
      if(delivery)context.getBeanFactory().registerSingleton("deliveryRuntime",lab.backend.identity.delivery.DeliveryRuntime.load(environment,settings));
      if (Arrays.equals(environment.getActiveProfiles(), new String[] {"session"})) {
        context.getBeanFactory().registerSingleton("sessionRuntime",
            lab.backend.identity.session.SessionRuntime.load(environment, settings));
      }
      return;
    }
    if (!Arrays.equals(environment.getActiveProfiles(), new String[] {"bootstrap"})) {
      throw new IllegalStateException(
          "R01b bootstrap requires only the explicit 'bootstrap' profile; trusted profiles belong to R02b.");
    }
    if (!"127.0.0.1".equals(environment.getProperty("server.address"))) {
      throw new IllegalStateException("R01b bootstrap requires server.address=127.0.0.1.");
    }
    Integer port = environment.getProperty("server.port", Integer.class);
    if (port == null || port < 0 || port > 65535 || (port != 0 && port < 1024)
        || port == 8080 || port == 8443) {
      throw new IllegalStateException(
          "R01b bootstrap requires port 0 or an unprivileged port other than 8080/8443.");
    }
    if (!"servlet".equals(environment.getProperty("spring.main.web-application-type"))
        || environment.getProperty("server.ssl.enabled", Boolean.class, false)
        || environment.containsProperty("management.server.port")) {
      throw new IllegalStateException(
          "R01b bootstrap allows one loopback HTTP servlet listener; TLS/management listeners belong to later tasks.");
    }
  }
}
