package finance

import finance.config.*
import finance.db.*
import finance.http.Routes
import finance.service.*

import cats.effect.*
import com.comcast.ip4s.*
import org.typelevel.otel4s.metrics.Meter.Implicits.noop
import org.typelevel.otel4s.trace.Tracer.Implicits.noop
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import skunk.*

object Main extends IOApp.Simple {

  def run: IO[Unit] = {
    val logger = Slf4jLogger.getLogger[IO]

    val server: Resource[IO, Unit] =
      for {
        cfg <- Resource.eval(AppConfig.load[IO])
        pool <- Session
          .Builder[IO]
          .pooled(
            max = cfg.db.poolMax
          )

        usersR = Users.make[IO](pool)
        acctsR = Accounts.make[IO](pool)
        txsR = Transactions.make[IO](pool)
        analytics = Analytics.make[IO](txsR)
        insights = Insights.make[IO](analytics)

        routes = new Routes[IO](usersR, acctsR, analytics, insights).routes
        app = Logger.httpApp(logHeaders = true, logBody = false)(routes.orNotFound)

        host <- Resource.eval(IO.fromOption(Host.fromString(cfg.http.host))(new IllegalArgumentException("bad host")))
        port <- Resource.eval(IO.fromOption(Port.fromInt(cfg.http.port))(new IllegalArgumentException("bad port")))

        _ <- EmberServerBuilder
          .default[IO]
          .withHost(host)
          .withPort(port)
          .withHttpApp(app)
          .build

        _ <- Resource.eval(logger.info(s"Listening on http://$host:$port"))
      } yield ()

    server.useForever
  }
}
