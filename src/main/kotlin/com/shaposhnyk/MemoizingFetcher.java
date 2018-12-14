package com.shaposhnyk;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class MemoizingFetcher<T> implements DataFetcher<T> {
  private final DataFetcher<T> delegate;
  private final CompletableFuture<T> futureResult = new CompletableFuture<>();
  private final AtomicBoolean isFetched = new AtomicBoolean(false);

  MemoizingFetcher(DataFetcher<T> delegate) {
    this.delegate = delegate;
  }

  public static <T> MemoizingFetcher<T> of(DataFetcher<T> delegate) {
    return new MemoizingFetcher<>(delegate);
  }

  @Override
  public T get(DataFetchingEnvironment environment) throws Exception {
    if (!isFetched.getAndSet(true)) {
      try {
        T result = delegate.get(environment);
        futureResult.complete(result);
      } catch (Exception e) {
        isFetched.set(false);
      }
    }
    return futureResult.get();
  }
}
