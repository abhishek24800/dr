package com.digitalriver.util.kafka;


import com.digitalriver.cpg.config.AwsParameterStoreUtil;
import com.digitalriver.cpg.logging.Level;
import com.digitalriver.cpg.payment.PaymentDaoFactory;
import com.digitalriver.cpg.payment.PaymentTransaction;
import com.digitalriver.messaging.Message;
import com.digitalriver.messaging.MessageBuilder;
import com.digitalriver.messaging.Publisher;
import com.digitalriver.messaging.PublisherBuilder;
import com.digitalriver.util.aqueduct.AqueductRetry;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import static com.digitalriver.polar.kafka.KafKaProviderType.KAFKA;
import static com.digitalriver.polar.kafka.KafkaPublisherConfigProperty.*;

public class CPGTransactionStatusChangeKafkaPublisher {

    private static Logger logger = Logger.getLogger("support.cpg.kafka");
    private PaymentTransaction pt = null;
    private boolean retryEventFailed;
    static int LOG = 0;
    static boolean isSettleRefund;

    public CPGTransactionStatusChangeKafkaPublisher(PaymentTransaction pt){
        this.pt = pt;
    }

    public Publisher<String> setupKafkaMessagePublisher(String topic) {
        isSettleRefund = null != topic && topic.contains("settleRefund");
        return buildKafkaMessagePublisher(topic);
    }

    protected Publisher<String> buildKafkaMessagePublisher(String topic) {
        Map<String, String> kafkaProperties = this.buildKafkaProperties();
        LOG = kafkaProperties.containsKey("kafka.log") ? Integer.parseInt(kafkaProperties.get("kafka.log")) : 1;
        PublisherBuilder<String> myBuilder = this.buildPublisher();
        if (LOG > 1) logger.log(com.digitalriver.cpg.logging.Level.FINE, "Building publisher for "+ kafkaProperties.get("kafka.bootstrap.server") ,pt);
        final CPGKafkaClusterConfiguration cpgKafkaClusterConfiguration = CPGKafkaClusterConfiguration.getInstance();
        return myBuilder.provider(KAFKA).cluster(cpgKafkaClusterConfiguration.buildClusterManager(kafkaProperties).getName()).
                publisher(this.buildPublisher(topic)).
                config(BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.get("kafka.bootstrap.server")).
                config(TOPIC, topic).
                config(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()).
                config(VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()).
                config(RETRIES_CONFIG, kafkaProperties.get("kafka.producer.retries")).
                config(RECONNECT_BACKOFF_MS_CONFIG, kafkaProperties.get("kafka.producer.reconnect.backoff")).
                config(RETRY_BACKOFF_MS_CONFIG, kafkaProperties.get("kafka.producer.retry.backoff")).
                config(REQUEST_TIMEOUT_MS_CONFIG, kafkaProperties.get("kafka.producer.timeout")).
                build();
    }

    private String buildPublisher(String topic) {
        if (topic != null && topic.contains("authorizeStatusChange")) {
            return "cpgAuthStatusChangePublisher";
        }
        if (topic != null && topic.contains("authenticateStatusChange")) {
            return "cpgAuthenticateStatusChangePublisher";
        }
        if (topic != null && topic.contains("accountUpdateStatusChange")) {
            return "cpgAccountUpdateStatusChangePublisher";
        }
        if (topic != null && isSettleRefund) {
            return "cpgSettleRefundChangePublisher";
        }
        return "CPG";
    }


    public boolean retryFailedKafkaMessages(String request, String topic, Publisher<String> messagePublisher, boolean retry) {
        logger.log(com.digitalriver.cpg.logging.Level.FINE, "Retrying message to Kafka: Topic Name is : " + topic + " Published Kafka request is : " + request, pt);
        this.publishKafkaMessage(request, topic, messagePublisher, retry);
        return retryEventFailed;
    }

    synchronized public void publishKafkaMessage(String request, String topic, Publisher<String> messagePublisher, boolean retry) {
        try {
            if (StringUtils.isNotBlank(request)) {
                logger.log(com.digitalriver.cpg.logging.Level.FINE, "Publishing message to Kafka: Topic Name is : " + topic + " Published Kafka request is : " + request, pt);
                final MessageBuilder<String> msgBuilder = messagePublisher.getMessageBuilder();
                final Message<String> keyedMessage = msgBuilder.payload(request).build();
                messagePublisher.publish(keyedMessage);
                if (LOG > 1) logger.log(com.digitalriver.cpg.logging.Level.FINE, "Keyed Message {0} published to Kafka!!!" + keyedMessage, pt);
            } else {
                logger.log(com.digitalriver.cpg.logging.Level.FINE, "Nothing to publish for topic" + topic + "Kafka request is empty.", pt);
            }
        } catch (Exception exception) {
            logger.log(com.digitalriver.cpg.logging.Level.SEVERE, "ERROR - Failed to publish message: " + exception.getMessage(), new Object[] {exception,request,pt});
            if (!(isSettleRefund) && !retry) {
                AqueductRetry aqueductRetry = new AqueductRetry();
                aqueductRetry.saveFailedAqueductEvent(request, topic, pt);
            } else if (isSettleRefund) {
                throw new RuntimeException(exception);
            } else {
                retryEventFailed = true;
            }
        }
    }

    protected PublisherBuilder<String> buildPublisher() {
        return new PublisherBuilder<>();
    }

    protected Map<String, String> buildKafkaProperties() {
        Properties config = PaymentDaoFactory.getCachedGlobalConfig();
        Map<String, String> kafkaProperties = new HashMap<>();
        if (config != null ) {
            kafkaProperties.put("kafka.bootstrap.server", this.getKafkaBootstrapServer(config,isSettleRefund));
            kafkaProperties.put("kafka.producer.timeout", config.getProperty("kafka.producer.timeout"));
            kafkaProperties.put("kafka.producer.retries", config.getProperty("kafka.producer.retries"));
            kafkaProperties.put("kafka.producer.reconnect.backoff", config.getProperty("kafka.producer.reconnect.backoff"));
            kafkaProperties.put("kafka.producer.retry.backoff", config.getProperty("kafka.producer.retry.backoff"));
            kafkaProperties.put("kafka.log", config.getProperty("kafka.log", "1"));
        }
        return kafkaProperties;
    }

    private String getKafkaBootstrapServer(Properties config, boolean isSettleRefund) {
        return isSettleRefund ?
                config.getProperty("kafka.settle-refund.bootstrap.server.vdc3", "kfkbrokersysvdc3.query.consul-nprd.lb.drsvcs.zone:7666")
                : config.getProperty("kafka.bootstrap.server.vdc3", "kfkbrokersysvdc3.query.consul-nprd.lb.drsvcs.zone:7666");
    }

}
