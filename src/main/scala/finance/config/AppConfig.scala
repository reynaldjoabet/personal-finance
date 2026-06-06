package finance.config

import cats.effect.Sync

final case class DbConfig(
    host: String,
    port: Int,
    user: String,
    password: Option[String],
    database: String,
    poolMax: Int
)

final case class HttpConfig(host: String, port: Int)

/** JWT signing config. RS256: the service signs with `privateKeyPem` and the same (or another) service verifies with
  * `publicKeyPem`. Both are PKCS#8/X.509 PEM strings (the usual `-----BEGIN PRIVATE KEY-----` / `-----BEGIN PUBLIC
  * KEY-----` formats); newlines in env vars are tolerated as either real `\n` or the literal two-character escape.
  */
final case class AuthConfig(
    privateKeyPem: String,
    publicKeyPem: String,
    issuer: String,
    audience: String,
    expirySeconds: Long
)

/** TrueLayer OAuth2 client config. `apiBase` is the data API; `authBase` is the consent/token host (different hostnames
  * in TrueLayer's sandbox and prod).
  */
final case class TrueLayerConfig(
    clientId: String,
    clientSecret: String,
    authBase: String, // e.g. https://auth.truelayer-sandbox.com
    apiBase: String, // e.g. https://api.truelayer-sandbox.com
    redirectUri: String, // must match what's registered with TrueLayer
    scopes: String, // space-separated, e.g. "info accounts balance transactions offline_access"
    providers: String // comma-separated TrueLayer provider ids, e.g. "uk-cs-mock,uk-ob-all"
)

final case class AppConfig(
    db: DbConfig,
    http: HttpConfig,
    auth: AuthConfig,
    truelayer: TrueLayerConfig
)

object AppConfig {

  /** Read configuration from environment variables. Fail fast if anything required is missing. */
  def load[F[_]: Sync]: F[AppConfig] = Sync[F].delay {
    def env(name: String): Option[String] = sys.env.get(name).filter(_.nonEmpty)
    def req(name: String): String = env(name).getOrElse(sys.error(s"missing env var: $name"))
    def int(name: String, default: Int): Int = env(name).map(_.toInt).getOrElse(default)
    def long(name: String, default: Long): Long = env(name).map(_.toLong).getOrElse(default)
    // Allow PEMs to be provided with literal "\n" sequences (common when stuffing into a single env line).
    def pem(name: String): String = req(name).replace("\\n", "\n")

    AppConfig(
      db = DbConfig(
        host = env("DB_HOST").getOrElse("localhost"),
        port = int("DB_PORT", 5432),
        user = req("DB_USER"),
        password = env("DB_PASSWORD"),
        database = req("DB_NAME"),
        poolMax = int("DB_POOL_MAX", 8)
      ),
      http = HttpConfig(
        host = env("HTTP_HOST").getOrElse("0.0.0.0"),
        port = int("HTTP_PORT", 8080)
      ),
      auth = AuthConfig(
        privateKeyPem = pem("AUTH_PRIVATE_KEY_PEM"),
        publicKeyPem = pem("AUTH_PUBLIC_KEY_PEM"),
        issuer = env("AUTH_ISSUER").getOrElse("personal-finance"),
        audience = env("AUTH_AUDIENCE").getOrElse("personal-finance-api"),
        expirySeconds = long("AUTH_EXPIRY_SECONDS", 24L * 3600L)
      ),
      truelayer = TrueLayerConfig(
        clientId = req("TRUELAYER_CLIENT_ID"),
        clientSecret = req("TRUELAYER_CLIENT_SECRET"),
        authBase = env("TRUELAYER_AUTH_BASE").getOrElse("https://auth.truelayer-sandbox.com"),
        apiBase = env("TRUELAYER_API_BASE").getOrElse("https://api.truelayer-sandbox.com"),
        redirectUri = req("TRUELAYER_REDIRECT_URI"),
        scopes = env("TRUELAYER_SCOPES").getOrElse("info accounts balance transactions offline_access"),
        providers = env("TRUELAYER_PROVIDERS").getOrElse("uk-cs-mock,uk-ob-all")
      )
    )
  }
}
