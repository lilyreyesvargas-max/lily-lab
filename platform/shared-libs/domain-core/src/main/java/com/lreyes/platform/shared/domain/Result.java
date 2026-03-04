package com.lreyes.platform.shared.domain;

import java.util.function.Function;

/**
 * Tipo funcional que encapsula éxito o fallo sin usar excepciones.
 * <p>
 * Uso:
 * <pre>
 *   Result&lt;Customer&gt; result = customerService.create(dto);
 *   return result.fold(
 *       customer -&gt; ResponseEntity.ok(customer),
 *       error   -&gt; ResponseEntity.badRequest().body(error)
 *   );
 * </pre>
 *
 * @param <T> tipo del valor en caso de éxito
 */
public sealed interface Result<T> {

    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    static <T> Result<T> failure(Error error) {
        return new Failure<>(error);
    }

    static <T> Result<T> failure(String code, String message) {
        return new Failure<>(new Error(code, message));
    }

    default boolean isSuccess() {
        return this instanceof Success<T>;
    }

    default boolean isFailure() {
        return this instanceof Failure<T>;
    }

    default T getValue() {
        if (this instanceof Success<T> s) return s.value();
        throw new IllegalStateException("Cannot get value from a Failure result");
    }

    default Error getError() {
        if (this instanceof Failure<T> f) return f.error();
        throw new IllegalStateException("Cannot get error from a Success result");
    }

    default <R> R fold(Function<T, R> onSuccess, Function<Error, R> onFailure) {
        if (this instanceof Success<T> s) return onSuccess.apply(s.value());
        if (this instanceof Failure<T> f) return onFailure.apply(f.error());
        throw new IllegalStateException("Unknown Result type");
    }

    default <R> Result<R> map(Function<T, R> mapper) {
        return fold(
                value -> Result.success(mapper.apply(value)),
                Result::failure
        );
    }

    record Success<T>(T value) implements Result<T> {}
    record Failure<T>(Error error) implements Result<T> {}

    record Error(String code, String message) {}
}
