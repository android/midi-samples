MIDI Samples
============

This repository contains a set of individual Android Studio projects to help you write apps using
MIDI APIs.

Android MIDI 2.0 API samples that run on Android T or later. These samples will run only with MIDI
2.0 USB devices at the moment.

These are primarily samples for developers to learn from, but may also be useful for OEMs to test
MIDI on new devices.


Samples
-------

* **[MidiUmpScope](MidiUmpScope)** (Kotlin) - Displays MIDI Messages on the screen. This also acts
  like a basic synth. This program first connects to the device via MIDI-CI, and then reacts to MIDI
  2.0 Note On, Note Off, Pitch Bend, and Program Change messages.

* **[MidiUmpKeyboard](MidiUmpKeyboard)** (Kotlin) - Displays a simple on-screen music keyboard. This
  program first connects to the device via MIDI-CI, and then writes MIDI 2.0 Note On, Note Off,
  Program Change, and Per Note Pitch Bend messages.

See the README files in the App directories for more information.


Library
-------

The [MidiTools](MidiTools) folder contains general purpose MIDI classes that are used by the other
samples.
