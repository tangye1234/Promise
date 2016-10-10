A Promise for android [![Build Status](https://travis-ci.org/tangye1234/Promise.png)](https://travis-ci.org/tangye1234/Promise)
======================

A Promise library for android which follows Promise/A+ standard

[![Promise/A+](https://rawgithub.com/promises-aplus/promises-spec/master/logo.svg)](https://promisesaplus.com/ "Promise/A+")

Introduction
=============

Nearly, all android promise open-source projects offer us a tool, which has async-thread-execution being involved
into their component. I must say, it is good, since `AsyncTask` is just like that - executing tasks in
a pre-built task-executors, or let user provide one for `AsyncTask`. But I think, Promise should just focus on
async processing functions with callbacks, without making any extra thread or using executors to handle `runnable`
or `Callable` process.

The Promise lib here dose not do any thread-making stuff, or not depends on any executors. It just produce a
`Deferred` object, letting you decide when to `resolve` a deferred or `reject` a deferred. Then making `then` on
a desired looper thread.

If you want your runnable to be running asynchronously, just make your own thread to take charge of running and
remember to `resolve` or `reject` the result.

While in `thenable` method, you should supply us a `resolver` which can turn the promise result into another promise
just like Promise in javascript.

And, The Promise lib here also provide us a lot of features that js promise owns too. Here is the example:


```java


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
		th3.then(new SimpleResolver<Number, String>() {
			@Override
			public String resolve(Number newValue) {
				System.out.println(newValue);
				return String.valueOf(newValue);
			}
		}).then(defer2);

		PromiseFactory.create(new DirectFunction<String>() {
			@Override
			public void run(Locker<String> locker) {
				locker.resolve("444");
			}
		}).make().then(defer2);

		Promise<Integer> intPromise = Promise.resolve(5);
		Promise<Integer> exPromise = Promise.reject(new Exception());
		Promise<Integer> clonePromise = Promise.resolve(th);

		Promise.all(intPromise, exPromise, clonePromise);


	}

}

```

Promise Static Method
-------
* `Promise.make` is a promise factory method which can make a new promise
* `Promise.all` equals to javascript `Promise.all`
* `Promise.race` equals to javascript `Promise.race`
* `Promise.series` means running functions sequentially
* `Promise.resolve` equals to javascript `Promise.resolve`
* `Promise.reject` equals to javascript `Promise.reject`
* `Promise.newException` is to wrap and convert any exception into an internal runtime exception
* `Promise.timeout` is to make an n milli-seconds timeout promise


Promise Instance Method
-------
* `promise.then` equals to javascript `promise.then`, we can resolve async/synchronized
* `promise.getThen` can make a new `DirectFunction` for making a new android promise
* `promise.exception` equals to javascript `promise.catch`
* `promise.finalResult` equals to javascript `promise.done`
* `promise.cast` can safely casting a Promise<T> to a NEW Promise<R>


Promise Resolver Types
-------
in javascript, calling then method is as simple as promise.then(resolve, reject)
in java, it's not easy to generate two object-arguments, so let's make it simple, that we can call `.then` like this
`promise.then(resolver)` within which the resolver implements two method `resolve` and `reject`
The Promise lib provides many pre-built resolvers, and all are based on `DirectResolver` or `PromiseResolver`.
The former returns a result directly while the latter returns a promise
* `DirectResolver` turns a result into a new one
* `PromiseResolver` turns a result into a promise
* `SimpleResolver` and `SimplePromiseResolver` only care about resolving the result, other than popping exceptions on rejecting
* `ExceptionResolver` and `ExceptionPromiseResolver` only focus on a specific type of exception
* `FinalResolver` only processes the final stuff whenever it is resolved or rejected
* `DoneResolver` only processes the final stuff with two arguments: exception and result
* `Deferred` a specific resolver which will deliver the result to another deferred callback


PromiseDeferred Static Method
-------
* `PromiseDeferred.make` is a factory method which can make a deferred object extending from `Promise.Locker` who is also a specific resolver


PromiseDeferred Instance Method
-------
* `resolve` equals to javascript `defer.resolve`, usually used as a locker in promise
* `reject` equals to javascript `defer.reject`, usually used as a locker in promise
* `done` detect whether this deferred has been ever invoked resolve or reject
* `post` equals to android `handler.post`, using the internal handler
* `postDelay` equals to android `handler.postDelay`, using the internal handler
* `removeCallbacks` equals to android 'handler.removeCallbacks', using the internal handler
* `promise` equals to javascript `defer.promise`, which will generate a promise object who is waiting for the deferred to be resolved


PromiseFactory Static Method
-------
* `PromiseFactory.create` will make a promise factory with a `DirectFunction` to implement


PromiseFactory Instance Method
-------
* `promiseFactory.run` is a function you should override when you create your own factory unless you use static `create`
* `promiseFactory.make` is to make a new promise, each time you call this, the `run(locker)` will be executed


License
-------

    Copyright 2016 tangye1234.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
