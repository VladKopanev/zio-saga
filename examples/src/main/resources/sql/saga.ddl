CREATE TABLE public.saga
(
    id sequence,
    initiator uuid NOT NULL,
    "createdAt" timestamp without time zone NOT NULL,
    "finishedAt" timestamp without time zone,
    data jsonb NOT NULL,
    type text,
    CONSTRAINT "Saga_pkey" PRIMARY KEY (id)
)

CREATE TABLE public.saga_step
(
    "sagaId" integer NOT NULL,
    name text NOT NULL,
    result jsonb,
    "finishedAt" timestamp without time zone,
    failure text,
    CONSTRAINT saga_step_pkey PRIMARY KEY ("sagaId", name),
    CONSTRAINT saga_id_fk FOREIGN KEY ("sagaId")
        REFERENCES public.saga (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
)