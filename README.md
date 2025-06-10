Simpletask Google Drive (fork)
==============================

This [app](https://github.com/fleapower/simpletask-android) is a fork of [Simpletask](https://github.com/willemw12/simpletask-android) was forked from the deprecated [Simpletask](https://github.com/mpcjanssen/simpletask-android) Android app.

This version supports Google Drive sync, but you will need to set up a Google project and compile it yourself (detailed instructions will be forthcoming).

Applicable portions of the original text follow below.


Simpletask
==========

[![CircleCI](https://circleci.com/gh/mpcjanssen/simpletask-android.svg?style=svg)](https://circleci.com/gh/mpcjanssen/simpletask-android)

Simpletask is a simple task manager for Android, based on the brilliant [todo.txt](http://todotxt.com) format by [Gina Trapani](http://ginatrapani.org/).

  * [Documentation](#documentation)
  * [Translation](#translation)
  * [Cloudless Version](#cloudless-version)

## Documentation

See documentation [here](./app/src/main/assets/index.en.md).

## Translation

Simpletask is translated using weblate: <a href="https://hosted.weblate.org/engage/simpletask/?utm_source=widget">
                                        <img src="https://hosted.weblate.org/widgets/simpletask/-/svg-badge.svg" alt="Translation status" />
                                        </a>

## Cloudless Version

<a href="https://f-droid.org/repository/browse/?fdid=nl.mpcjanssen.simpletask" target="_blank">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80"/></a>


Cloudless stores tasks in a todo.txt file on the device. A separate app (such as Syncthing) can be used to Sync the file.


Because the todo.txt file could be anywhere on the device to allow sync by a different app, the Cloudless version
requests full storage access. This is unfortunately the only way to make common use case work while staying sane.
