package cc.openxiot.common.util;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FutureMergerTest {

    @Test
    void mergeList_emptyList() {
        Future<List<String>> result = FutureMerger.mergeList(List.of());

        assertTrue(result.isComplete());
        assertTrue(result.succeeded());
        assertTrue(result.result().isEmpty());
    }

    @Test
    void mergeList_singleFuture() {
        Future<List<String>> future = Future.succeededFuture(List.of("a", "b"));
        Future<List<String>> result = FutureMerger.mergeList(List.of(future));

        assertTrue(result.succeeded());
        assertEquals(List.of("a", "b"), result.result());
    }

    @Test
    void mergeList_multipleFutures() {
        Future<List<String>> f1 = Future.succeededFuture(List.of("a", "b"));
        Future<List<String>> f2 = Future.succeededFuture(List.of("c", "d"));
        Future<List<String>> f3 = Future.succeededFuture(List.of("e"));

        Future<List<String>> result = FutureMerger.mergeList(List.of(f1, f2, f3));

        assertTrue(result.succeeded());
        assertEquals(List.of("a", "b", "c", "d", "e"), result.result());
    }

    @Test
    void mergeList_withNullResult_skipsNull() {
        Future<List<String>> f1 = Future.succeededFuture(List.of("a"));
        Future<List<String>> f2 = Future.succeededFuture(null);
        Future<List<String>> f3 = Future.succeededFuture(List.of("b"));

        Future<List<String>> result = FutureMerger.mergeList(List.of(f1, f2, f3));

        assertTrue(result.succeeded());
        assertEquals(List.of("a", "b"), result.result());
    }

    @Test
    void mergeList_withFailedFuture_fails() {
        Future<List<String>> f1 = Future.succeededFuture(List.of("a"));
        Future<List<String>> f2 = Future.failedFuture(new RuntimeException("test error"));

        Future<List<String>> result = FutureMerger.mergeList(List.of(f1, f2));

        assertTrue(result.isComplete());
        assertTrue(result.failed());
        assertInstanceOf(RuntimeException.class, result.cause());
    }
}
