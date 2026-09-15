package com.expleo.simulator;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

import javax.jms.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JmsListenerWorker implements Runnable, MessageListener {

    private final Logger logger;
    private final ExecutorService executor;
    private Connection connection;
    private Session session;
    private Session producerSession;
    private MessageConsumer consumer;
    private MessageProducer producer;
    private Queue outQueue;

    private volatile boolean isRunning = false;
    private final boolean isPerfMode;
    private final String inputQueueName;
    private final String outputQueueName;

    // Metrics for Perf Mode
    private final AtomicInteger msgCount = new AtomicInteger(0);
    private final AtomicInteger errCount = new AtomicInteger(0);
    private int lastCount = 0;
    private int lastErrCount = 0;
    private final SimulatorControlUI uiRef;

    // Idempotency cache to prevent processing duplicate incoming messages
    private static final java.util.Map<String, Boolean> processedMsgIds = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, Boolean>(1000, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> eldest) {
                    return size() > 5000;
                }
            }
    );

    public JmsListenerWorker(SimulatorControlUI uiRef, String inputQueueName, String outputQueueName) {
        this.uiRef = uiRef;
        this.inputQueueName = inputQueueName.trim();
        this.outputQueueName = outputQueueName.trim();
        this.logger = LoggerHelper.getLogger("JmsListener-" + this.inputQueueName);
        this.isPerfMode = Config.getBoolean("high_perf_mode", false);
        this.executor = Executors.newFixedThreadPool(100);
    }

    private void log(String msg) {
        log(msg, false);
    }

    private void log(String msg, boolean isError) {
        if (isError) {
            logger.severe(msg);
        } else {
            logger.info(msg);
        }
        if (uiRef != null) {
            uiRef.appendLog(msg);
        }
    }

    public void startConnections() throws JMSException {
        String host = Config.getJSONObject("mq_config").optString("host", "localhost");
        String port = Config.getJSONObject("mq_config").optString("port", "61616");
        String user = Config.getJSONObject("mq_config").optString("username", "master");
        String pass = Config.getJSONObject("mq_config").optString("password", "master");

        // Simple tcp url for Artemis OpenWire or Core
        String brokerUrl = "tcp://" + host + ":" + port;
        String trustStorePath = Config.getJSONObject("mq_config").optString("trust_store_path", "");
        String trustStorePass = Config.getJSONObject("mq_config").optString("trust_store_pass", "");

        if (!trustStorePath.isEmpty()) {
            brokerUrl += "?sslEnabled=true" +
                    "&trustStorePath=" + trustStorePath +
                    "&trustStorePassword=" + trustStorePass + "&reconnectAttempts=-1" ;
//                    "&keyStorePath=" + trustStorePath +
//                    "&keyStorePassword=" + trustStorePass +
//                    "&verifyHost=false";
        }

        log("Connecting to Expleo PT AMQ at " + brokerUrl + "...");

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        factory.setUser(user);
        factory.setPassword(pass);

        connection = factory.createConnection();
        // Client Acknowledge if not in perf mode. Auto Acknowledge if in perf mode.
        int ackMode = isPerfMode ? Session.AUTO_ACKNOWLEDGE : Session.CLIENT_ACKNOWLEDGE;
        session = connection.createSession(false, ackMode);

        // Dedicated session for producing to avoid thread collision with consumer
        // session
        producerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Queue inQueue = session.createQueue(inputQueueName);
        consumer = session.createConsumer(inQueue);
        consumer.setMessageListener(this);

        outQueue = producerSession.createQueue(outputQueueName);
        producer = producerSession.createProducer(null); // Anonymous producer
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

        connection.start();
        isRunning = true;
        log("Connected successfully. Subscribed to " + inputQueueName + "...");

        if (isPerfMode) {
            log("High-Performance Mode ACTIVE. Disk logging is bypassed.");
            Thread metricsThread = new Thread(this::metricsReporter);
            metricsThread.setDaemon(true);
            metricsThread.start();
        }
    }

    public void stopConnections() {
        isRunning = false;
        log("Shutting down AMQ Listener...");
        executor.shutdown();
        try {
            if (consumer != null)
                consumer.close();
            if (producer != null)
                producer.close();
            if (session != null)
                session.close();
            if (producerSession != null)
                producerSession.close();
            if (connection != null)
                connection.close();
        } catch (Exception ignored) {
        }
        log("AMQ Listener stopped.");
    }

    @Override
    public void onMessage(Message message) {
        if (message instanceof TextMessage) {
            try {
                // Must extract synchronously before passing to executor
                // to prevent use-after-free if AUTO_ACKNOWLEDGE closes the message
                String xmlStr = ((TextMessage) message).getText();
                String correlationId = message.getJMSCorrelationID();
                if (correlationId == null) {
                    correlationId = message.getJMSMessageID();
                }
                final String fCorrId = correlationId;

                executor.submit(() -> processAsync(xmlStr, fCorrId, message));
            } catch (Exception e) {
                log("Error parsing incoming message synchronously: " + e.getMessage(), true);
            }
        } else {
            log("Received non-text message. Ignoring.", true);
            if (!isPerfMode) {
                try {
                    message.acknowledge();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void processAsync(String xmlStr, String correlationId, Message originalMsg) {
        String incomingMsgId = extractMsgId(xmlStr);
        if (!"N/A".equals(incomingMsgId)) {
            if (processedMsgIds.putIfAbsent(incomingMsgId, Boolean.TRUE) != null) {
//                log("Duplicate incoming message detected, ignoring: " + incomingMsgId);
                if (!isPerfMode) {
                    try { originalMsg.acknowledge(); } catch (Exception ignored) {}
                }
                return;
            }
        }
        LocalDateTime receiveTime = LocalDateTime.now();
        try {
            String responseXml = RequestProcessor.processMessageString(xmlStr, logger);

            if (responseXml == null || responseXml.trim().isEmpty()) {
                if (!isPerfMode) {
                    originalMsg.acknowledge();
                }
                return;
            }

            synchronized (producerSession) {
                TextMessage responseMsg = producerSession.createTextMessage(responseXml);
                responseMsg.setJMSCorrelationID(correlationId);

                producer.send(outQueue, responseMsg);
                LocalDateTime sendTime = LocalDateTime.now();
                String outgoingMsgId = extractMsgId(responseXml);
                LoggerHelper.logMessageTiming(receiveTime, sendTime, incomingMsgId, outgoingMsgId);
            }

            if (!isPerfMode) {
                log("Response successfully sent to " + outputQueueName);
                originalMsg.acknowledge();
            } else {
                msgCount.incrementAndGet();
            }

        } catch (Exception e) {
            if (isPerfMode) {
                int errs = errCount.incrementAndGet();
                if (errs <= 5) {
                    log("PerfMode Error processing message: " + e.getMessage(), true);
                }
            } else {
                log("Fatal Error processing message: " + e.getMessage(), true);
            }
        }
    }

    private void metricsReporter() {
        while (isRunning) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
            int current = msgCount.get();
            int errors = errCount.get();

            int diff = current - lastCount;
            int errDiff = errors - lastErrCount;

            if (diff > 0 || errDiff > 0) {
                double tps = diff / 5.0;
                log(String.format("[PERFORMANCE] Processed %d msgs in 5s. (TPS: %.2f) | Total: %d | Errors: %d", diff,
                        tps, current, errors));
            }

            lastCount = current;
            lastErrCount = errors;
        }
    }

    @Override
    public void run() {
        try {
            startConnections();
            while (isRunning) {
                Thread.sleep(1000); // Keep alive
            }
        } catch (Exception e) {
            log("Failed in AMQ listener loop: " + e.getMessage(), true);
            if (uiRef != null) {
                uiRef.stopServer();
            }
        } finally {
            stopConnections();
        }
    }

    private String extractMsgId(String xml) {
        if (xml == null) return "N/A";
        Matcher m = Pattern.compile("(?i)<MsgId>([^<]+)</MsgId>").matcher(xml);
        if (m.find()) {
            return m.group(1);
        }
        return "N/A";
    }
}
