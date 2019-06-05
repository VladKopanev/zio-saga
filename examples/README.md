This is the example that extends one provided in the parent REAMDE file.

This projects shows one of the implementation variants of Saga Executor Coordinator
that also writes transaction log to database and knows how to recover from failures. 

TODO:
- add recovery step to main function
- pack project to docker image
- pass trace id  in requests to transaction participants