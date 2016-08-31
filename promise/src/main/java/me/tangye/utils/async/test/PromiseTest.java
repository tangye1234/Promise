package me.tangye.utils.async.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

import me.tangye.utils.async.Promise;
import me.tangye.utils.async.Promise.DirectFunction;
import me.tangye.utils.async.Promise.Locker;
import me.tangye.utils.async.Thenable;
import me.tangye.utils.async.resolver.DirectResolver;
import me.tangye.utils.async.resolver.ExceptionPromiseResolver;
import me.tangye.utils.async.resolver.ExceptionResolver;
import me.tangye.utils.async.resolver.PromiseDeferred;
import me.tangye.utils.async.resolver.SimplePromiseResolver;
import me.tangye.utils.async.resolver.SimpleResolver;

public class PromiseTest {
	
	public static void main(String...args) {		
		Promise<Integer> p1 = Promise.make(new DirectFunction<Integer>() {

			@Override
			public void run(final Locker<Integer> locker) {
				new Thread() {
					public void run() {
						try {
							System.out.println("p1 is running");
							Thread.sleep(5000);
						} catch (InterruptedException e) {
							locker.reject(e);
						}
						locker.resolve(100);
						System.out.println("p1 is over");
					}
				}.start();
			}
			
		});


		Promise<Object> px = Promise.make(new DirectFunction<Object>() {
			@Override
			public void run(Locker<Object> locker) {
				locker.reject(new TimeoutException("timeout"));
			}
		}).exception(new ExceptionResolver<Object, TimeoutException>() {
			@Override
			public Object onCatch(TimeoutException exception) {
				exception.printStackTrace();
				throw Promise.newException(exception);
			}
		});

		px.exception(new ExceptionPromiseResolver<Object, InterruptedException>() {
			@Override
			public Promise<Object> onCatch(InterruptedException exception) {
				throw Promise.newException(new IOException());
			}
		});
		
		Promise<Integer> p1p = p1.clone();
				
		Promise<Integer> p2 = Promise.make(new DirectFunction<Integer>() {
			@Override
			public void run(final Locker<Integer> locker) {
				new Thread() {
					public void run() {
						try {
							System.out.println("p2 is running");
							Thread.sleep(4000);
						} catch (InterruptedException e) {
							locker.reject(e);
						}
						locker.resolve(100);
						System.out.println("p2 is over");
					}
				}.start();
			}
		});
		
		ArrayList<Promise<?>> list = new ArrayList<Promise<?>>();
		list.add(p1);
		list.add(p1p);
		list.add(p2);
		
		Promise.race(list).then(new DirectResolver<Object, Void>() {

			@Override
			public Void resolve(Object newValue) {
				System.out.println("race complete" + newValue);
				return null;
			}

			@Override
			public Void reject(Exception exception) {
				return null;
			}
		});		
		
		p1.then(new DirectResolver<Integer, String>() {

			@Override
			public String resolve(Integer newValue) {
				System.out.println(newValue);
				return "p1 resolved";
			}

			@Override
			public String reject(Exception exception) {
				throw Promise.newException(exception);
			}
		}).then(new SimplePromiseResolver<String, Integer>() {

			@Override
			public Promise<Integer> resolve(String newValue) {
				System.out.println(newValue);
				return Promise.make(new DirectFunction<Integer>() {

					@Override
					public void run(final Locker<Integer> locker) {
						new Thread() {
							public void run() {
								try {
									Thread.sleep(5000);
								} catch (InterruptedException e) {
									locker.reject(e);
								}
								locker.resolve(3000);
							}
						}.start();
					}
					
				});
			}
			
		}).then(new SimpleResolver<Integer, Integer>() {

			@Override
			public Integer resolve(Integer newValue) {
				System.out.println("then resolve a new value=" + newValue);
				return null;
			}
		}).then(new SimpleResolver<Number, Integer>() {
			@Override
			public Integer resolve(Number newValue) {
				return null;
			}
		});

		// making a deferred, then use defer to propagate a promise
		final PromiseDeferred<Integer> defer = PromiseDeferred.make();

		final PromiseDeferred<String> defer2 = PromiseDeferred.make();




		defer.promise().then(new SimpleResolver<Integer, Void>() {
			@Override
			public Void resolve(Integer newValue) {
				System.out.println("Defer:::" + newValue);
				return null;
			}
		}).exception(new ExceptionResolver<Void, NullPointerException>() {
			@Override
			public Void onCatch(NullPointerException exception) {
				exception.printStackTrace();
				return null;
			}
		}).exception(new ExceptionResolver<Void, TimeoutException>() {
			@Override
			public Void onCatch(TimeoutException exception) {
				exception.printStackTrace();
				return null;
			}
		});

		new Thread() {
			public void run() {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					defer.reject(e);
				}
				defer.reject(new TimeoutException("defer timeout"));
			}
		}.start();


		Thenable<Integer> th = p1;

		Thenable<Integer> th2 = th.then(new SimpleResolver<Number, Integer>() {
			@Override
			public Integer resolve(Number newValue) {
				return 4;
			}
		});

		Thenable<Number> th3 = th2.cast();
		th3.then(new SimpleResolver<Number, Object>() {
			@Override
			public Object resolve(Number newValue) {
				System.out.println(newValue);
				return null;
			}
		});

	}

}
