package me.tangye.utils.async;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import me.tangye.utils.async.resolver.BaseResolver;
import me.tangye.utils.async.resolver.Deferred;
import me.tangye.utils.async.resolver.DirectResolver;
import me.tangye.utils.async.resolver.DoneResolver;
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
 * @param <D> 该Future模型的期望返回数据类型
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
	private List<CachedResolver<? super D, ?>> deferreds;

	/* a resolver who receives promise as a value */
	@SuppressWarnings("FieldCanBeLocal")
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
	 * @hide 请不要使用这个方法制造Promise
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
	 * the promise being cloned will provide the result to the new promise,
	 * the new promise will not run twice the internal function.
	 * call p.clone() mean call Promise.resolve(p)
	 * @see PromiseFactory
	 * @see #clone(Looper)
	 * @return new Promise
	 */
	@SuppressWarnings({"unchecked", "CloneDoesntCallSuperClone"})
	public Promise<D> clone() {
		return clone(handler.getLooper());
	}

	/**
	 * clone a Promise with a new looper
	 * @param looper new Promise looper
	 * @return new Promise
	 */
	public Promise<D> clone(Looper looper) {
		return Promise.resolve(this, looper);
	}

	private <T> void postResolve(final Function<T> function,
			final Deferred<T> internalResolver) {
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

	private class ProcessResolver<T> implements Deferred<T> {

		/**
		 * deferred interface<br>
		 * a private resolve function for internal use<br>
		 * many times, arguments receive a new value that is handled by<br>
		 * a {@link Promise} which is probably not the current one<br>
		 * This is not thread safe, but only to resolve a new result of current
		 * promise <br>
		 * 每当locker.resolve执行时，该方法发生回调, 该方法属于内部公共defer对象
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
						// 产生迭代效果(recursively call ProcessResolver)
						// 只要返回类型是一个Thenable, 比如Promise
						if (newValue instanceof Thenable) {
							final Thenable<D> p = (Thenable<D>) newValue;
							doResolve(p.getThen(), NON_PROMISE_RESOLVER);
						} else {
							// 记录最终的结果
							state = true;
							// here we may get cast exception due do wrong resolve situation
							// FIXME currently print stack trace and reject
							nonPromiseValue = (D) newValue;
							finale();
						}
					} catch (Exception e) {
						if (e instanceof ClassCastException) {
							e.printStackTrace();
							// FIXME maybe this exception should be thrown
						}
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
	@SuppressWarnings("WeakerAccess")
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
	public DirectFunction<D> getThen() {
		return new DirectFunction<D>() {
			@Override
			public void run(final Locker<D> locker) {
				Promise.this.then(locker);
			}
		};
	}

	@Override
	public <D1> Promise<D1> then(final DirectResolver<? super D, ? extends D1> resolver) {
		return Promise.make(new DirectFunction<D1>() {
			@Override
			public void run(Locker<D1> locker) {
				handle(new CachedResolver<>(resolver, locker));
			}
		}, handler.getLooper());
	}

	@Override
	public <D1> Promise<D1> then(final PromiseResolver<? super D, ? extends D1> resolver) {
		return Promise.make(new PromiseFunction<D1>() {
			@Override
			public void run(final Locker<Promise<? extends D1>> locker) {
				handle(new PromiseCachedResolver<>(resolver, locker));
			}
		}, handler.getLooper());
	}

	@Override
	public <D1> Promise<D1> cast() {
		return then(new SimpleResolver<D, D1>() {
			@Override
			public D1 resolve(D newValue) {
				//noinspection unchecked
				return (D1) newValue;
			}
		});
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
	 * A short-hand for then(DoneResolver)<br>
	 * 当Promise有结果时,触发
	 *
	 * @param doneResolver 结果处理器
	 * @return 上一个Promise的延续(不改变任何传递内容)
     */
	public Promise<D> done(final DoneResolver<D> doneResolver) {
		return then(doneResolver);
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

	private <D1> void handle(final CachedResolver<? super D, D1> cachedResolver) {
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
				BaseResolver<? super D, ? extends D1> t = cachedResolver.resolver;
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
	 * Running an async function with the defer who will asynchronously invoke
	 * resolve or reject once, acting as the deferred object of the function argument <br>
	 * We use internalResolver deferred object to run the function for a result <br>
	 * 
	 * @param function the running function to execute
	 * @param internalResolver the specific resolver callback as a deferred
	 *
	 */
	@SuppressWarnings("WeakerAccess")
	protected static <T> void doResolve(final Function<T> function,
										final Deferred<T> internalResolver) {
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

		ValuePromise(T nonPromiseValue, Looper looper) {
			super(null, looper);
			if (nonPromiseValue instanceof Exception) {
				throw new IllegalArgumentException("value should not be exception");
			}
			this.nonPromiseValue = nonPromiseValue;
			this.state = true;
		}
	}

	/**
	 * 立马返回一个指定thenable/promise对应的Promise对象
	 *
	 * @param thenable 一个Thenable对象
	 * @return 值对应的Promise
	 */
	public static <D> Promise<D> resolve(final Thenable<D> thenable) {
		return resolve(thenable, Looper.myLooper());
	}

	/**
	 * 立马返回一个指定thenable/promise对应的Promise对象
	 *
	 * @param thenable 一个Thenable对象
	 * @param looper Promise执行所在Looper
	 * @return 值对应的Promise
	 */
	public static <D> Promise<D> resolve(final Thenable<D> thenable, Looper looper) {
		try {
			Function<D> f = thenable.getThen();
			return resolveDirectFunction(f, looper);
		} catch (Exception e) {
			return Promise.reject(e, looper);
		}
	}

	/**
	 * 立马返回一个指定function对应的Promise对象
	 *
	 * @param function 一个DirectFunction对象
	 * @return 值对应的Promise
	 */
	public static <D> Promise<D> resolve(final DirectFunction<D> function) {
		return resolve(function, Looper.myLooper());
	}

	/**
	 * 立马返回一个指定function对应的Promise对象
	 *
	 * @param function 一个DirectFunction对象
	 * @param looper Promise执行所在Looper
	 * @return 值对应的Promise
	 */
	public static <D> Promise<D> resolve(final DirectFunction<D> function, Looper looper) {
		try {
			return resolveDirectFunction(function, looper);
		} catch (Exception e) {
			return Promise.reject(e, looper);
		}
	}

	/**
	 * 立马返回一个指定value对应的Promise对象
	 *
	 * @param value 指定value值
	 * @param <D> 对应的类型
     * @return 值对应的Promise
     */
	public static <D> Promise<D> resolve(final D value) {
		return resolve(value, Looper.myLooper());
	}

	/**
	 * 立马返回一个指定value对应的Promise对象
	 *
	 * @param value 指定value值
	 * @param <D> 对应的类型
	 * @param looper Promise执行所在Looper
	 * @return 值对应的Promise
	 */
	public static <D> Promise<D> resolve(final D value, Looper looper) {
		if (value == null) {
			return new ValuePromise<>(null, looper);
		} else if (value instanceof Exception) {
			return Promise.reject((Exception) value, looper);
		}
		return new ValuePromise<>(value, looper);
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
	 * @deprecated try use Promise.resolve() instead
	 */
	public static <D> Promise<?> resolveValue(final D value, Looper looper) {
		if (value == null)
			return new ValuePromise<Void>(null, looper);
		if (value instanceof Thenable) {
			Thenable<?> t = (Thenable<?>) value;
			try {
				Function<?> f = t.getThen();
				return resolveFunction(f, looper);
			} catch (Exception e) {
				return Promise.reject(e, looper);
			}
		} else if (value instanceof Function) {
			try {
				Function<?> f = (Function<?>) value;
				return resolveFunction(f, looper);
			} catch (Exception e) {
				return Promise.reject(e, looper);
			}
		} else if (value instanceof Exception) {
			return Promise.reject((Exception) value, looper);
		}
		return new ValuePromise<>(value, looper);
	}

	private static Promise<?> resolveFunction(Function<?> f, Looper looper) {
		if (PromiseFunction.class.isInstance(f)) {
			return Promise.make((PromiseFunction<?>) f, looper);
		} else if (DirectFunction.class.isInstance(f)) {
			return Promise.make((DirectFunction<?>) f, looper);
		} else {
			throw new IllegalArgumentException(
					"Thenable.getThen should only be PromiseFunction or DirectFunction");
		}
	}

	private static <D> Promise<D> resolveDirectFunction(Function<D> f, Looper looper) {
		if (DirectFunction.class.isInstance(f)) {
			return Promise.make((DirectFunction<D>) f, looper);
		} else {
			throw new IllegalArgumentException("Unsupported getThen()");
		}
	}

	/**
	 * 立马返回一个指定value对应的Promise对象
	 *
	 * @param value
	 *            非Thenable，可以直接处理为Promise
	 * @return 新的ValuePromise
	 * @deprecated
	 * @see #resolve(Object)
	 */
	@Deprecated
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
	 * @deprecated
	 * @see #resolve(Object, Looper)
	 */
	@Deprecated
	public static <D> Promise<D> resolveNonPromiseValue(final D value, Looper looper) {
		if (value instanceof Thenable || value instanceof Function) {
			throw new IllegalArgumentException("Value should be non-promise/non-function value, " +
					"this value is an instance of " + value.getClass());
		}
		return new ValuePromise<>(value, looper);
	}

	/**
	 * 立马返回一个指定Exception的Promise对象
	 * 
	 * @param e Exception值
	 * @return return 一个Exception Promise
	 * @see #reject(Exception)
	 * @see #reject(Exception, Looper)
	 * @deprecated
	 */
	@Deprecated
	public static Promise<Void> rejectException(final Exception e) {
		return rejectException(e, Looper.myLooper());
	}

	/**
	 * 立马返回一个指定Exception的Promise对象
	 * 
	 * @param e Exception值
	 * @param looper Promise所在Looper
	 * @return return 一个Exception Promise
	 * @see #reject(Exception)
	 * @see #reject(Exception, Looper)
	 * @deprecated
	 */
	@Deprecated
	public static Promise<Void> rejectException(final Exception e, Looper looper) {
		return reject(e, looper);
	}

	/**
	 * 立马返回一个指定Exception的Promise对象
	 *
	 * @param e Exception值
	 * @return return 一个Exception Promise
	 * @see #reject(Exception, Looper)
	 */
	public static <D> Promise<D> reject(final Exception e) {
		return reject(e, Looper.myLooper());
	}

	/**
	 * 立马返回一个指定Exception的Promise对象
	 *
	 * @param e Exception值
	 * @param looper Promise所在Looper
	 * @return return 一个Exception Promise
	 * @see #reject(Exception)
	 */
	public static <D> Promise<D> reject(final Exception e, Looper looper) {
		return new Promise<>(new DirectFunction<Void>() {
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
	 * @deprecated 请使用 {@link #newException(Exception) throw Promise.newException(exception)} 来代替
	 * @see #newException(Exception)
	 */
	@Deprecated
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
	 * 同时运行多个Promise/Function，当所有Promise/Function结果都完成后，返回所有结果的Object数组
	 *
	 * @param objects 参与all的所有值
	 * @return 所有值的总Promise
     */
	public static Promise<Object[]> all(Object ... objects) {
		return all(Arrays.asList(objects));
	}

	/**
	 * 同时运行多个Promise/Function，当所有Promise/Function结果都完成后，返回所有结果的Object数组
	 * 
	 * @param values 参与all的所有值
	 * @return 所有值的总Promise
	 */
	public static Promise<Object[]> all(final Collection<?> values) {
		return all(values, Looper.myLooper());
	}

	/**
	 * 同时运行多个Promise/Function，当所有Promise/Function结果都完成后，返回所有结果的Object数组
	 * 
	 * @param values 参与all的所有值
	 * @param looper
	 *            在指定的looper上构造all promise, 如果为Collection中含有Function,Function也将在指定的looper运行
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
					if (val instanceof Thenable || val instanceof Function) {
						res(i, Promise.resolve(val, looper));
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
	 * 同时处理多个Promise/Function，第一个返回的value将会触发Promise处理完成
	 *
	 * @param objects 参与race的所有值数组
	 * @return 返回一个race的Promise
     */
	public static Promise<Object> race(final Object ... objects) {
		return race(Arrays.asList(objects));
	}

	/**
	 * 同时处理多个Promise/Function，第一个返回的value将会触发Promise处理完成
	 * 
	 * @param values 参与race的所有值
	 * @return 返回一个race的Promise
	 */
	public static Promise<Object> race(final Collection<?> values) {
		return race(values, Looper.myLooper());
	}

	/**
	 * 同时处理多个Promise/Function，第一个返回的value将会触发Promise处理完成
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
					Promise<?> p;
					if (val instanceof Promise) {
						p = (Promise<?>) val;
					} else if (val instanceof Thenable || val instanceof Function) {
						p = Promise.resolve(val, looper);
					} else {
						locker.resolve(val);
						continue;
					}
					@SuppressWarnings("unchecked")
					Promise<Object> pr = (Promise<Object>) p;
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
	 * 依次执行所有Functions,一个执行完成才去执行下一个,最后一个执行完成后返回<br>
	 * 中途有任何问题将会暂停执行直接抛出问题
	 *
	 * @param functions 参与series的所有值
	 * @return 返回一个series的Promise
	 */
	public static Promise<Object[]> series(DirectFunction<?> ... functions) {
		return series(Arrays.asList(functions));
	}

	/**
	 * 依次执行所有Functions,一个执行完成才去执行下一个,最后一个执行完成后返回<br>
	 * 中途有任何问题将会暂停执行直接抛出问题
	 *
	 * @param values 参与series的所有值
	 * @return 返回一个series的Promise
	 */
	public static Promise<Object[]> series(final Collection<DirectFunction<?>> values) {
		return series(values, Looper.myLooper());
	}

	/**
	 * 依次执行所有Functions,一个执行完成才去执行下一个,最后一个执行完成后返回<br>
	 * 中途有任何问题将会暂停执行直接抛出问题
	 *
	 * @param values 参与series的所有值
	 * @param looper Promise执行的looper
	 * @return 返回一个series的Promise
	 */
	public static Promise<Object[]> series(final Collection<DirectFunction<?>> values,
										   final Looper looper) {
		return Promise.make(new DirectFunction<Object[]>() {
			private Promise<Object> makePromise(final Iterator<DirectFunction<?>> iterator, final Object[] result, final int index) {
				DirectFunction<?> val = iterator.next();
				@SuppressWarnings("unchecked")
				Promise<Object> pr = (Promise<Object>) Promise.make(val, looper);
				if (index < result.length - 1) {
					pr = pr.then(new SimplePromiseResolver<Object, Object>() {
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
				final Iterator<DirectFunction<?>> iterator = values.iterator();
				final Object[] result = new Object[values.size()];
				makePromise(iterator, result, 0).then(new DirectResolver<Object, Void>() {
					@Override
					public Void resolve(Object newValue) {
						locker.resolve(result);
						return null;
					}
					@Override
					public Void reject(Exception exception) {
						locker.reject(exception);
						return null;
					}
				});
			}
		});
	}

	/**
	 * 生成一个Timeout Promise,规定的时间内抛出指定的异常,若Exception为空,则规定时间内返回Void结果
	 *
	 * @param timeout 指定的超时时间,该时间不会特别准确,因为是post到指定的Promise Looper上执行的
	 * @param exception 指定的异常,可以为null
	 * @return timeout [exception] promise
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
	public interface PromiseFunction<D> extends Function<Promise<? extends D>> {
	}

	/**
	 * 该方法用于执行处理结果, 属于Function的run方法的处理对象参数 <br>
	 * Locker作为一个Android专属的Deferred对象,除了可以用于async调用resolve或者reject <br>
	 * 还可以使用 post postDelay done removeCallbacks 等特殊方法
	 * 
	 * @author tangye
	 * @param <D> 处理的结果类型
	 */
	public static abstract class Locker<D> implements Deferred<D> {

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
	 * @author tangye
	 * @param <T> 该Resolver将会接收到的数据类
	 * @param <R> 该Resolver处理后的数据类
	 */
	private static class CachedResolver<T, R> {
		final BaseResolver<T, ? extends R> resolver;
		final Locker<R> locker;

		/**
		 * 记录下这个Resolver对象，并绑定一个处理输出的locker回调
		 * 
		 * @param resolver
		 *            被缓存的resolver对象
		 * @param locker
		 *            绑定一个处理结果locker回调
		 */
		CachedResolver(BaseResolver<T, ? extends R> resolver, Locker<R> locker) {
			this.resolver = resolver;
			this.locker = locker;
		}
	}

	/**
	 * 一个被缓存的PromiseResolver包装对象，类似一个处理数据的Pipe<br>
	 * 输入为D类型，输出为D1类型
	 * @author tangye
	 * @param <T> 该Resolver将会接收到的数据类
	 * @param <R> 该Resolver处理后的数据类
	 */
	private static class PromiseCachedResolver<T, R> extends CachedResolver<T, Promise<? extends R>> {
		PromiseCachedResolver(PromiseResolver<T, ? extends R> resolver,
									 Locker<Promise<? extends R>> locker) {
			super(resolver, locker);
		}
	}
}
