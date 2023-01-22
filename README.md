# Pāli Platform Meditation Timer (PPMT)
A minimalist but usable med-timer.

## Motivation for development

This is a pilot project used for my study of Android application development. One main reason of this study is to see the possibility of porting `Pāli Platform`, at least partly, to the Android platform. (There is no certain promise to do so at this stage.) Another personal reason is I want to make a med-timer, an application I use everyday, that satisfies my need.

I had been used med-timer apps since I had old Androids (2.3 or so). Several are good ones. But in new Androids, none of those old apps works if the screen is not kept awake. Most new apps are bloated, yet very few work to my expectation. That made me make my own audio tracks for meditation (mostly silence with bell sounds at the end) and play them with audio players. This method always works in most devices. So I have been forgotten about med-timer apps for years. When I started to learn Android application development, I was tempted to deal with med-timer first. So here I am.

Another key factor that shapes the project is my environment of development. I insist to use only core library, no supports, and without Android Studio. To make it more difficult, I prefer to use my beloved 32-bit Linux laptop. Such a setting is impossible in Google's point of view, because 32-bit SDK and Android Studio are no longer supported.

Fortunately and thanks to the Debian Android Tools Team, using any 32-bit Debian-based Linux is now possible to write Android applications, with some limitations though. At least, I can learn to write Android apps in my familiar way. That is good to understand how things really work.

The main problem of med-timer apps on Android is they get killed or suppressed by the system when the device goes asleep, under the power management scheme. The strictness varies from brand to brand. For example, none of med-timers, as far as I found, cannot survive my Oppo F7's (ColorOS 7.1/Android 10) aggressive power management when the apps run in sleep-mode, whereas on generic Androids some apps work fine.

I experimented with several implementations to tackle this problem. I see this as a problem because it makes no sense that while you meditate and you have to keep the device's screen on, just sounds are desirable. Using CountDownTimer as background service fails, as well as, the foreground version of it which is a little better but unpredictable. Using AlarmManager fails sometimes (I still have no clue why it fails or works in some settings).

Finally, I apply my own method that I use with my meditation tracks. So, the app is in fact a media player in essence, but programmed for this specific use.

## Android application development in frugal environment

Now I will explain how I built the project using my 32-bit Linux, and I will mention 64-bit settings briefly at the end. I think those who use Windows and macOS machines have to follow the official way of doing things, e.g., using Android Studio and its ecosystem. So, I hold that non-Linux operating systems are irrelevant to the project.

### Using Debian-based GNU/Linux

If you use a Debian-based OS, you can use [Android tools in Debian](https://wiki.debian.org/AndroidTools) to build Android applications. Even if you can certainly use Android Studio and/or Gradle to build a project with Debian's tools, the combination looks really odd to me, and we will not use these.

To build apps on Debian-based Linux, you just need *Android SDK*, an *Android Platform*, and *GNU Make*. Another tool unnecessary but really helpful in developing process is `adb`. This can help install and debug the app. So, the installation looks like this:

```
$ sudo apt-get install android-sdk android-sdk-platform-23 make adb
```

For now, only platform-23 or Android 6.0 platform (API level 23) is available, even the build-tools is now version 29.0.3. It might be possible if we use a newer platform according to the build-tools (I have never tried this yet). In that case, you can install other versions of platform by yourself, either by Debian's helper installer or by `sdkmanager` (described below).

Another essential tool is Java SDK (version 7 or newer). Using Kotlin is still impractical in our setting. By using Debian's repository the JDK can be installed by this command (I use OpenJDK 17):

```
$ sudo apt-get install openjdk-17-jdk
```

Now you can build the app by going to the project's root directory and simply enter:

```
$ make
```

In the first build, you will asked to enter your personal information to generate the key file (named `keystore.jks`). Then the app will be signed with this key, so it can be installed or distributed. You have to keep this key file throughout the lifetime of the project's development. If you generate a new key, even with the same information, and sign with it, Android will see the app as a different one. You cannot reinstall or update the app in this case, because the app use the same package name but has different signature.

If you want to clean the built result, enter `$ make clean`. This will not delete the key file. If you really want to remove it, do it manually. And if you want to just compile the Java code, enter `$ make compile`. These are all options you can do with `make` in this project. If you want to modify the project, you may need to edit the `Makefile`.

The end-product of the build process is **`ppmt-1.0.apk`**. You only can test the app by install it to the real device. The best way to do this is via `adb`. First, you have to enable *Development options* by going to *About phone* and tab *Build number* seven times until the options appear. Go to *Development options*, turn it on and enable *USB debugging*.

Once you connect your device to the computer, enter this to see whether it is seen by the system:

```
$ adb devices
```

If you see the device ID, it is connected. Now you can install the app by typing this:

```
$ adb install -r ppmt-1.0.apk
```

With the flag `-r`, the installation will replace the existing with the current one. It has no effect in the first installation. Now you can see the app in your device, and you can test it. To uninstall the app, you need package name. So, in this case, you have to enter this:

```
$ adb uninstall paliplatform.tools.ppmt
```

That is all you need to know to set up the environment, to build the app, and to test the result. However, when you start to edit the project, you have to know how to find out when things go wrong.

### Debugging

The handiest way the check states of variables while the program is running is using `android.widget.Toast`. This will pop up a text at the bottom of the screen. Here is an example:

```
Toast.makeText(MainActivity.this, someVar, Toast.LENGTH_SHORT).show();
```

Using `Toast` can be awkward in some situations. A more viable way of debugging is `logcat`. When the app is closed unexpectedly, and you have no clue what is going on, you can check the error messages using `logcat`. Here is the way I do it:

```
$ adb logcat -d AndroidRuntime:E *:S | grep '01-19 13:2'
```

Because `logcat` can overwhelm you with many irrelevant messages, we have to filter them properly. In this case, we focus only on tag `AndroidRuntime` with `Error` (E) status, and silence the rest `(*:S)`. Then we select only messages with a recent timestamp with `grep`. Do not use the time I show here, use your own time. With a relevant timestamp, you can see what went wrong when the program crashed. Please check the time on your device not the computer. We use flag `-d` to stop `logcat` showing new messages, so we see only what has happened. 

For a more sophisticated way of debugging, you can make your own tag and insert your messages into the log system by using `android.util.Log`. In the class `Log`, we have 5 methods, i.e., `Log.v(), Log.d(), Log.i(), Log.w(), and Log.e()`, according to status: `VERBOSE, DEBUG, INFO, WARN, and ERROR` respectively. I use mostly `Log.d()`. 

To use the methods, you have to name your tag properly so that your can filter it afterwards. Here is an example to check the variable `i` in your code:

```
private static final String TAG = "MyAwesomeActivity";
...
Log.d(TAG, "index=" + i);
```

Once you add the logging in your code, you can check it when the app has run by entering this:

```
$ adb logcat -d MyAwesomeActivity:D *:S
```
If you run the app several times, filtering further with a proper timestamp (see above) can be helpful. When you finish your debugging process and want to make a release, do not forget to remove the logging from your code. 

With all these simple tools mentioned, you can develop any kind of Android application that the core library (the platform) allows you.

(It is possible to include the old support libraries, but not the newer Jetpack libraries, to the project to have slicker UI. We have to use another `Makefile` (not provided here) in this case. I see this unnecessary. It just makes the product bigger but with the same functionality. So, I make no effort to show an example of this here. For those who need a guideline, see links below.)

### Notes on 64-bit machines

If you use a 64-bit computer, you have all possibilities of developing applications. You can use Android Studio and all of its tools, or you can use just Gradle to build an Android project with your favorite IDE/editor. Most people go that way. 

For 64-bit Debian-based OS, you can do everything as described from the beginning. However, for any 64-bit Linux distribution, you can set up the environment likewise by using the SDK from Google. Here comes the tool called `sdkmanager`.

As a part of Android tools, `sdkmanager` is used for downloading things from Google. So, at the very start, we just need this. If you are lucky, your repository may provide this package. So, you can install it by this:

```
$ sudo apt-get install sdkmanager
```

If not, you can use this version: [sdkmanager](https://pypi.org/project/sdkmanager/). This program uses python to run. If you use this, download the package, unpack it, go to its directory and replace `sdkmanager` below with `./sdkmanager.py`. Once you have the tool installed, you can check the availability by this command (the Internet connection needed):

```
$ sdkmanager --list
```

This will list all the things you can download. To imitate what we have done previously, you can enter these commands to install our necessities:

```
$ sudo sdkmanager --install "build-tools;29.0.3"
$ sudo sdkmanager --install "platforms;android-23"
```

When you use `sdkmanager`, the packages downloaded will be installed to `/opt/android-sdk`. To make use of these, you have to edit our `Makefile` accordingly. So, you can build the project as described above. I suppose that most Linux users know this well, so I skip the detail.

## Useful links
* [Building an Android App from the Command Line](https://www.hanshq.net/command-line-android.html)
* [The original hello-world example](https://gitlab.com/Matrixcoffee/hello-world-debian-android)
* [Android SDK Documentation up to API level 24](http://dl.google.com/android/repository/docs-24_r01.zip)
* [Support v.7 Demos](https://drive.google.com/file/d/1ZpfiUJQhE2GPIPrXzdj7cz-65LEKkYhk/view?usp=sharing)
* [Support v.13 Demos](https://drive.google.com/file/d/1MxiRnpKAsz_d5rQ2rOt9W4ppTFmUJpL6/view?usp=sharing)
* [Pāli Platform](http://paliplatform.blogspot.com)

## License
```
Copyright (C) 2023 J.R. Bhaddacak

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
