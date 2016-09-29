package me.tangye.utils.async;

import me.tangye.utils.async.resolver.PromiseDeferred;

/**
 *
 * 具体需要重复做的过程最终封装成一个 Promise对象,
 * RetryHandler会使用Promise对象监听结果是否失败,如果重复
 */
public abstract class PromiseFactory<T> implements Promise.Function<T> {

    /**
     * 构造一个异步过程
     * @param deferred 可以传入一个外部的deferred对象
     * @return 构造异步过程的Promise
     */
    public final Promise<T> make(PromiseDeferred<T> deferred) {
        if (!deferred.done()) {
            run(deferred);
        }
        return deferred.promise();
    }

    /**
     * 构造一个异步过程
     * @return 构造异步过程的Promise
     */
    public final Promise<T> make() {
        PromiseDeferred<T> deferred = PromiseDeferred.make();
        return make(deferred);
    }

    /**
     * 通过一个DirectFunction构造一个PromiseFactory
     * @param function 需要重复执行的过程
     * @param <T> 生成的Promise的类型
     * @return 一个实现好的PromiseFactory
     */
    public static <T> PromiseFactory<T> create(final Promise.DirectFunction<T> function) {
        return new PromiseFactory<T>() {
            @Override
            public void run(Promise.Locker<T> locker) {
                function.run(locker);
            }
        };
    }
}
