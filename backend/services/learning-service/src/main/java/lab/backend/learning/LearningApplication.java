package lab.backend.learning;

import lab.backend.learning.configuration.BootstrapListenerGuard;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LearningApplication {

  public static void main(String[] args) {
    application().run(args);
  }

  static SpringApplication application() {
    SpringApplication application = new SpringApplication(LearningApplication.class);
    application.addInitializers(new BootstrapListenerGuard());
    return application;
  }
}
