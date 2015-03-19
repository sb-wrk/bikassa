
package com.eb.warehouse.io.socket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.inject.Provider;

import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Stubber;

import com.eb.warehouse.io.socket.RecurringConnectSocketTask;
import com.eb.warehouse.util.ThreadDelegator;
import com.google.common.util.concurrent.MoreExecutors;

public class RecurringConnectSocketTaskTest {

  private Socket fakeSocket;
  private Provider<Socket> socketProvider;
  private ThreadDelegator mockThread;
  private InetSocketAddress anyAddress;
  private RecurringConnectSocketTask task;

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    fakeSocket = mock(Socket.class);
    socketProvider = mock(Provider.class);
    when(socketProvider.get()).thenReturn(fakeSocket);
    mockThread = mock(ThreadDelegator.class);
    anyAddress = new InetSocketAddress(0);
    task = new RecurringConnectSocketTask(socketProvider, anyAddress, mockThread);
  }

  @Test
  public void call_connectsSocketSuccessfullyAtFirstAttempt() throws Exception {
    assertEquals(fakeSocket, task.call());
    verify(socketProvider).get();
    verify(fakeSocket).connect(eq(anyAddress));
    verify(fakeSocket, times(0)).close();
    verify(mockThread, times(0)).sleepCurrentThread(anyInt(), any(TimeUnit.class));
  }

  @Test
  public void call_connectsSocketSuccessfullyAtTenthAttempt() throws Exception {
    connectSuccessfullyAtNthAttempt(10);
  }

  @Test
  public void call_connectsSocketSuccessfullyAt999ThAttempt() throws Exception {
    connectSuccessfullyAtNthAttempt(999);
  }

  private void connectSuccessfullyAtNthAttempt(int attempts) throws Exception {
    IOException e = new IOException();
    Stubber doThrowBuilder = doThrow(e);
    for (int i = 0; i < attempts - 2; i++) {
      doThrowBuilder.doThrow(e);
    }
    doThrowBuilder.doNothing().when(fakeSocket).connect(any(InetSocketAddress.class));
    assertEquals(fakeSocket, task.call());
    verify(socketProvider, times(attempts)).get();
    verify(fakeSocket, times(attempts)).connect(eq(anyAddress));
    verify(fakeSocket, times(attempts - 1)).close();
    verify(mockThread, times(attempts - 1)).sleepCurrentThread(anyInt(), any(TimeUnit.class));
  }

  @Test
  public void call_throwsInterruptedExceptionBecauseSleepThrowsInterruptedException() throws Exception {
    doThrow(new InterruptedException()).when(mockThread).sleepCurrentThread(anyInt(), any(TimeUnit.class));
    doThrow(new IOException()).when(fakeSocket).connect(any(InetSocketAddress.class));
    try {
      task.call();
      fail();
    } catch (InterruptedException e) {
      assertNotNull(e);
    }
    verify(socketProvider).get();
    verify(fakeSocket).connect(eq(anyAddress));
    verify(fakeSocket).close();
    verify(mockThread).sleepCurrentThread(anyInt(), any(TimeUnit.class));
  }

  @Test
  public void cancelTask_futureClosesSocketWhileConnectBlocksThread() throws Exception {
    doThrow(new IOException()).when(fakeSocket).connect(any(InetSocketAddress.class));
    when(mockThread.isCurrentThreadInterrupted(anyBoolean())).thenReturn(true);
    Future<Socket> f = task.submitTo(MoreExecutors.newDirectExecutorService());
    f.cancel(false); // 
    verify(fakeSocket, times(2)).close();
  }
}

//---------------------------- Revision History ----------------------------
//$Log$
//