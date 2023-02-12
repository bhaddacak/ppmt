# Pāli Platform Meditation Timer (PPMT Simple)
A minimalist but usable med-timer. (Simple Player version)

## Notes on the implementation
After experimenting for a while, finally I come up with the right solution. First, we have to make a silent track, but not totally silent. (Playing a totally silent track has unpredictable results. Some phones do not play it through the end, for example.) And second, we have to play it in a foreground service.

In this experiment, I use a simple technique: using multiple silent tracks with various length. The tracks have a bell at the end to make them not totally silent. This simple strategy is reliable, but at the expense of various options. Hence, I call this *Simple* Player version.

Formerly in the main implementation of PPMT, I used only one track of 1-minute silence with a melody at the end (but the melody will be never heard). This method needs CountDownTimer to cut and combine the silence pieces. The result is good but not perfect. Sometimes the app behaves oddly, due to the CountDownTimer, I suppose.

Finally, I discard the use of one small silent track and apply the *simple* method to the *main* branch, but silent tracks with a click are used instead to allow more options on alarm sounds. Now both versions can run as expected while the phone is asleep, as long as the app's restriction is removed or its running in background is allowed (see the app's power option in your phone). This should be the case in most Android devices as well.

That is to say, now `PPMT` and `PPMT Simple` are not different in terms of implementation strategy. They just use different silent tracks, and the *main* version has more options to choose. Both can be installed and used side by side in the same phone.

For the general idea and how to build the project, please see [README.md](https://github.com/bhaddacak/ppmt/tree/main#readme) of the *main* branch. 

## Useful links
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
