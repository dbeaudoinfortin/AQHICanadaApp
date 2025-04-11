# AQHI Canada Android App <img src="https://github.com/user-attachments/assets/2aa9f5e2-b4b7-4dc5-a3df-0d04fb6171a7" height="35"/>

Android app and widgets for displaying Canadian Air Quality Health Index (AQHI) conditions, forecast and historical readings.

## Main App

<p align="center">
  <img src="https://github.com/user-attachments/assets/f79e5a43-eb0f-4f45-a2b8-96435382de78" width="400" />
  <img src="https://github.com/user-attachments/assets/9ee88b49-10e0-45fe-89f5-d128f8934a15" width="400" />
  <img src="https://github.com/user-attachments/assets/159ce2f9-c024-4efe-b952-3ecdc3d397ea" width="400" />
  <img src="https://github.com/user-attachments/assets/19365e74-b855-40aa-99f3-fbe53e3ecc8c" width="400" />

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

## Typical AQHI

The typical AQHI is calculated as the median hourly AQHI for the current hour of the day and current week of the year, based on data from your selected location. This calculation uses a 10-year period from 2014 to 2023, where at least 5 years of data are available for that location.

These values were generated using my [NAPS Data Analysis Toolbox](https://github.com/dbeaudoinfortin/NAPSDataAnalysis), which sources raw data from the [National Air Pollution Surveillance (NAPS)](https://data-donnees.az.ec.gc.ca/data/air/monitor/national-air-pollution-surveillance-naps-program/) program, part of Environment and Climate Change Canada. I calculated the tables for the median AQHI from the raw concentration readings of the 3 constituent pollutants (O3, NO2, & PM2.5) for all roughly 300 current active NAPS sites. These sites are then matched to the correct GeoMet stations using a bit of fuzzy logic with the latitude and longitude coordinates that both data sets provide. The formula for calculating AQHI is the following:

![AQHI Formula](https://github.com/user-attachments/assets/c5da4175-0a3b-4902-8c6d-15b99fc6a67c)

## Map Data

The map used for viewing and selecting locations is stored entirely on-device, as I do not want to send your location off-device. The base map data comes from [CBCT3978](https://maps-cartes.services.geo.ca/server2_serveur2/rest/services/BaseMaps/CBCT3978/MapServer), which is freely provided by Natural Resources Canada under the terms of the [Open Government Licence](https://open.canada.ca/en/open-government-licence-canada). 

The map uses the Lambert Conformal Conic projection, which minimizes distortion across Canada. I cleaned up visible JPEG compression artifacts using a neural filter, then manually stitched together the tiles into a single 1-gigapixel image (35,000 × 30,000 pixels).

This high-resolution map was then tiled into 8 zoom levels and compressed in WebP format. The result is a folder structure containing approximately 21,500 files, taking up only about 160MB of space. The map is diplayed in the app using [Pierre Laurence's MapView library](https://github.com/p-lr/MapView). Check out his project!

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
