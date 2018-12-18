package com.shaposhnyk;

import graphql.execution.ExecutionId;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.jetbrains.annotations.NotNull;
import org.jooq.Record;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class MemoizingFetcher<T> implements DataFetcher<T> {
  private final DataFetcher<T> delegate;
  private final CompletableFuture<T> futureResult = new CompletableFuture<>();
  private final AtomicBoolean isFetched = new AtomicBoolean(false);

  private MemoizingFetcher(DataFetcher<T> delegate) {
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

  @NotNull
  public static DataFetcher<Iterable<Record>> perRequestUsing(
      @NotNull Supplier<DataFetcher<Iterable<Record>>> delegateSupplier) {
    // this should be replaced by a correct Cache implementation, with eviction

    return new DataFetcher<Iterable<Record>>() {
      private final ConcurrentHashMap<ExecutionId, DataFetcher<Iterable<Record>>> fetcherById =
          new ConcurrentHashMap<>();

      @Override
      public Iterable<Record> get(DataFetchingEnvironment environment) throws Exception {
        ExecutionId key = environment.getExecutionId();
        DataFetcher<Iterable<Record>> fetcher =
            fetcherById.computeIfAbsent(key, k2 -> MemoizingFetcher.of(delegateSupplier.get()));

        return fetcher.get(environment);
      }
    };
  }
}
