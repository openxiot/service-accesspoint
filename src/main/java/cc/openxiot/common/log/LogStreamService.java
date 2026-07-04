package cc.openxiot.common.log;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

@ApplicationScoped
public class LogStreamService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @Inject
    org.jboss.logging.Logger logger;

    @Inject
    LogBroadcaster broadcaster;

    void serveLogPage(@Observes Router router) {
        // /log → /log/index.html （静态资源由 Quarkus 自动从 META-INF/resources/ 提供）
        router.get("/log").handler(ctx ->
            ctx.reroute("/log/index.html")
        );
    }

    void setupSse(@Observes Router router) {
        router.get("/log/stream").handler(ctx -> {
            var resp = ctx.response();
            resp.setChunked(true);
            resp.putHeader("Content-Type", "text/event-stream");
            resp.putHeader("Cache-Control", "no-cache");
            resp.putHeader("Connection", "keep-alive");

            broadcaster.register(resp);
            resp.closeHandler(v -> broadcaster.unregister(resp));
            resp.exceptionHandler(e -> broadcaster.unregister(resp));
        });
    }

    void registerLogHandler(@Observes @Initialized(ApplicationScoped.class) Object ignore) {
        Logger root = Logger.getLogger("");
        root.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                broadcaster.broadcast(format(record));
            }

            @Override public void flush() {}
            @Override public void close() {}

            private String format(LogRecord record) {
                String time = FORMATTER.format(Instant.ofEpochMilli(record.getMillis()));
                String level = record.getLevel().getName();
                String loggerName = record.getLoggerName();
                if (loggerName != null && loggerName.contains(".")) {
                    loggerName = loggerName.substring(loggerName.lastIndexOf('.') + 1);
                }
                String message = record.getMessage();
                if (record.getThrown() != null) {
                    var sw = new java.io.StringWriter();
                    record.getThrown().printStackTrace(new java.io.PrintWriter(sw));
                    message += "\n" + sw;
                }
                return time + " [" + level + "] (" + loggerName + ") " + message;
            }
        });
    }
}
