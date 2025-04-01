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

<p align="center">
  <img src="https://github.com/user-attachments/assets/a27f71ca-6110-43d9-9bfe-3b1914a128e6" />
  <img src="https://github.com/user-attachments/assets/8b34092d-ea5a-4c4e-b08a-ac57d15c4eee" />
</p>

<p align="center">
  <img src="https://github.com/user-attachments/assets/7e22940d-4ab8-4e21-9f03-9f397eb8dbc3" width="400"/>
  <img src="https://github.com/user-attachments/assets/742c6ad0-ecdb-46c8-81be-d08509e88693" width="400"/>
</p>

Widgets are configurable: you can set the background transparency as well as the light/dark mode. A preview of the widget is shown as you configure it.

<p align="center">
  <img src="https://github.com/user-attachments/assets/e3b143b1-a41f-4182-b6f3-8d8c42e5924f" width="400"/>
  <img src="https://github.com/user-attachments/assets/1d7829cb-10e0-4435-bdda-74062d95af6e" width="400"/>
</p>

> [!TIP]
> Setting light/dark mode to automatic will make the widget switch automatically with the system preference.

## How it works
AQHI Data is pulled from Environment and Climate Change Canada's public API using the closest active monitoring station to your current location. The station definitions, current location, and current AQHI readings are all cached to prevent excessive calls to the API. Data is shared between the main app and the widgets.

The rendering of heat maps is entirely written from scratch by myself. My [GitHub project](https://github.com/dbeaudoinfortin/HeatMaps) contains rendering libraries for both Android and Java2D. Check it out if you want to add beautiful, customizable heat maps to you own Java project.

## About AQHI
The Canadian Air Quality Health Index (AQHI) is a made-in-Canada scale developed by Environment and Climate Change Canada (ECCC) to provide real-time information about the health risks associated with air pollution. It combines measurements from ground-level ozone, fine particulate matter (PM2.5), and nitrogen dioxide into a single numerical value on a scale from 1 (low risk) to 10 (high risk). All values above 10 are assigned a reading of 10+, indicating a very high risk.

**Scale:**
- 1-3 Low health risk
- 4-6 Moderate health risk
- 7-10 High health risk
- 10+  Very high health risk

As per ECCC, the calculation of the AQHI may change over time to reflect new understanding associated with air pollution health effects. AQHI values are calculated automatically using observations from a network of air quality monitoring stations across Canada. These measurements are collected in real-time and are not verified prior to publication. Forecast and historical AQHI values are reported on an hourly basis.

If you are interested in the analysis of Canadian air quality data that has been validated, check out my [NAPS Data Analysis Project](https://github.com/dbeaudoinfortin/NAPSDataAnalysis) on GitHub.</p>

For more information about the AQHI, visit [ECCC's website](https://www.canada.ca/en/environment-climate-change/services/air-quality-health-index/about.html).

## Requirements
- This app was developed for and tested on Android 14. 

## Privacy Statement
This app is made by David Fortin, an individual - not a corporation. It does not collect or share any of your personal information or data.

Your device's location is used only to identify the closest air quality monitoring station, and only if you explicitly grant the app permission to use location services. Your location information stays exclusively on your device and is never transmitted or shared.
 
This app determines your nearest air quality monitoring station by comparing your location (locally, on your device) with a list of stations obtained from Environment and Climate Change Canada's (ECCC) GeoMet service. Your location data is never sent to ECCC or any other server.

You can learn more about ECCC's GeoMet service [here](https://eccc-msc.github.io/open-data/).


## Legal Stuff

Copyright (c) 2024 David Fortin

This software is provided by David Fortin under the MIT License, meaning you are free to use it however you want, as long as you include the original copyright notice (above) and license notice in any copy you make. You just can't hold me liable in case something goes wrong. License details can be read [here](https://github.com/dbeaudoinfortin/AQHICanadaApp?tab=MIT-1-ov-file)
