package lab.backend.learning.contentreplica;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/** Local shared/exclusive gate. Network download must finish before exclusive() is entered. */
public final class PublicationGate {
  private final ReentrantReadWriteLock lock=new ReentrantReadWriteLock(true);
  public <T>T contentSensitive(Supplier<T> callback){var l=lock.readLock();l.lock();try{return callback.get();}finally{l.unlock();}}
  public <T>T exclusive(Supplier<T> callback){var l=lock.writeLock();l.lock();try{return callback.get();}finally{l.unlock();}}
}
