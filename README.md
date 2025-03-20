# AQHI Canada Android App <img src="https://github.com/user-attachments/assets/2aa9f5e2-b4b7-4dc5-a3df-0d04fb6171a7" height="35"/>

Android app and widgets for displaying Canadian Air Quality Health Index (AQHI) conditions, forecast and historical readings.

## Main App

<p align="center">
  <img src="https://github.com/user-attachments/assets/f79e5a43-eb0f-4f45-a2b8-96435382de78" width="400" />
  <img src="https://github.com/user-attachments/assets/9ee88b49-10e0-45fe-89f5-d128f8934a15" width="400" />
  <img src="https://github.com/user-attachments/assets/159ce2f9-c024-4efe-b952-3ecdc3d397ea" width="400" />
</p>

<p align="center">
  <img src="https://github.com/user-attachments/assets/0c14c8de-2ecb-4d81-b82e-a9a400ea8476" width="400" />
  <img src="https://github.com/user-attachments/assets/7dc414ad-5137-485f-bb24-67abbf16ba4d" width="400" />
  <img src="https://github.com/user-attachments/assets/debaab1a-7bc8-4684-ab10-60d5af95e0b4" width="400" />
</p>

> [!NOTE]  
> Both light mode and dark mode are supported and will switch automatically with the system preference.

> [!TIP]
> Tapping on the heat maps will reveal the raw data values!

## Widgets

Two widgets are provided: a small widget that displays the most recent AQHI reading at your location, and a larger, wider widget that shows the AQHI reading on a relative scale.

![widget_small_preview](https://github.com/user-attachments/assets/fbf4306c-1aaa-408e-9ac9-44a6876de35a)
![widget_large_preview_2](https://github.com/user-attachments/assets/e3dbb23d-6a0d-44bb-a27d-07b3fa2a09d3)

Widgets are configurable: you can set the background transparency as well the Light/Dark mode.

> [!TIP]
> Setting light/dark mode to automatic will make the widget switch automatically with the system preference.

## How it works

AQHI Data is pulled from Environment and Climate Change Canada's public API using the closest active monitoring station to your current location. The station definitions, current location, and current AQHI readings are all cached to prevent excessive calls to the API. Data is shared between the main app and the widgets.

The rendering of heat maps is entirely written from scratch by myself. My [GitHub project](https://github.com/dbeaudoinfortin/HeatMaps) rendering libraries for both Android and Java2D. Check it out if you want to add beautiful, customizable heat maps to you own Java project.

## Requirements
- This app was developed for and tested on Android 14. 

## Legal Stuff

Copyright (c) 2024 David Fortin

This software is provided by David Fortin under the MIT License, meaning you are free to use it however you want, as long as you include the original copyright notice (above) and license notice in any copy you make. You just can't hold me liable in case something goes wrong. License details can be read [here](https://github.com/dbeaudoinfortin/AQHICanadaApp?tab=MIT-1-ov-file)
