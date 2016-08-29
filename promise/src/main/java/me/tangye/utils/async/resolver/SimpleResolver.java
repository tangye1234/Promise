package me.tangye.utils.async.resolver;

import me.tangye.utils.async.Promise;

/**
 * 与 {@link DirectResolver} 一致，只是无需处理异常，异常将会继续抛给下一个Promise
 * @author tangye
 *
 * @param <D> 接收到的数据类型
 * @param <D1> 处理后的数据类型
 */
public abstract class SimpleResolver<D, D1> implements DirectResolver<D, D1> {
	@Override
	public final D1 reject(Exception exception) {
		throw Promise.newException(exception);
	}
}
