//=============================================================================
// HornetQ-Test
//
// @description: Module for providing functions to work with Consumer objects
// @author: Elisha Lai
// @version: 1.0 27/06/2017
//=============================================================================

package com.elishalai;

import org.hornetq.api.core.HornetQBuffer;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSession.QueueQuery;
import org.hornetq.api.core.client.ClientSessionFactory;

public class ConsumerClient extends BaseClient {
  private static final String QUEUE_NAME = "testQueue";

  private static int numMessages = -1;
  private static Logger latencyLogWriter = null;
  private static Logger throughputLogWriter = null;
  private ClientSession session = null;

  public static void main(String[] arguments) throws Exception {
    try {
      String serverAddress = arguments[0];
      int serverPort = Integer.parseInt(arguments[1]);
      numMessages = Integer.parseInt(arguments[2]);
      
      latencyLogWriter = new Logger("latency.csv");
      latencyLogWriter.logLatencyLogHeader();

      throughputLogWriter = new Logger("consumer-throughput.csv");
      throughputLogWriter.logThroughputLogHeader();
      
      new ConsumerClient(serverAddress, serverPort).run();
      System.out.println("Consumer executed successfully.");
    } catch (Exception exception) {
      System.out.println("Consumer wasn't able to execute successfully. An error has occurred.");
      exception.printStackTrace();
    } finally {
      if (latencyLogWriter != null) {
        latencyLogWriter.close();
      }

      if (throughputLogWriter != null) {
        throughputLogWriter.close();
      }
    }
  }

  // Constructor
  private ConsumerClient(String serverAddress, int serverPort) {
    super(serverAddress, serverPort);
  }

  // Receive messages from the server
  private void run() throws Exception {
    try {
      // Initialize the base client
      initialize();

      // Create a session to check if the queue exists
      session = getSessionFactory().createSession(false, false, false);

      // Check if the queue exists. If not, create the queue
      SimpleString simpleString = new SimpleString(QUEUE_NAME);
      QueueQuery queueQuery = session.queueQuery(simpleString);
      if (!queueQuery.isExists()) {
        session.createQueue(QUEUE_NAME, QUEUE_NAME, true);
      }

      session.close();
      session = null;

      // Create a session to consume messages
      session = getSessionFactory().createSession();

      // Create a consumer to consume messages from the queue
      ClientConsumer consumer = session.createConsumer(QUEUE_NAME);

      session.start();
      
      // Consume messages from the queue
      int messagesCount = 0;
      long duration = 0;
      while (messagesCount < numMessages) {
        long startTime = System.currentTimeMillis();
        ClientMessage message = consumer.receive();
        long endTime = System.currentTimeMillis();

        if (message != null) {
          message.acknowledge();
          messagesCount += 1;
          duration += (endTime - startTime);

          int messageID = messagesCount;
          long sentTimestamp = getSentTimestamp(message);
          long receivedTimestamp = System.currentTimeMillis();
          long latency = receivedTimestamp - sentTimestamp;
          latencyLogWriter.logLatencyLogEntry(messageID, sentTimestamp,
            receivedTimestamp, latency);
        }
      }

      session.stop();

      // Calculate the throughput of the consumer
      double throughput = calculateThroughput(numMessages, duration);
      throughputLogWriter.logThroughputLogEntry(numMessages, duration, throughput);
    } catch (Exception exception) {
      throw exception;
    } finally {
      if (session != null) {
        session.close();
      }

      // Clean up the base client
      cleanup();
    }
  }

  // Get the sent timestamp from the message
  long getSentTimestamp(ClientMessage message) throws Exception {
    HornetQBuffer buffer = message.getBodyBuffer();
    long sentTimestamp = buffer.readLong();

    return sentTimestamp;
  }
}
