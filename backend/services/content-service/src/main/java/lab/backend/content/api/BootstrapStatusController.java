package lab.backend.content.api;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("bootstrap")
public class BootstrapStatusController {

  @GetMapping(value = "/bootstrap/status", produces = MediaType.APPLICATION_JSON_VALUE)
  public BootstrapStatus status() {
    return new BootstrapStatus("content-service", "bootstrap", false);
  }

  public record BootstrapStatus(String service, String phase, boolean businessReady) {}
}
