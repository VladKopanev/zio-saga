This is the example that extends one provided in the parent REAMDE file.

This projects shows one of the implementation variants of Saga Executor Coordinator
that also writes transaction log to database and knows how to recover from failures. 

TODO:
- pack project to docker image
- minimal GUI, SSE for saga execution tracking
- in case of coordinator scaling we need to differentiate saga that is ongoing from failed saga that we need to restore