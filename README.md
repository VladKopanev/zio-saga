# ZIO-SAGA

| CI | Coverage | Release |
| --- | --- | --- |
| [![Build Status][Badge-Travis]][Link-Travis] | [![Coverage Status][Badge-Codecov]][Link-Codecov] | [![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases] |

Build your transactions in purely functional way.

ZIO-SAGA allows you to compose your requests and compensating actions from Saga pattern in one transaction
without any boilerplate.


Backed by ZIO it adds a simple abstraction called Saga that takes the responsibility of
proper composition of effects and associated compensating actions.

# Getting started

Add zio-saga dependency to your `build.sbt`:

`libraryDependencies += "com.vladkopanev" %% "zio-saga-core" % "0.1.1"`

# Example of usage:

Consider the following case, we have built our food delivery system in microservices fashion, so
we have `Order` service, `Payment` service, `LoyaltyProgram` service, etc. 
And now we need to implement a closing order method, that collects *payment*, assigns *loyalty* points 
and closes the *order*. This method should run transactionally so if e.g. *closing order* fails we will 
rollback the state for user and *refund payments*, *cancel loyalty points*.

Applying Saga pattern we need a compensating action for each call to particular microservice, those 
actions needs to be run for each completed request in case some of the requests fails.

![Order Saga Flow](./images/diagrams/Order%20Saga%20Flow.jpeg)

Let's think for a moment about how we could implement this pattern without any specific libraries.

The naive implementation could look like this:

```scala
def orderSaga(): IO[SagaError, Unit] = {
    for {
      _ <- collectPayments(2d, 2) orElse refundPayments(2d, 2)
      _ <- assignLoyaltyPoints(1d, 1) orElse cancelLoyaltyPoints(1d, 1)
      _ <- closeOrder(1) orElse reopenOrder(1)
    } yield ()
  }
```

Looks pretty simple and straightforward, `orElse` function tries to recover the original request if it fails.
We have covered every request with a compensating action. But what if last request fails? We know for sure that corresponding 
compensation `reopenOrder` will be executed, but when other compensations would be run? Right, they would not be triggered, 
because the error would not be propagated higher, thus not triggering compensating actions. That is not what we want, we want 
full rollback logic to be triggered in Saga, whatever error occurred.
 
Second try, this time let's somehow trigger all compensating actions.
  
```scala
def orderSaga(): IO[SagaError, Unit] = {
    collectPayments(2d, 2).flatMap { _ = >
        assignLoyaltyPoints(1d, 1).flatMap { _ => 
            closeOrder(1) orElse(reopenOrder(1)  *> IO.fail(new SagaError))
        } orElse (cancelLoyaltyPoints(1d, 1)  *> IO.fail(new SagaError))
    } orElse(refundPayments(2d, 2) *> IO.fail(new SagaError))
  }
```

This works, we trigger all rollback actions by failing after each. 
But the implementation itself looks awful, we lost expressiveness in the call-back hell, imagine 15 saga steps implemented in such manner,
and we also lost the original error that we wanted to show to the user.

You can solve this problems in different ways, but you will encounter a number of difficulties, and your code still would 
look pretty much the same as we did in our last try. 

Achieve a generic solution is not that simple, so you will end up
repeating the same boilerplate code from service to service.

`ZIO-SAGA` tries to address this concerns and provide you with simple syntax to compose your Sagas.

With `ZIO-SAGA` we could do it like so:

```scala
def orderSaga(): IO[SagaError, Unit] = {
    import com.vladkopanev.zio.saga.Saga._

    (for {
      _ <- collectPayments(2d, 2) compensate refundPayments(2d, 2)
      _ <- assignLoyaltyPoints(1d, 1) compensate cancelLoyaltyPoints(1d, 1)
      _ <- closeOrder(1) compensate reopenOrder(1)
    } yield ()).transact
  }
```

`compensate` pairs request IO with compensating action IO and returns a new `Saga` object which then you can compose with other
`Sagas`.
To materialize `Saga` object to `ZIO` when it's complete it is required to use `transact` method.

As you can see with `ZIO-SAGA` the process of building your Sagas is greatly simplified comparably to ad-hoc solutions. 
ZIO-Sagas are composable, boilerplate-free and intuitively understandable for people that aware of Saga pattern.
This library let you compose transaction steps both in sequence and in parallel, this feature gives you more powerful control 
over transaction execution.

# Additional capabilities

### Retrying 
`ZIO-SAGA` provides you with functions for retrying your compensating actions, so you could 
write:

 ```scala
collectPayments(2d, 2) retryableCompensate (refundPayments(2d, 2), Schedule.exponential(1.second))
```

In this example your Saga will retry compensating action `refundPayments` after exponentially 
increasing timeouts (based on `ZIO#retry` and `ZSchedule`).


### Parallel execution
Saga pattern does not limit transactional requests to run only in sequence.
Because of that `ZIO-SAGA` contains methods for parallel execution of requests. 

```scala
    val flight          = bookFlight compensate cancelFlight
    val hotel           = bookHotel compensate cancelHotel
    val bookingSaga     = flight zipPar hotel
```

### Cats Compatible Sagas

[cats-saga](https://github.com/VladKopanev/cats-saga)

### See in the next releases:
- Even more powerful control over compensation execution 

[Link-Codecov]: https://codecov.io/gh/VladKopanev/zio-saga?branch=master "Codecov"
[Link-Travis]: https://travis-ci.com/VladKopanev/zio-saga "circleci"
[Link-SonatypeReleases]: https://oss.sonatype.org/content/repositories/releases/com/vladkopanev/zio-saga-core_2.12/ "Sonatype Releases"

[Badge-Codecov]: https://codecov.io/gh/VladKopanev/zio-saga/branch/master/graph/badge.svg "Codecov" 
[Badge-Travis]: https://travis-ci.com/VladKopanev/zio-saga.svg?branch=master "Codecov" 
[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/com.vladkopanev/zio-saga-core_2.11.svg "Sonatype Releases"
