package cc.openxiot.device.api.accesspoint.server.configurator;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

public class XcpCertConfigurator extends ServerEndpointConfig.Configurator {

    @Override
    public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
        String cn = extractCnFromHeader(request);
        config.getUserProperties().put("XCP_CLIENT_CN", cn != null ? cn : "unknown");
    }

    private String extractCnFromHeader(HandshakeRequest request) {
        List<String> headers = request.getHeaders().get("X-Forwarded-Client-Cert");
        if (headers == null || headers.isEmpty()) {
            return "no-cert-header";
        }

        String value = headers.getFirst();

        // 优先从 Subject="CN=xxx" 直接取
        int subjectIdx = value.indexOf("Subject=\"");
        if (subjectIdx >= 0) {
            int cnIdx = value.indexOf("CN=", subjectIdx);
            if (cnIdx >= 0) {
                int end = value.indexOf("\"", cnIdx);
                if (end > cnIdx) {
                    return value.substring(cnIdx, end).replace("\"", "");
                }
            }
        }

        // fallback: 解码 Cert 字段的 PEM，从 X509Certificate 取 CN
        return parseCertFromHeader(value);
    }

    private String parseCertFromHeader(String headerValue) {
        try {
            int certIdx = headerValue.indexOf("Cert=\"");
            if (certIdx < 0) {
                return "parse-failed";
            }

            int start = certIdx + 6;
            int end = headerValue.indexOf("\"", start);
            if (end < 0) {
                return "parse-failed";
            }

            String certData = headerValue.substring(start, end);

            // PEM 格式（含 -----BEGIN CERTIFICATE----- 头尾）
            if (certData.contains("-----BEGIN")) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate cert = (X509Certificate) cf.generateCertificate(
                        new ByteArrayInputStream(certData.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                return cert.getSubjectX500Principal().getName();
            }

            // fallback: 裸 Base64（无 PEM 头尾）
            byte[] der = Base64.getDecoder().decode(certData);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
            return cert.getSubjectX500Principal().getName();
        } catch (Exception e) {
            return "parse-error: " + e.getMessage();
        }
    }
}
