package lab.backend.learning;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LearningBootstrapConfigTests {

  @Test
  void doesNotStartWithoutAnExplicitBootstrapProfile() {
    assertRejected("explicit 'bootstrap' profile", "--spring.profiles.active=");
  }

  @ParameterizedTest
  @ValueSource(strings = {"lab", "pilot", "private", "bootstrap,pilot", "unknown"})
  void rejectsUnimplementedOrMixedProfiles(String profiles) {
    assertRejected("explicit 'bootstrap' profile", "--spring.profiles.active=" + profiles);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.0.0.0", "::", "192.0.2.1", "localhost"})
  void doesNotBindTheBootstrapToAnotherAddress(String address) {
    assertRejected("server.address=127.0.0.1", "--spring.profiles.active=bootstrap", "--server.address=" + address);
  }

  @ParameterizedTest
  @ValueSource(strings = {"8080", "8443", "80", "-1", "65536"})
  void rejectsReservedPrivilegedOrInvalidPorts(String port) {
    assertRejected("other than 8080/8443", "--spring.profiles.active=bootstrap", "--server.port=" + port);
  }

  @ParameterizedTest
  @ValueSource(strings = {"--server.ssl.enabled=true", "--management.server.port=18084", "--spring.main.web-application-type=none"})
  void doesNotStartAnotherListenerOrAHeadlessBootstrap(String argument) {
    assertRejected("one loopback HTTP servlet listener", "--spring.profiles.active=bootstrap", argument);
  }

  private void assertRejected(String reason, String... arguments) {
    var application = LearningApplication.application();
    application.setRegisterShutdownHook(false);
    application.setLogStartupInfo(false);
    application.setDefaultProperties(java.util.Map.of("logging.level.root", "OFF"));
    assertThatThrownBy(() -> application.run(arguments))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining(reason);
  }
}
