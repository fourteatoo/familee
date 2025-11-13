[![Clojars Project](https://img.shields.io/clojars/v/io.github.fourteatoo/familee.svg?include_prereleases)](https://clojars.org/io.github.fourteatoo/familee)
[![cljdoc badge](https://cljdoc.org/badge/io.github.fourteatoo/familee)](https://cljdoc.org/d/io.github.fourteatoo/familee)
[![CircleCI](https://dl.circleci.com/status-badge/img/gh/fourteatoo/familee/tree/main.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/fourteatoo/familee/tree/main)
[![Coverage Status](https://coveralls.io/repos/github/fourteatoo/familee/badge.svg)](https://coveralls.io/github/fourteatoo/familee)

# familee

Read, save and restore Google Family Link restrictions.

Provided you use Firefox, this program can access your Google Family
Link configuration and modify it, if necessary.  Currently it can only
change the app permissions, which is all I personally need.

It takes inspiration from a smiliar Python project
<https://github.com/tducret/familylink>.


## Installation

Compile

    $ lein uberjar

then copy the jar file somewhere you can find again

    $ cp target/uberjar/familee-<version>-standalone.jar some/where/else

## Usage

    $ java -jar target/uberjar/familee-<version>-standalone.jar -h
	
will get you an usage string.  It is straightforward.

    $ java -jar target/familee-<version>-standalone.jar -s file.edn
	
dumps in `file.edn` your current app restrictions, for each supervised
user in your family nucleus.

You can then edit `file.edn` and apply the changes with

    $ java -jar target/familee-<version>-standalone.jar -r file.edn

You can also visualize the differences between your stored
configuration and the one active in the cloud

    $ java -jar target/familee-<version>-standalone.jar -d file.edn

which will result in something like:

```
User Junior

|                      :title |                       :package |    :active |  :stored |
|-----------------------------+--------------------------------+------------+----------|
| Spotify: Music and Podcasts |              com.spotify.music | :unlimited | :allowed |
|               Google Photos | com.google.android.apps.photos |   :allowed |       10 |
```

## Options

  -s, --save FILE       download current app restrictions status and save it into FILE
  -r, --restore FILE    restore app restrictions if different from FILE
  -d, --diff FILE       compare current restrictions with those in FILE
  -p, --print           print family and apps state
  -n, --dry-run         do not apply changes, just print them
  -v, --verbose         increase logging verbosity
  -h, --help            display program usage


## Examples

The EDN file saved by the program has the following structure:

```clojure
;; user id
{"12345678901234567890"
 {:name "Junior",                       ; user name
  ;; list of restricted applications
  :apps
  {"com.google.android.youtube"
   ;; Youtube for 20 minutes a day
   {:title "YouTube", :limit 20},
   "com.zeptolab.ctr.ads"
   ;; 15 minutes a day
   {:title "Cut the Rope", :limit 15},
   "com.mobisystems.office"
   {:title "MobiOffice: Word, Sheets, PDF", :limit :allowed},
   "com.gameloft.android.GloftDMKF"
   ;; not allowed at all, ever
   {:title "Disney M. K.", :limit :blocked},
   "com.duolingo"
   ;; unlimited use 24/7 regardless the daily screen time
   {:title "Duolinguo: Language Lessons", :limit :unlimited},
   "com.makewonder.wonder"
   {:title "Wonder for Dash & Dot Robots", :limit :allowed},
   "org.fdroid.fdroid"
   ;; zero minutes a day
   {:title "F-Droid", :limit 0},
   ;; allowed for as long as the daily screen time
   "com.google.android.apps.photos"
   {:title "Google Photos", :limit :allowed},
...
```

The `:limit` is the only thing that can be edited (and uploaded).
Things like the user `:name` and application `:title` are there only
for your convenience.  And no, I don't know the difference between 0
minutes a day and `:blocked`.

You don't need to keep track of all your apps; you can upload just a
subset.  The rest will be left untouched.

The following changes just the YouTube app, limiting it to 10 minutes
a day:

```clojure
{"12345678901234567890" {:apps {"com.google.android.youtube" {:limit 10}}}}
```

all the other apps will not be affected.


If you wish you can peek inside the raw configuration with the `-p`
option.

```
$ java -jar target/uberjar/familee-0.1.0-SNAPSHOT-standalone.jar -p
```
=>
```clojure
{"12345678901234567890"
 {:role "member",
  :profile
  {:display-name "Junior",
   :profile-image-url
   "https://lh3.googleusercontent.com/a-/SOMESTUFFHERE",
   :email "junior@gmail.com",
   :family-name "Smith",
   :given-name "Junior",
   :standard-gender "male",
   :birthday {:day 4, :month 2, :year 2020},
   :default-profile-image-url
   "https://lh3.googleusercontent.com/a/default-user"},
  :state "regular",
  :member-supervision-info
  {:is-supervised-member true, :is-guardian-linked-account false},
  :member-attributes
  {:has-griffin-policy true,
   :remove-from-family-after-opt-out-supervision true,
   :show-parental-password-reset true},
  :ui-customizations
  {:settings-group
   ["tabletSettingGroup"
    "playSettingGroup"
    "websitePermissionSettingGroup"
    "searchSettingGroup"
    "youtubeParentToolsSettingGroup"
    "a4kSettingGroup"
    "appsSettingGroup"
    "locationSettingGroup"
    "nativeKidEditSettingGroup"
    "googlePhotosSettingGroup"
    "locationConsentSettingGroup"
    "appActivitySettingGroup"
    "googleActivitySettingGroup"
    "unsupervisedSignInSettingGroup"
    "thirdPartySettingGroup"
    "resetScreenTimeLimitsGroup"
    "privacySettingGroup"],
   :privacy-policy-url "https://policies.google.com/privacy",
   :supervised-user-type "account"},
  :apps
  [{:iap-support-status "iapSupported",
    :supervision-setting
    {:hidden false,
     :hidden-set-explicitly true,
     :always-allowed-app-info
     {:always-allowed-state "alwaysAllowedStateEnabled"}},
    :enforced-enabled-status "statusUnknown",
    :supervision-capabilities
    ["capabilityAlwaysAllowApp"
     "capabilityBlock"
     "capabilityUsageLimit"],
    :app-source "googlePlay",
    :icon-url
    "https://lh3.googleusercontent.com/48wwD4kfFSStoxwuwCIu6RdM2IeZmZKfb1ZeQkga0qEf1JKsiD-hK3Qf8qvxHL09lQ",
    :title "Amazon Kindle",
    :install-time-millis "1733832804041",
    :ad-support-status "adsSupported",
    :device-ids ["blahblahblah"],
    :package-name "com.amazon.kindle"}
   {:iap-support-status "iapSupported",
    :supervision-setting
    {:hidden false,
     :hidden-set-explicitly true,
     :usage-limit {:daily-usage-limit-mins 15, :enabled true}},
    :enforced-enabled-status "statusUnknown",
...
```

which dumps on your screen a lot of info for your own curiosity.


## The Library

The jar can be used as a library and included in your own project.
All the authentication aspects are handled automatically for you.
Consider, though, that the code looks for your access tokens in your
default Firefox cookies.  At the moment no other browser is supported.


With Leiningen:

```clojure
[io.github.fourteatoo/familee "LATEST"]
```

The namaspace you want to use is

```clojure
(require '[fourteatoo.familee.api :as fam])
```

To see your family composition you just

```clojure
(fam/get-family-members)
```

to see the supervised members only

```clojure
(fam/get-supervised-members)
````

to get the apps restrictions

```clojure
(fam/get-apps-usage "your kid's user id")
```

You need the ID, not the name.  You fiund the user id in the maps
returned by `fam/get-family-members` or `fam/get-supervised-members`.

For instance, for all of your kids you would

```clojure
(map (comp fam/get-apps-usage :user-id)
     (fam/get-supervised-members))
```

to change an application limit

```clojure
(fam/update-restrictions user-id package limit)
```

You find the `user-id` inside the map returned by
`fam/get-supervised-members`.  The `package` can be found in the maps
returned by `fam/get-apps-usage`.  The limit can be any of:
`:allowed`, `:unlimited`, `:blocked` or an integer which is the time
in minutes.

Examples:

```clojure
;; limit Spotify by the daily screen time
(fam/update-restrictions "87238265899123764" "com.spotify.music" :allowed)
;; allow the calculator any time of the day
(fam/update-restrictions "48723826589912376" "com.google.android.calculator" :unlimited)
;; limit YouTube
(fam/update-restrictions "64872382658991237" "com.google.android.youtube" 15)
;; block TikTok
(fam/update-restrictions "64872382658991237" "com.zhiliaoapp.musically" :blocked)
```


### Bugs

Expected.


## License

Copyright © 2025 Walter C. Pelissero

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
