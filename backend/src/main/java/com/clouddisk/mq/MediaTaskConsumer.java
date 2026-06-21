package com.clouddisk.mq;

import com.clouddisk.media.ImageCompressService;
import com.clouddisk.media.MediaProcessService;
import com.clouddisk.media.VideoProcessService;
import com.clouddisk.service.NotificationService;
import com.clouddisk.service.ThumbnailService;
import com.clouddisk.websocket.UploadProgressHandler;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "clouddisk.rabbitmq.enabled", havingValue = "true")
public class MediaTaskConsumer {

    private final ThumbnailService thumbnailService;
    private final VideoProcessService videoProcessService;
    private final ImageCompressService imageCompressService;
    private final NotificationService notificationService;
    private final UploadProgressHandler progressHandler;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_THUMBNAIL)
    public void onThumbnail(MediaMessage message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag)
            throws Exception {
        try {
            if (message.getFileType() != null && message.getFileType().startsWith("image/")) {
                thumbnailService.generate(message.getFileId());
            }
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("缩略图队列处理失败 fileId={}", message.getFileId(), e);
            channel.basicNack(tag, false, true);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_VIDEO_TRANSCODE)
    public void onVideoTranscode(MediaMessage message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag)
            throws Exception {
        try {
            if (MediaProcessService.isVideo(message.getFileType(), message.getFileName())) {
                videoProcessService.processVideo(message.getFileId());
            }
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("视频转码队列处理失败 fileId={}", message.getFileId(), e);
            channel.basicNack(tag, false, true);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_IMAGE_COMPRESS)
    public void onImageCompress(MediaMessage message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag)
            throws Exception {
        try {
            imageCompressService.compressIfNeeded(message.getFileId());
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("图片压缩队列处理失败 fileId={}", message.getFileId(), e);
            channel.basicNack(tag, false, true);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATION)
    public void onNotification(NotificationMessage message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag)
            throws Exception {
        try {
            var saved = notificationService.save(message.getUserId(), message.getType(),
                    message.getTitle(), message.getContent(), message.getRefId());
            Map<String, String> statuses = notificationService.resolveActionStatuses(
                    message.getType(), message.getRefId());
            progressHandler.sendNotificationWithStatuses(message.getUserId(), message.getType(),
                    message.getTitle(), message.getContent(), message.getRefId(), saved.getId(), statuses);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("通知队列处理失败 userId={}", message.getUserId(), e);
            channel.basicNack(tag, false, true);
        }
    }
}
