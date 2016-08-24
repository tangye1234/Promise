package me.tangye.utils.async;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import me.tangye.utils.async.resolver.BaseResolver;
import me.tangye.utils.async.resolver.Defer;
import me.tangye.utils.async.resolver.DirectResolver;
import me.tangye.utils.async.resolver.ExceptionPromiseResolver;
import me.tangye.utils.async.resolver.ExceptionResolver;
import me.tangye.utils.async.resolver.FinalResolver;
import me.tangye.utils.async.resolver.PromiseResolver;
import me.tangye.utils.async.resolver.SimplePromiseResolver;
import me.tangye.utils.async.resolver.SimpleResolver;

/**
 * Promise异步模型，类似于Future 该模型本身也是一个 @{link Thenable}
 * 
 * @author tangye
 *
 * @param <D>
 *            该Future模型的期望返回数据类型
 */
public class Promise<D> implements Thenable<D>, Cloneable {

	/** 当前Promise的版本 **/
	public static final String VERSION = "1.0.5";

	/* value may be a non promise value */
	protected volatile D nonPromiseValue;

	/* result exception in promise */
	protected volatile Exception exception;

	/* if promise is resolved or reject or pending */
	protected Boolean state;

	/* a function cache for cloning */
	private Function<?> func;

	/* a throwable initialized in promise constructor */
	private final Throwable throwable;

	/*
	 * when promise is not resolved, resolvers got in function [then] should be
	 * deferred
	 */
	private List<CachedResolver<D, ?>> deferreds;

	/* a resolver who receives promise as a value */
	private final ProcessResolver<Promise<D>> PROMISE_RESOLVER = new ProcessResolver<>();

	/* a resolver who receives non-promise as a value */
	private final ProcessResolver<D> NON_PROMISE_RESOLVER = new ProcessResolver<>();

	/* every run should be called in this handler */
	protected Handler handler;

	/**
	 * 构造一个Promise对象，使用DirectFunction，来构造一个带参数的Runnable过程<br>
	 * 该Function.run将在当前的线程的Looper中运行
	 * 
	 * @param function 提交的执行函数
	 * @return Promise对象
	 * @throws RuntimeException
	 *             当当前线程不是一个Looper线程时抛出
	 */
	public static <D> Promise<D> make(DirectFunction<D> function) {
		return make(function, Looper.myLooper());
	}

	/**
	 * 构造一个Promise对象，使用PromiseFunction，来构造一个带参数的Runnable过程<br>
	 * 该Function.run将在当前的线程的Looper中运行
	 * 
	 * @param function 提交的执行函数
	 * @return Promise对象
	 * @throws RuntimeException
	 *             当当前线程不是一个Looper线程时抛出
	 */
	public static <D> Promise<D> make(PromiseFunction<D> function) {
		return make(function, Looper.myLooper());
	}

	/**
	 * 构造一个Promise对象，使用DirectFunction，来构造一个带参数的Runnable过程<br>
	 * 该Function.run将在指定的线程的Looper中运行
	 * 
	 * @param function 提交的执行函数
	 * @param looper
	 *            所有的异步过程，包括Then中的处理过程，运行在的含有该looper的线程
	 * @return Promise对象
	 */
	public static <D> Promise<D> make(final DirectFunction<D> function,
			Looper looper) {
		return new Promise<>(function, looper);
	}

	/**
	 * 构造一个Promise对象，使用PromiseFunction，来构造一个带参数的Runnable过程<br>
	 * 该Function.run将在指定的线程的Looper中运行
	 * 
	 * @param function 提交的执行函数
	 * @param looper
	 *            所有的异步过程，包括Then中的处理过程，运行在的含有该looper的线程
	 * @return Promise对象
	 */
	public static <D> Promise<D> make(final PromiseFunction<D> function,
			Looper looper) {
		return new Promise<>(function, looper);
	}

	private <Q> Promise(Function<Q> function, Looper looper) {
		this.state = null;
		this.nonPromiseValue = null;
		this.exception = null;
		this.func = function;
		this.throwable = new Throwable();
		Assert.assertNotNull(looper);
		handler = new Handler(looper);
		if (function != null) {
			this.deferreds = new ArrayList<>();
			if (PromiseFunction.class.isInstance(function)) {
				@SuppressWarnings("unchecked")
				Function<Promise<D>> f = (Function<Promise<D>>) function;
				postResolve(f, PROMISE_RESOLVER);
			} else if (DirectFunction.class.isInstance(function)) {
				@SuppressWarnings("unchecked")
				DirectFunction<D> f = (DirectFunction<D>) function;
				postResolve(f, NON_PROMISE_RESOLVER);
			} else {
				throw new IllegalArgumentException(
						"function should only be DirectFunction Or PromiseFunction");
			}
		}
	}

	/**
	 * clone a Promise with the same looper
	 */
	@SuppressWarnings("unchecked")
	public Promise<D> clone() {
		if (this instanceof ValuePromise) {
			ValuePromise<D> promise = (ValuePromise<D>) this;
			D value = promise.nonPromiseValue;
			return (Promise<D>) Promise.resolveValue(value, handler.getLooper());
		}
		return new Promise<>(func, handler.getLooper());
	}

	/**
	 * clone a Promise with a new looper
	 * @param looper new Promise looper
	 * @return new Promise
	 */
	public Promise<D> clone(Looper looper) {
		if (looper == handler.getLooper()) {
			return clone();
		}
		@SuppressWarnings("unchecked")
		Promise<D> promise = (Promise<D>) Promise.resolveValue(this, looper);
		return promise;
	}

	private <T> void postResolve(final Function<T> function,
			final InternalResolver<T> internalResolver) {
		Runnable r = new Runnable() {
			public void run() {
				doResolve(function, internalResolver);
			}
		};
		runForHandler(r, handler);
	}

	public static void runForHandler(Runnable r, Handler h) {
		if (h.getLooper() == Looper.myLooper()) {
			r.run();
		} else {
			h.post(r);
		}
	}

	private class ProcessResolver<T> implements InternalResolver<T> {

		private ProcessResolver() {
		}

		/**
		 * resolver interface<br>
		 * a private resolve function for internal use<br>
		 * many times, arguments receive a new value that is handled by<br>
		 * a {@link Promise} which is probably not the current one<br>
		 * This is not thread safe, but only to resolve a new result of current
		 * promise 每当locker.resolve执行时，该方法发生回调
		 */
		@SuppressWarnings("unchecked")
		@Override
		public final Void resolve(final T newValue) {
			Runnable r = new Runnable() {
				@Override
				public void run() {
					try {
						if (newValue == Promise.this)
							throw new RuntimeException(
									"A promise cannot be resolved with itself.");
						// 为了产生迭代效果，如果返回类型是一个Thenable, 比如Promise
						if (newValue instanceof Thenable) {
							final Thenable<D> p = (Thenable<D>) newValue;
							doResolve(p.getThen(), NON_PROMISE_RESOLVER);
						} else {
							// 记录最终的结果
							state = true;
							nonPromiseValue = (D) newValue;
							finale();
						}
					} catch (Exception e) {
						// 记录异常结果
						reject(e);
					}
				}
			};
			runForHandler(r, handler);
			return null;
		}

		/**
		 * resolver interface<br>
		 * a private reject function for internal use<br>
		 * 每当locker.reject执行时，该方法发生回调
		 */
		@Override
		public final Void reject(final Exception e) {
			Runnable r = new Runnable() {
				@Override
				public void run() {
					state = false;
					exception = unwrap(e);
					finale();
				}
			};
			runForHandler(r, handler);
			return null;
		}
	}

	/**
	 * 给exception赋值前，先进行解包
	 * @param e 任意locker传入的或者try catch的Exception
	 * @return 解包的Exception
     */
	protected Exception unwrap(Exception e) {
		while (e instanceof ExecuteException && e.getCause() instanceof Exception) {
			e = (Exception) e.getCause();
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			e.addSuppressed(throwable);
		}
		return e;
	}

	/**
	 * 检查是否有未执行的Resolver对象<br>
	 * 当上次任务完成后，将会检查被缓存在resolver对象池中的Defer<br>
	 * handle所有池中的Resolver
	 */
	private void finale() {
		for (int i = 0, len = deferreds.size(); i < len; i++) {
			handle(deferreds.get(i));
		}
		deferreds.clear();
		deferreds = null;
	}

	@Override
	public Function<D> getThen() {
		return new DirectFunction<D>() {
			@Override
			public void run(final Locker<D> locker) {
				Promise.this.then(new DirectResolver<D, Void>() {
					@Override
					public Void resolve(D newValue) {
						return locker.resolve(newValue);
					}

					@Override
					public Void reject(Exception exception) {
						return locker.reject(exception);
					}
				});
			}
		};
	}

	@Override
	public <D1> Promise<D1> then(final DirectResolver<D, D1> resolver) {
		return Promise.make(new DirectFunction<D1>() {
			@Override
			public void run(Locker<D1> locker) {
				handle(new CachedResolver<>(resolver, locker));
			}
		}, handler.getLooper());
	}

	@Override
	public <D1> Promise<D1> then(final PromiseResolver<D, D1> resolver) {
		return Promise.make(new PromiseFunction<D1>() {
			@Override
			public void run(final Locker<Promise<D1>> locker) {
				handle(new PromiseCachedResolver<>(resolver, locker));
			}
		}, handler.getLooper());
	}

	/**
	 * A short-hand for then(FinalResolver)<br>
	 * 当Promise有结果时,触发
	 *
	 * @param finalResolver 最终结果处理器
	 * @return 上一个Promise的延续(不改变任何传递内容)
	 */
	public Promise<D> finalResult(final FinalResolver<D> finalResolver) {
		return then(finalResolver);
	}

	/**
	 * 捕获异常处理器
	 * @deprecated 请参考
	 * @see #exception(ExceptionResolver)
	 * @param resolver 结果处理器
	 * @param <E> 异常类型
	 * @return 新的Promise
	 */
	@Deprecated
	public <E extends Throwable> Promise<D> catchException(
			final ExceptionResolver<D, E> resolver) {
		return then(resolver);
	}

	/**
	 * 捕获异常处理器
	 * @deprecated 请参考
	 * @see #exception(ExceptionPromiseResolver)
	 * @param resolver 结果处理器
	 * @param <E> 异常类型
	 * @return 新的Promise
	 */
	@Deprecated
	public <E extends Throwable> Promise<D> catchException(
			final ExceptionPromiseResolver<D, E> resolver) {
		return then(resolver);
	}

	/**
	 * A short-hand for then(ExceptionResolver)<br>
	 * 捕获指定的异常
	 *
	 * @param resolver 异常解析器
	 * @return 新的Promise
	 */
	public <E extends Throwable> Promise<D> exception(
			final ExceptionResolver<D, E> resolver) {
		return then(resolver);
	}

	/**
	 * A short-hand for then(ExceptionPromiseResolver)<br>
	 * 捕获指定的异常
	 *
	 * @param resolver 异常解析器
	 * @return 新的Promise
	 */
	public <E extends Throwable> Promise<D> exception(
			final ExceptionPromiseResolver<D, E> resolver) {
		return then(resolver);
	}

	/**
	 * 将Promise方法转化为同步方法，阻塞住当前线程
	 * 
	 * @return Promise完成时，返回resolve之后的值
	 * @throws IllegalStateException
	 * 			   当前线程与Promise执行线程不能为同一个线程
	 * @throws Exception
	 *             当任何exception发生时，抛出excpetion异常
	 */
	public final D sync() throws Exception {
		if (handler.getLooper().getThread() == Thread.currentThread()) {
			throw new IllegalStateException("当前线程与Promise执行线程不能为同一个线程");
		}
		if (state == null) {
			final Object lock = new Object();
			then(new DirectResolver<D, Void>() {

				@Override
				public Void resolve(D newValue) {
					lock.notify();
					return null;
				}

				@Override
				public Void reject(Exception exception) {
					lock.notify();
					return null;
				}

			});
			lock.wait();
		}
		if (!state && exception != null) {
			throw exception;
		} else {
			return nonPromiseValue;
		}
	}

	private <D1> void handle(final CachedResolver<D, D1> cachedResolver) {
		// Promise未完成时，直接缓存
		if (state == null) {
			deferreds.add(cachedResolver);
			return;
		}
		// 否则在下一个时间中，尝试处理缓存的resolver
		handler.post(new Runnable() {
			@Override
			public void run() {
				Locker<D1> l = cachedResolver.locker;
				BaseResolver<D, D1> t = cachedResolver.resolver;
				if (t == null) {
					throw new IllegalArgumentException(
							"resolver should not be null");
				} else {
					D1 p;
					try {
						p = (state ? t.resolve(nonPromiseValue) : t
								.reject(exception));
						if (p == null) {
							l.resolve();
						} else {
							l.resolve(p);
						}
					} catch (Exception e) {
						l.reject(e);
					}
				}
			}
		});
	}

	/**
	 * Running an async function with the locker who will asynchronously invoke
	 * resolve or reject once<br>
	 * resolver function <br>
	 * 
	 * @param function
	 *            the running function to execute
	 * @param internalResolver
	 *            the specific resolver callback
	 */
	protected static <T> void doResolve(Function<T> function,
			final InternalResolver<T> internalResolver) {
		final AtomicBoolean done = new AtomicBoolean(false);
		final Handler handler = new Handler();
		try {
			function.run(new Locker<T>() {

				@Override
				public boolean done() {
					return done.get();
				}

				@Override
				public Void resolve(T r) {
					if (done.compareAndSet(false, true)) {
						return internalResolver.resolve(r);
					}
					return null;
				}

				@Override
				public Void reject(Exception exception) {
					if (done.compareAndSet(false, true)) {
						return internalResolver.reject(exception);
					}
					return null;
				}

				@Override
			    public void post(Runnable runnable) {
					if (!done.get()) {
						handler.post(runnable);
					}
				}

				@Override
				public void postDelayed(Runnable runnable, long delay) {
					if (!done.get()) {
						handler.postDelayed(runnable, delay);
					}
				}

				@Override
				public void removeCallbacks(Runnable runnable) {
					handler.removeCallbacks(runnable);
				}
			});
		} catch (Exception e) {
			if (done.compareAndSet(false, true)) {
				internalResolver.reject(e);
			}
		}
	}

	private static class ValuePromise<T> extends Promise<T> {

		public ValuePromise(T nonPromiseValue, Looper looper) {
			super(null, looper);
			if (nonPromiseValue instanceof Exception) {
				throw new IllegalArgumentException("value should not be exception");
			}
			this.nonPromiseValue = nonPromiseValue;
			this.state = true;
		}
	}

	/**
	 * 立马返回一个指定value对应的Promise对象
	 * 
	 * @param value 普通值
	 * @return 值对应的Promise
	 */
	public static <D> Promise<?> resolveValue(final D value) {
		return resolveValue(value, Looper.myLooper());
	}

	/**
	 * 立马返回一个指定value对应的Promise对象
	 * 
	 * @param value
	 *            非Thenable，可以直接处理为Promise，Thenable将会被转化成一个对等的Promise
	 * @param looper Promise执行所在Looper
	 * @return 新的ValuePromise
	 */
	public static <D> Promise<?> resolveValue(final D value, Looper looper) {
		if (value == null)
			return new ValuePromise<Void>(null, looper);
		if (value instanceof Thenable) {
			Thenable<?> t = (Thenable<?>) value;
			try {
				Function<?> f = t.getThen();
				if (PromiseFunction.class.isInstance(f)) {
					return Promise.make((PromiseFunction<?>) f, looper);
				} else if (DirectFunction.class.isInstance(f)) {
					return Promise.make((DirectFunction<?>) f, looper);
				} else {
					throw new IllegalArgumentException(
							"Thenable.getThen should only be PromiseFunction or DirectFunction");
				}
			} catch (Exception e) {
				return Promise.rejectException(e, looper);
			}
		} else if (value instanceof Exception) {
			return Promise.rejectException((Exception) value, looper);
		}
		return new ValuePromise<>(value, looper);
	}

	/**
	 * 立马返回一个指定value对应的Promise对象
	 *
	 * @param value
	 *            非Thenable，可以直接处理为Promise
	 * @return 新的ValuePromise
	 */
	public static <D> Promise<D> resolveNonPromiseValue(final D value) {
		return resolveNonPromiseValue(value, Looper.myLooper());
	}

	/**
	 * 立马返回一个指定value对应的Promise对象
	 *
	 * @param value
	 *            非Thenable，可以直接处理为Promise
	 * @param looper Promise 执行所在Looper
	 * @return 新的ValuePromise
	 */
	public static <D> Promise<D> resolveNonPromiseValue(final D value, Looper looper) {
		if (value instanceof Thenable) {
			throw new IllegalArgumentException("Value should be non-promise value, this value is an instance of " + value);
		}
		return new ValuePromise<>(value, looper);
	}

	/**
	 * 立马返回一个指定Exception的Promise对象
	 * 
	 * @param e Exception值
	 * @return return 一个Exception Promise
	 */
	public static Promise<Void> rejectException(final Exception e) {
		return rejectException(e, Looper.myLooper());
	}

	/**
	 * 立马返回一个指定Exception的Promise对象
	 * 
	 * @param e Exception值
	 * @param looper Promise所在Looper
	 * @return return 一个Exception Promise
	 */
	public static Promise<Void> rejectException(final Exception e, Looper looper) {
		return new Promise<>(new Function<Void>() {
			@Override
			public void run(Locker<Void> locker) {
				locker.reject(e);
			}
		}, looper);
	}

	/**
	 * 可以通过该方法继续抛出能够传递的Exception
	 * @param e 要抛出的异常
	 * 返回类型使用Void表示, 可以使用return Promise.throwException(e) as a Void function
	 */
	public static Void throwException(final Exception e) {
		if (e instanceof ExecuteException) {
			throw (ExecuteException) e;
		}
		throw new ExecuteException(e);
	}

	/**
	 * 生成Promise内部的Exception，避免显视的申明throws方法
	 * @param e 包装一个Exception源
	 * @return 生成一个无需申明的Exception
     */
	public static ExecuteException newException(final Exception e) {
		if (e instanceof ExecuteException) {
			return (ExecuteException) e;
		}
		return new ExecuteException(e);
	}

	/**
	 * 同时运行多个Promise，当所有Promise结果都完成后，返回所有结果的Object数组
	 * 
	 * @param values 参与all的所有值
	 * @return 所有值的总Promise
	 */
	public static Promise<Object[]> all(final Collection<?> values) {
		return all(values, Looper.myLooper());
	}

	/**
	 * 同时运行多个Promise，当所有Promise结果都完成后，返回所有结果的Object数组
	 * 
	 * @param values 参与all的所有值
	 * @param looper
	 *            在指定的looper上构造all promise
	 * @return 所有值的总Promise
	 */
	public static Promise<Object[]> all(final Collection<?> values,
			final Looper looper) {

		return Promise.make(new DirectFunction<Object[]>() {

			private Locker<Object[]> locker;
			private Object[] result;
			private int remaining;

			private void res(final int i, final Object val) {
				try {
					if (val instanceof Promise) {
						@SuppressWarnings("unchecked")
						Promise<Object> pr = (Promise<Object>) val;
						pr.then(new DirectResolver<Object, Void>() {

							@Override
							public Void resolve(Object newValue) {
								res(i, newValue);
								return null;
							}

							@Override
							public Void reject(Exception exception) {
								locker.reject(exception);
								return null;
							}

						});
						return;
					}
					if (val instanceof Thenable) {
						res(i, Promise.resolveValue(val, looper));
						return;
					}
					result[i] = val;
					if (--remaining == 0) {
						locker.resolve(result);
					}
				} catch (Exception e) {
					locker.reject(e);
				}
			}

			@Override
			public void run(Locker<Object[]> locker) {
				if (values == null || values.size() == 0) {
					locker.resolve(new Object[0]);
					return;
				}
				this.locker = locker;
				this.remaining = values.size();
				this.result = new Object[remaining];
				int i = 0;

				for (final Object pr : values) {
					res(i++, pr);
				}
			}

		}, looper);

	}

	/**
	 * 同时处理多个Promise，第一个返回的value将会触发Promise处理完成
	 * 
	 * @param values 参与race的所有值
	 * @return 返回一个race的Promise
	 */
	public static Promise<Object> race(final Collection<?> values) {
		return race(values, Looper.myLooper());
	}

	/**
	 * 同时处理多个Promise，第一个返回的value将会触发Promise处理完成
	 * 
	 * @param values 参与race的所有值
	 * @param looper Promise执行的looper
	 * @return 返回一个race的Promise
	 */
	public static Promise<Object> race(final Collection<?> values,
			final Looper looper) {
		return Promise.make(new DirectFunction<Object>() {

			@Override
			public void run(final Locker<Object> locker) {
				for (final Object val : values) {
					@SuppressWarnings("unchecked")
					Promise<Object> pr = (Promise<Object>) Promise
							.resolveValue(val, looper);
					pr.then(new DirectResolver<Object, Void>() {

						@Override
						public Void resolve(Object newValue) {
							locker.resolve(newValue);
							return null;
						}

						@Override
						public Void reject(Exception exception) {
							locker.reject(exception);
							return null;
						}
					});
				}
			}
		}, looper);
	}

	/**
	 * 依次执行所有values,一个执行完成采取执行下一个,最后一个执行完成后返回<br>
	 * 中途有任何问题将会暂停执行直接抛出问题
	 *
	 * @param values 参与queue的所有值
	 * @return 返回一个queue的Promise
	 */
	public static Promise<Object[]> queue(final Collection<?>values) {
		return queue(values, Looper.myLooper());
	}

	/**
	 * 依次执行所有values,一个执行完成采取执行下一个,最后一个执行完成后返回<br>
	 * 中途有任何问题将会暂停执行直接抛出问题
	 *
	 * @param values 参与queue的所有值
	 * @param looper Promise执行的looper
	 * @return 返回一个queue的Promise
	 */
	public static Promise<Object[]> queue(final Collection<?>values,
			final Looper looper) {
		return Promise.make(new DirectFunction<Object[]>() {
			private Promise<Object> makePromise(final Iterator<?> iterator, final Object[] result, final int index) {
				Object val = iterator.next();
				@SuppressWarnings("unchecked")
				Promise<Object> pr = (Promise<Object>) Promise
						.resolveValue(val, looper);
				if (index < result.length - 1) {
					pr.then(new SimplePromiseResolver<Object, Object>() {
						@Override
						public Promise<Object> resolve(Object newValue) {
							result[index] = newValue;
							return makePromise(iterator, result, index + 1);
						}
					});
				}
				return pr;
			}

			@Override
			public void run(final Locker<Object[]> locker) {
				final Iterator<?> iterator = values.iterator();
				final Object[] result = new Object[values.size()];
				makePromise(iterator, result, 0).then(new SimpleResolver<Object, Void>() {
					@Override
					public Void resolve(Object newValue) {
						locker.resolve(result);
						return null;
					}
				}).exception(new ExceptionResolver<Void, Exception>() {
					@Override
					public Void onCatch(Exception exception) {
						locker.reject(exception);
						return null;
					}
				});
			}
		});
	}

	/**
	 * 生成一个TimeoutPromsie,规定的时间内抛出指定的异常,若Exception为空,则规定时间内返回Void结果
	 *
	 * @param timeout 指定的超时时间,该时间不会特别准确,因为是post到指定的Promise Looper上执行的
	 * @param exception 指定的异常,可以为null
	 * @return timeout exception promise
	 */
	public static Promise<Void> timeout(final long timeout, final Exception exception) {
		return timeout(timeout, exception, Looper.myLooper());
	}

	/**
	 * 生成一个TimeoutPromsie,规定的时间内抛出指定的异常,若Exception为空,则规定时间内返回Void结果
	 *
	 * @param timeout 指定的超时时间,该时间不会特别准确,因为是post到指定的Promise Looper上执行的
	 * @param exception 指定的异常,可以为null
	 * @param looper Promise执行的looper
	 * @return timeout exception promise
	 */
	public static Promise<Void> timeout(final long timeout, final Exception exception, final Looper looper) {
		return Promise.make(new Promise.DirectFunction<Void>() {
			@Override
			public void run(final Promise.Locker<Void> locker) {
				locker.postDelayed(new Runnable() {
					@Override
					public void run() {
						if (exception != null) {
							locker.reject(exception);
						} else {
							locker.resolve();
						}
					}
				}, timeout);
			}
		}, looper);
	}

	interface Function<D> {
		void run(final Locker<D> locker);
	}

	/**
	 * 一个直接处理过程函数，需要实现run方法
	 * 
	 * @author tangye
	 *
	 * @param <D>
	 *            实现的结果类型
	 */
	public interface DirectFunction<D> extends Function<D> {
	}

	/**
	 * 一个间接处理过程函数，需要实现run方法
	 * 
	 * @author tangye
	 *
	 * @param <D>
	 *            实现的Promise结果类型
	 */
	public interface PromiseFunction<D> extends Function<Promise<D>> {
	}

	/**
	 * 该方法用于执行处理结果
	 * 
	 * @author tangye
	 *
	 * @param <D>
	 *            处理的结果类型
	 */
	public static abstract class Locker<D> implements Defer<D> {

		/**
		 * 快捷方法{@link #resolve(D result)}<br>
		 * 当执行结果成功后，执行，执行结果为null
		 */
		public final void resolve() {
			resolve(null);
		}

		/**
		 * 返回是否已经完成
		 * @return 已经完成时，返回true
		 */
		public abstract boolean done();

		/**
		 * 如果当前Locker还没有完成, 在当前looper上post一个Runnable
		 * @param runnable 要执行的Runnable
		 */
		public abstract void post(Runnable runnable);

		/**
		 * 如果当前Locker还没有完成, 在当前looper上post一个Runnable
		 * @param runnable 要执行的Runnable
		 * @param delay, 延迟的时间
		 */
		public abstract void postDelayed(Runnable runnable, long delay);

		/**
		 * 在当前looper上删除一个Runnable
		 * @param runnable 要删除的Runnable
		 */
		public abstract void removeCallbacks(Runnable runnable);
	}

	/**
	 * 一个被缓存的Resolver包装对象，类似一个处理数据的Pipe<br>
	 * 输入为D类型，输出为D1类型
	 * 
	 * @author tangye
	 *
	 * @param <D>
	 *            该Resolver将会接收到的数据类
	 * @param <D1>
	 *            该Resolver处理后的数据类
	 */
	private static class CachedResolver<D, D1> {
		public final BaseResolver<D, D1> resolver;
		public final Locker<D1> locker;

		/**
		 * 记录下这个Resolver对象，并绑定一个处理输出的locker回调
		 * 
		 * @param resolver
		 *            被缓存的resolver对象
		 * @param locker
		 *            绑定一个处理结果locker回调
		 */
		public CachedResolver(BaseResolver<D, D1> resolver, Locker<D1> locker) {
			this.resolver = resolver;
			this.locker = locker;
		}
	}

	private static class PromiseCachedResolver<D, D1> extends CachedResolver<D, Promise<D1>> {
		public PromiseCachedResolver(PromiseResolver<D, D1> resolver,
									 Locker<Promise<D1>> locker) {
			super(resolver, locker);
		}
	}

}
