package me.tangye.utils.async.resolver;

import me.tangye.utils.async.Promise;

/**
 * 与 {@link DirectResolver} 一致，只是仅仅处理一个总结果(结果中含有exception 或者 result), 参考<br>
 * @see Promise#done(DoneResolver) Promise.finalResult(FinalResolver)
 * 该方法只处理结果(正常异常都会走),并且不影响结果传递
 * @author tangye
 *
 * @param <D> 接收和输出的数据类型
 */
public abstract class DoneResolver<D> implements DirectResolver<D,D> {
    @Override
    public final D resolve(D newValue) {
        callback(null, newValue);
        return newValue;
    }

    @Override
    public final D reject(Exception exception) {
        callback(exception, null);
        throw Promise.newException(exception);
    }

    /**
     * 将Promise转化为标准Callback
     * @param exception 如果有问题,exception不为null
     * @param result 如果没问题,将会获得这个result
     */
    public abstract void callback(Exception exception, D result);
}
