# sbt-jib

This project tries to make a sbt plugin for the awesome [jib](https://github.com/GoogleContainerTools/jib) project from google.

currently to build it yourself you need to get the submodule and run the jib patch in the submodule directory

## Developing

Currently to develop jib you first need to clone the project and then you actually need to do the following to fix any build issue's:

    cd jib && git apply ../jib.patch

