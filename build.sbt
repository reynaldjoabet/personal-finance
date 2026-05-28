import Dependencies.*

ThisBuild / scalaVersion := "3.3.7"
ThisBuild / organization := "io.finance"
ThisBuild / version := "0.1.0"

lazy val root = (project in file("."))
  .settings(
    name := "personal-finance",
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-no-indent",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Wunused:all",
      "-Wvalue-discard",
      "-Wnonunit-statement",
      "-Ykind-projector",
      "-Xmax-inlines",
      "64"
    ),
    libraryDependencies ++= all
  )
