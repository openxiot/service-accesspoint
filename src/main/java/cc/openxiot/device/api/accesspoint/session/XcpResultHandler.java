package cc.openxiot.device.api.accesspoint.session;

import cn.geekcity.xiot.spec.error.IotError;
import cn.geekcity.xiot.spec.status.Status;
import cn.geekcity.xiot.xcp.stanza.iq.IQ;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.jboss.logging.Logger;

import java.util.function.Function;

public class XcpResultHandler {

    private final Vertx vertx;
    private final Logger logger;
    private final String queryId;
    private final long timerId;
    private final Handler<AsyncResult<IQ>> handler;
    private final Function<String, XcpResultHandler> timeoutHandler;

    public XcpResultHandler(
            Vertx vertx,
            Logger logger,
            String queryId,
            Handler<AsyncResult<IQ>> handler,
            int timeoutMS,
            Function<String, XcpResultHandler> timeoutHandler
    ) {
        this.vertx = vertx;
        this.logger = logger;
        this.queryId = queryId;
        this.handler = handler;
        this.timeoutHandler = timeoutHandler;
        this.timerId = vertx.setTimer(timeoutMS, timerId -> handleResponseTimeout());
    }

    public void handle(AsyncResult<IQ> ar) {
        vertx.cancelTimer(timerId);
        handler.handle(ar);
    }

    private void handleResponseTimeout() {
        logger.info("handleResponseTimeout, queryId: " + queryId);
        timeoutHandler.apply(queryId);
        handler.handle(Future.failedFuture(new IotError(Status.TIMEOUT, "response timeout")));
    }
}
