package lab.backend.identity.delivery;

import static lab.backend.identity.delivery.DeliveryWire.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import lab.backend.identity.transport.WireCodec;

/** Monotonic auth lane has no strict event stream/cursor join or canonical state writes. */
public final class AuthDeliveryStore {
  private final DataSource source;
  private final UUID realm,generation;
  private final Map<String,UUID> peers;
  public AuthDeliveryStore(DataSource source,UUID realm,UUID generation,Map<String,UUID> peers){
    this.source=source;this.realm=realm;this.generation=generation;this.peers=Map.copyOf(peers);
    if(!this.peers.keySet().equals(SERVICES)||!generation.equals(this.peers.get("identity")))throw new IllegalArgumentException("Explicit peer generations required");
  }
  public record Lease(AuthDeliveryWire.Command command,UUID token,int attempt){
    @Override public String toString(){return "AuthDeliveryLease[operation="+command.operation()+"]";}
  }
  /** Owning coordinator may call append on its existing own transaction in R04d. */
  public void append(Connection c,byte[] exactBytes)throws SQLException{
    if(c.getAutoCommit())throw new SQLException("Auth outbox requires caller-owned local transaction");
    var e=AuthDeliveryWire.command(exactBytes);bound(e);environment(c);
    try(var s=c.prepareStatement("INSERT INTO identity.auth_delivery_outbox(operation_id,consumer,realm_id,participant_generation,command_bytes,command_digest,envelope_digest) VALUES (?,?,?,?,?,?,?) ON CONFLICT DO NOTHING")){
      s.setObject(1,e.operation());s.setString(2,e.consumer());s.setObject(3,realm);s.setObject(4,e.generation());s.setBytes(5,e.bytes());s.setString(6,e.digest());s.setString(7,e.envelopeDigest());s.executeUpdate();
    }
    var saved=stored(c,e.operation(),e.consumer());if(!saved.envelopeDigest().equals(e.envelopeDigest()))throw deny(409,"AUTH_COMMAND_CONFLICT");
  }
  public void enqueue(byte[] exactBytes,DeliveryStore.Work work){
    var e=AuthDeliveryWire.command(exactBytes);bound(e);
    transaction(c->{
      environment(c);
      // Serialize the immutable operation/participant before invoking local producer work.
      try(var s=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")){s.setString(1,"auth:"+e.operation()+":"+e.consumer());s.executeQuery().close();}
      var old=stored(c,e.operation(),e.consumer());if(old!=null){if(!old.envelopeDigest().equals(e.envelopeDigest()))throw deny(409,"AUTH_COMMAND_CONFLICT");return null;}
      work.run(DeliveryStore.local(c));append(c,e.bytes());return null;
    });
  }
  public Optional<Lease> claim(){
    return transaction(c->{
      environment(c);
      try(var s=c.createStatement()){s.executeUpdate("UPDATE identity.auth_delivery_outbox SET delivery_status='PAUSED',lease_token=NULL,lease_until=NULL,last_error='RETRY_EXHAUSTED' WHERE delivery_status='PENDING' AND attempts=8 AND (lease_until IS NULL OR lease_until<=clock_timestamp())");}
      UUID operation;String consumer;
      try(var s=c.prepareStatement("SELECT operation_id,consumer FROM identity.auth_delivery_outbox WHERE realm_id=? AND delivery_status='PENDING' AND attempts<8 AND next_attempt_at<=clock_timestamp() AND (lease_until IS NULL OR lease_until<=clock_timestamp()) ORDER BY next_attempt_at,operation_id,consumer FOR UPDATE SKIP LOCKED LIMIT 1")){
        s.setObject(1,realm);try(var r=s.executeQuery()){if(!r.next())return Optional.empty();operation=r.getObject(1,UUID.class);consumer=r.getString(2);}
      }
      var e=stored(c,operation,consumer);bound(e);UUID token=UUID.randomUUID();int attempt;
      try(var s=c.prepareStatement("UPDATE identity.auth_delivery_outbox SET attempts=attempts+1,lease_token=?,lease_until=clock_timestamp()+interval '15 seconds' WHERE operation_id=? AND consumer=? RETURNING attempts")){
        s.setObject(1,token);s.setObject(2,operation);s.setString(3,consumer);try(var r=s.executeQuery()){r.next();attempt=r.getInt(1);}
      }return Optional.of(new Lease(e,token,attempt));
    });
  }
  public boolean delivered(Lease lease,byte[] exactAck){
    exactAck=exactAck.clone();
    var e=AuthDeliveryWire.command(lease.command().bytes());AuthDeliveryWire.ack(exactAck,e);bound(e);
    final byte[] validatedAck=exactAck;
    return transaction(c->{
      environment(c);same(c,e);
      try(var s=c.prepareStatement("UPDATE identity.auth_delivery_outbox SET delivery_status='DELIVERED',ack_bytes=?,lease_token=NULL,lease_until=NULL,last_error=NULL WHERE operation_id=? AND consumer=? AND lease_token=? AND lease_until>clock_timestamp() AND delivery_status='PENDING'")){
        s.setBytes(1,validatedAck);s.setObject(2,e.operation());s.setString(3,e.consumer());s.setObject(4,lease.token());return s.executeUpdate()==1;
      }
    });
  }
  public boolean failed(Lease lease,String reason,boolean permanent,int jitter){
    if(reason==null||!reason.matches("[A-Z][A-Z0-9_]{0,63}"))throw new IllegalArgumentException("Bounded error token required");
    long delay=DeliveryStore.retryDelayMillis(lease.attempt(),jitter);var e=AuthDeliveryWire.command(lease.command().bytes());bound(e);
    return transaction(c->{
      environment(c);same(c,e);
      try(var s=c.prepareStatement("UPDATE identity.auth_delivery_outbox SET delivery_status=CASE WHEN ? OR attempts=8 THEN 'PAUSED' ELSE 'PENDING' END,next_attempt_at=clock_timestamp()+(? * interval '1 millisecond'),last_error=?,lease_token=NULL,lease_until=NULL WHERE operation_id=? AND consumer=? AND lease_token=? AND lease_until>clock_timestamp() AND delivery_status='PENDING'")){
        s.setBoolean(1,permanent);s.setLong(2,delay);s.setString(3,reason);s.setObject(4,e.operation());s.setString(5,e.consumer());s.setObject(6,lease.token());return s.executeUpdate()==1;
      }
    });
  }
  private void same(Connection c,AuthDeliveryWire.Command e)throws SQLException{var old=stored(c,e.operation(),e.consumer());if(old==null||!old.envelopeDigest().equals(e.envelopeDigest()))throw deny(409,"AUTH_COMMAND_CONFLICT");}
  private AuthDeliveryWire.Command stored(Connection c,UUID operation,String consumer)throws SQLException{
    try(var s=c.prepareStatement("SELECT command_bytes,envelope_digest FROM identity.auth_delivery_outbox WHERE operation_id=? AND consumer=?")){s.setObject(1,operation);s.setString(2,consumer);try(var r=s.executeQuery()){if(!r.next())return null;var e=AuthDeliveryWire.command(r.getBytes(1));if(!e.envelopeDigest().equals(r.getString(2)))throw deny(503,"STORED_AUTH_DELIVERY_CORRUPT");return e;}}
  }
  private void bound(AuthDeliveryWire.Command e){if(!e.realm().equals(realm)||!e.generation().equals(peers.get(e.consumer())))throw deny(409,"AUTH_DELIVERY_GENERATION_DENIED");}
  private void environment(Connection c)throws SQLException{
    try(var s=c.createStatement();var r=s.executeQuery("SELECT realm_id,db_generation,identity_generation,content_generation,learning_generation,ready,maintenance FROM identity.delivery_environment WHERE singleton FOR SHARE")){
      if(!r.next()||!realm.equals(r.getObject(1,UUID.class))||!generation.equals(r.getObject(2,UUID.class))||!generation.equals(r.getObject(3,UUID.class))||!peers.get("content").equals(r.getObject(4,UUID.class))||!peers.get("learning").equals(r.getObject(5,UUID.class))||!r.getBoolean(6)||r.getBoolean(7))throw deny(503,"DELIVERY_NOT_READY");
    }
  }
  @FunctionalInterface private interface Sql<T>{T run(Connection c)throws SQLException;}
  private <T>T transaction(Sql<T> work){
    try(var c=source.getConnection()){c.setAutoCommit(false);try{try(var s=c.createStatement()){s.execute("SET LOCAL lock_timeout='5000ms'; SET LOCAL statement_timeout='5000ms'; SET LOCAL transaction_timeout='6000ms'");}T value=work.run(c);c.commit();return value;}catch(Exception failure){try{c.rollback();}catch(SQLException ignored){}if(failure instanceof WireCodec.Denied denied)throw denied;throw deny(503,"AUTH_DELIVERY_UNAVAILABLE");}}
    catch(SQLException failure){throw deny(503,"AUTH_DELIVERY_UNAVAILABLE");}
  }
}
