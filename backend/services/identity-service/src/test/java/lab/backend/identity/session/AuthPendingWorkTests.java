package lab.backend.identity.session;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** JDBC doubles kiểm cursor/budget/resource boundary, không là own-PG/worker
 * runtime proof. Scripted pages độc lập với production SQL để không chỉ copy
 * thuật toán query; actual PostgreSQL phải kiểm SQL/order/filter ở lớp IT. */
class AuthPendingWorkTests {
  static final UUID REALM=UUID.randomUUID(),HIGH=id(18);
  final DataSource source=mock(DataSource.class);
  final Deque<Object> pages=new ArrayDeque<>();
  final List<PreparedStatement> statements=new ArrayList<>();
  final List<Connection> connections=new ArrayList<>();
  final List<ResultSet> results=new ArrayList<>();
  final List<String> queries=new ArrayList<>();
  AuthPendingWork work;
  static UUID id(int value){return UUID.fromString(String.format("00000000-0000-4000-8000-%012x",value));}
  static List<UUID> batch(){return IntStream.rangeClosed(1,16).mapToObj(AuthPendingWorkTests::id).toList();}
  @BeforeEach void setup()throws Exception {
    when(source.getConnection()).thenAnswer(open->{
      Connection connection=mock(Connection.class);connections.add(connection);
      when(connection.prepareStatement(anyString())).thenAnswer(prepare->{
        queries.add(prepare.getArgument(0));PreparedStatement statement=mock(PreparedStatement.class);statements.add(statement);
        Object page=pages.removeFirst();
        if(page instanceof SQLException failure)when(statement.executeQuery()).thenThrow(failure);
        else{
          @SuppressWarnings("unchecked") List<UUID> ids=(List<UUID>)page;
          ResultSet rows=mock(ResultSet.class);results.add(rows);var position=new AtomicInteger(-1);
          when(rows.next()).thenAnswer(next->position.incrementAndGet()<ids.size());
          when(rows.getObject(1,UUID.class)).thenAnswer(get->ids.get(position.get()));when(statement.executeQuery()).thenReturn(rows);
        }
        return statement;
      });return connection;
    });work=new AuthPendingWork(source,REALM);
  }
  /** Không cần báo effect success để tiến scan:16poison đầu vẫn pending, rồi
   * page tiếp tới poison17 + good18; vòng sau lại retry đầu mà không xóa ID. */
  @ParameterizedTest @EnumSource(AuthPendingWork.Phase.class)
  void poisonBatchCannotHideLaterWork(AuthPendingWork.Phase phase)throws Exception {
    pages.add(List.of(HIGH));pages.add(batch());pages.add(List.of(id(17),HIGH));pages.add(List.of(HIGH));pages.add(batch());
    assertThat(work.next(phase)).containsExactlyElementsOf(batch());
    assertThat(work.next(phase)).containsExactly(id(17),HIGH);
    assertThat(work.next(phase)).containsExactlyElementsOf(batch());
    int offset=phase==AuthPendingWork.Phase.TRANSITIONS?0:1;
    verify(statements.get(2)).setObject(2+offset,id(16),Types.OTHER);
    verify(statements.get(2)).setObject(3+offset,HIGH,Types.OTHER);
    if(offset==1)for(var statement:statements)verify(statement).setObject(1,REALM);
    assertThat(queries.get(0)).endsWith("DESC LIMIT 1");assertThat(queries.get(2)).endsWith("LIMIT 16");
  }
  @Test void phasesKeepIndependentWindows()throws Exception {
    pages.add(List.of(HIGH));pages.add(batch());pages.add(List.of(HIGH));pages.add(List.of(id(1)));pages.add(List.of(id(17),HIGH));
    work.next(AuthPendingWork.Phase.TRANSITIONS);work.next(AuthPendingWork.Phase.COMPLETE);work.next(AuthPendingWork.Phase.TRANSITIONS);
    verify(statements.get(3)).setObject(3,null,Types.OTHER);verify(statements.get(4)).setObject(2,id(16),Types.OTHER);
  }
  @Test void arrivalsWaitForNextBoundedRound()throws Exception {
    pages.add(List.of(HIGH));pages.add(batch());pages.add(List.of(id(17),HIGH));pages.add(List.of(id(19)));pages.add(List.of(id(19)));
    work.next(AuthPendingWork.Phase.RECONCILE);work.next(AuthPendingWork.Phase.RECONCILE);
    assertThat(work.next(AuthPendingWork.Phase.RECONCILE)).containsExactly(id(19));
    verify(statements.get(2)).setObject(4,HIGH,Types.OTHER);verify(statements.get(4)).setObject(4,id(19),Types.OTHER);
  }
  @Test void disappearedTailEndsRoundWithoutSkippingNextRound()throws Exception {
    pages.add(List.of(HIGH));pages.add(batch());pages.add(List.of());pages.add(List.of(id(1)));pages.add(List.of(id(1)));
    work.next(AuthPendingWork.Phase.TRANSITIONS);assertThat(work.next(AuthPendingWork.Phase.TRANSITIONS)).isEmpty();
    assertThat(work.next(AuthPendingWork.Phase.TRANSITIONS)).containsExactly(id(1));verify(statements.get(4)).setObject(2,null,Types.OTHER);
  }
  @Test void sqlFailureDoesNotAdvanceOrRetargetWindow()throws Exception {
    pages.add(List.of(HIGH));pages.add(new SQLException("fixture unavailable"));pages.add(batch());
    assertThatThrownBy(()->work.next(AuthPendingWork.Phase.COMPLETE)).isInstanceOf(SQLException.class);
    assertThat(work.next(AuthPendingWork.Phase.COMPLETE)).containsExactlyElementsOf(batch());
    verify(statements.get(2)).setObject(3,null,Types.OTHER);verify(statements.get(2)).setObject(4,HIGH,Types.OTHER);
    verify(connections.get(1)).close();verify(statements.get(1)).close();
  }
  @Test void snapshotClosesBeforeEffectsAndCannotBeRewritten()throws Exception {
    pages.add(List.of(id(1)));pages.add(List.of(id(1)));var ids=work.next(AuthPendingWork.Phase.TRANSITIONS);
    assertThatThrownBy(()->ids.add(HIGH)).isInstanceOf(UnsupportedOperationException.class);
    for(var connection:connections)verify(connection).close();for(var result:results)verify(result).close();
    for(var statement:statements){verify(statement).close();verify(statement).setQueryTimeout(1);}
  }
  @Test void emptyAndMissingInputsCannotInventPendingWork()throws Exception {
    pages.add(List.of());assertThat(work.next(AuthPendingWork.Phase.COMPLETE)).isEmpty();assertThat(statements).hasSize(1);
    assertThatThrownBy(()->new AuthPendingWork(null,REALM)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(()->new AuthPendingWork(source,null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(()->work.next(null)).isInstanceOf(NullPointerException.class);
  }
}
