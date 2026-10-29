package lab.backend.content;

import lab.backend.content.configuration.BootstrapListenerGuard;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ContentApplication {

  public static void main(String[] args) {
    application().run(args);
  }

  static SpringApplication application() {
    SpringApplication application = new SpringApplication(ContentApplication.class);
    application.addInitializers(new BootstrapListenerGuard());
    return application;
  }
}
