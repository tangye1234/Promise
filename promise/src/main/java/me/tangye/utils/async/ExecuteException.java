package me.tangye.utils.async;

public final class ExecuteException extends RuntimeException {

	private static final long serialVersionUID = 6037102241576792115L;
	
	public ExecuteException() {
		super();
	}

	public ExecuteException(String detailMessage) {
		super(detailMessage);
	}

	public ExecuteException(Throwable throwable) {
		super(throwable);
	}

	public ExecuteException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

}
