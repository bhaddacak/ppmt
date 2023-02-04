# Pāli Platform Meditation Timer (PPMT Simple)
A minimalist but usable med-timer. (Simple Player version)

## Notes on the implementation
After experimenting for a while, finally I come up with the right solution. First, we have to make a silent track, but not totally silent. (Playing a totally silent track has unpredictable results. Some phones do not play it through the end, for example.) And second, we have to play it in a foreground service.

In the main implementation of PPMT, I use only one track of 1-minute silence with a melody at the end (but the melody will be never heard). Using a small silence piece allows us to have any of interval times. Also we can have options on the alarm sounds. This makes the main app versatile. It can run while asleep, as long as the app's restriction is removed or its running in background is allowed. Yet, sometimes in my Oppo it behaves oddly.

In this implementation, I use a simpler technique: using multiple silent tracks with various length. The tracks have a bell at the end to make them not totally silent. This simple strategy is more reliable, but at the expense of various options. Hence, I call this 'Simple' Player version. This can be a good alternative to the main one, for those who want just a simple thing that works.

For the general idea and how to build the project, please see the `README.md` of the `main` branch. 

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
