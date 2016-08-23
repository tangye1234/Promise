package me.tangye.utils.async.resolver;

/**
 * Created by coffee3689 on 16/8/23.
 * 标准的延迟对象，等待类型为D，类似Future&lt;D&gt;
 */
public interface Defer<D> extends BaseResolver<D, Void> {
}
