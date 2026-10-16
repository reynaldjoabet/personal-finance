import Dependencies.*

ThisBuild / scalaVersion := "3.9.0"
ThisBuild / version      := "0.1.0-SNAPSHOT"

ThisBuild / scalacOptions := Seq(
  "-encoding",
  "UTF-8",
  "-no-indent",
  "-deprecation",
  "-feature",
  "-unchecked",
  // "-Werror",
  // "-Wunused:all",
  "-Wvalue-discard",
  "-Wnonunit-statement",
  "-language:strictEquality",
  "-Xcheck-macros",
  "-Xmax-inlines:64"
)

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val root = (project in file("."))
  .settings(
    name                 := "personal-finance",
    libraryDependencies ++= Seq(
      // Core
      Dependencies.core.catsEffect,
      Dependencies.core.fs2,
      Dependencies.kafka.fs2,
      Dependencies.domain.chimney,
      Dependencies.domain.uuidGenerator,
      // HTTP
      Dependencies.http.http4s.circe,
      Dependencies.http.sttp.core,
      Dependencies.http.sttp.cats,
      Dependencies.http.sttp.circe,
      Dependencies.http.sttp.backends.http4s,
      // JSON
      Dependencies.json.circe.core,
      Dependencies.json.circe.generic,
      Dependencies.json.circe.parser,
      Dependencies.json.jsoniter.circe,
      // Refinement
      Dependencies.domain.iron.core,
      Dependencies.domain.iron.skunk,
      Dependencies.domain.iron.circe,
      Dependencies.domain.iron.pureconfig,
      // DB
      Dependencies.database.skunk,
      Dependencies.database.postgres,
      Dependencies.database.hikaricp,
      Dependencies.database.flyway,
      // Security
      Dependencies.security.auth0,
      Dependencies.security.jwtScala,
      Dependencies.security.password4j,
      Dependencies.security.nimbus.joseJwt,
      Dependencies.security.nimbus.oauth2Oidc,
      Dependencies.security.bouncycastle.pkix,
      Dependencies.security.bouncycastle.provider,
      Dependencies.security.vault,
      // Cache
      Dependencies.cache.caffeine,
      // Logging
      Dependencies.logging.log4cats,
      Dependencies.logging.logback,
      // Observability
      Dependencies.observability.datadog,
      Dependencies.observability.kamon.bundle,
      Dependencies.observability.kamon.prometheus,
      // Config
      Dependencies.config.pureconfig.core,
      Dependencies.config.pureconfig.generic,
      // Testing
      Dependencies.testing.munit,
      Dependencies.testing.munitCatsEffect,
      Dependencies.testing.scalacheck
    )
      ++ Dependencies.http.http4s.core
      ++ Dependencies.json.jsoniter.core
      ++ Dependencies.observability.otel4s
      ++ Dependencies.testing.hedgehog
  )
