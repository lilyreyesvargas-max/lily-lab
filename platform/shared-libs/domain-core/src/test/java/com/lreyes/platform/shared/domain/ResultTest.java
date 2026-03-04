package com.lreyes.platform.shared.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void success_shouldContainValue() {
        Result<String> result = Result.success("ok");

        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
        assertEquals("ok", result.getValue());
    }

    @Test
    void failure_shouldContainError() {
        Result<String> result = Result.failure("ERR_001", "algo falló");

        assertTrue(result.isFailure());
        assertFalse(result.isSuccess());
        assertEquals("ERR_001", result.getError().code());
        assertEquals("algo falló", result.getError().message());
    }

    @Test
    void getValue_onFailure_shouldThrow() {
        Result<String> result = Result.failure("ERR", "fail");
        assertThrows(IllegalStateException.class, result::getValue);
    }

    @Test
    void getError_onSuccess_shouldThrow() {
        Result<String> result = Result.success("ok");
        assertThrows(IllegalStateException.class, result::getError);
    }

    @Test
    void fold_shouldApplyCorrectBranch() {
        Result<Integer> success = Result.success(42);
        Result<Integer> failure = Result.failure("ERR", "fail");

        String successResult = success.fold(v -> "val:" + v, e -> "err:" + e.code());
        String failureResult = failure.fold(v -> "val:" + v, e -> "err:" + e.code());

        assertEquals("val:42", successResult);
        assertEquals("err:ERR", failureResult);
    }

    @Test
    void map_shouldTransformSuccessValue() {
        Result<Integer> result = Result.success(10);
        Result<String> mapped = result.map(v -> "num:" + v);

        assertTrue(mapped.isSuccess());
        assertEquals("num:10", mapped.getValue());
    }

    @Test
    void map_shouldPreserveFailure() {
        Result<Integer> result = Result.failure("ERR", "fail");
        Result<String> mapped = result.map(v -> "num:" + v);

        assertTrue(mapped.isFailure());
        assertEquals("ERR", mapped.getError().code());
    }
}
