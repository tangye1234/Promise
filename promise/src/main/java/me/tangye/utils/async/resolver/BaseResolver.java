package me.tangye.utils.async.resolver;

/**
 * Resolver基类，请不要使用该基类构造用于Promise的Resolver<br>
 * 请使用 {@link DirectResolver} 或者 {@link PromiseResolver} 来构造自定义的Resolver
 * @author tangye
 *
 * @param <T>
 * @param <R>
 */
public interface BaseResolver<T, R> {
	/**
	 * 异步回调结果成功方法，如果你想直接处理异常抛出<br>
	 * 可以使用 throw {@link me.tangye.utils.async.Promise#newException(Exception) Promise.newException(e)}
	 * 来包装实际的异常
	 * @param newValue resolve的结果
	 * @return 解析结果
	 * 
	 */
	R resolve(T newValue);
	/**
	 * 异步回调结果失败方法，如果你想直接处理异常抛出<br>
	 * 可以使用 throw {@link me.tangye.utils.async.Promise#newException(Exception) Promise.newException(e)}
	 * 来包装实际的异常
	 * @param exception 发生的exception
	 * @return 挽救结果
	 * 
	 */
	R reject(Exception exception);
}
