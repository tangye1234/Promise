package me.tangye.utils.async;

import me.tangye.utils.async.Promise.Function;
import me.tangye.utils.async.resolver.DirectResolver;
import me.tangye.utils.async.resolver.PromiseResolver;

/**
 * 一个标准的Promise对象必须实现以下Thenable接口<br>
 * then方法实现了Promise的链式迭代方法
 * getThen方法提供了Promise转换的可能
 * @author tangye
 *
 * @param <D> 该Thenable接收数据返回类型
 */
public interface Thenable<D> {
	/**
	 * 执行该Promise的后续操作，使用then可以获取Promise操作结果，并进一步处理
	 * 
	 * @param resolver 一个直接处理的解析器，{@link DirectResolver}
	 * @param <D1> 转化后新的Promise结果返回类型
	 * @return 返回一个新的Promise，其继承已有的Promise的Runtime Looper
	 */
	<D1> Thenable<D1> then(final DirectResolver<? super D, ? extends D1> resolver);

	/**
	 * 执行该Promise的后续操作，使用then可以获取Promise操作结果，并进一步处理
	 * 
	 * @param resolver 一个间接处理的解析器，{@link PromiseResolver}
	 * @param <D1> 转化后新的Promise结果返回类型
	 * @return 返回一个新的Promise，其继承已有的Promise的Runtime Looper
	 */
	<D1> Thenable<D1> then(final PromiseResolver<? super D, ? extends D1> resolver);

	/**
	 * 将该Thenable的结果作为一个新的Function的执行过程<br>
	 * 在完成Promise转换时非常有用<br>
	 * <code>
	 *     Promise.make(thenable.getThen());
	 * </code>
	 * <br>
	 * 返回的Function, {@link Promise }只会返回 {@link Promise.DirectFunction} 或者 {@link Promise.PromiseFunction}
	 * 
	 * @return Function执行过程
	 */
	Function<D> getThen();

	/**
	 * 类型转变
	 * @param <D1> 转换为新的类型
	 * @return Thenable
     */
	<D1> Thenable<D1> cast();
}