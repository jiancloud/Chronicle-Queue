/*
 * Copyright 2016 higherfrequencytrading.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package net.openhft.chronicle.queue.micros;

import net.openhft.chronicle.bytes.MethodReader;
import net.openhft.chronicle.core.OS;
import net.openhft.chronicle.core.io.IOTools;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.MessageHistory;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/*
 * Created by Peter Lawrey on 24/03/16.
 */
public class OrderManagerTest {

    @Test
    public void testOnOrderIdea() {
        // what we expect to happen
        OrderListener listener = createMock(OrderListener.class);
        listener.onOrder(new Order("EURUSD", Side.Buy, 1.1167, 1_000_000));
        replay(listener);

        // build our scenario
        OrderManager orderManager = new OrderManager(listener);
        SidedMarketDataCombiner combiner = new SidedMarketDataCombiner(orderManager);

        // events in
        orderManager.onOrderIdea(new OrderIdea("EURUSD", Side.Buy, 1.1180, 2e6)); // not expected to trigger

        combiner.onSidedPrice(new SidedPrice("EURUSD", 123456789000L, Side.Sell, 1.1172, 2e6));
        combiner.onSidedPrice(new SidedPrice("EURUSD", 123456789100L, Side.Buy, 1.1160, 2e6));

        combiner.onSidedPrice(new SidedPrice("EURUSD", 123456789100L, Side.Buy, 1.1167, 2e6));

        orderManager.onOrderIdea(new OrderIdea("EURUSD", Side.Buy, 1.1165, 1e6)); // expected to trigger

        verify(listener);
    }

    @Test
    public void testWithQueue() {
        File queuePath = new File(OS.TARGET, "testWithQueue-" + System.nanoTime());
        try {
            try (SingleChronicleQueue queue = SingleChronicleQueueBuilder.binary(queuePath).testBlockSize().build()) {
                OrderIdeaListener orderManager = queue.acquireAppender().methodWriter(OrderIdeaListener.class, MarketDataListener.class);
                SidedMarketDataCombiner combiner = new SidedMarketDataCombiner((MarketDataListener) orderManager);

                // events in
                orderManager.onOrderIdea(new OrderIdea("EURUSD", Side.Buy, 1.1180, 2e6)); // not expected to trigger

                combiner.onSidedPrice(new SidedPrice("EURUSD", 123456789000L, Side.Sell, 1.1172, 2e6));
                combiner.onSidedPrice(new SidedPrice("EURUSD", 123456789100L, Side.Buy, 1.1160, 2e6));

                combiner.onSidedPrice(new SidedPrice("EURUSD", 123456789100L, Side.Buy, 1.1167, 2e6));

                orderManager.onOrderIdea(new OrderIdea("EURUSD", Side.Buy, 1.1165, 1e6)); // expected to trigger
            }

// what we expect to happen
            OrderListener listener = createMock(OrderListener.class);
            listener.onOrder(new Order("EURUSD", Side.Buy, 1.1167, 1_000_000));
            replay(listener);

            try (SingleChronicleQueue queue = SingleChronicleQueueBuilder.binary(queuePath).testBlockSize().build()) {
                // build our scenario
                OrderManager orderManager = new OrderManager(listener);
                MethodReader reader = queue.createTailer().methodReader(orderManager);
                for (int i = 0; i < 5; i++)
                    assertTrue(reader.readOne());

                assertFalse(reader.readOne());
//                System.out.println(queue.dump());
            }

            verify(listener);
        } finally {
            try {
                IOTools.shallowDeleteDirWithFiles(queuePath);
            } catch (Exception e) {
            }
        }
    }

    /*
    When all the tests are run gets
[main] WARN net.openhft.chronicle.queue.micros.OrderManagerTest$$Lambda$478/807322507 - Failure to dispatch message: onOrder [!Order {
  symbol: EURUSD,
  side: Buy,
  limitPrice: 1.1167,
  quantity: 1E6
}
]
java.lang.AssertionError: expected:<4> but was:<7>
	at org.junit.Assert.fail(Assert.java:88)
	at org.junit.Assert.failNotEquals(Assert.java:834)
	at org.junit.Assert.assertEquals(Assert.java:645)
	at org.junit.Assert.assertEquals(Assert.java:631)
	at net.openhft.chronicle.queue.micros.OrderManagerTest.lambda$testWithQueueHistory$0(OrderManagerTest.java:163)
	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.lang.reflect.Method.invoke(Method.java:498)
	at net.openhft.chronicle.wire.MethodReader.invoke(MethodReader.java:284)
	at net.openhft.chronicle.wire.MethodReader.lambda$addParseletForMethod$3(MethodReader.java:164)
	at net.openhft.chronicle.wire.WireParser.parseOne(WireParser.java:41)
	at net.openhft.chronicle.wire.WireParser.accept(WireParser.java:49)
	at net.openhft.chronicle.wire.MethodReader.readOne(MethodReader.java:311)
	at net.openhft.chronicle.queue.micros.OrderManagerTest.testWithQueueHistory(OrderManagerTest.java:165)
     */
    @Ignore("TODO FIX")
    @Test
    public void testWithQueueHistory() {
        File queuePath = new File(OS.TARGET, "testWithQueueHistory-" + System.nanoTime());
        File queuePath2 = new File(OS.TARGET, "testWithQueueHistory-down-" + System.nanoTime());
        try {
            try (SingleChronicleQueue out = SingleChronicleQueueBuilder.binary(queuePath).testBlockSize().build()) {
                OrderIdeaListener orderManager = out.acquireAppender()
                        .methodWriterBuilder(OrderIdeaListener.class)
                        .addInterface(MarketDataListener.class)
                        .recordHistory(true)
                        .get();
                SidedMarketDataCombiner combiner = new SidedMarketDataCombiner((MarketDataListener) orderManager);

                // events in
                orderManager.onOrderIdea(new OrderIdea("EURUSD", Side.Buy, 1.1180, 2e6)); // not expected to trigger

                combiner.onSidedPrice(new SidedPrice("EURUSD", 123456789000L, Side.Sell, 1.1172, 2e6));
                combiner.onSidedPrice(new SidedPrice("EURUSD", 123456789100L, Side.Buy, 1.1160, 2e6));

                combiner.onSidedPrice(new SidedPrice("EURUSD", 123456789100L, Side.Buy, 1.1167, 2e6));

                orderManager.onOrderIdea(new OrderIdea("EURUSD", Side.Buy, 1.1165, 1e6)); // expected to trigger
            }

            try (SingleChronicleQueue in = SingleChronicleQueueBuilder.binary(queuePath)
                    .testBlockSize()
                    .sourceId(1)
                    .build();
                 SingleChronicleQueue out = SingleChronicleQueueBuilder.binary(queuePath2).testBlockSize().build()) {

                OrderListener listener = out.acquireAppender()
                        .methodWriterBuilder(OrderListener.class)
                        .recordHistory(true)
                        .get();
                // build our scenario
                OrderManager orderManager = new OrderManager(listener);
                MethodReader reader = in.createTailer().methodReader(orderManager);
                for (int i = 0; i < 5; i++)
                    assertTrue(reader.readOne());

                assertFalse(reader.readOne());
//                System.out.println(out.dump());
            }

            try (SingleChronicleQueue in = SingleChronicleQueueBuilder.binary(queuePath2).testBlockSize().sourceId(2).build()) {
                MethodReader reader = in.createTailer().methodReader((OrderListener) order -> {
                    MessageHistory x = MessageHistory.get();
                    // Note: this will have one extra timing, the time it was written to the console.
                    System.out.println(x);
                    assertEquals(1, x.sourceId(0));
                    assertEquals(2, x.sourceId(1));
                    assertEquals(4, x.timings());
                });
                assertTrue(reader.readOne());
                assertFalse(reader.readOne());
            }
        } finally {
            try {
                IOTools.shallowDeleteDirWithFiles(queuePath);
                IOTools.shallowDeleteDirWithFiles(queuePath2);
            } catch (Exception e) {
            }
        }
    }

    /*
    Fails when all the tests are run, but not this test alone.
     */
    @Ignore("TODO FIX")
    @Test
    public void testRestartingAService() {
        File queuePath = new File(OS.TARGET, "testRestartingAService-" + System.nanoTime());
        File queuePath2 = new File(OS.TARGET, "testRestartingAService-down-" + System.nanoTime());
        try {

            try (SingleChronicleQueue out = SingleChronicleQueueBuilder.binary(queuePath)
                    .testBlockSize()
                    .rollCycle(RollCycles.TEST_DAILY)
                    .build()) {
                SidedMarketDataListener combiner = out.acquireAppender()
                        .methodWriterBuilder(SidedMarketDataListener.class)
                        .recordHistory(true)
                        .get();

                combiner.onSidedPrice(new SidedPrice("EURUSD1", 123456789000L, Side.Sell, 1.1172, 2e6));
                combiner.onSidedPrice(new SidedPrice("EURUSD2", 123456789100L, Side.Buy, 1.1160, 2e6));

                for (int i = 2; i < 10; i += 2) {
                    combiner.onSidedPrice(new SidedPrice("EURUSD3", 123456789100L, Side.Sell, 1.1173, 2.5e6));
                    combiner.onSidedPrice(new SidedPrice("EURUSD4", 123456789100L, Side.Buy, 1.1167, 1.5e6));
                }
            }

            // TODO FIx for more.
            for (int i = 0; i < 10; i++) {
                // read one message at a time
                try (SingleChronicleQueue in = SingleChronicleQueueBuilder.binary(queuePath)
                        .testBlockSize()
                        .sourceId(1)
                        .build();
                     SingleChronicleQueue out = SingleChronicleQueueBuilder.binary(queuePath2)
                             .testBlockSize()
                             .rollCycle(RollCycles.TEST_DAILY)
                             .build()) {

                    ExcerptAppender excerptAppender = out.acquireAppender();
                    MarketDataListener mdListener = excerptAppender
                            .methodWriterBuilder(MarketDataListener.class)
                            .recordHistory(true)
                            .get();
                    SidedMarketDataCombiner combiner = new SidedMarketDataCombiner(mdListener);
                    ExcerptTailer tailer = in.createTailer()
                            .afterLastWritten(out);
                    assertEquals(i, in.rollCycle().toSequenceNumber(tailer.index()));
                    MethodReader reader = tailer
                            .methodReader(combiner);

//                    System.out.println("#### IN\n" + in.dump());
//                    System.out.println("#### OUT:\n" + out.dump());
                    assertTrue("i: " + i, reader.readOne());
                }
            }
        } finally {
            try {
                IOTools.shallowDeleteDirWithFiles(queuePath);
                IOTools.shallowDeleteDirWithFiles(queuePath2);
            } catch (Exception e) {
            }
        }
    }
}