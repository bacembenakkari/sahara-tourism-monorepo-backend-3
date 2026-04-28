package com.camping.duneinsolite.service.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@duneinsolite.com}")
    private String fromAddress;

    @Value("${app.frontend.url:https://duneinsolite.com}")
    private String frontendUrl;

    /**
     * Sends a welcome email with temporary password to a newly created user (admin flow).
     * Runs asynchronously so it never blocks the HTTP response.
     */
    @Async
    public void sendWelcomeEmail(String to, String name, String temporaryPassword) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject("Bienvenue sur Dune Insolite — Vos identifiants de connexion");
            helper.setText(buildPlainText(name, to, temporaryPassword), false);
            helper.setText(buildHtml(name, to, temporaryPassword), true);

            mailSender.send(message);
            log.info("✅ Welcome email sent to: {}", to);

        } catch (MessagingException e) {
            // Log the error but do NOT crash the user-creation flow
            log.error("❌ Failed to send welcome email to: {} — {}", to, e.getMessage());
        }
    }

    // ── Plain-text fallback ───────────────────────────────────────

    private String buildPlainText(String name, String email, String password) {
        return """
            Bonjour %s,

            Votre compte a été créé avec succès sur Dune Insolite.

            Vos identifiants de connexion :

              Email          : %s
              Mot de passe   : %s

            ⚠️ Pour des raisons de sécurité, veuillez changer votre mot de passe
            dès votre première connexion.

            Se connecter : %s/login

            Cordialement,
            L'équipe Dune Insolite
            """.formatted(name, email, password, frontendUrl);
    }

    // ── HTML email ────────────────────────────────────────────────

    private String buildHtml(String name, String email, String password) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin:0;padding:0;background:#f4f4f5;font-family:'Segoe UI',Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:40px 0;">
                <tr>
                  <td align="center">
                    <table width="600" cellpadding="0" cellspacing="0"
                           style="background:#ffffff;border-radius:12px;overflow:hidden;
                                  box-shadow:0 2px 12px rgba(0,0,0,0.08);">

                      <!-- Header -->
                      <tr>
                        <td style="background:linear-gradient(135deg,#c8963e,#a07030);
                                   padding:36px 40px;text-align:center;">
                          <h1 style="margin:0;color:#ffffff;font-size:28px;font-weight:700;
                                     letter-spacing:1px;">🏕️ Dune Insolite</h1>
                          <p style="margin:8px 0 0;color:rgba(255,255,255,0.85);font-size:14px;">
                            Bienvenue dans votre espace partenaire
                          </p>
                        </td>
                      </tr>

                      <!-- Body -->
                      <tr>
                        <td style="padding:40px 40px 24px;">
                          <p style="margin:0 0 16px;font-size:16px;color:#374151;">
                            Bonjour <strong>%s</strong>,
                          </p>
                          <p style="margin:0 0 24px;font-size:15px;color:#6b7280;line-height:1.6;">
                            Votre compte a été créé avec succès par l'administrateur.
                            Voici vos identifiants de connexion temporaires :
                          </p>

                          <!-- Credentials box -->
                          <table width="100%%" cellpadding="0" cellspacing="0"
                                 style="background:#fef9f0;border:1px solid #f0d9a8;
                                        border-radius:8px;margin-bottom:24px;">
                            <tr>
                              <td style="padding:24px 28px;">
                                <table cellpadding="6" cellspacing="0">
                                  <tr>
                                    <td style="font-size:13px;color:#9ca3af;font-weight:600;
                                               text-transform:uppercase;letter-spacing:0.5px;
                                               padding-right:16px;">Email</td>
                                    <td style="font-size:15px;color:#111827;font-weight:500;">%s</td>
                                  </tr>
                                  <tr>
                                    <td style="font-size:13px;color:#9ca3af;font-weight:600;
                                               text-transform:uppercase;letter-spacing:0.5px;
                                               padding-right:16px;">Mot de passe</td>
                                    <td>
                                      <code style="font-size:15px;color:#c8963e;font-weight:700;
                                                   background:#fff8ed;border:1px solid #f0d9a8;
                                                   padding:3px 10px;border-radius:4px;
                                                   letter-spacing:1px;">%s</code>
                                    </td>
                                  </tr>
                                </table>
                              </td>
                            </tr>
                          </table>

                          <!-- Warning -->
                          <table width="100%%" cellpadding="0" cellspacing="0"
                                 style="background:#fff7ed;border-left:4px solid #f59e0b;
                                        border-radius:4px;margin-bottom:28px;">
                            <tr>
                              <td style="padding:14px 18px;font-size:14px;color:#92400e;line-height:1.5;">
                                ⚠️ <strong>Important :</strong> Ce mot de passe est temporaire.
                                Veuillez le changer dès votre première connexion pour sécuriser votre compte.
                              </td>
                            </tr>
                          </table>

                          <!-- CTA button -->
                          <table width="100%%" cellpadding="0" cellspacing="0">
                            <tr>
                              <td align="center">
                                <a href="%s/login"
                                   style="display:inline-block;background:linear-gradient(135deg,#c8963e,#a07030);
                                          color:#ffffff;font-size:15px;font-weight:600;
                                          text-decoration:none;padding:14px 36px;
                                          border-radius:8px;letter-spacing:0.3px;">
                                  Se connecter →
                                </a>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>

                      <!-- Footer -->
                      <tr>
                        <td style="padding:24px 40px 36px;border-top:1px solid #f3f4f6;
                                   text-align:center;">
                          <p style="margin:0;font-size:13px;color:#9ca3af;line-height:1.6;">
                            Cet email a été envoyé automatiquement — merci de ne pas y répondre.<br>
                            © 2025 Dune Insolite. Tous droits réservés.
                          </p>
                        </td>
                      </tr>

                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(name, email, password, frontendUrl);
    }
}