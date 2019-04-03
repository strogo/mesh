package com.gentics.mesh.core.data.search.request;

import com.gentics.mesh.search.SearchProvider;
import io.reactivex.Completable;
import io.reactivex.functions.Action;

import java.util.function.Function;

import static com.gentics.mesh.util.RxUtil.NOOP;

public interface SearchRequest {
	int requestCount();
	Completable execute(SearchProvider searchProvider);

	default Action onComplete() {
		return NOOP;
	}

	static SearchRequest create(Function<SearchProvider, Completable> function) {
		return new SearchRequest() {
			@Override
			public int requestCount() {
				return 1;
			}

			@Override
			public Completable execute(SearchProvider searchProvider) {
				return function.apply(searchProvider);
			}
		};
	}
}
