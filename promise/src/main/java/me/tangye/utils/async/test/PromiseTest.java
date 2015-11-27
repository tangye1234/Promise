package me.tangye.utils.async.test;

import java.util.ArrayList;

import me.tangye.utils.async.Promise;
import me.tangye.utils.async.Promise.DirectFunction;
import me.tangye.utils.async.Promise.Locker;
import me.tangye.utils.async.resolver.DirectResolver;
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
				Promise.throwException(exception);
				return null;
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
			
		}).then(new SimpleResolver<Integer, Void>() {

			@Override
			public Void resolve(Integer newValue) {
				System.out.println("then resolve a new value=" + newValue);
				return null;
			}
		});
		System.out.println("start Promise");

	}

}
