sbt-uglify-es
=============

An sbt-web plugin to perform [uglify-es optimization](https://github.com/mishoo/UglifyJS2/tree/harmony) on the asset pipeline.

Usage
-----
**This plugin is not published yet. So you have to clone this repository and execute `publishLocal`.**

```sh
git clone git@github.com:akiomik/sbt-uglify-es.git
cd sbt-uglify-es
sbt '^ publishLocal'
```

To use this plugin, use the addSbtPlugin command within your project's `plugins.sbt` file:

```scala
addSbtPlugin("com.github.akiomik" % "sbt-uglify-es" % "1.0.0-SNAPSHOT")
```

Your project's build file also needs to enable sbt-web plugins. For example, with build.sbt:

```scala
lazy val root = (project in file(".")).enablePlugins(SbtWeb)
```

As with all sbt-web asset pipeline plugins you must declare their order of execution:

```scala
pipelineStages := Seq(uglify)
```

A standard build profile for the Uglify optimizer is provided which will mangle variables for obfuscation and
compression. Each input `.js` file found in your assets folders will have a corresponding `.min.js` file and source maps will also be generated.

## includeFilter

If you wish to limit or extend what is uglified then you can use filters:
```scala
includeFilter in uglify := GlobFilter("myjs/*.js"),
```
...where the above will include only those files under the `myjs` folder.

The sbt `excludeFilter` is also available to the `uglify` scope and defaults to excluding the public folder and extracted Webjars.

## uglifyOps

If you wish to change how files are mapped from input to output, you can change the `uglifyOps` setting to point at another grouping.

The default ops takes a source file and produces minified file and source map:
```scala
uglifyOps := UglifyOps.singleFileWithSourceMapOut
```

This ops takes a source file and produces minified file only (no source map):
```scala
uglifyOps := UglifyOps.singleFile
```

This ops takes a source file and source map and produces minified file and combined source map. Your includeFilter must include source map files for this to work:
```scala
uglifyOps := UglifyOps.singleFileWithSourceMapInAndOut
```

## Settings
You are able to use and/or customize settings already made, and add your own. Here are a list of relevant settings and
their meanings (please refer to the [uglify-es documentation](https://github.com/mishoo/UglifyJS2/tree/harmony) for details on the
options):

Option                  | Description                                                                                   | Default
------------------------|-----------------------------------------------------------------------------------------------|----------
uglifyBeautify          | Enables beautify.                                                                             | `true`
uglifyBeautifyOptions   | Options for beautify such as beautify, preamble etc.                                          | `Nil`
uglifyComments          | Specifies comments handling.                                                                  | `None`
uglifyCompress          | Enables compression. Set true to compress.                                                    | `true`
uglifyCompressOptions   | A sequence of options for compression such as pure_funcs etc.                      						| `Nil`
uglifyConfigFile        | Read `minify()` options from JSON file.                                                       | `None`
uglifyDefine            | Define globals.                                                                               | `None`
uglifyEcma              | Specifies ECMAScript release.                                                                 | `None`
uglifyIe8               | Supports non-standard Internet Explorer 8.                                                    | `false`
uglifyKeepClassnames    | Does not mangle/drop class names.                                                             | `false`
uglifyKeepFnames        | Does not mangle/drop function names.                                                          | `false`
uglifyMangle            | Enables name mangling.                                                                        | `true`
uglifyMangleOptions     | Options for mangling such as builtins, debug etc.                                             | `Nil`
uglifyNameCache         | Specifies a file to hold mangled name mappings.                                               | `None`
uglifyParse             | Specifies parser options such as acorn, bare_returns etc.                                     | `Nil`
uglifySafari10          | Supports non-standard Safari 10/11.                                                           | `false`
uglifySelf              | Builds UglifyJS as a library.                                                                 | `false`
uglifyTimings           | Displays operations run time on STDERR.                                                       | `false`
uglifyToplevel          | Compresses and/or mangles variables in top level scope.                                       | `false`
uglifyVerbose           | Prints diagnostic messages.                                                                   | `false`
uglifyWarn              | Prints warning messages.                                                                      | `false`
uglifyWrap              | Embed everything in a big function.                                                           | `None`
uglifyOps               | A function defining how to combine input files into output files.                             | `UglifyOps.singleFileWithSourceMapOut`

The plugin is built on top of [JavaScript Engine](https://github.com/typesafehub/js-engine) which supports different JavaScript runtimes.

## Testing

```sh
sbt '^ scripted'
```

&copy; Typesafe Inc., 2014
&copy; Akiomi KAMAKURA, 2018
