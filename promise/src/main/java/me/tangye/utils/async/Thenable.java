package me.tangye.utils.async;

import me.tangye.utils.async.Promise.Function;
import me.tangye.utils.async.resolver.DirectResolver;
import me.tangye.utils.async.resolver.PromiseResolver;

/**
 * 一个标准的Promise对象必须实现以下接口<br>
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
	 * @param <D> Promise的处理返回结果类型
	 * @param <D1> 转化后新的Promise结果返回类型
	 * @return 返回一个新的Promise，其继承已有的Promise的Runtime Looper
	 */
	<D1> Promise<D1> then(final DirectResolver<D, D1> resolver);

	/**
	 * 执行该Promise的后续操作，使用then可以获取Promise操作结果，并进一步处理
	 * 
	 * @param resolver 一个间接处理的解析器，{@link PromiseResolver}
	 * @param <D> Promise的处理返回结果类型
	 * @param <D1> 转化后新的Promise结果返回类型
	 * @return 返回一个新的Promise，其继承已有的Promise的Runtime Looper
	 */
	<D1> Promise<D1> then(final PromiseResolver<D, D1> resolver);

	/**
	 * 将该Thenable的结果作为一个新的Function的执行过程<br>
	 * 在完成Promise转换时非常有用<br>
	 * 返回的Function请使用 {@link DirectFunction} 或者 {@link PromiseFunction}
	 * 
	 * @param <D> Thenable代表的处理结果返回类型
	 * @return Function执行过程
	 */
	Function<D> getThen();
}