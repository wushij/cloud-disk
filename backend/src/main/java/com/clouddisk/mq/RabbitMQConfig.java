package com.clouddisk.mq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "clouddisk.rabbitmq.enabled", havingValue = "true")
public class RabbitMQConfig {

    public static final String EXCHANGE_MEDIA = "exchange.media";
    public static final String EXCHANGE_NOTIFY = "exchange.notify";

    public static final String QUEUE_THUMBNAIL = "queue.thumbnail";
    public static final String QUEUE_VIDEO_TRANSCODE = "queue.video-transcode";
    public static final String QUEUE_IMAGE_COMPRESS = "queue.image-compress";
    public static final String QUEUE_NOTIFICATION = "queue.notification";

    public static final String ROUTING_KEY_THUMBNAIL = "media.thumbnail";
    public static final String ROUTING_KEY_VIDEO_TRANSCODE = "video.transcode";
    public static final String ROUTING_KEY_IMAGE_COMPRESS = "image.compress";
    public static final String ROUTING_KEY_NOTIFY = "notify.push";

    @Bean
    public DirectExchange mediaExchange() {
        return new DirectExchange(EXCHANGE_MEDIA, true, false);
    }

    @Bean
    public DirectExchange notifyExchange() {
        return new DirectExchange(EXCHANGE_NOTIFY, true, false);
    }

    @Bean
    public Queue thumbnailQueue() {
        return QueueBuilder.durable(QUEUE_THUMBNAIL).build();
    }

    @Bean
    public Queue videoTranscodeQueue() {
        return QueueBuilder.durable(QUEUE_VIDEO_TRANSCODE).build();
    }

    @Bean
    public Queue imageCompressQueue() {
        return QueueBuilder.durable(QUEUE_IMAGE_COMPRESS).build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATION).build();
    }

    @Bean
    public Binding thumbnailBinding(Queue thumbnailQueue, DirectExchange mediaExchange) {
        return BindingBuilder.bind(thumbnailQueue).to(mediaExchange).with(ROUTING_KEY_THUMBNAIL);
    }

    @Bean
    public Binding videoTranscodeBinding(Queue videoTranscodeQueue, DirectExchange mediaExchange) {
        return BindingBuilder.bind(videoTranscodeQueue).to(mediaExchange).with(ROUTING_KEY_VIDEO_TRANSCODE);
    }

    @Bean
    public Binding imageCompressBinding(Queue imageCompressQueue, DirectExchange mediaExchange) {
        return BindingBuilder.bind(imageCompressQueue).to(mediaExchange).with(ROUTING_KEY_IMAGE_COMPRESS);
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue, DirectExchange notifyExchange) {
        return BindingBuilder.bind(notificationQueue).to(notifyExchange).with(ROUTING_KEY_NOTIFY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);
        return factory;
    }
}
