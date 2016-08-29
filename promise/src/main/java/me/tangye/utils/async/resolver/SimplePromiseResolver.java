package me.tangye.utils.async.resolver;

import me.tangye.utils.async.Promise;

/**
 * 与 {@link PromiseResolver} 一致，只是无需处理异常，异常将会继续抛给下一个Promise
 * @author tangye
 *
 * @param <D> 接收到的数据类型
 * @param <D1> 处理后的Promise数据类型
 */
public abstract class SimplePromiseResolver<D, D1> implements PromiseResolver<D, D1> {
	@Override
	public final Promise<D1> reject(Exception exception) {
		throw Promise.newException(exception);
	}
}