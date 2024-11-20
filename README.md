Simpletask (fork)
=================

This [app](https://github.com/willemw12/simpletask-android) is a fork of the deprecated [Simpletask](https://github.com/mpcjanssen/simpletask-android) Android app.

An APK file is available from the [releases](https://github.com/willemw12/simpletask-android/releases) page.

Only the Cloudless version is supported and mainly only core features are supported.

The original text follows below.


Simpletask
==========

[![CircleCI](https://circleci.com/gh/mpcjanssen/simpletask-android.svg?style=svg)](https://circleci.com/gh/mpcjanssen/simpletask-android)

Simpletask is a simple task manager for Android, based on the brilliant [todo.txt](http://todotxt.com) format by [Gina Trapani](http://ginatrapani.org/).

  * [Documentation](#documentation)
  * [Translation](#translation)
  * [Cloudless Version](#cloudless-version)
  * [Nextcloud Version](#nextcloud-version)
  * [WebDAV Version](#webdav-version)
  * [Dropbox](#dropbox)

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

## Nextcloud Version

<a href="https://f-droid.org/repository/browse/?fdid=nl.mpcjanssen.simpletask.nextcloud" target="_blank">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80"/></a>


Nextcloud stores tasks in a todo.txt file using Nextcloud.

## WebDAV Version

<a href="https://f-droid.org/repository/browse/?fdid=nl.mpcjanssen.simpletask.webdav" target="_blank">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80"/></a>


Nextcloud stores tasks in a todo.txt file on any WebDAV server.

## Dropbox

The Dropbox version is not available from F-Droid. You can download builds of the different versions from: https://mpcjanssen.nl/artifacts/
