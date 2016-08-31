package me.tangye.utils.async.resolver;

import me.tangye.utils.async.Promise;

/**
 * 与 {@link DirectResolver} 一致，只是仅仅处理一个总结果, 参考<br>
 * @see Promise#finalResult(FinalResolver) Promise.finalResult(FinalResolver)
 * 该方法只处理结果(正常异常都会走),并且不影响结果传递
 * @author tangye
 *
 * @param <D> 接收和输出的数据类型
 */
public abstract class FinalResolver<D> implements DirectResolver<D,D> {

    @Override
    public final D resolve(D newValue) {
        onFinal(true);
        return newValue;
    }

    @Override
    public final D reject(Exception exception) {
        onFinal(false);
        throw Promise.newException(exception);
    }

    public abstract void onFinal(boolean success);
}
