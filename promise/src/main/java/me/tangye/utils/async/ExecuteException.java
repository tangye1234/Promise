package me.tangye.utils.async;

public final class ExecuteException extends RuntimeException {

	private static final long serialVersionUID = 6037102241576792115L;

	/**
	 * Promise内部异常对象, 用于对非RuntimeException进行Runtime改造,再在resover的reject中进行解包
	 */
	public ExecuteException() {
		super();
	}

	/**
	 * Promise内部异常对象, 用于对非RuntimeException进行Runtime改造,再在resover的reject中进行解包
	 */
	public ExecuteException(String detailMessage) {
		super(detailMessage);
	}

	/**
	 * Promise内部异常对象, 用于对非RuntimeException进行Runtime改造,再在resover的reject中进行解包
	 */
	public ExecuteException(Throwable throwable) {
		super(throwable);
	}

	/**
	 * Promise内部异常对象, 用于对非RuntimeException进行Runtime改造,再在resover的reject中进行解包
	 */
	public ExecuteException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

}
