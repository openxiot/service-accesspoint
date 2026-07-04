package cc.openxiot.device.api.accesspoint.replica;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Objects;

@ApplicationScoped
public class ReplicaService {

    @Inject
    Logger logger;

    private String ip;

    public String getIp() {
        if (ip == null) {
            this.ip = _getIp();
        }

        return ip;
    }

    private String _getIp() {
        try {
            // Kubernetes 等平台会通过环境变量暴露 POD IP
            String podIp = System.getenv("POD_IP");
            if (podIp != null && !podIp.isBlank()) {
                return podIp;
            }

            // 遍历网络接口，找到第一个非回环的 IPv4 地址
            var interfaces = NetworkInterface.networkInterfaces()
                    .filter(Objects::nonNull)
                    .flatMap(NetworkInterface::inetAddresses)
                    .toList();

            for (var addr : interfaces) {
                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                    return addr.getHostAddress();
                }
            }

            // fallback
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            logger.errorv("Failed to resolve container IP: {0}", e.getMessage());
            return "unknown";
        }
    }
}
