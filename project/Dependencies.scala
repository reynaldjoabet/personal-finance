import sbt.*

object Dependencies {

  private object Versions {

    // --- FP ---
    val catsEffect = "3.7.1"
    val fs2        = "3.14.0"
    val fs2Kafka   = "4.1.3"
    val chimney    = "2.1.0"

    // Locked set: iron-skunk 3.4.x is the first iron built against skunk-core 2.x, and
    // skunk 2.x in turn depends on otel4s-core 1.1.0. Bump iron, skunk and otel4s
    // together or not at all -- moving one alone fails sbt's eviction check.
    val iron = "3.4.0-RC1"

    // --- HTTP ---
    val http4s = "0.23.37"
    val sttp4  = "4.0.26"

    // --- JSON ---
    val jsoniter = "2.40.1"
    val circe    = "0.14.16"

    // --- DB ---
    val skunk    = "2.0.0-RC3" // locked to iron and otel4s; see the note on Versions.iron
    val hikaricp = "7.1.0"
    val flyway   = "13.10.0"
    val postgres = "42.7.14"

    // --- Security ---
    val jwtScala         = "11.0.4"
    val bouncycastle     = "1.86"
    val password4j       = "1.8.4"
    val auth0            = "4.6.1"
    val nimbusJoseJwt    = "10.10"
    val nimbusOauth2Oidc = "11.38.2"
    val vault            = "5.1.0"

    // --- Logging ---
    val log4cats = "2.8.0"
    val logback  = "1.6.5"

    // --- Cache ---
    val caffeine = "3.3.0"

    // --- Observability ---
    val datadog = "2.61.0"
    val kamon   = "2.8.1"
    val otel4s  = "1.1.0" // locked to iron and skunk; see the note on Versions.iron

    // --- Config ---
    val pureconfig = "0.17.10"

    // --- Testing ---
    val munit           = "1.3.6"
    val munitCatsEffect = "2.2.1"
    val hedgehog        = "0.14.0"
    val scalacheck      = "1.20.0"

  }

  // Helpers
  private def http4sDep(a: String) =
    "org.http4s" %% s"http4s-$a" % Versions.http4s

  private def sttpDep(a: String) =
    "com.softwaremill.sttp.client4" %% a % Versions.sttp4

  private def circeDep(a: String) =
    "io.circe" %% s"circe-$a" % Versions.circe

  private def ironDep(a: String) =
    "io.github.iltotore" %% a % Versions.iron

  object core {

    val catsEffect = "org.typelevel" %% "cats-effect" % Versions.catsEffect
    val fs2        = "co.fs2"        %% "fs2-core"    % Versions.fs2

  }

  object http {

    object http4s {

      val core  = Seq(http4sDep("dsl"), http4sDep("ember-server"), http4sDep("ember-client"))
      val circe = http4sDep("circe")

    }

    // Clients for the exchange / custody integrations.
    object sttp {

      val core  = sttpDep("core")
      val cats  = sttpDep("cats")
      val circe = sttpDep("circe")

      object backends {

        val http4s = sttpDep("http4s-backend")

      }

    }

  }

  object json {

    object jsoniter {

      // The macros are compile-time only and always needed alongside core.
      val core = Seq(
        "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % Versions.jsoniter,
        "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % Versions.jsoniter % Provided
      )

      val circe =
        "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-circe" % Versions.jsoniter

    }

    object circe {

      val core    = circeDep("core")
      val generic = circeDep("generic")
      val parser  = circeDep("parser")

    }

  }

  object domain {

    val chimney = "io.scalaland" %% "chimney" % Versions.chimney

    object iron {

      val core       = ironDep("iron")
      val skunk      = ironDep("iron-skunk")
      val circe      = ironDep("iron-circe")
      val pureconfig = ironDep("iron-pureconfig")

    }

  }

  object logging {

    val log4cats = "org.typelevel" %% "log4cats-slf4j"  % Versions.log4cats
    val logback  = "ch.qos.logback" % "logback-classic" % Versions.logback

  }

  object config {

    object pureconfig {

      val core = "com.github.pureconfig" %% "pureconfig-core" % Versions.pureconfig

      val generic = "com.github.pureconfig" %% "pureconfig-generic-scala3" % Versions.pureconfig

    }

  }

  object database {

    val postgres = "org.postgresql" % "postgresql"  % Versions.postgres
    val flyway   = "org.flywaydb"   % "flyway-core" % Versions.flyway
    val hikaricp = "com.zaxxer"     % "HikariCP"    % Versions.hikaricp
    val skunk    = "org.tpolecat"  %% "skunk-core"  % Versions.skunk

  }

  object kafka {

    val fs2 = "org.typelevel" %% "fs2-kafka" % Versions.fs2Kafka

  }

  object cache {

    val caffeine = "com.github.ben-manes.caffeine" % "caffeine" % Versions.caffeine

  }

  object security {

    object nimbus {

      val joseJwt    = "com.nimbusds" % "nimbus-jose-jwt" % Versions.nimbusJoseJwt
      val oauth2Oidc = "com.nimbusds" % "oauth2-oidc-sdk" % Versions.nimbusOauth2Oidc

    }

    object bouncycastle {

      val pkix     = "org.bouncycastle" % "bcpkix-jdk18on" % Versions.bouncycastle
      val provider = "org.bouncycastle" % "bcprov-jdk18on" % Versions.bouncycastle

    }

    val jwtScala   = "com.github.jwt-scala" %% "jwt-core"          % Versions.jwtScala
    val auth0      = "com.auth0"             % "java-jwt"          % Versions.auth0
    val password4j = "com.password4j"        % "password4j"        % Versions.password4j
    val vault      = "com.bettercloud"       % "vault-java-driver" % Versions.vault

  }

  object observability {

    object kamon {

      val bundle     = "io.kamon" %% "kamon-bundle"     % Versions.kamon
      val prometheus = "io.kamon" %% "kamon-prometheus" % Versions.kamon

    }

    // Only the noop Tracer and Meter providers are used, but skunk 2.x resolves them.
    val otel4s = Seq(
      "org.typelevel" %% "otel4s-core"     % Versions.otel4s,
      "org.typelevel" %% "otel4s-oteljava" % Versions.otel4s
    )

    val datadog =
      ("com.datadoghq" % "datadog-api-client" % Versions.datadog).classifier("shaded-jar")

  }

  object testing {

    val munit = "org.scalameta" %% "munit" % Versions.munit % Test

    val munitCatsEffect = "org.typelevel" %% "munit-cats-effect" % Versions.munitCatsEffect % Test

    val scalacheck = "org.scalacheck" %% "scalacheck" % Versions.scalacheck % Test

    val hedgehog = Seq(
      "qa.hedgehog" %% "hedgehog-core" % Versions.hedgehog % Test,
      "qa.hedgehog" %% "hedgehog-sbt"  % Versions.hedgehog % Test
    )

  }

}
