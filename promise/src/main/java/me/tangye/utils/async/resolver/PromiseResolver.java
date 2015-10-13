package me.tangye.utils.async.resolver;

import me.tangye.utils.async.Promise;

/**
 * 异步解析器，解析Promise的结果或者异常<br>
 * 返回的Promise数据将直接用于下一次构造Promise的期待值
 * 
 * @author tangye
 *
 * @param <D> 接收到的数据类型
 * @param <D1> 处理后的Promise数据类型
 */
public interface PromiseResolver<D, D1> extends BaseResolver<D, Promise<D1>> {
}
