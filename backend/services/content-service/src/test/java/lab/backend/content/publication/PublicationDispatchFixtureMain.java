package lab.backend.content.publication;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import lab.backend.content.transport.TransportSettings;
import org.springframework.core.env.StandardEnvironment;

/** Test-only barrier làm nóng TLS trước held receipt; GO vẫn chạy exact product CLI. */
public final class PublicationDispatchFixtureMain {
  public static void main(String[] arguments) throws Exception {
    var settings = TransportSettings.load(new StandardEnvironment());
    try (var warmup = new PublicationRuntime(settings).coordinator()) {
      // Tạo/đóng actual peer client ngoài barrier; không gửi phase hay thay domain state.
    }
    System.out.println("READY");
    System.out.flush();
    try (var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      if (!"GO".equals(reader.readLine())) {
        throw new IllegalArgumentException("Barrier command required");
      }
    }
    PublicationLabCli.main(arguments);
  }

  private PublicationDispatchFixtureMain() {}
}
