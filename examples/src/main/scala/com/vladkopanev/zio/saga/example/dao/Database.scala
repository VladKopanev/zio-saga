package com.vladkopanev.zio.saga.example.dao

import zio.{ Ref, ZIO }

// FIXME
final case class User(id: Long)
final case class ExpectedFailure()

trait UserRepository {
  val repository: UserRepository.Service
}

object UserRepository {
  trait Service {
    def get(id: Long): ZIO[Any, ExpectedFailure, Option[User]]

    def create(user: User): ZIO[Any, ExpectedFailure, Unit]

    def delete(id: Long): ZIO[Any, ExpectedFailure, Unit]
  }
}

final case class InMemoryUserRepository(ref: Ref[Map[Long, User]]) extends UserRepository.Service {
  def get(id: Long): ZIO[Any, ExpectedFailure, Option[User]] =
    for {
      user <- ref.get.map(_.get(id))
      u <- user match {
            case Some(s) => ZIO.some(s)
            case None    => ZIO.none
          }
    } yield u

  def create(user: User): ZIO[Any, ExpectedFailure, Unit] = ref.update(map => map.+(user.id -> user)).unit

  def delete(id: Long): ZIO[Any, ExpectedFailure, Unit] = ref.update(map => map.-(id)).unit
}
