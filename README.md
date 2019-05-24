# ZIO-SAGA

This library helps to implement Saga Pattern in purely function way.

It allows you to compose your requests and compensating actions in one distributed transaction
without boilerplate.


Backed by ZIO it adds a simple abstraction called Saga that takes the responsibility of
proper composition of both requests to other systems and associated compensating actions.

### Example of usage:

Consider the following case, we have built our food delivery system in microservices fashion, so
we have `Order` service, `Payment` service, `LoyaltyProgram` service, etc. 
And now we need to implement a closing order method, that collects *payment*, assigns *loyalty* points 
and closes the *order*. This method should run transactionally so if e.g. *closing order* fails we will 
rollback the state for user and *refund payments*, *cancel loyalty points*.

Applying Saga pattern we need a compensation action for each call to particular microservice, those 
actions needs to be run for each completed request in case some of the requests fails.

With ZIO-SAGA we could do it like this:

```
def orderSaga(): ZIO[Any with Clock, SagaError, Unit] = {
    import Saga._
    import scalaz.zio.duration._

    (for {
      _ <- collectPayments(2d, 2) retryableCompensate (refundPayments(2d, 2), Schedule.exponential(1.second))
      _ <- assignLoyaltyPoints(1d, 1) retryableCompensate (cancelLoyaltyPoints(1d, 1), Schedule.once)
      _ <- closeOrder(1) compensate reopenOrder(1)
    } yield ()).run
  }
```

### TODO:
- Tests
- Log sagas actions to database and restore in case of failure
- Improve docs, show that it's not a simple task to compose sagas by your own
