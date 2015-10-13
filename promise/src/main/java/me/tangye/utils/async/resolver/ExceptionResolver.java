package me.tangye.utils.async.resolver;

import me.tangye.utils.async.ExecuteException;
import me.tangye.utils.async.Promise;

/**
 * 与 {@link DirectResolver} 一致，只是仅仅处理指定的异常<br>
 * 如果指定异常不存在，则所有数据传递到下一次Promise
 * @author tangye
 *
 * @param <D> 接收和输出的数据类型
 * @param <E> 要求捕获到的异常类型
 */
public abstract class ExceptionResolver<D, E extends Throwable> implements DirectResolver<D, D> {

	@Override
	public final D resolve(D newValue) {
		return newValue;
	}

	@Override
	public final D reject(Exception exception) {
		try {
			@SuppressWarnings("unchecked")
			E e = (E) exception;
			return onCatch(e);
		} catch(ExecuteException e0) {
			throw e0;
		} catch(Exception e0) {
			Promise.throwException(exception);
		}
		return null;
	}

	public abstract D onCatch(E exception);

}
