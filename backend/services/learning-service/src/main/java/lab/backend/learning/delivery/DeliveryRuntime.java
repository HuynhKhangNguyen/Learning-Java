package lab.backend.learning.delivery;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import lab.backend.learning.transport.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.env.ConfigurableEnvironment;

/** Explicit owned-lab fixture profile. No domain EVT adapter or runtime migration/provision. */
public final class DeliveryRuntime {
  private final DeliveryStore store;
  private final DeliveryHttpHandler handler;
  private final Map<String,UUID> generations;
  private final PGSimpleDataSource source;
  private DeliveryRuntime(DeliveryStore store,PGSimpleDataSource source,Map<String,UUID> generations){
    this.store=store;this.source=source;this.generations=Map.copyOf(generations);
    handler=new DeliveryHttpHandler(store,new DeliveryStore.Consumer(){
      public void guard(Connection c,DeliveryWire.Envelope e)throws SQLException{
        // Local fixture only: no canonical suppression/phase/learning authority.
        try(var s=c.createStatement();var r=s.executeQuery("SELECT id FROM learning.transaction_probe LIMIT 0")){r.close();}
      }
      public String poison(DeliveryWire.Envelope e){
        if(!e.type().equals("LAB_APPLY")||!e.phase().equals("APPLY"))return "UNSUPPORTED_DELIVERY_TYPE";
        try{var n=WireCodec.strict(e.payload());if(n.size()!=1||!n.has("delta")||!n.get("delta").isInt()||n.get("delta").asInt()!=1)return "UNSUPPORTED_LAB_PAYLOAD";}catch(Exception failure){return "UNSUPPORTED_LAB_PAYLOAD";}return null;
      }
      public void apply(Connection c,DeliveryWire.Envelope e)throws SQLException{
        try(var s=c.prepareStatement("INSERT INTO learning.transaction_probe VALUES (?,1,'delivery-lab-effect')")){s.setString(1,e.message().toString());s.executeUpdate();}
      }
    });
  }
  public DeliveryHttpHandler handler(){return handler;}
  public DeliveryStore store(){return store;}
  public PGSimpleDataSource source(){return source;}
  public Map<String,UUID> generations(){return generations;}
  public static DeliveryRuntime load(ConfigurableEnvironment env,TransportSettings transport){
    try{
      String input=env.getProperty("backendlab.delivery.config");if(input==null||input.isBlank())throw new Exception();
      Path config=Path.of(input).toRealPath();if(!config.getParent().equals(transport.directory()))throw new Exception();
      Properties p=new Properties();
      for(String line:Files.readAllLines(config,StandardCharsets.UTF_8)){
        if(line.isBlank()||line.startsWith("#"))continue;
        if(!line.matches("[A-Za-z][A-Za-z0-9]*=[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Canonical delivery config required");
        int equals=line.indexOf('=');String key=line.substring(0,equals);
        if(p.putIfAbsent(key,line.substring(equals+1))!=null)throw new IllegalArgumentException("Duplicate delivery config denied");
      }
      if(!p.stringPropertyNames().equals(Set.of("schemaVersion","enabled","realm","generation","dbRunId","identityGeneration","contentGeneration","learningGeneration","adapter"))
          ||!"1".equals(p.getProperty("schemaVersion"))||!"true".equals(p.getProperty("enabled"))||!"LAB_APPLY".equals(p.getProperty("adapter"))
          ||!transport.realm().equals(p.getProperty("realm"))||!transport.generation().equals(p.getProperty("generation"))||!transport.dbRunId().equals(p.getProperty("dbRunId"))||transport.clockOffsetMs()!=0)throw new Exception();
      var peers=Map.of("identity",uuid(p,"identityGeneration"),"content",uuid(p,"contentGeneration"),"learning",uuid(p,"learningGeneration"));
      UUID realm=uuid(p,"realm"),generation=uuid(p,"generation");if(!peers.get(DeliveryWire.SERVICE).equals(generation))throw new Exception();
      var ds=new PGSimpleDataSource();ds.setServerNames(new String[]{"127.0.0.1"});ds.setPortNumbers(new int[]{transport.dbPort()});ds.setDatabaseName("learning_db");ds.setUser("learning_app");ds.setPassword(transport.dbPassword());ds.setConnectTimeout(1);ds.setSocketTimeout(8);ds.setSslMode("disable");
      var store=new DeliveryStore(ds,realm,generation,peers);store.verifyRuntime();
      try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT id FROM learning.transaction_probe LIMIT 0")){r.close();}
      return new DeliveryRuntime(store,ds,peers);
    }catch(Exception failed){throw new IllegalStateException("Owned-lab delivery config/schema/readiness unavailable");}
  }
  private static UUID uuid(Properties p,String key){String value=p.getProperty(key);UUID id=UUID.fromString(value);if(!id.toString().equals(value))throw new IllegalArgumentException("Canonical generation required");return id;}
  @Override public String toString(){return "DeliveryRuntime[owned-lab,adapter=LAB_APPLY,secrets=REDACTED]";}
}


