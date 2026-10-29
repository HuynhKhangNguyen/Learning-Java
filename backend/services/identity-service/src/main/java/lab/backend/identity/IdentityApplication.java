package lab.backend.identity;

import lab.backend.identity.configuration.BootstrapListenerGuard;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IdentityApplication {

  public static void main(String[] args) {
    application().run(args);
  }

  static SpringApplication application() {
    SpringApplication application = new SpringApplication(IdentityApplication.class);
    application.addInitializers(new BootstrapListenerGuard());
    return application;
  }
}
