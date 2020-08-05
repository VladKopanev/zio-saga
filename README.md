# ZIO-SAGA

| CI | Coverage | Release |  |
| --- | --- | --- | --- |
| [![Build Status][Badge-Travis]][Link-Travis] | [![Coverage Status][Badge-Codecov]][Link-Codecov] | [![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases] | [![Scala Steward badge][Badge-ScalaSteward]][Link-ScalaSteward] |

Build your transactions in purely functional way.

zio-saga allows you to compose your requests and compensating actions from Saga pattern in one transaction
without any boilerplate.


Backed by ZIO it adds a simple abstraction called Saga that takes the responsibility of
proper composition of effects and associated compensating actions.

# Getting started

Add zio-saga dependency to your `build.sbt`:

`libraryDependencies += "com.vladkopanev" %% "zio-saga-core" % "0.4.0"`

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

`zio-saga` tries to address this concerns and provide you with simple syntax to compose your Sagas.

With `zio-saga` we could do it like so:

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

As you can see with `zio-saga` the process of building your Sagas is greatly simplified comparably to ad-hoc solutions. 
ZIO-Sagas are composable, boilerplate-free and intuitively understandable for people that aware of Saga pattern.
This library let you compose transaction steps both in sequence and in parallel, this feature gives you more powerful control 
over transaction execution.

# Advanced

Advanced example of working application that stores saga state in DB (journaling) could be found 
here [examples](/examples).

### Retrying 
`zio-saga` provides you with functions for retrying your compensating actions, so you could 
write:

 ```scala
collectPayments(2d, 2) retryableCompensate (refundPayments(2d, 2), Schedule.exponential(1.second))
```

In this example your Saga will retry compensating action `refundPayments` after exponentially 
increasing timeouts (based on `ZIO#retry` and `ZSchedule`).


### Parallel execution
Saga pattern does not limit transactional requests to run only in sequence.
Because of that `zio-saga` contains methods for parallel execution of requests. 

```scala
    val flight          = bookFlight compensate cancelFlight
    val hotel           = bookHotel compensate cancelHotel
    val bookingSaga     = flight zipPar hotel
```

Note that in this case two compensations would run in sequence, one after another by default.
If you need to execute compensations in parallel consider using `Saga#zipWithParAll` function, it allows arbitrary 
combinations of compensating actions.

### Result dependent compensations

Depending on the result of compensable effect you may want to execute specific compensation, for such cases `zio-saga`
contains specific functions:
- `compensate(compensation: Either[E, A] => Compensator[R, E])` this function makes compensation dependent on the result 
of corresponding effect that either fails or succeeds.
- `compensateIfFail(compensation: E => Compensator[R, E])` this function makes compensation dependent only on error type 
hence compensation will only be triggered if corresponding effect fails.
- `compensateIfSuccess(compensation: A => Compensator[R, E])` this function makes compensation dependent only on
successful result type hence compensation can only occur if corresponding effect succeeds.

### Notes on compensation action failures

By default, if some compensation action fails no other compensation would run and therefore user has the ability to 
choose what to do: stop compensation (by default), retry failed compensation step until it succeeds or proceed to next 
compensation steps ignoring the failure.

### Cats Compatible Sagas

[cats-saga](https://github.com/VladKopanev/cats-saga)

[Link-Codecov]: https://codecov.io/gh/VladKopanev/zio-saga?branch=master "Codecov"
[Link-Travis]: https://travis-ci.com/VladKopanev/zio-saga "circleci"
[Link-SonatypeReleases]: https://oss.sonatype.org/content/repositories/releases/com/vladkopanev/zio-saga-core_2.12/ "Sonatype Releases"
[Link-ScalaSteward]: https://scala-steward.org

[Badge-Codecov]: https://codecov.io/gh/VladKopanev/zio-saga/branch/master/graph/badge.svg "Codecov" 
[Badge-Travis]: https://travis-ci.com/VladKopanev/zio-saga.svg?branch=master "Codecov" 
[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/com.vladkopanev/zio-saga-core_2.11.svg "Sonatype Releases"
[Badge-ScalaSteward]: https://img.shields.io/badge/Scala_Steward-helping-brightgreen.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=
