sbt-nifi-nar plugin
========

A sbt plugin for creating NiFi Archive bundles to support the classloader isolation model of NiFi.
Many functionality is from project [sbt-pack](https://github.com/xerial/sbt-pack) and [nifi-maven](https://github.com/apache/nifi-maven)

### Features

- `sbt nar` creates a distributable package in `target/nar` folder.
  - All dependent jars including scala-library.jar are collected in `target/nar/META-INF/bundled-dependencies` folder.
  - Create `target/nar/META-INF/MANIFEST.MF` with necessary properties
- `sbt narArchive` generates `nar` archive that is ready to distribute.
  - The archive name is `target/{project name}-{version}.nar`

### Usage

Add `sbt-nifi-nar` plugin to your sbt configuration:

**project/plugins.sbt**

```scala
addSbtPlugin("sk.vub" % "sbt-nifi-nar" % "(version)")
```

#### Minimum configuration

**build.sbt**
```
// [Required] Enable plugin
enablePlugins(NarPlugin)
// [Required] Nifi version
nifiVersion := "1.9.2"

// [Optional] check trait `NarKeys`
```

Now you can use `sbt nar` command in your project.

### TODO
  * dependency graph
  * support extensions
  * add more information in MANIFEST.MF

### Test
To test sbt-nifi-nar plugin, run

    $ sbt scripted

### License

Except as otherwise noted this software is licensed under the
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
