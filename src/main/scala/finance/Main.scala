package finance

import cats.effect.*

import finance.config.*
import finance.db.*
import finance.http.{AuthMiddleware as _, Routes, TlStateStore}
import finance.service.*
import finance.truelayer.TrueLayer
import com.comcast.ip4s.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.otel4s.metrics.Meter.Implicits.noop
import org.typelevel.otel4s.trace.Tracer.Implicits.noop
import skunk.*

object Main extends IOApp.Simple {

  def run: IO[Unit] = {
    given org.typelevel.log4cats.Logger[IO] = Slf4jLogger.getLogger[IO]

    val server: Resource[IO, Unit] =
      for {
        cfg <- Resource.eval(AppConfig.load[IO])

        pool <- Session
                  .Builder[IO]
                  .pooled(max = cfg.db.poolMax)

        httpClient <- EmberClientBuilder.default[IO].build

        usersR    = Users.make[IO](pool)
        acctsR    = Accounts.make[IO](pool)
        txsR      = Transactions.make[IO](pool)
        tlTokensR = TrueLayerTokens.make[IO](pool)

        analytics = Analytics.make[IO](txsR)
        insights  = Insights.make[IO](analytics)

        truelayer = TrueLayer.fromHttp[IO](httpClient, cfg.truelayer)
        sync      = TrueLayerSync.make[IO](truelayer, tlTokensR, acctsR, txsR)

        authSvc  <- Resource.eval(Auth.make[IO](usersR, cfg.auth))
        tlStates <- Resource.eval(TlStateStore.make[IO])

        routes = new Routes[IO](
                   authSvc,
                   usersR,
                   acctsR,
                   analytics,
                   insights,
                   truelayer,
                   tlTokensR,
                   sync,
                   tlStates
                 ).routes
        app = Logger.httpApp(logHeaders = true, logBody = false)(routes.orNotFound)

        host <-
          Resource.eval(
            IO.fromOption(Host.fromString(cfg.http.host))(new IllegalArgumentException("bad host"))
          )
        port <-
          Resource.eval(
            IO.fromOption(Port.fromInt(cfg.http.port))(new IllegalArgumentException("bad port"))
          )

        _ <- EmberServerBuilder
               .default[IO]
               .withHost(host)
               .withPort(port)
               .withHttpApp(app)
               .build

        _ <- Resource.eval(
               summon[org.typelevel.log4cats.Logger[IO]].info(s"Listening on http://$host:$port")
             )
      } yield ()

    server.useForever
  }

}
