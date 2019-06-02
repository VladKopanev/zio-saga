# ZIO-SAGA

This library helps to implement Saga Pattern in purely functional way.

It allows you to compose your requests and compensating actions in one distributed transaction
without boilerplate.


Backed by ZIO it adds a simple abstraction called Saga that takes the responsibility of
proper composition of both requests to other systems and associated compensating actions.

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

```
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
compensating action `reopenOrder` will be executed, but when other compensating actions would be run? Right, they would not, 
because the error would not be propagated higher, thus not triggering compensating actions. That is not what we want, we want 
all compensating actions to be triggered in Saga, whatever error occurred.
 
Second try, this time let's somehow trigger all compensating actions.
  
```
def orderSaga(): IO[SagaError, Unit] = {
    collectPayments(2d, 2).flatMap { _ = >
        assignLoyaltyPoints(1d, 1).flatMap { _ => 
            closeOrder(1) orElse(reopenOrder(1)  *> IO.fail(new SagaError))
        } orElse (cancelLoyaltyPoints(1d, 1)  *> IO.fail(new SagaError))
    } orElse(refundPayments(2d, 2) *> IO.fail(new SagaError))
  }
```

This works, we trigger all compensating actions by failing after each compensating action. 
But the implementation itself looks awful, we lost expressiveness in the call-back hell 
and we also lost the original error that we wanted to show to the user.

You can solve this problems in different ways, but you will encounter a number of difficulties, and your code still would 
look pretty much the same as we did in our last try. 

Achieve a generic solution is not that simple, so you will end up
repeating the same boilerplate code from service to service.

`ZIO-SAGA` tries to address this concerns and provide you with simple syntax to compose your Sagas.

With `ZIO-SAGA` we could do it like so:

```
def orderSaga(): IO[SagaError, Unit] = {
    import Saga._

    (for {
      _ <- collectPayments(2d, 2) compensate refundPayments(2d, 2)
      _ <- assignLoyaltyPoints(1d, 1) compensate cancelLoyaltyPoints(1d, 1)
      _ <- closeOrder(1) compensate reopenOrder(1)
    } yield ()).transact
  }
```

`compensate` pairs request IO with compensating action IO and returns a new `Saga` object which then you can compose with other
`Sagas`.
To materialize `Saga` object to `ZIO` when it's complete we need to use `transact` method.

As you can see with `ZIO-SAGA` the process of building your own Sagas is really simple. ZIO-Sagas are composable, 
boilerplate-free and intuitively understandable for people that aware of Saga pattern.

# Additional capabilities

### Retrying 
`ZIO-SAGA` provides you with functions for retrying your compensating actions, so you could 
write:

 ```
collectPayments(2d, 2) retryableCompensate (refundPayments(2d, 2), Schedule.exponential(1.second))
```

In this example your Saga will retry compensating action `refundPayments` after exponentially 
increasing timeouts (based on `ZIO#retry` and `ZSchedule`).


### Parallel execution
Saga pattern does not limit transactional requests to run only in sequence.
Because of that `ZIO-SAGA` contains methods for parallel execution of requests. 

```
    val flight          = bookFlight compensate cancelFlight
    val hotel           = bookHotel compensate cancelHotel
    val bookingSaga     = flight zipPar hotel
```

### TODO:
- Help user to deal with timeout failures
- Log sagas actions to database and restore in case of failure
- Cats interop
