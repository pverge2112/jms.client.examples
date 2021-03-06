package org.activemq.artemis.jms.client;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;

public class ArtemisJNDIGroupClient {

	public static void main(final String[] args) throws Exception {

		final int numMessages = 30;

		Connection connection = null;

		InitialContext initialContext = null;

		try {
			
			// Step 1. Get an initial context for looking up JNDI from the
			// server #1
			initialContext = new InitialContext();

			// Step 2. Look up the JMS resources from JNDI
			Queue queue = (Queue) initialContext.lookup("queue/exampleQueue");
			ConnectionFactory connectionFactory = (ConnectionFactory) initialContext.lookup("ConnectionFactory");

			// Step 3. Create a JMS Connection
			connection = connectionFactory.createConnection();

			// Step 4. Create a *non-transacted* JMS Session with client
			// acknowledgement
			Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

			// Step 5. Start the connection to ensure delivery occurs
			connection.start();

			// Step 6. Create a JMS MessageProducer and a MessageConsumer
			MessageProducer producer = session.createProducer(queue);
			MessageConsumer consumer = session.createConsumer(queue);

			// Step 7. Send some messages to server #1, the live server
			for (int i = 0; i < numMessages; i++) {
				TextMessage message = session.createTextMessage("BatchA: This is text message " + i);
				producer.send(message);
				System.out.println("Sent message: " + message.getText());
			}

			System.out.println("Going to sleep for 20s, please kill -9 master now");
			Thread.sleep(20000);

			for (int i = 0; i < numMessages; i++) {
				TextMessage message = session.createTextMessage("BatchB: This is text message " + i);
				producer.send(message);
				System.out.println("Sent message: " + message.getText());
			}
			
			System.out.println("Going to sleep for 20s, please kill -9 slave now");
			Thread.sleep(20000);

			for (int i = 0; i < numMessages; i++) {
				TextMessage message = session.createTextMessage("BatchC: This is text message " + i);
				producer.send(message);
				System.out.println("Sent message: " + message.getText());
			}
			
			// Step 8. Receive and acknowledge a third of the sent messages
			TextMessage message0 = null;
			for (int i = 0; i < numMessages / 3; i++) {
				message0 = (TextMessage) consumer.receive(5000);
				System.out.println("Got message: " + message0.getText());
			}
			message0.acknowledge();

			// Step 9. Receive the rest third of the sent messages but *do not*
			// acknowledge them yet
			for (int i = numMessages / 3; i < numMessages; i++) {
				message0 = (TextMessage) consumer.receive(5000);
				System.out.println("Got message: " + message0.getText());
			}

			// Step 10. Crash server #0, the live server, and wait a little
			// while to make sure
			// it has really crashed
			Thread.sleep(5000);
			System.out.println("Master was killed");

			// Step 11. Acknowledging the 2nd half of the sent messages will
			// fail as failover to the
			// backup server has occurred
			try {
				message0.acknowledge();
			} catch (JMSException e) {
				System.err.println("Got exception while acknowledging message: " + e.getMessage());
			}

			// Step 12. Consume again the 2nd third of the messages again. Note
			// that they are not considered as redelivered.
			for (int i = numMessages / 3; i < (numMessages / 3) * 2; i++) {
				message0 = (TextMessage) consumer.receive(5000);
				System.out.printf("Got message: %s (redelivered?: %s)%n", message0.getText(),
						message0.getJMSRedelivered());
			}
			message0.acknowledge();

			System.out.println("Slave was killed");

			// Step 11. Acknowledging the 2nd half of the sent messages will
			// fail as failover to the
			// backup server has occurred
			try {
				message0.acknowledge();
			} catch (JMSException e) {
				System.err.println("Got exception while acknowledging message: " + e.getMessage());
			}

			// Step 12. Consume again the 2nd third of the messages again. Note
			// that they are not considered as redelivered.
			for (int i = (numMessages / 3) * 2; i < numMessages; i++) {
				message0 = (TextMessage) consumer.receive(5000);
				System.out.printf("Got message: %s (redelivered?: %s)%n", message0.getText(),
						message0.getJMSRedelivered());
			}
			message0.acknowledge();
			
			Thread.sleep(1000);
			
		} finally {
			// Step 13. Be sure to close our resources!

			if (connection != null) {
				connection.close();
			}

			if (initialContext != null) {
				initialContext.close();
			}

		}
	}
}
