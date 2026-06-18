package com.clouddisk.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "clouddisk.rabbitmq.enabled", havingValue = "true")
public class MediaMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendThumbnailTask(MediaMessage message) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_MEDIA, RabbitMQConfig.ROUTING_KEY_THUMBNAIL, message);
    }

    public void sendVideoTranscodeTask(MediaMessage message) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_MEDIA, RabbitMQConfig.ROUTING_KEY_VIDEO_TRANSCODE, message);
    }

    public void sendImageCompressTask(MediaMessage message) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_MEDIA, RabbitMQConfig.ROUTING_KEY_IMAGE_COMPRESS, message);
    }

    public void sendNotification(NotificationMessage message) {
        log.debug("发送通知: userId={} type={}", message.getUserId(), message.getType());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NOTIFY, RabbitMQConfig.ROUTING_KEY_NOTIFY, message);
    }
}
