package lab.backend.content.delivery;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import lab.backend.content.transport.TransportSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.env.*;

/** Kiểm ranh giới parser config: config sai không được tạo DB client.
 * Chặn constructor chỉ là unit fixture; readiness/PKI thật vẫn do PG/mTLS IT kiểm. */
class DeliveryRuntimeConfigTests {
  @TempDir Path directory;
  static final String REALM="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1";
  static final String GEN="bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2";
  static final String RUN="cccccccc-cccc-4ccc-8ccc-ccccccccccc3";
  Path config;
  String valid;
  @BeforeEach void fixture()throws Exception{
    directory=directory.toRealPath();config=directory.resolve("delivery.properties");
    var p=new LinkedHashMap<String,String>();
    p.put("schemaVersion","1");p.put("enabled","true");p.put("adapter","LAB_APPLY");
    p.put("realm",REALM);p.put("generation",GEN);p.put("dbRunId",RUN);
    for(String service:List.of("identity","content","learning"))
      p.put(service+"Generation",service.equals(DeliveryWire.SERVICE)?GEN:"dddddddd-dddd-4ddd-8ddd-ddddddddddd4");
    valid=p.entrySet().stream().map(e->e.getKey()+"="+e.getValue()+"\n").collect(java.util.stream.Collectors.joining());
    Files.writeString(config,valid);
  }
  TransportSettings transport(long offset){
    // Record giả lập đầu vào transport đã tin cậy cho parser; không chứng minh certificate hợp lệ.
    return new TransportSettings(directory,directory.resolve("unused-key.p12"),directory.resolve("unused-ca.p12"),
      "unit-only-no-runtime-secret",12345,"unit-only-no-runtime-secret",RUN,REALM,GEN,offset);
  }
  StandardEnvironment environment(String input){
    var env=new StandardEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("fixture",Map.of("backendlab.delivery.config",input)));
    return env;
  }
  @ParameterizedTest
  @ValueSource(strings={"missingInput","missingFile","outsideDirectory","duplicate","unknown","missingKey",
    "schema","disabled","adapter","realm","generation","dbRunId","ownPeer","invalidPeer","uppercasePeer","clockOffset","whitespace","dottedKey"})
  void invalidConfigCannotConstructDatabaseClient(String variant)throws Exception{
    // Mỗi biến thể phá một điều kiện config. Kiểm cả lỗi redacted (không lộ nguyên nhân
    // chứa secret) và không tạo DS, tránh test xanh chỉ vì DB giả cũng đang lỗi.
    String text=valid,input=config.toString();long offset=0;
    switch(variant){
      case "missingInput"->input="";
      case "missingFile"->Files.delete(config);
      case "outsideDirectory"->{Path outside=Files.createDirectory(directory.resolve("other")).resolve("delivery.properties");Files.writeString(outside,valid);input=outside.toString();}
      case "duplicate"->text+="enabled=true\n";
      case "unknown"->text+="unexpected=true\n";
      case "missingKey"->text=text.replace("adapter=LAB_APPLY\n","");
      case "schema"->text=text.replace("schemaVersion=1","schemaVersion=2");
      case "disabled"->text=text.replace("enabled=true","enabled=false");
      case "adapter"->text=text.replace("adapter=LAB_APPLY","adapter=PUBLICATION");
      case "realm"->text=text.replace("realm="+REALM,"realm="+GEN);
      case "generation"->text=text.replace("generation="+GEN,"generation="+REALM);
      case "dbRunId"->text=text.replace("dbRunId="+RUN,"dbRunId="+REALM);
      case "ownPeer"->text=text.replace(DeliveryWire.SERVICE+"Generation="+GEN,DeliveryWire.SERVICE+"Generation="+REALM);
      case "invalidPeer"->text=text.replace("dddddddd-dddd-4ddd-8ddd-ddddddddddd4","invalid");
      case "uppercasePeer"->text=text.replace("dddddddd-dddd-4ddd-8ddd-ddddddddddd4","DDDDDDDD-DDDD-4DDD-8DDD-DDDDDDDDDDD4");
      case "clockOffset"->offset=1;
      case "whitespace"->text+=" enabled=true\n";
      case "dottedKey"->text+="test.offset=0\n";
      default->throw new AssertionError(variant);
    }
    if(!variant.equals("missingFile"))Files.writeString(config,text);
    var env=environment(input);var trusted=transport(offset);
    try(var construction=mockConstruction(PGSimpleDataSource.class)){
      assertThatThrownBy(()->DeliveryRuntime.load(env,trusted)).isInstanceOf(IllegalStateException.class)
        .hasMessage("Owned-lab delivery config/schema/readiness unavailable").hasNoCause();
      assertThat(construction.constructed()).as("Parser must reject before any DB client: %s",variant).isEmpty();
    }
  }
  @Test void validConfigStillRequiresOwnDatabaseReadiness(){
    try(var construction=mockConstruction(PGSimpleDataSource.class)){
      // Positive control (đối chứng hợp lệ): parser cho tới bước readiness, nhưng
      // connection null của mock không thể biến config thành runtime-ready.
      assertThatThrownBy(()->DeliveryRuntime.load(environment(config.toString()),transport(0)))
        .isInstanceOf(IllegalStateException.class).hasMessage("Owned-lab delivery config/schema/readiness unavailable");
      assertThat(construction.constructed()).hasSize(1);
    }
  }
  /** Mapping vectors độc lập, không PKI/listener proof. Foundation constructor
   * giữ ports cũ; canonical phải có sáu ports riêng, không tùy chọn endpoint. */
  @Test void namedCanonicalTopologyKeepsStaticPortsAndLegacyDefaults(){
    var old=transport(0);assertThat(old.publicPort()).isEqualTo(8080);assertThat(old.privatePort()).isEqualTo(8443);assertThat(old.canonicalLab()).isFalse();
    var named=new TransportSettings(directory,old.keyStore(),old.trustStore(),old.storePassword(),old.dbPort(),old.dbPassword(),RUN,REALM,GEN,0,TransportSettings.CANONICAL);
    assertThat(named.canonicalLab()).isTrue();assertThat(named.peerPrivatePort("identity")).isEqualTo(18441);assertThat(named.peerPrivatePort("content")).isEqualTo(18442);assertThat(named.peerPrivatePort("learning")).isEqualTo(18443);
    int expected=switch(TransportSettings.SERVICE){case "identity"->18081;case "content"->18082;case "learning"->18083;default->throw new AssertionError();};
    assertThat(named.publicPort()).isEqualTo(expected);assertThat(named.privatePort()).isEqualTo(expected+360);
    assertThatThrownBy(()->named.peerPrivatePort("browser")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(()->new TransportSettings(directory,old.keyStore(),old.trustStore(),old.storePassword(),old.dbPort(),old.dbPassword(),RUN,REALM,GEN,0,"arbitrary-topology")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(()->new TransportSettings(directory,old.keyStore(),old.trustStore(),old.storePassword(),old.dbPort(),old.dbPassword(),RUN,REALM,GEN,1,TransportSettings.CANONICAL)).isInstanceOf(IllegalArgumentException.class);
  }
  /** Arbitrary server.port bị chặn trước file/certificate/DB; own named port
   * vẫn cần config thật, không được coi mapping hợp lệ là readiness. */
  @Test void canonicalTransportRejectsArbitraryListenerBeforeOpeningDatabase(){
    for(int port:List.of(8080,8443,12345,18441,18442,18443)){
      var env=new StandardEnvironment();env.setActiveProfiles("canonical");env.getPropertySources().addFirst(new MapPropertySource("bad-port",Map.of("server.port",port)));
      assertThatThrownBy(()->TransportSettings.load(env)).isInstanceOf(IllegalStateException.class).hasMessage("Transport listener configuration denied");
    }
    var env=new StandardEnvironment();env.setActiveProfiles("canonical");
    assertThatThrownBy(()->TransportSettings.load(env)).isInstanceOf(IllegalStateException.class).hasMessage("Transport config missing; no protected readiness");
  }
}
