package com.github.akiomik.uglify

import com.github.akiomik.proguard.Sbt10Compat
import com.typesafe.sbt.jse.{SbtJsEngine, SbtJsTask}
import com.typesafe.sbt.web.incremental._
import com.typesafe.sbt.web.pipeline.Pipeline
import com.typesafe.sbt.web.{Compat, PathMapping, SbtWeb, incremental}
import monix.reactive.Observable
import sbt.Keys._
import sbt.{Task, _}
import Sbt10Compat.SbtIoPath._

import scala.concurrent.Await

object Import {

  val uglify = TaskKey[Pipeline.Stage]("uglify", "Perform UglifyJS optimization on the asset pipeline.")

    val uglifyBeautify        = settingKey[Boolean]("Enables beautify. Default: true")
    val uglifyBeautifyOptions = settingKey[Seq[String]]("Options for beautify such as beautify, preamble etc. Default: Nil")
    val uglifyBuildDir        = settingKey[File]("Where UglifyJS will copy source files and write minified files to. Default: resourceManaged / build")
    val uglifyComments        = settingKey[Option[String]]("Specifies comments handling. Default: None")
    val uglifyCompress        = settingKey[Boolean]("Enables compression. Default: true")
    val uglifyCompressOptions = settingKey[Seq[String]]("Options for compression such as pure_funcs etc. Default: Nil")
    val uglifyConfigFile      = settingKey[Option[File]]("Read minify() options from JSON file. Default: None")
    val uglifyDefine          = settingKey[Option[String]]("Define globals. Default: None")
    val uglifyEcma            = settingKey[Option[Int]]("Specifies ECMAScript release. Default: None")
    val uglifyIe8             = settingKey[Boolean]("Supports non-standard Internet Explorer 8. Default: false")
    val uglifyKeepClassnames  = settingKey[Boolean]("Does not mangle/drop class names. Default: false")
    val uglifyKeepFnames      = settingKey[Boolean]("Does not mangle/drop function names. Default: false")
    val uglifyMangle          = settingKey[Boolean]("Enables name mangling. Default: true")
    val uglifyMangleOptions   = settingKey[Seq[String]]("Options for mangling such as builtins, debug etc. Default: Nil")
    val uglifyNameCache       = settingKey[Option[File]]("Specifies a file to hold mangled name mappings. Default: None")
    val uglifyParse           = settingKey[Seq[String]]("Specifies parser options such as acorn, bare_returns etc. Default: Nil")
    val uglifySafari10        = settingKey[Boolean]("Supports non-standard Safari 10/11. Default: false")
    val uglifySelf            = settingKey[Boolean]("Builds UglifyJS as a library. Default: false")
    val uglifyTimings         = settingKey[Boolean]("Displays operations run time on STDERR. Default: false")
    val uglifyToplevel        = settingKey[Boolean]("Compresses and/or mangles variables in top level scope. Default: false")
    val uglifyVerbose         = settingKey[Boolean]("Prints diagnostic messages. Default: false")
    val uglifyWarn            = settingKey[Boolean]("Prints warning messages. Default: false")
    val uglifyWrap            = settingKey[Option[String]]("Embed everything in a big function. Default: None")
    val uglifyOps             = settingKey[UglifyOps.UglifyOpsMethod]("A function defining how to combine input files into output files. Default: UglifyOps.singleFileWithSourceMapOut")

  object UglifyOps {

    /** A list of input files mapping to a single output file. */
    case class UglifyOpGrouping(inputFiles: Seq[PathMapping], outputFile: String, inputMapFile: Option[PathMapping], outputMapFile: Option[String])

    type UglifyOpsMethod = (Seq[PathMapping]) => Seq[UglifyOpGrouping]

    def dotMin(file: String): String = {
      val exti = file.lastIndexOf('.')
      val (pfx, ext) = if (exti == -1) (file, "")
      else file.splitAt(exti)
      pfx + ".min" + ext
    }

    /** Use when uglifying single files */
    val singleFile: UglifyOpsMethod = { mappings =>
      mappings.map(fp => UglifyOpGrouping(Seq(fp), dotMin(fp._2), None, None))
    }

    /** Use when uglifying single files and you want a source map out */
    val singleFileWithSourceMapOut: UglifyOpsMethod = { mappings =>
      mappings.map(fp => UglifyOpGrouping(Seq(fp), dotMin(fp._2), None, Some(dotMin(fp._2) + ".map")))
    }

    /** Use when uglifying single files and you want a source map in and out - remember to includeFilter .map files */
    val singleFileWithSourceMapInAndOut: UglifyOpsMethod = { mappings =>
      val sources = mappings.filter(source => source._2.endsWith(".js"))
      val sourceMaps = mappings.filter(sourceMap => sourceMap._2.endsWith(".js.map"))

      sources.map { source =>
        UglifyOpGrouping(
          Seq(source),
          dotMin(source._2),
          sourceMaps.find(sourceMap =>
            sourceMap._2 equals (source._2 + ".map")
          ),
          Some(dotMin(source._2) + ".map")
        )
      }
    }
  }

}

object SbtUglify extends AutoPlugin {

  override def requires = SbtJsTask

  override def trigger = AllRequirements

  val autoImport = Import

  import SbtJsEngine.autoImport.JsEngineKeys._
  import SbtJsTask.autoImport.JsTaskKeys._
  import SbtWeb.autoImport._
  import WebKeys._
  import autoImport._
  import UglifyOps._

  implicit private class RichFile(val self: File) extends AnyVal {
    def startsWith(dir: File): Boolean = self.getPath.startsWith(dir.getPath)
  }

  override def projectSettings = Seq(
    uglifyBeautify        := true,
    uglifyBeautifyOptions := Nil,
    uglifyBuildDir        := (resourceManaged in uglify).value / "build",
    uglifyComments        := None,
    uglifyCompress        := true,
    uglifyCompressOptions := Nil,
    uglifyConfigFile      := None,
    uglifyDefine          := None,
    uglifyEcma            := None,
    uglifyIe8             := false,
    uglifyKeepClassnames  := false,
    uglifyKeepFnames      := false,
    uglifyMangle          := true,
    uglifyMangleOptions   := Nil,
    uglifyNameCache       := None,
    uglifyParse           := Nil,
    uglifySafari10        := false,
    uglifySelf            := false,
    uglifyTimings         := false,
    uglifyToplevel        := false,
    uglifyVerbose         := false,
    uglifyWarn            := false,
    uglifyWrap            := None,
    uglifyOps             := singleFileWithSourceMapOut,

    excludeFilter in uglify :=
      HiddenFileFilter ||
        GlobFilter("*.min.js") ||
        new SimpleFileFilter({ file =>
          file.startsWith((WebKeys.webModuleDirectory in Assets).value)
        }),
    includeFilter in uglify := GlobFilter("*.js"),
    resourceManaged in uglify := webTarget.value / uglify.key.label,
    uglify := runOptimizer.dependsOn(webJarsNodeModules in Plugin).value
  )

  private def runOptimizer: Def.Initialize[Task[Pipeline.Stage]] = Def.task {
    val beautifyValue        = uglifyBeautify.value
    val beautifyOpsValue     = uglifyBeautifyOptions.value
    val buildDirValue        = uglifyBuildDir.value
    val commentsValue        = uglifyComments.value
    val compressValue        = uglifyCompress.value
    val compressOptionsValue = uglifyCompressOptions.value
    val configFileValue      = uglifyConfigFile.value
    val defineValue          = uglifyDefine.value
    val ecmaValue            = uglifyEcma.value
    val ie8Value             = uglifyIe8.value
    val keepClassnamesValue  = uglifyKeepClassnames.value
    val keepFnamesValue      = uglifyKeepFnames.value
    val mangleValue          = uglifyMangle.value
    val mangleOptionsValue   = uglifyMangleOptions.value
    val nameCacheValue       = uglifyNameCache.value
    val parseValue           = uglifyParse.value
    val safari10Value        = uglifySafari10.value
    val selfValue            = uglifySelf.value
    val timingsValue         = uglifyTimings.value
    val toplevelValue        = uglifyToplevel.value
    val verboseValue         = uglifyVerbose.value
    val warnValue            = uglifyWarn.value
    val wrapValue            = uglifyWrap.value
    val uglifyOpsValue       = uglifyOps.value

    val include = (includeFilter in uglify).value
    val exclude = (excludeFilter in uglify).value
    val streamsValue = streams.value
    val nodeModuleDirectoriesInPluginValue = (nodeModuleDirectories in Plugin).value
    val webJarsNodeModulesDirectoryInPluginValue = (webJarsNodeModulesDirectory in Plugin).value
    val timeout = (timeoutPerSource in uglify).value
    val stateValue = state.value
    val engineTypeInUglifyValue = (engineType in uglify).value
    val commandInUglifyValue = (command in uglify).value
    val options = Seq(
      beautifyValue,
      beautifyOpsValue,
      commentsValue,
      compressValue,
      compressOptionsValue,
      configFileValue,
      defineValue,
      ecmaValue,
      ie8Value,
      keepClassnamesValue,
      keepFnamesValue,
      mangleValue,
      mangleOptionsValue,
      nameCacheValue,
      parseValue,
      safari10Value,
      selfValue,
      timingsValue,
      toplevelValue,
      verboseValue,
      warnValue,
      wrapValue,
      include,
      exclude,
      (resourceManaged in uglify).value
    ).mkString("|")

    (mappings) => {
      val optimizerMappings = mappings.filter(f => !f._1.isDirectory && include.accept(f._1) && !exclude.accept(f._1))

      SbtWeb.syncMappings(
        Compat.cacheStore(streamsValue, "uglify-cache"),
        optimizerMappings,
        buildDirValue
      )
      val appInputMappings = optimizerMappings.map(p => uglifyBuildDir.value / p._2 -> p._2)
      val groupings = uglifyOpsValue(appInputMappings)

      implicit val opInputHasher = OpInputHasher[UglifyOpGrouping](io =>
        OpInputHash.hashString(
          (io.outputFile +: io.inputFiles.map(_._1.getAbsolutePath)).mkString("|") + "|" + options
        )
      )

      val (outputFiles, ()) = incremental.syncIncremental(streamsValue.cacheDirectory / "run", groupings) {
        modifiedGroupings: Seq[UglifyOpGrouping] =>
          if (modifiedGroupings.nonEmpty) {

            streamsValue.log.info(s"Optimizing ${modifiedGroupings.size} JavaScript(s) with Uglify")

            val nodeModulePaths = nodeModuleDirectoriesInPluginValue.map(_.getPath)
            val uglifyjsShell = webJarsNodeModulesDirectoryInPluginValue / "uglify-es" / "bin" / "uglifyjs"

            val beautifyArgs = if (beautifyValue) {
              val stdArg = Seq("--beautify")
              if (beautifyOpsValue.isEmpty) stdArg else stdArg :+ beautifyOpsValue.mkString(",")
            } else {
              Nil
            }

            val commentsArgs = commentsValue.map(Seq("--comments", _)).getOrElse(Nil)

            val compressArgs = if (compressValue) {
              val stdArg = Seq("--compress")
              if (compressOptionsValue.isEmpty) stdArg else stdArg :+ compressOptionsValue.mkString(",")
            } else {
              Nil
            }

            val configFileArgs = configFileValue.map(a => Seq("--config-file", a.getPath)).getOrElse(Nil)

            val defineArgs = defineValue.map(Seq("--define", _)).getOrElse(Nil)

            val ecmaArgs = ecmaValue.map(a => Seq("--ecma", a.toString)).getOrElse(Nil)

            val ie8Args = if (ie8Value) Seq("--ie8") else Nil

            val keepClassnamesArgs = if (keepClassnamesValue) Seq("--keep-classnames") else Nil

            val keepFnamesArgs = if (keepFnamesValue) Seq("--keep-fnames") else Nil

            val mangleArgs = if (mangleValue) {
              val stdArg = Seq("--mangle")
              if (mangleOptionsValue.isEmpty) stdArg else stdArg :+ mangleOptionsValue.mkString(",")
            } else {
              Nil
            }

            val nameCacheArgs = nameCacheValue.map(a => Seq("--name-cache", a.getPath)).getOrElse(Nil)

            val parseArgs = if (parseValue.isEmpty) Nil else Seq("--parse", parseValue.mkString(","))

            val safari10Args = if (safari10Value) Seq("--safari10") else Nil

            val selfArgs = if (selfValue) Seq("--self") else Nil

            val timingsArgs = if (timingsValue) Seq("--timings") else Nil

            val toplevelArgs = if (toplevelValue) Seq("--toplevel") else Nil

            val verboseArgs = if (verboseValue) Seq("--verbose") else Nil

            val warnArgs = if (warnValue) Seq("--warn") else Nil

            val wrapArgs = wrapValue.map(Seq("--wrap", _)).getOrElse(Nil)

            val commonArgs =
              beautifyArgs ++
                commentsArgs ++
                compressArgs ++
                configFileArgs ++
                defineArgs ++
                ecmaArgs ++
                ie8Args ++
                keepClassnamesArgs ++
                keepFnamesArgs ++
                mangleArgs ++
                nameCacheArgs ++
                parseArgs ++
                safari10Args ++
                selfArgs ++
                timingsArgs ++
                toplevelArgs ++
                verboseArgs ++
                warnArgs ++
                wrapArgs

            def executeUglify(args: Seq[String]) = monix.eval.Task {

              SbtJsTask.executeJs(
                stateValue.copy(),
                engineTypeInUglifyValue,
                commandInUglifyValue,
                nodeModulePaths,
                uglifyjsShell,
                args: Seq[String],
                timeout
              )
            }


            val resultObservable: Observable[(UglifyOpGrouping, OpResult)] = Observable.fromIterable(
              modifiedGroupings
                .sortBy(_.inputFiles.map(_._1.length()).sum)
                .reverse
            ).map { grouping =>
              val inputFiles = grouping.inputFiles.map(_._1)
              val inputFileArgs = inputFiles.map(_.getPath)

              val outputFile = buildDirValue / grouping.outputFile
              IO.createDirectory(outputFile.getParentFile)
              val outputFileArgs = Seq("--output", outputFile.getPath)

              val (outputMapFile, outputMapFileArgs) = if (grouping.outputMapFile.isDefined) {
                val outputMapFile = buildDirValue / grouping.outputMapFile.get
                IO.createDirectory(outputMapFile.getParentFile)

                val outputMapFileOptionsBase = Seq(
                  s"base='${outputMapFile.getParentFile}'",
                  s"url='${outputMapFile.getName}'",
                  s"filename='${outputFile.getName}'"
                )

                val outputMapFileOptions =
                  if (grouping.inputMapFile.isDefined) {
                    val inputMapFile = grouping.inputMapFile.get._1
                    outputMapFileOptionsBase :+ s"content='${inputMapFile.getPath}'"
                  } else {
                    outputMapFileOptionsBase
                  }

                (Some(outputMapFile), Seq("--source-map", outputMapFileOptions.mkString(",")))
              } else {
                (None, Nil)
              }

              val args =
                outputFileArgs ++
                  inputFileArgs ++
                  outputMapFileArgs ++
                  commonArgs


              executeUglify(args).map { result =>
                val success = result.headOption.fold(true)(_ => false)
                grouping -> (
                  if (success)
                    OpSuccess(inputFiles.toSet, Set(outputFile) ++ outputMapFile)
                  else
                    OpFailure)
              }
            }.mergeMap(task => Observable.fromTask(task))

            val uglifyPool = monix.execution.Scheduler.computation(
              parallelism = java.lang.Runtime.getRuntime.availableProcessors
            )
            val result = Await.result(
              resultObservable.toListL.runAsync(uglifyPool),
              timeout * modifiedGroupings.size
            )

            (result.toMap, ())
          } else {
            (Map.empty, ())
          }
      }

      (mappings.toSet ++ outputFiles.pair(relativeTo(buildDirValue))).toSeq
    }
  }
}
