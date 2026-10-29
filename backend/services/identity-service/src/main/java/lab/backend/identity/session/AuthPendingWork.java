package lab.backend.identity.session;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

/** Quét ID của ba pha worker, không cấp quyền xử lý hoặc đánh dấu hoàn tất.
 * Mỗi pha có cursor riêng và high-water mark (ID lớn nhất khi bắt đầu vòng).
 * Nhờ vậy hơn16 ID lỗi không giữ mãi đầu hàng đợi; IDs mới đợi vòng kế tiếp.
 * Cursor chỉ ở RAM: restart được quét lại, còn idempotency/lease/ACK vẫn ở DB. */
public final class AuthPendingWork {
  public enum Phase {
    TRANSITIONS("SELECT t.operation_id FROM identity.credential_transition t WHERE t.state='PENDING' AND NOT EXISTS (SELECT 1 FROM identity.auth_operation o WHERE o.operation_id=t.operation_id)","t.operation_id",false),
    RECONCILE("SELECT o.operation_id FROM identity.auth_operation o WHERE o.state='PENDING_FENCES' AND o.realm_id=? AND (SELECT count(*) FROM identity.auth_delivery_outbox d WHERE d.operation_id=o.operation_id)<2","o.operation_id",true),
    COMPLETE("SELECT o.operation_id FROM identity.auth_operation o WHERE o.state='PENDING_FENCES' AND o.realm_id=? AND (SELECT count(*) FROM identity.auth_delivery_outbox d WHERE d.operation_id=o.operation_id AND d.delivery_status='DELIVERED')=2","o.operation_id",true);

    final String query,column;final boolean realmScoped;
    Phase(String query,String column,boolean realmScoped){this.query=query;this.column=column;this.realmScoped=realmScoped;}
  }
  private record Window(UUID highWater,UUID after){}
  private final DataSource source;
  private final UUID realm;
  private final EnumMap<Phase,Window> windows=new EnumMap<>(Phase.class);

  public AuthPendingWork(DataSource source,UUID realm){this.source=Objects.requireNonNull(source);this.realm=Objects.requireNonNull(realm);}

  /** Trả tối đa16 ID, đóng connection trước khi caller reconcile/network/lock.
   * Cursor tiến sau khi đọc thành công, KHÔNG chờ effect thành công: ID poison
   * được giữ pending và thử lại vòng sau, không bỏ đói các ID phía sau.
   * SQL lỗi không tiến cursor; tail đã biến mất kết thúc vòng, lần gọi sau wrap. */
  public synchronized List<UUID> next(Phase phase)throws SQLException {
    Objects.requireNonNull(phase);Window window=windows.get(phase);
    if(window==null){
      var highest=read(phase,null,true);if(highest.isEmpty())return List.of();
      window=new Window(highest.getFirst(),null);windows.put(phase,window);
    }
    var ids=read(phase,window,false);
    if(ids.isEmpty()||ids.getLast().equals(window.highWater))windows.remove(phase);
    else windows.put(phase,new Window(window.highWater,ids.getLast()));
    return List.copyOf(ids);
  }

  private List<UUID> read(Phase phase,Window window,boolean highest)throws SQLException {
    // SQL chỉ từ enum nội bộ; không nối input HTTP. ORDER BY dùng thứ tự UUID
    // của PostgreSQL, không Java UUID.compareTo (khác thứ tự ở bit dấu).
    String sql=phase.query+(highest?" ORDER BY "+phase.column+" DESC LIMIT 1"
        :" AND (?::uuid IS NULL OR "+phase.column+">?::uuid) AND "+phase.column+"<=?::uuid ORDER BY "+phase.column+" LIMIT 16");
    var ids=new ArrayList<UUID>();
    try(var connection=source.getConnection();var statement=connection.prepareStatement(sql)){
      int index=1;if(phase.realmScoped)statement.setObject(index++,realm);
      if(!highest){statement.setObject(index++,window.after,Types.OTHER);statement.setObject(index++,window.after,Types.OTHER);statement.setObject(index,window.highWater,Types.OTHER);}
      statement.setQueryTimeout(1);
      try(var rows=statement.executeQuery()){while(rows.next())ids.add(rows.getObject(1,UUID.class));}
    }
    return ids;
  }
  @Override public String toString(){return "AuthPendingWork[scanOnly,ownIdentity]";}
}
