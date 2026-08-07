package com.clouddisk.security;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.clouddisk.cache.CacheService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailCodeService {

    private static final String EMAIL_CODE_KEY = "email:code:";
    private static final String EMAIL_LIMIT_KEY = "email:limit:";
    private static final String EMAIL_DAILY_KEY = "email:daily:";

    private final CacheService cacheService;
    private final CloudDiskProperties properties;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public void sendCode(String toEmail, String scene) {
        if (StrUtil.isBlank(toEmail)) {
            throw new BusinessException("邮箱不能为空");
        }
        String email = toEmail.trim();
        if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new BusinessException("邮箱格式不正确");
        }

        String normalizedScene = StrUtil.isBlank(scene) ? "bind" : scene.trim().toLowerCase();

        // 1. 频率间隔限制（默认 60 秒）
        int intervalSec = properties.getMail().getCodeIntervalSeconds();
        String limitKey = EMAIL_LIMIT_KEY + normalizedScene + ":" + email;
        if (cacheService.get(limitKey) != null) {
            throw new BusinessException("发送过于频繁，请 " + intervalSec + " 秒后再试");
        }

        // 2. 每日发送上限限制（默认 10 次）
        int dailyLimit = properties.getMail().getDailyLimitPerEmail();
        String dailyKey = EMAIL_DAILY_KEY + email + ":" + DateUtil.today();
        String currentCountStr = cacheService.get(dailyKey);
        int currentCount = currentCountStr != null ? Integer.parseInt(currentCountStr) : 0;
        if (currentCount >= dailyLimit) {
            throw new BusinessException("该邮箱今日发送验证码次数已达上限（" + dailyLimit + "次），请明天后再试");
        }

        // 3. 生成 6 位纯数字验证码
        String code = RandomUtil.randomNumbers(6);
        int expireMin = properties.getMail().getCodeExpireMinutes();

        // 存储验证码到 Redis / 缓存
        String codeKey = EMAIL_CODE_KEY + normalizedScene + ":" + email;
        cacheService.set(codeKey, code, expireMin * 60L);
        cacheService.set(limitKey, "1", (long) intervalSec);
        cacheService.set(dailyKey, String.valueOf(currentCount + 1), 86400L);

        // 4. 组装邮件文本与 HTML 模板
        String platformName = properties.getMail().getFromName();
        String subject;
        String sceneTitle;
        String actionText;

        switch (normalizedScene) {
            case "register":
                subject = "【" + platformName + "】账号注册验证码";
                sceneTitle = "账号注册";
                actionText = "您正在注册【" + platformName + "】账号";
                break;
            case "login":
                subject = "【" + platformName + "】快捷登录验证码";
                sceneTitle = "快捷登录";
                actionText = "您正在登录【" + platformName + "】系统";
                break;
            case "resetpwd":
            case "modifypwd":
                subject = "【" + platformName + "】重置密码验证码";
                sceneTitle = "重置密码";
                actionText = "您正在申请重置密码";
                break;
            case "bind":
            default:
                subject = "【" + platformName + "】绑定邮箱验证码";
                sceneTitle = "绑定邮箱";
                actionText = "您正在进行邮箱绑定/更换操作";
                break;
        }

        String plainText = "您好！" + actionText + "，您的验证码为：" + code + "，有效期 " + expireMin + " 分钟。如非本人操作请忽略。";
        String htmlContent = buildHtmlCodeTemplate(platformName, sceneTitle, actionText, code, expireMin);

        // 打印控制台开发日志
        log.info("📧 [邮箱验证码通知] 目标邮箱: {}, 场景: {}, 验证码: {}, 有效期: {}分钟", email, normalizedScene, code, expireMin);

        // 5. 异步发送邮件（multipart/alternative 结构，防止垃圾邮件判定）
        if (mailSender != null && StrUtil.isNotBlank(mailUsername)) {
            CompletableFuture.runAsync(() -> {
                try {
                    MimeMessage mimeMessage = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                    helper.setFrom(new InternetAddress(mailUsername, platformName, "UTF-8"));
                    helper.setTo(email);
                    helper.setSubject(subject);
                    
                    // 设置纯文本与 HTML 兼容结构
                    helper.setText(plainText, htmlContent);

                    mailSender.send(mimeMessage);
                    log.info("【{}】场景 HTML 邮件验证码已成功发送至 {}", normalizedScene, email);
                } catch (Exception e) {
                    log.error("发送【{}】场景邮件验证码至 {} 失败", normalizedScene, email, e);
                }
            });
        }
    }

    public void verifyCode(String email, String scene, String code) {
        if (StrUtil.isBlank(email) || StrUtil.isBlank(code)) {
            throw new BusinessException("邮箱和验证码不能为空");
        }
        String normalizedScene = StrUtil.isBlank(scene) ? "bind" : scene.trim().toLowerCase();
        String codeKey = EMAIL_CODE_KEY + normalizedScene + ":" + email.trim();
        String savedCode = cacheService.get(codeKey);

        if (savedCode == null) {
            throw new BusinessException("验证码已过期或不存在，请重新获取");
        }
        if (!savedCode.equalsIgnoreCase(code.trim())) {
            throw new BusinessException("验证码错误");
        }
        // 校验成功后立即清除验证码，防止二次使用（单次有效）
        cacheService.delete(codeKey);
    }

    // ====== HTML 邮件模板生成 ======

    private String buildHtmlCodeTemplate(String platformName, String sceneTitle, String actionText, String code, int expireMin) {
        String logoHtml = getLogoHtml();

        return "<!DOCTYPE html>" +
                "<html>" +
                "<head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>" +
                "<body style=\"margin: 0; padding: 30px 0; background-color: #f4f6f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;\">" +
                "<div style=\"max-width: 560px; margin: 0 auto; background: #ffffff; border-radius: 16px; overflow: hidden; border: 1px solid #e5e7eb; box-shadow: 0 10px 25px rgba(0,0,0,0.05);\">" +
                "  <!-- Header -->" +
                "  <div style=\"background: #0f172a; padding: 24px 32px;\">" +
                "    <table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">" +
                "      <tr>" +
                "        <td style=\"vertical-align: middle;\">" +
                "          " + logoHtml +
                "          <span style=\"color: #ffffff; font-size: 18px; font-weight: 700; vertical-align: middle; margin-left: 10px;\">" + escapeHtml(platformName) + "</span>" +
                "        </td>" +
                "        <td style=\"text-align: right; vertical-align: middle;\">" +
                "          <span style=\"background: rgba(255,255,255,0.15); color: #ffffff; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 500;\">" + escapeHtml(sceneTitle) + "</span>" +
                "        </td>" +
                "      </tr>" +
                "    </table>" +
                "  </div>" +
                "  <!-- Body -->" +
                "  <div style=\"padding: 36px 32px;\">" +
                "    <h2 style=\"margin: 0 0 12px 0; color: #1f2937; font-size: 18px; font-weight: 600;\">您好！</h2>" +
                "    <p style=\"margin: 0 0 24px 0; color: #4b5563; font-size: 14px; line-height: 1.6;\">" + escapeHtml(actionText) + "，本次操作的验证码如下：</p>" +
                "    <!-- Code Box -->" +
                "    <div style=\"background: #f8fafc; border: 2px dashed #cbd5e1; border-radius: 12px; padding: 24px; text-align: center; margin-bottom: 24px;\">" +
                "      <div style=\"font-size: 36px; font-weight: 800; letter-spacing: 12px; color: #2563eb; font-family: Consolas, Monaco, monospace;\">" + escapeHtml(code) + "</div>" +
                "      <div style=\"margin-top: 10px; font-size: 13px; color: #64748b;\">⏱ 验证码有效期为 <strong>" + expireMin + " 分钟</strong>，请尽快完成验证</div>" +
                "    </div>" +
                "    <!-- Safety Warning -->" +
                "    <div style=\"background: #fffbeb; border-radius: 8px; padding: 14px 16px; border-left: 4px solid #f59e0b; color: #b45309; font-size: 13px; line-height: 1.5;\">" +
                "      🔒 <strong>安全提示：</strong>验证码包含敏感操作权限，请勿转发或提供给任何人。如非本人操作，请忽略此邮件。" +
                "    </div>" +
                "  </div>" +
                "  <!-- Footer -->" +
                "  <div style=\"background: #f8fafc; border-top: 1px solid #f1f5f9; padding: 20px 32px; text-align: center; color: #94a3b8; font-size: 12px; line-height: 1.6;\">" +
                "    <div>此邮件由 " + escapeHtml(platformName) + " 系统自动发出，请勿直接回复</div>" +
                "    <div style=\"margin-top: 4px;\">© " + DateUtil.thisYear() + " " + escapeHtml(platformName) + " · All rights reserved.</div>" +
                "  </div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    /**
     * 生成 100% 邮件客户端兼容的 CloudDisk Pro 项目专属 HTML/CSS 矢量 Logo（圆角弧形 A 标识）
     */
    private String getLogoHtml() {
        return "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"display: inline-block; vertical-align: middle; width: 34px; height: 34px; background: #0f172a; background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); border-radius: 10px; overflow: hidden;\">" +
                "  <tr>" +
                "    <td style=\"width: 34px; height: 34px; text-align: center; vertical-align: middle; padding: 0;\">" +
                "      <table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" align=\"center\" style=\"margin: 0 auto;\">" +
                "        <tr>" +
                "          <td style=\"text-align: center; padding-bottom: 2px;\">" +
                "            <div style=\"width: 14px; height: 12px; border: 3px solid #ffffff; border-bottom: none; border-radius: 7px 7px 0 0; margin: 0 auto;\"></div>" +
                "          </td>" +
                "        </tr>" +
                "        <tr>" +
                "          <td style=\"text-align: center;\">" +
                "            <div style=\"width: 4px; height: 4px; background: #ffffff; border-radius: 50%; margin: 0 auto;\"></div>" +
                "          </td>" +
                "        </tr>" +
                "      </table>" +
                "    </td>" +
                "  </tr>" +
                "</table>";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
