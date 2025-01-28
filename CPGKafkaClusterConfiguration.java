package com.digitalriver.util.kafka;

import com.digitalriver.config.ConfigProperties;
import com.digitalriver.messaging.Polar;
import com.digitalriver.polar.kafka.KafKaProviderType;
import com.digitalriver.polar.kafka.KafkaClusterManager;
import com.digitalriver.polar.kafka.KafkaPublisherConfigProperty;
import com.digitalriver.polar.kafka.spi.KafkaProvider;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.util.Map;
import java.util.logging.Logger;

import static com.digitalriver.polar.kafka.KafkaPublisherConfigProperty.KEY_SERIALIZER_CLASS_CONFIG;
import static com.digitalriver.polar.kafka.KafkaPublisherConfigProperty.VALUE_SERIALIZER_CLASS_CONFIG;

public class CPGKafkaClusterConfiguration {

    public static CPGKafkaClusterConfiguration CPGKafkaClusterConfiguration = null;
    private KafkaClusterManager clusterMgr = null;
    private static Logger logger = Logger.getLogger("support.cpg.kafka");

    public static synchronized CPGKafkaClusterConfiguration getInstance() {
        if (CPGKafkaClusterConfiguration == null) {
            CPGKafkaClusterConfiguration = new CPGKafkaClusterConfiguration();
        }
        return CPGKafkaClusterConfiguration;
    }

    public synchronized KafkaClusterManager buildClusterManager(Map<String, String> kafkaProperties)  {
        if(this.clusterMgr == null) {
            KafkaProvider kafkaProvider = (KafkaProvider) Polar.getPolarProvider(KafKaProviderType.KAFKA);
            ConfigProperties kafkaProviderDefaultClusterProperties = kafkaProvider.getDefaultClusterProperties();
            kafkaProviderDefaultClusterProperties.updateProperty(KafkaPublisherConfigProperty.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.get("kafka.bootstrap.server"));
            kafkaProviderDefaultClusterProperties.updateProperty(KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
            kafkaProviderDefaultClusterProperties.updateProperty(VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
            this.clusterMgr = kafkaProvider.createClusterManager("cpg-kafka-publisher", kafkaProviderDefaultClusterProperties);
        }
        return clusterMgr;
    }

}

